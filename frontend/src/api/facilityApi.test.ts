import { afterEach, expect, it, vi } from 'vitest'
import { fetchFacilityAlerts, transitionFacilityAlert } from './facilityApi'
afterEach(() => vi.unstubAllGlobals())
it('preserves distinct Snowflake identifiers through JSON and the action URL', async () => {
  const ids = ['2097225926893142017', '2097225926893142019', '9223372036854775807']
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { items: ids.map(alertId => ({ alertId })) } })))
  vi.stubGlobal('fetch', fetch)
  const page = await fetchFacilityAlerts('PENDING', '')
  expect(page.items.map(a => a.alertId)).toEqual(ids)
  for (const id of ids) {
    fetch.mockResolvedValueOnce(new Response(JSON.stringify({ data: { alertId: id } })))
    await transitionFacilityAlert(id, { expectedStatus: 'PENDING', targetStatus: 'CONFIRMED', remark: '核查' })
    expect(fetch.mock.lastCall?.[0]).toBe(`/api/v1/facility-alerts/${id}/status-transitions`)
  }
})
