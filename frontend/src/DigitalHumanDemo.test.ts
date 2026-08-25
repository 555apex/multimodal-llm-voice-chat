import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DigitalHumanDemo from './DigitalHumanDemo.vue'

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
    vi.unstubAllGlobals()
  })

  it('switches rapidly through all four prototype states', async () => {
    const wrapper = mount(DigitalHumanDemo)
    await flushPromises()

    const states = ['listening', 'speaking', 'error', 'idle']
    for (const state of states) {
      const button = wrapper.findAll('.prototype-state-buttons button')
        .find((candidate) => candidate.text().includes(state))
      expect(button).toBeDefined()
      await button!.trigger('click')
      expect(wrapper.get('.prototype-human-panel').attributes('data-state')).toBe(state)
    }

    expect(wrapper.get('.prototype-load-status').text()).toContain('可以开始评审')
  })
})
