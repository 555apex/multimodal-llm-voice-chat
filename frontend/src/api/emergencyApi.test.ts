import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchNextEmergency } from './emergencyApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('fetchNextEmergency', () => {
  it('returns the pending event from the standard API envelope', async () => {
    const alert = {
      event: {
        eventId: '1',
        customId: 'EVT-1',
        eventType: 'DT01',
        description: '边坡崩塌',
      },
      pendingCount: 1,
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ code: 'OK', message: 'success', data: alert }),
    }))

    await expect(fetchNextEmergency()).resolves.toEqual(alert)
  })

  it('turns an invalid response body into a readable error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => { throw new Error('invalid json') },
    }))

    await expect(fetchNextEmergency()).rejects.toThrow('紧急事件查询失败')
  })
})
