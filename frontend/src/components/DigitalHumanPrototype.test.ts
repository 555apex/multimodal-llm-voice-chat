import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DigitalHumanPrototype from './DigitalHumanPrototype.vue'
import type { DigitalHumanMode } from '../types/digitalHuman'

let failingSources = new Set<string>()

class FakeImage {
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  set src(value: string) {
    queueMicrotask(() => failingSources.has(value) ? this.onerror?.() : this.onload?.())
  }
}

describe('DigitalHumanPrototype', () => {
  beforeEach(() => {
    failingSources = new Set()
    vi.stubGlobal('Image', FakeImage)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const stateCases: Array<[DigitalHumanMode, string, string]> = [
    ['idle', 'idle', '在线待命'],
    ['listening', 'idle', '正在聆听'],
    ['thinking', 'thinking', '正在研判'],
    ['answering', 'explaining', '正在讲解'],
    ['speaking', 'explaining', '正在语音汇报'],
    ['error', 'idle', '处理异常'],
  ]

  it.each(stateCases)('maps %s to the %s pose', async (state, pose, label) => {
    const wrapper = mount(DigitalHumanPrototype, { props: { state } })
    await flushPromises()

    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe(state)
    expect(wrapper.get('.digital-human-portrait-layer.is-active').attributes('data-pose')).toBe(pose)
    expect(wrapper.get('.prototype-state').text()).toContain(label)
    expect(wrapper.emitted('ready-change')?.at(-1)).toEqual([true])
  })

  it('falls back to idle when both formats for the requested pose fail', async () => {
    failingSources = new Set([
      '/digital-human/guardian/guardian-thinking.webp',
      '/digital-human/guardian/guardian-thinking.png',
    ])
    const wrapper = mount(DigitalHumanPrototype, { props: { state: 'thinking' } })
    await flushPromises()

    expect(wrapper.get('.digital-human-portrait-stage').attributes('data-fallback')).toBe('true')
    expect(wrapper.get('.digital-human-portrait-layer.is-active').attributes('data-pose')).toBe('idle')
    expect(wrapper.emitted('asset-error')?.at(-1)).toEqual(['thinking'])
  })
})
