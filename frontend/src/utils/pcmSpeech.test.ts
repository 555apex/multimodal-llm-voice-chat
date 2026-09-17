import { afterEach, expect, it, vi } from 'vitest'
import { receiveSpeech, PcmSpeechPlayer } from './pcmSpeech'
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })
const frame = (name: string, data: unknown) => `event: ${name}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`
const start = frame('audio.start', { sampleRate: 24000, channels: 1, format: 's16le' })
function response(text: string) {
  return new Response(new ReadableStream({ start(controller) {
    for (const char of text) controller.enqueue(new TextEncoder().encode(char))
    controller.close()
  } }))
}
it('accepts CRLF split across chunks and verifies terminal chunk count', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(start + frame('audio.chunk', { sequence: 0, pcm: 'AAA=' }) + frame('audio.completed', { chunks: 1 }))))
  const push = vi.fn()
  await receiveSpeech('测试', { push }, new AbortController().signal)
  expect(push).toHaveBeenCalledExactlyOnceWith('AAA=')
})
it('rejects EOF without completion and out-of-order audio', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(response(start + frame('audio.chunk', { sequence: 0, pcm: 'AAA=' })))
    .mockResolvedValueOnce(response(start + frame('audio.chunk', { sequence: 1, pcm: 'AAA=' })))
  vi.stubGlobal('fetch', fetch)
  await expect(receiveSpeech('测试', { push: vi.fn() }, new AbortController().signal)).rejects.toThrow()
  await expect(receiveSpeech('测试', { push: vi.fn() }, new AbortController().signal)).rejects.toThrow()
})

it('rejects audio appended after the terminal event', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(start
    + frame('audio.chunk', { sequence: 0, pcm: 'AAA=' })
    + frame('audio.completed', { chunks: 1 })
    + frame('audio.chunk', { sequence: 1, pcm: 'AAA=' }))))
  await expect(receiveSpeech('测试', { push: vi.fn() }, new AbortController().signal)).rejects.toThrow()
})

it('waits for the last rendered sample to reach the output clock before completing', async () => {
  vi.useFakeTimers()
  let audible = 1
  const close = vi.fn().mockResolvedValue(undefined)
  const node = { port: { onmessage: null as null | ((e: { data: unknown }) => void), postMessage: vi.fn() }, connect: vi.fn(), disconnect: vi.fn() }
  vi.stubGlobal('AudioContext', class {
    audioWorklet = { addModule: vi.fn().mockResolvedValue(undefined) }
    destination = {}
    state = 'running'
    currentTime = 1.3
    baseLatency = 0.02
    outputLatency = 0.1
    getOutputTimestamp() { return { contextTime: audible, performanceTime: 0 } }
    resume = vi.fn().mockResolvedValue(undefined)
    close = close
  })
  vi.stubGlobal('AudioWorkletNode', class { constructor() { return node } })
  const player = new PcmSpeechPlayer(vi.fn())
  await player.open()
  let completed = false
  void player.done.then(() => { completed = true })
  node.port.onmessage?.({ data: { type: 'drained', renderEndTime: 1.2 } })
  await vi.advanceTimersByTimeAsync(60)
  expect(completed).toBe(false)
  expect(close).not.toHaveBeenCalled()
  audible = 1.21
  await vi.advanceTimersByTimeAsync(15)
  expect(completed).toBe(true)
  expect(close).not.toHaveBeenCalled()
  player.close()
  expect(close).toHaveBeenCalledOnce()
})

it('cancelling during output drain cancels the timer and releases playback', async () => {
  vi.useFakeTimers()
  const node = { port: { onmessage: null as null | ((e: { data: unknown }) => void), postMessage: vi.fn() }, connect: vi.fn(), disconnect: vi.fn() }
  vi.stubGlobal('AudioContext', class {
    audioWorklet = { addModule: vi.fn().mockResolvedValue(undefined) }
    destination = {}; state = 'running'; currentTime = 0; baseLatency = 0.02; outputLatency = 0.1
    resume = vi.fn().mockResolvedValue(undefined); close = vi.fn().mockResolvedValue(undefined)
  })
  vi.stubGlobal('AudioWorkletNode', class { constructor() { return node } })
  const player = new PcmSpeechPlayer(vi.fn())
  await player.open()
  node.port.onmessage?.({ data: { type: 'drained', renderEndTime: 1 } })
  player.close()
  await player.done
  expect(vi.getTimerCount()).toBe(0)
})
