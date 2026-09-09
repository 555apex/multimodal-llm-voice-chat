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
      alertId: '2097166786449965057', facilityName: '闽江大桥', metricName: '主梁应变',
      alarmLevel: 'EMERGENCY', alarmLevelName: '紧急',
      collectTime: '2026-09-08T01:59:30Z', triggerTime: '2026-09-08T01:59:00Z',
      status: 'CONFIRMED', statusName: '处理中', remark: '【已确认】已派员',
      thresholdAssessment: '状态型异常', sourceConsistent: true,
    })
    const store = useFacilityStore()

    await store.transition('2097166786449965057', 'PENDING', 'CONFIRMED', '已派员')

    expect(transitionFacilityAlert).toHaveBeenCalledWith('2097166786449965057', {
      expectedStatus: 'PENDING', targetStatus: 'CONFIRMED',
      resolutionType: undefined, remark: '已派员',
    })
    expect(fetchFacilityAlerts).toHaveBeenCalledWith('PENDING', '', 0, 20)
  })

  it('discards a stale pending response after switching to confirmed alerts', async () => {
    const pendingPage: FacilityAlertPage = {
      ...page,
      items: [{
        alertId: '1', facilityName: '待确认桥梁', metricName: '位移',
        alarmLevel: 'WARNING', alarmLevelName: '警告',
        collectTime: '2026-09-08T01:58:30Z', triggerTime: '2026-09-08T01:58:00Z',
        status: 'PENDING', statusName: '待确认',
        thresholdAssessment: '超过上限', sourceConsistent: true,
      }],
    }
    const confirmedPage: FacilityAlertPage = {
      ...page,
      items: [{
        alertId: '2', facilityName: '已确认隧道', metricName: '沉降',
        alarmLevel: 'SEVERE', alarmLevelName: '严重',
        collectTime: '2026-09-08T01:59:30Z', triggerTime: '2026-09-08T01:59:00Z',
        status: 'CONFIRMED', statusName: '处理中',
        thresholdAssessment: '超过上限', sourceConsistent: true,
      }],
    }
    let resolvePending!: (value: FacilityAlertPage) => void
    vi.mocked(fetchFacilityAlerts)
      .mockImplementationOnce(() => new Promise(resolve => { resolvePending = resolve }))
      .mockResolvedValueOnce(confirmedPage)
    const store = useFacilityStore()

    const staleRequest = store.loadAlerts(0)
    await store.selectStatus('CONFIRMED')
    expect(store.pageData?.items.map(item => item.status)).toEqual(['CONFIRMED'])

    resolvePending(pendingPage)
    await staleRequest
    expect(store.selectedStatus).toBe('CONFIRMED')
    expect(store.pageData?.items.map(item => item.status)).toEqual(['CONFIRMED'])
    expect(store.polling).toBe(false)
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

  it('does not switch filters or views while an action form is open', async () => {
    const store = useFacilityStore()
    store.formOpen = true

    await store.selectStatus('CONFIRMED')
    await store.selectAlarmLevel('EMERGENCY')
    await store.showReport()

    expect(store.selectedStatus).toBe('PENDING')
    expect(store.selectedAlarmLevel).toBe('')
    expect(store.viewMode).toBe('alerts')
    expect(fetchFacilityAlerts).not.toHaveBeenCalled()
    expect(fetchFacilityHealthReport).not.toHaveBeenCalled()
  })
})
