import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SpeechApiError, transcribeSpeech } from '../api/speechApi'
import VoiceInputButton from './VoiceInputButton.vue'

vi.mock('../api/speechApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/speechApi')>(),
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
      text: '福建省目前整体交通态势如何', language: 'zh', durationMs: 900,
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

    await wrapper.get('.voice-input-button').trigger('click')
    expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('recording')
    expect(wrapper.get('.voice-input-finish').text()).toBe('停止并识别')
    expect(wrapper.find('.voice-recording-time').exists()).toBe(false)
    await wrapper.get('.voice-input-finish').trigger('click')

    await vi.waitFor(() => expect(wrapper.emitted('transcribed')?.[0])
      .toEqual(['福建省目前整体交通态势如何']))
    expect(transcribeSpeech).toHaveBeenCalledOnce()
  })

  it('keeps the finish action enabled while the parent marks recording as disabled', async () => {
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        disabled: false,
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('.voice-input-button').trigger('click')
    await wrapper.setProps({ disabled: true })

    expect(wrapper.get('.voice-input-button').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.voice-input-finish').attributes('disabled')).toBeUndefined()
    await wrapper.get('.voice-input-finish').trigger('click')
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

  it('cancels an active recording when the chat surface is hidden', async () => {
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        active: true,
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('.voice-input-button').trigger('click')
    expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('recording')
    await wrapper.setProps({ active: false })

    await vi.waitFor(() => {
      expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('idle')
    })
    expect(wrapper.emitted('recordingChanged')?.at(-1)).toEqual([false])
    expect(transcribeSpeech).not.toHaveBeenCalled()
  })

  it('returns to idle without an error prompt when no speech is recognized', async () => {
    vi.mocked(transcribeSpeech).mockRejectedValueOnce(
      new SpeechApiError('未检测到有效语音', 'ASR_NO_SPEECH', 'trace-1'),
    )
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('.voice-input-button').trigger('click')
    await wrapper.get('.voice-input-finish').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('idle')
    })
    expect(wrapper.find('.voice-input-error').exists()).toBe(false)
    expect(wrapper.emitted('transcribed')).toBeUndefined()
  })

  it('still shows genuine speech service failures', async () => {
    vi.mocked(transcribeSpeech).mockRejectedValueOnce(
      new SpeechApiError('语音识别服务调用失败', 'ASR_REQUEST_FAILED', 'trace-2'),
    )
    const wrapper = mount(VoiceInputButton, {
      global: { plugins: [createPinia()] },
      props: {
        available: true,
        maxRecordingSeconds: 60,
        maxAudioBytes: 10485760,
      },
    })

    await wrapper.get('.voice-input-button').trigger('click')
    await wrapper.get('.voice-input-finish').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.get('.voice-input-control').attributes('data-status')).toBe('error')
    })
    expect(wrapper.get('.voice-input-error').text()).toBe('语音识别服务调用失败')
  })
})
