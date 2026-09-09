import { afterEach, expect, it, vi } from 'vitest'
import { receiveSpeech } from './pcmSpeech'
afterEach(() => vi.unstubAllGlobals())
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
