import { afterEach, describe, expect, it } from 'vitest'
import { setScreenActionHandler, submitScreenAction, validScreenActionIntent } from './screenAction'

afterEach(() => setScreenActionHandler())

describe('screen action protocol validation', () => {
  it('accepts only the five allowlisted actions with exact parameter shapes', () => {
    expect(validScreenActionIntent({ action: 'OPEN_MONITOR', params: { name: '福州南' } })).toBe(true)
    expect(validScreenActionIntent({ action: 'OPEN_MONITOR', params: {} })).toBe(false)
    expect(validScreenActionIntent({ action: 'OPEN_COMMAND', params: { url: 'https://example.com' } })).toBe(false)
    expect(validScreenActionIntent({ action: 'OPEN_MATERIALS', params: {} })).toBe(true)
    expect(validScreenActionIntent({ action: 'EVAL_SCRIPT', params: {} })).toBe(false)
  })

  it('rejects dangerous map operations and invalid coordinates', () => {
    expect(validScreenActionIntent({ action: 'SET_MAP_RESOURCES', params: {
      items: [{ resource: 'bridge', enabled: true }],
    } })).toBe(true)
    expect(validScreenActionIntent({ action: 'SET_MAP_RESOURCES', params: {
      items: [{ resource: 'all', enabled: true }],
    } })).toBe(false)
    expect(validScreenActionIntent({ action: 'SET_MAP_RESOURCES', params: {
      items: [{ resource: 'bridge', enabled: true }, { resource: 'bridge', enabled: false }],
    } })).toBe(false)
    expect(validScreenActionIntent({ action: 'SEARCH_NEARBY_RESOURCES', params: {
      lng: 119.29734, lat: 26.06979,
    } })).toBe(true)
    expect(validScreenActionIntent({ action: 'SEARCH_NEARBY_RESOURCES', params: {
      lng: '119.29734', lat: 26.06979,
    } })).toBe(false)
  })

  it('does not claim execution when no screen callback is registered', async () => {
    const receipt = await submitScreenAction({ action: 'OPEN_COMMAND', params: {} })
    expect(receipt.status).toBe('failed')
    expect(receipt.message).toContain('已接入的大屏')
  })
})
