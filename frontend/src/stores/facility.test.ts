import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchFacilityAlerts,
  fetchFacilityFocus,
  fetchFacilityHealthReport,
  transitionFacilityAlert,
} from '../api/facilityApi'
import type { FacilityAlertPage } from '../types/facility'
import { useFacilityStore } from './facility'

vi.mock('../api/facilityApi', () => ({
  fetchFacilityAlerts: vi.fn(),
  fetchFacilityFocus: vi.fn(),
  fetchFacilityHealthReport: vi.fn(),
  transitionFacilityAlert: vi.fn(),
}))

const page: FacilityAlertPage = {
  items: [], page: 0, size: 20, total: 0,
  counts: {
    pending: 6, confirmed: 1, closed: 2,
    activeWarning: 3, activeSevere: 2, activeEmergency: 2,
  },
}

describe('facility warning store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setActivePinia(createPinia())
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    vi.mocked(fetchFacilityAlerts).mockResolvedValue(page)
  })

  afterEach(() => vi.useRealTimers())

  it('polls pending warnings every five seconds only while visible', async () => {
    const store = useFacilityStore()
    store.startPolling()
    await vi.waitFor(() => expect(fetchFacilityAlerts).toHaveBeenCalledTimes(1))
    expect(fetchFacilityAlerts).toHaveBeenLastCalledWith('PENDING', '', 0, 20)

    await vi.advanceTimersByTimeAsync(5000)
    expect(fetchFacilityAlerts).toHaveBeenCalledTimes(2)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchFacilityAlerts).toHaveBeenCalledTimes(2)
    store.stopPolling()
  })

  it('writes expected and target statuses then refreshes the list', async () => {
    vi.mocked(transitionFacilityAlert).mockResolvedValue({
      alertId: 42, facilityName: '闽江大桥', metricName: '主梁应变',
      alarmLevel: 'EMERGENCY', alarmLevelName: '紧急',
      collectTime: '2026-09-08T01:59:30Z', triggerTime: '2026-09-08T01:59:00Z',
      status: 'CONFIRMED', statusName: '处理中', remark: '【已确认】已派员',
      thresholdAssessment: '状态型异常', sourceConsistent: true,
    })
    const store = useFacilityStore()

    await store.transition(42, 'PENDING', 'CONFIRMED', '已派员')

    expect(transitionFacilityAlert).toHaveBeenCalledWith(42, {
      expectedStatus: 'PENDING', targetStatus: 'CONFIRMED',
      resolutionType: undefined, remark: '已派员',
    })
    expect(fetchFacilityAlerts).toHaveBeenCalledWith('PENDING', '', 0, 20)
  })

  it('loads reports and focus objects through explicit views', async () => {
    vi.mocked(fetchFacilityHealthReport).mockResolvedValue({
      generatedAt: '2026-09-08T02:00:00Z', activeAlertCount: 0,
      affectedFacilityCount: 0, warningCount: 0, severeCount: 0,
      emergencyCount: 0, pendingCount: 0, confirmedCount: 0,
      overallHealth: 'NO_ACTIVE_ALERT', overallHealthName: '当前无活动告警',
      summary: '当前无活动告警', facilities: [],
    })
    vi.mocked(fetchFacilityFocus).mockResolvedValue([])
    const store = useFacilityStore()

    await store.showReport()
    expect(fetchFacilityHealthReport).toHaveBeenCalledOnce()
    expect(store.viewMode).toBe('report')
    await store.showFocus()
    expect(fetchFacilityFocus).toHaveBeenCalledWith(10)
    expect(store.viewMode).toBe('focus')
  })
})
