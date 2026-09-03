import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolveDigitalHumanMode, useDigitalHumanSignal } from './useDigitalHumanSignal'
import type { AgentMessage } from '../types/agent'
import type { SpeechPlaybackStatus } from '../types/speech'

describe('resolveDigitalHumanMode', () => {
  const baseline = {
    recording: false,
    playbackStatus: 'idle' as SpeechPlaybackStatus,
    running: false,
    hasAnswerDelta: false,
    emergencyActionBusy: false,
    errorActive: false,
  }

  it('follows the fixed state priority', () => {
    expect(resolveDigitalHumanMode({ ...baseline })).toBe('idle')
    expect(resolveDigitalHumanMode({ ...baseline, errorActive: true })).toBe('error')
    expect(resolveDigitalHumanMode({ ...baseline, emergencyActionBusy: true, errorActive: true })).toBe('thinking')
    expect(resolveDigitalHumanMode({ ...baseline, running: true, hasAnswerDelta: true })).toBe('answering')
    expect(resolveDigitalHumanMode({ ...baseline, playbackStatus: 'playing', running: true, hasAnswerDelta: true })).toBe('speaking')
    expect(resolveDigitalHumanMode({ ...baseline, recording: true, playbackStatus: 'playing' })).toBe('listening')
  })

  it('treats TTS loading as thinking but paused playback as idle', () => {
    expect(resolveDigitalHumanMode({ ...baseline, playbackStatus: 'loading' })).toBe('thinking')
    expect(resolveDigitalHumanMode({ ...baseline, playbackStatus: 'paused' })).toBe('idle')
  })
})

describe('useDigitalHumanSignal', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('recognizes answer deltas and returns to idle three seconds after an Agent failure', async () => {
    vi.useFakeTimers()
    const recording = ref(false)
    const playbackStatus = ref<SpeechPlaybackStatus>('idle')
    const running = ref(true)
    const emergencyActionBusy = ref(false)
    const lastAssistantMessage = ref<AgentMessage>({
      id: 'assistant-1', role: 'assistant', content: '正在输出', status: 'pending',
    })

    const Harness = defineComponent({
      setup() {
        const signal = useDigitalHumanSignal({
          recording, playbackStatus, running, lastAssistantMessage, emergencyActionBusy,
        })
        return { signal }
      },
      template: '<span data-testid="mode">{{ signal.mode }}</span>',
    })
    const wrapper = mount(Harness)
    expect(wrapper.get('[data-testid="mode"]').text()).toBe('answering')

    running.value = false
    lastAssistantMessage.value = { ...lastAssistantMessage.value, status: 'failed', content: '' }
    await nextTick()
    expect(wrapper.get('[data-testid="mode"]').text()).toBe('error')

    vi.advanceTimersByTime(2999)
    await nextTick()
    expect(wrapper.get('[data-testid="mode"]').text()).toBe('error')
    vi.advanceTimersByTime(1)
    await nextTick()
    expect(wrapper.get('[data-testid="mode"]').text()).toBe('idle')

    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
