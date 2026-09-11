import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import VoiceInputButton from '../components/VoiceInputButton.vue'
import { transcribeSpeech } from '../api/speechApi'
vi.mock('../api/speechApi', () => ({ transcribeSpeech: vi.fn() }))
const deferred = <T,>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r }); return { promise, resolve } }
let recorders: Recorder[] = []
class Recorder {
  static isTypeSupported() { return true }
  state = 'inactive'; mimeType = 'audio/webm'; onstop: (() => void) | null = null
  ondataavailable: ((event: { data: Blob }) => void) | null = null; onerror: (() => void) | null = null
  constructor() { recorders.push(this) }
  start() { this.state = 'recording' }
  stop() { this.state = 'inactive' }
  finish() { this.ondataavailable?.({ data: new Blob(['voice']) }); this.onstop?.() }
}
const media = () => { const stop = vi.fn(); return { stream: { getTracks: () => [{ stop }] } as unknown as MediaStream, stop } }
const create = () => mount(VoiceInputButton, { global: { plugins: [createPinia()] }, props: { available: true, maxRecordingSeconds: 60, maxAudioBytes: 10000 } })
beforeEach(() => { recorders = []; vi.clearAllMocks(); vi.stubGlobal('MediaRecorder', Recorder); Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn() } }) })
afterEach(() => { vi.useRealTimers() })
describe('recording session ownership', () => {
  it('recovers after denied permission or an ASR failure', async () => {
    vi.mocked(navigator.mediaDevices.getUserMedia).mockRejectedValueOnce(new DOMException('Denied', 'NotAllowedError')).mockImplementation(async () => media().stream)
    vi.mocked(transcribeSpeech).mockRejectedValueOnce(new Error('未识别到清晰语音，请靠近麦克风重试')).mockResolvedValue({ text: '恢复成功', language: 'zh', durationMs: 900 })
    const w = create(); await w.get('button').trigger('click'); await flushPromises()
    expect(w.text()).toContain('麦克风权限被拒绝')
    await w.get('button').trigger('click'); recorders[0]!.finish(); await flushPromises()
    expect(w.text()).toContain('未识别到清晰语音')
    await w.get('button').trigger('click'); recorders[1]!.finish(); await flushPromises()
    expect(w.emitted('transcribed')).toEqual([['恢复成功']]); w.unmount()
  })
  it('keeps two users isolated for twenty-five sessions each', async () => {
    const tracks: ReturnType<typeof media>[] = []
    vi.mocked(navigator.mediaDevices.getUserMedia).mockImplementation(async () => { const m = media(); tracks.push(m); return m.stream })
    vi.mocked(transcribeSpeech).mockResolvedValue({ text: '交通态势', language: 'zh', durationMs: 900 })
    const users = [create(), create()]
    for (let i = 0; i < 25; i++) {
      await Promise.all(users.map(w => w.get('button').trigger('click')))
      recorders[i * 2 + 1]!.finish(); recorders[i * 2]!.finish(); await flushPromises()
    }
    for (const w of users) { expect(w.emitted('transcribed')).toHaveLength(25); w.unmount() }
    expect(tracks.every(m => m.stop.mock.calls.length === 1)).toBe(true)
  })
  it('cancels a permission request and closes late tracks without creating a recorder', async () => {
    const pending = deferred<MediaStream>(), m = media(); vi.mocked(navigator.mediaDevices.getUserMedia).mockReturnValue(pending.promise)
    const w = create(); await w.get('button').trigger('click'); await w.get('button').trigger('click')
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledTimes(1)
    pending.resolve(m.stream); await flushPromises()
    expect(m.stop).toHaveBeenCalledOnce(); expect(recorders).toHaveLength(0); expect(w.attributes('data-status')).toBe('idle'); w.unmount()
  })
  it('closes a late microphone after the surface is hidden or unmounted', async () => {
    for (const unmount of [false, true]) {
      const pending = deferred<MediaStream>(), m = media(); vi.mocked(navigator.mediaDevices.getUserMedia).mockReturnValue(pending.promise)
      const w = create(); await w.get('button').trigger('click'); if (unmount) w.unmount(); else await w.setProps({ active: false })
      pending.resolve(m.stream); await flushPromises(); expect(m.stop).toHaveBeenCalledOnce(); if (!unmount) w.unmount()
    }
    expect(recorders).toHaveLength(0)
  })
  it('ignores late recognition results and cleanup from a cancelled previous request', async () => {
    const first = deferred<{ text: string; language: string; durationMs: number }>()
    vi.mocked(navigator.mediaDevices.getUserMedia).mockImplementation(async () => media().stream)
    vi.mocked(transcribeSpeech).mockReturnValueOnce(first.promise).mockResolvedValueOnce({ text: '新录音', language: 'zh', durationMs: 900 })
    const w = create(); await w.get('button').trigger('click'); recorders[0]!.finish(); await flushPromises()
    const oldSignal = vi.mocked(transcribeSpeech).mock.calls[0]![2]!
    await w.get('.voice-input-button').trigger('click'); await w.get('.voice-input-button').trigger('click')
    first.resolve({ text: '旧录音', language: 'zh', durationMs: 900 }); await flushPromises()
    expect(oldSignal.aborted).toBe(true); expect(w.attributes('data-status')).toBe('recording'); expect(w.emitted('transcribed')).toBeUndefined()
    recorders[1]!.finish(); await flushPromises(); expect(w.emitted('transcribed')).toEqual([['新录音']]); w.unmount()
  })
  it('measures the stop request and submits only once despite delayed duplicate onstop', async () => {
    vi.useFakeTimers(); const m = media(); vi.mocked(navigator.mediaDevices.getUserMedia).mockResolvedValue(m.stream)
    vi.mocked(transcribeSpeech).mockResolvedValue({ text: '有效录音', language: 'zh', durationMs: 60000 })
    const w = create(); await w.get('button').trigger('click'); const done = recorders[0]!.onstop!
    await vi.advanceTimersByTimeAsync(60000); await vi.advanceTimersByTimeAsync(2000)
    recorders[0]!.finish(); done(); await vi.advanceTimersByTimeAsync(0)
    expect(transcribeSpeech).toHaveBeenCalledTimes(1); expect(vi.mocked(transcribeSpeech).mock.calls[0]![1]).toBe(60000)
    expect(m.stop).toHaveBeenCalledOnce(); w.unmount()
  })
  it('supports fifty consecutive sessions without retaining tracks', async () => {
    const tracks: ReturnType<typeof media>[] = []
    vi.mocked(navigator.mediaDevices.getUserMedia).mockImplementation(async () => { const m = media(); tracks.push(m); return m.stream })
    vi.mocked(transcribeSpeech).mockResolvedValue({ text: '交通态势', language: 'zh', durationMs: 900 })
    const w = create()
    for (let i = 0; i < 50; i++) { await w.get('button').trigger('click'); recorders[i]!.finish(); await flushPromises() }
    expect(w.emitted('transcribed')).toHaveLength(50); expect(tracks.every(m => m.stop.mock.calls.length === 1)).toBe(true); w.unmount()
  })
})
