import { afterEach, describe, expect, it, vi } from 'vitest'
import { transcribeSpeech, synthesizeSpeech } from './speechApi'
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })
describe('speech error protocol', () => {
  it('sends an explicit cancellation for the owned MP3 synthesis request', async () => {
    const controller=new AbortController()
    const fetcher=vi.fn((_url, options) => options.method==='DELETE' ? Promise.resolve(new Response(null,{status:204})) : new Promise<Response>((_resolve,reject) => options.signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')))))
    vi.stubGlobal('fetch',fetcher)
    const result=expect(synthesizeSpeech('道路通行正常。',controller.signal)).rejects.toThrow('Aborted')
    controller.abort();await result
    const id=fetcher.mock.calls[0]![1].headers['X-Speech-Request-Id']
    expect(fetcher.mock.calls[1]![0]).toContain('/syntheses/'+id)
    expect(fetcher.mock.calls[1]![1]).toEqual({method:'DELETE',keepalive:true})
  })
  it.each([
    ['ASR_NO_SPEECH', 422, '未识别到清晰语音'], ['ASR_INVALID_AUDIO', 415, '录音格式无法解码'],
    ['ASR_BUSY', 429, '语音服务正忙'], ['ASR_TIMEOUT', 504, '语音识别超时'], ['ASR_UNAVAILABLE', 503, '语音识别暂不可用'],
  ])('displays a recoverable message for %s', async (code, status, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code, message: 'upstream error' }), { status })))
    await expect(transcribeSpeech(new Blob(['audio']), 900)).rejects.toThrow(message)
  })
  it('cancels an in-flight request and reports a distinct timeout', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((_url, options) => new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    })))
    const result = expect(transcribeSpeech(new Blob(['audio']), 900)).rejects.toThrow('语音识别超时')
    await vi.advanceTimersByTimeAsync(100000); await result
    expect(vi.getTimerCount()).toBe(0)
  })
})
