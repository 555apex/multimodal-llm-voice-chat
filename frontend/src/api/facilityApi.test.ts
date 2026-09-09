import { afterEach, describe, expect, it, vi } from 'vitest'
import { transitionFacilityAlert } from './facilityApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('transitionFacilityAlert', () => {
  it('keeps a BIGINT alert ID exact in the request path', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 'OK', message: 'success',
        data: { alertId: '2097166786449965057', status: 'CONFIRMED' },
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await transitionFacilityAlert('2097166786449965057', {
      expectedStatus: 'PENDING', targetStatus: 'CONFIRMED', remark: '测试处置',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/facility-alerts/2097166786449965057/status-transitions',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
