import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DigitalHumanPrototype from './DigitalHumanPrototype.vue'
import type { PrototypeDigitalHumanState } from '../types/digitalHumanPrototype'

class FakeImage {
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  set src(_value: string) {
    queueMicrotask(() => this.onload?.())
  }
}

describe('DigitalHumanPrototype', () => {
  beforeEach(() => {
    vi.stubGlobal('Image', FakeImage)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const stateCases: Array<[PrototypeDigitalHumanState, string, string]> = [
    ['idle', 'idle', '在线待命'],
    ['listening', 'thinking', '正在聆听 / 处理中'],
    ['speaking', 'explaining', '正在讲解'],
    ['error', 'idle', '处理异常'],
  ]

  it.each(stateCases)('maps %s to the %s pose', async (state, pose, label) => {
    const wrapper = mount(DigitalHumanPrototype, { props: { state } })
    await flushPromises()

    expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe(state)
    expect(wrapper.get('.prototype-portrait-layer.is-active').attributes('data-pose')).toBe(pose)
    expect(wrapper.get('.prototype-state').text()).toContain(label)
    expect(wrapper.emitted('ready-change')?.at(-1)).toEqual([true])
  })

  it('falls back to idle when the requested pose fails', async () => {
    const wrapper = mount(DigitalHumanPrototype, { props: { state: 'listening' } })
    await flushPromises()

    await wrapper.get(".prototype-portrait-layer[data-pose='thinking']").trigger('error')

    expect(wrapper.get('.prototype-human-panel').attributes('data-fallback')).toBe('true')
    expect(wrapper.get('.prototype-portrait-layer.is-active').attributes('data-pose')).toBe('idle')
    expect(wrapper.get('.prototype-state').text()).toContain('已回退至待命图')
    expect(wrapper.emitted('asset-error')?.at(-1)).toEqual(['thinking'])
  })
})
