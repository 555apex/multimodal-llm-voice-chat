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
    expect(wrapper.findAll('.digital-human-mouth-frame')).toHaveLength(3)
    expect(wrapper.get('.digital-human-portrait-stage').attributes('aria-label')).toContain('正在讲解')
    expect(wrapper.emitted('ready-change')?.at(-1)).toEqual([true])
  })

  it('uses the PNG fallback when WebP preload fails', async () => {
    failingSources = new Set(['/digital-human/guardian/guardian-thinking.webp'])
    const wrapper = mount(DigitalHumanPortrait, { props: { mode: 'thinking' } })
    await flushPromises()

    expect(wrapper.get(".digital-human-portrait-layer[data-pose='thinking'] .digital-human-portrait-image").attributes('src'))
      .toBe('/digital-human/guardian/guardian-thinking.png')
    expect(wrapper.emitted('asset-error')).toBeUndefined()
  })

  it('blends the three mouth frames from the real speech level', async () => {
    const wrapper = mount(DigitalHumanPortrait, {
      props: { mode: 'speaking', speechLevel: 0.725 },
    })
    await flushPromises()

    expect((wrapper.get("[data-mouth='closed']").element as HTMLElement).style.opacity).toBe('0')
    expect((wrapper.get("[data-mouth='half']").element as HTMLElement).style.opacity).toBe('0.5')
    expect((wrapper.get("[data-mouth='open']").element as HTMLElement).style.opacity).toBe('0.5')

    await wrapper.setProps({ mode: 'answering' })
    expect((wrapper.get("[data-mouth='closed']").element as HTMLElement).style.opacity).toBe('1')
    expect((wrapper.get("[data-mouth='open']").element as HTMLElement).style.opacity).toBe('0')
  })

  it('falls back to the original static explaining image when a mouth asset fails', async () => {
    failingSources = new Set([
      '/digital-human/guardian/guardian-explaining-mouth-half-v2.webp',
      '/digital-human/guardian/guardian-explaining-mouth-half-v2.png',
    ])
    const wrapper = mount(DigitalHumanPortrait, { props: { mode: 'speaking' } })
    await flushPromises()

    expect(wrapper.get('.digital-human-portrait-stage').attributes('data-mouth-fallback')).toBe('true')
    expect(wrapper.findAll('.digital-human-mouth-frame')).toHaveLength(0)
    expect(wrapper.get(".digital-human-portrait-layer[data-pose='explaining'] .digital-human-portrait-image")
      .attributes('src')).toBe('/digital-human/guardian/guardian-explaining.webp')
    expect(wrapper.emitted('asset-error')?.at(-1)).toEqual(['explaining'])
  })

  it('keeps all poses mounted for interruption-safe 600ms transitions', async () => {
    const wrapper = mount(DigitalHumanPortrait, { props: { mode: 'idle' } })
    await flushPromises()

    await wrapper.setProps({ mode: 'thinking' })
    await wrapper.setProps({ mode: 'answering' })
    expect(wrapper.findAll('.digital-human-portrait-layer')).toHaveLength(3)
    expect(wrapper.get('.digital-human-portrait-layer.is-active').attributes('data-pose')).toBe('explaining')

    const source = readFileSync(join(process.cwd(), 'src/components/DigitalHumanPortrait.vue'), 'utf8')
    expect(source).toContain('opacity 600ms cubic-bezier(.22, 1, .36, 1)')
    expect(source).not.toContain('visibility: hidden')
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
    expect(source).toContain('transition: opacity 120ms ease !important')
  })
})
