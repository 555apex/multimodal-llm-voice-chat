import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchSpeechCapabilities, synthesizeSpeech } from '../api/speechApi'
import { useSpeechStore } from './speech'

vi.mock('../api/speechApi', () => ({
  fetchSpeechCapabilities: vi.fn(),
  synthesizeSpeech: vi.fn(),
}))

class FakeAudio {
  static instances: FakeAudio[] = []
  static captureFactory: (() => MediaStream) | undefined
  onended: (() => void) | null = null
  onerror: (() => void) | null = null
  paused = true
  currentTime = 0
  captureStream = FakeAudio.captureFactory

  constructor(public readonly src: string) {
    FakeAudio.instances.push(this)
  }

  play() {
    this.paused = false
    return Promise.resolve()
  }

  pause() {
    this.paused = true
  }
}

describe('speech store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    FakeAudio.instances = []
    FakeAudio.captureFactory = undefined
    vi.mocked(fetchSpeechCapabilities).mockResolvedValue({
      asrAvailable: true,
      ttsAvailable: true,
      asrModel: 'small',
      ttsVoice: 'Xiaoxiao',
      maxRecordingSeconds: 60,
      maxAudioBytes: 10485760,
    })
    vi.mocked(synthesizeSpeech).mockResolvedValue(new Blob(['mp3'], { type: 'audio/mpeg' }))
    vi.stubGlobal('Audio', FakeAudio)
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:test'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('loads capabilities and disables autoplay by default', async () => {
    const store = useSpeechStore()
    await store.loadCapabilities()

    expect(store.capabilities?.asrAvailable).toBe(true)
    expect(store.autoReadEnabled).toBe(false)
  })

  it('supports pause, resume and completion for one message', async () => {
    const store = useSpeechStore()
    await store.loadCapabilities()
    const playing = store.speak('message-1', '道路通行正常。')
    await vi.waitFor(() => expect(FakeAudio.instances).toHaveLength(1))

    await store.toggleMessage('message-1', '道路通行正常。')
    expect(store.playbackStatus).toBe('paused')
    await store.toggleMessage('message-1', '道路通行正常。')
    expect(store.playbackStatus).toBe('playing')

    FakeAudio.instances[0].onended?.()
    await playing
    expect(store.playbackStatus).toBe('idle')
  })

  it('turning autoplay off stops the active queue', async () => {
    const store = useSpeechStore()
    await store.loadCapabilities()
    store.setAutoRead(true)
    const playing = store.speak('message-2', '正在朗读当前回答。')
    await vi.waitFor(() => expect(store.playbackStatus).toBe('playing'))

    store.setAutoRead(false)

    expect(store.autoReadEnabled).toBe(false)
    expect(store.playbackStatus).toBe('idle')
    await playing
  })

  it('stops playback and blocks hidden-surface speech', async () => {
    const store = useSpeechStore()
    await store.loadCapabilities()
    const playing = store.speak('message-3', '正在播放的回答。')
    await vi.waitFor(() => expect(store.playbackStatus).toBe('playing'))

    store.setSurfaceActive(false)

    expect(store.playbackStatus).toBe('idle')
    await playing
    await store.speak('message-4', '隐藏时不应播放。')
    expect(FakeAudio.instances).toHaveLength(1)
  })

  it('derives a smoothed amplitude from the playing audio and releases analysis resources', async () => {
    const frameCallbacks = new Map<number, FrameRequestCallback>()
    let nextFrame = 0
    const tracks = [{ stop: vi.fn() }]
    FakeAudio.captureFactory = () => ({ getTracks: () => tracks } as unknown as MediaStream)
    vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => {
      nextFrame += 1
      frameCallbacks.set(nextFrame, callback)
      return nextFrame
    }))
    vi.stubGlobal('cancelAnimationFrame', vi.fn((handle: number) => frameCallbacks.delete(handle)))

    const disconnect = vi.fn()
    const close = vi.fn().mockResolvedValue(undefined)
    class FakeAudioContext {
      state: AudioContextState = 'running'
      createMediaStreamSource() {
        return { connect: vi.fn(), disconnect }
      }
      createAnalyser() {
        return {
          fftSize: 256,
          smoothingTimeConstant: 0,
          getByteTimeDomainData(samples: Uint8Array) {
            samples.forEach((_, index) => { samples[index] = index % 2 ? 168 : 88 })
          },
        }
      }
      resume() { return Promise.resolve() }
      close = close
    }
    vi.stubGlobal('AudioContext', FakeAudioContext)

    const store = useSpeechStore()
    await store.loadCapabilities()
    const playing = store.speak('message-amplitude', '正在分析真实音频振幅。')
    await vi.waitFor(() => expect(store.playbackStatus).toBe('playing'))

    const frame = [...frameCallbacks.entries()].at(-1)
    expect(frame).toBeDefined()
    frameCallbacks.delete(frame![0])
    frame![1](34)
    expect(store.playbackAmplitude).toBeGreaterThan(0)

    FakeAudio.instances[0].onended?.()
    await playing
    expect(store.playbackAmplitude).toBe(0)
    expect(disconnect).toHaveBeenCalledOnce()
    expect(tracks[0].stop).toHaveBeenCalledOnce()
    expect(close).toHaveBeenCalledOnce()
    expect(frameCallbacks.size).toBe(0)
  })

  it('falls back safely when browser audio analysis is unavailable', async () => {
    const frameCallbacks = new Map<number, FrameRequestCallback>()
    let nextFrame = 0
    vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => {
      nextFrame += 1
      frameCallbacks.set(nextFrame, callback)
      return nextFrame
    }))
    vi.stubGlobal('cancelAnimationFrame', vi.fn((handle: number) => frameCallbacks.delete(handle)))
    vi.stubGlobal('AudioContext', undefined)

    const store = useSpeechStore()
    await store.loadCapabilities()
    const playing = store.speak('message-fallback', '分析器不可用时继续朗读。')
    await vi.waitFor(() => expect(store.playbackStatus).toBe('playing'))

    const frame = [...frameCallbacks.entries()].at(-1)!
    frameCallbacks.delete(frame[0])
    FakeAudio.instances[0].currentTime = 0.2
    frame[1](200)
    expect(store.playbackAmplitude).toBeGreaterThan(0)

    store.stop()
    await playing
    expect(store.playbackAmplitude).toBe(0)
    expect(frameCallbacks.size).toBe(0)
  })

  it('releases fallback frames and object URLs across twenty consecutive plays', async () => {
    const frameCallbacks = new Map<number, FrameRequestCallback>()
    let nextFrame = 0
    vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => {
      nextFrame += 1
      frameCallbacks.set(nextFrame, callback)
      return nextFrame
    }))
    vi.stubGlobal('cancelAnimationFrame', vi.fn((handle: number) => frameCallbacks.delete(handle)))
    vi.stubGlobal('AudioContext', undefined)

    const store = useSpeechStore()
    await store.loadCapabilities()
    for (let index = 0; index < 20; index += 1) {
      const playing = store.speak(`message-loop-${index}`, `第${index + 1}次朗读。`)
      await vi.waitFor(() => expect(FakeAudio.instances).toHaveLength(index + 1))
      FakeAudio.instances[index].onended?.()
      await playing
      expect(store.playbackAmplitude).toBe(0)
      expect(frameCallbacks.size).toBe(0)
    }

    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(20)
  })
})
