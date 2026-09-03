import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import DigitalHumanDemo from './DigitalHumanDemo.vue'
import { useSpeechStore } from './stores/speech'

class FakeImage {
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  set src(_value: string) {
    queueMicrotask(() => this.onload?.())
  }
}

describe('DigitalHumanDemo', () => {
  beforeEach(() => {
    vi.stubGlobal('Image', FakeImage)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  function mountDemo() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const speech = useSpeechStore()
    speech.capabilities = {
      asrAvailable: true,
      ttsAvailable: true,
      maxRecordingSeconds: 60,
      maxAudioBytes: 10 * 1024 * 1024,
    }
    vi.spyOn(speech, 'loadCapabilities').mockResolvedValue()
    vi.spyOn(speech, 'stop').mockImplementation(() => {
      speech.playbackStatus = 'idle'
    })
    return { wrapper: mount(DigitalHumanDemo, { global: { plugins: [pinia] } }), speech }
  }

  it('switches through all six production states', async () => {
    const { wrapper } = mountDemo()
    await flushPromises()

    const states = ['idle', 'listening', 'thinking', 'answering', 'speaking', 'error']
    for (const state of states) {
      const button = wrapper.findAll('.prototype-state-buttons button')
        .find((candidate) => candidate.text().includes(state))
      expect(button).toBeDefined()
      await button!.trigger('click')
      expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe(state)
    }
    expect(wrapper.get('.prototype-load-status').text()).toContain('可以开始演示')
  })

  it('starts, pauses, replays, loops and releases the auto-demo timer', async () => {
    vi.useFakeTimers()
    const { wrapper } = mountDemo()
    await flushPromises()

    await wrapper.findAll('.prototype-playback-controls button')[0].trigger('click')
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('idle')
    vi.advanceTimersByTime(1800)
    await nextTick()
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('listening')

    await wrapper.findAll('.prototype-playback-controls button')[1].trigger('click')
    vi.advanceTimersByTime(10000)
    await nextTick()
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('listening')

    await wrapper.findAll('.prototype-playback-controls button')[2].trigger('click')
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('idle')
    vi.advanceTimersByTime(1800 + 1800 + 2400 + 2400 + 3200)
    await nextTick()
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('idle')

    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('follows real TTS loading and playing states', async () => {
    const { wrapper, speech } = mountDemo()
    await flushPromises()
    let finishSpeech: (() => void) | undefined
    vi.spyOn(speech, 'speak').mockImplementation(async () => {
      speech.playbackStatus = 'loading'
      await new Promise<void>((resolve) => { finishSpeech = resolve })
    })

    await wrapper.get('.prototype-report-demo > button').trigger('click')
    await nextTick()
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('thinking')

    speech.playbackStatus = 'playing'
    await nextTick()
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('speaking')

    speech.playbackStatus = 'idle'
    finishSpeech?.()
    await flushPromises()
    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe('idle')
  })

  it('keeps the visual demo available when TTS playback fails', async () => {
    const { wrapper, speech } = mountDemo()
    await flushPromises()
    vi.spyOn(speech, 'speak').mockImplementation(async () => {
      speech.playbackError = '浏览器拒绝播放'
      speech.playbackStatus = 'failed'
    })

    await wrapper.get('.prototype-report-demo > button').trigger('click')
    await flushPromises()

    expect(wrapper.get('.prototype-report-notice').text()).toContain('浏览器拒绝播放')
    expect(wrapper.findAll('.prototype-state-buttons button').every((button) => !button.attributes('disabled'))).toBe(true)
  })
})
