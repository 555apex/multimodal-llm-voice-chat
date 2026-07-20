import { afterEach, describe, expect, it, vi } from 'vitest'
import { queryRealtimeTraffic, TrafficApiError } from './trafficApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('queryRealtimeTraffic', () => {
  it('returns the standard API envelope', async () => {
    const payload = {
      code: 'OK',
      message: 'success',
      traceId: 'trace-1',
      timestamp: '2026-07-17T08:00:00Z',
      data: { roadName: '五四路' },
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => payload,
    }))

    const result = await queryRealtimeTraffic({ areaCode: '350100', roadName: '五四路' })

    expect(result.traceId).toBe('trace-1')
    expect(fetch).toHaveBeenCalledWith('/api/v1/traffic/queries', expect.objectContaining({ method: 'POST' }))
  })

  it('converts backend errors into a readable exception', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ code: 'AMAP_AUTH_OR_PERMISSION_ERROR', message: '高德Key无权限', traceId: 'trace-2' }),
    }))

    try {
      await queryRealtimeTraffic({ areaCode: '350100', roadName: '五四路' })
      throw new Error('expected query to fail')
    } catch (error) {
      expect(error).toBeInstanceOf(TrafficApiError)
      const apiError = error as TrafficApiError
      expect(apiError.code).toBe('AMAP_AUTH_OR_PERMISSION_ERROR')
      expect(apiError.traceId).toBe('trace-2')
    }
  })
})
