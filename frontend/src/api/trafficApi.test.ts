import { afterEach, describe, expect, it, vi } from 'vitest'
import { queryHighwayTraffic, TrafficApiError } from './trafficApi'

afterEach(() => vi.unstubAllGlobals())

describe('queryHighwayTraffic', () => {
  it('posts the unified MySQL traffic contract', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ code: 'OK', message: 'success', data: { source: 'MYSQL' }, traceId: 'trace-1' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    const response = await queryHighwayTraffic({
      queryType: 'CITY_PAIR', originCity: '宁德市', destinationCity: '福州市',
    })

    expect(response.data.source).toBe('MYSQL')
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/traffic/queries', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        queryType: 'CITY_PAIR', originCity: '宁德市', destinationCity: '福州市',
      }),
    }))
  })

  it('surfaces backend refreshing errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({
        code: 'TRAFFIC_DATA_REFRESHING', message: '交通数据正在更新', traceId: 'trace-2',
      }),
    }))

    await expect(queryHighwayTraffic({ queryType: 'PROVINCE_OVERVIEW' }))
      .rejects.toMatchObject({
        code: 'TRAFFIC_DATA_REFRESHING', traceId: 'trace-2',
      } satisfies Partial<TrafficApiError>)
  })
})
