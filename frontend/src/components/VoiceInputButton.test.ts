import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { transcribeSpeech } from '../api/speechApi'
import VoiceInputButton from './VoiceInputButton.vue'

vi.mock('../api/speechApi', () => ({
  transcribeSpeech: vi.fn(),
}))

class FakeMediaRecorder {
  static isTypeSupported() { return true }
  state: RecordingState = 'inactive'
  mimeType = 'audio/webm;codecs=opus'
  ondataavailable: ((event: BlobEvent) => void) | null = null
  onerror: (() => void) | null = null
  onstop: (() => void) | null = null

  constructor(_stream: MediaStream, _options?: MediaRecorderOptions) {}

  start() {
    this.state = 'recording'
  }

  stop() {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['audio'], { type: this.mimeType }) } as BlobEvent)
    this.onstop?.()
  }
}

describe('voice input button', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(transcribeSpeech).mockResolvedValue({
      text: '福州五四路现在堵吗', language: 'zh', durationMs: 900,
    })
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn().mockResolvedValue({
          getTracks: () => [{ stop: vi.fn() }],
        }),
      },
    })
  })

  it('records, transcribes and emits text without sending it', async () => {
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('button').trigger('click')
    expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('recording')
    await wrapper.get('button').trigger('click')

    await vi.waitFor(() => expect(wrapper.emitted('transcribed')?.[0]).toEqual(['福州五四路现在堵吗']))
    expect(transcribeSpeech).toHaveBeenCalledOnce()
  })

  it('keeps the stop control enabled while recording even if the parent becomes disabled', async () => {
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('.voice-input-button').trigger('click')
    await wrapper.setProps({ disabled: true })

    const stopButton = wrapper.get('.voice-input-button')
    expect(stopButton.attributes('disabled')).toBeUndefined()
    expect(stopButton.text()).toContain('停止')
    await stopButton.trigger('click')

    await vi.waitFor(() => expect(transcribeSpeech).toHaveBeenCalledOnce())
  })

  it('cancels an active recording without sending it to ASR', async () => {
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('.voice-input-button').trigger('click')
    await wrapper.get('.voice-input-cancel').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('idle')
    })
    expect(transcribeSpeech).not.toHaveBeenCalled()
    expect(wrapper.emitted('transcribed')).toBeUndefined()
  })
})
