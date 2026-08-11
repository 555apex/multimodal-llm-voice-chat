import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchSpeechCapabilities, synthesizeSpeech } from '../api/speechApi'
import { useSpeechStore } from './speech'

vi.mock('../api/speechApi', () => ({
  fetchSpeechCapabilities: vi.fn(),
  synthesizeSpeech: vi.fn(),
}))

class FakeAudio {
  static instances: FakeAudio[] = []
  onended: (() => void) | null = null
  onerror: (() => void) | null = null
  paused = true

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
})
