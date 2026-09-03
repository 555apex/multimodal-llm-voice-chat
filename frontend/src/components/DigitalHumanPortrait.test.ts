import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DigitalHumanPortrait from './DigitalHumanPortrait.vue'

let failingSources = new Set<string>()

class FakeImage {
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  set src(value: string) {
    queueMicrotask(() => failingSources.has(value) ? this.onerror?.() : this.onload?.())
  }
}

describe('DigitalHumanPortrait', () => {
  beforeEach(() => {
    failingSources = new Set()
    vi.stubGlobal('Image', FakeImage)
  })

  afterEach(() => vi.unstubAllGlobals())

  it('preloads the three poses and exposes an accessible live-state label', async () => {
    const wrapper = mount(DigitalHumanPortrait, { props: { mode: 'answering' } })
    await flushPromises()

    expect(wrapper.findAll('.digital-human-portrait-layer')).toHaveLength(3)
    expect(wrapper.get('.digital-human-portrait-stage').attributes('aria-label')).toContain('正在讲解')
    expect(wrapper.emitted('ready-change')?.at(-1)).toEqual([true])
  })

  it('uses the PNG fallback when WebP preload fails', async () => {
    failingSources = new Set(['/digital-human/guardian/guardian-thinking.webp'])
    const wrapper = mount(DigitalHumanPortrait, { props: { mode: 'thinking' } })
    await flushPromises()

    expect(wrapper.get(".digital-human-portrait-layer[data-pose='thinking']").attributes('src'))
      .toBe('/digital-human/guardian/guardian-thinking.png')
    expect(wrapper.emitted('asset-error')).toBeUndefined()
  })

  it('shows the placeholder when the idle WebP and PNG both fail', async () => {
    failingSources = new Set([
      '/digital-human/guardian/guardian-idle.webp',
      '/digital-human/guardian/guardian-idle.png',
    ])
    const wrapper = mount(DigitalHumanPortrait, { props: { mode: 'idle' } })
    await flushPromises()

    expect(wrapper.get('.digital-human-loading.error').text()).toContain('人物资产暂时不可用')
    expect(wrapper.find('.digital-human-portrait-layer.is-active').exists()).toBe(false)
  })

  it('disables looping motion when the user prefers reduced motion', () => {
    const source = readFileSync(join(process.cwd(), 'src/components/DigitalHumanPortrait.vue'), 'utf8')
    expect(source).toContain('@media (prefers-reduced-motion: reduce)')
    expect(source).toContain('animation: none !important')
  })
})
