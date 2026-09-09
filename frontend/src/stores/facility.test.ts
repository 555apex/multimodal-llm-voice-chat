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
  it('retains the form after a failed action and permits switching to focus', async () => {
    const store = useFacilityStore()
    store.formOpen = true
    store.pageData = page
    vi.mocked(transitionFacilityAlert).mockRejectedValueOnce(new Error('设施告警不存在'))
    await expect(store.transition('2097225926893142019', 'PENDING', 'CONFIRMED', '核查')).rejects.toThrow()
    expect(store.actionError).toBe('设施告警不存在')
    expect(store.errorMessage).toBe('')
    expect(store.formOpen).toBe(true)
    vi.mocked(fetchFacilityFocus).mockResolvedValueOnce([])
    await store.showFocus()
    expect(store.formOpen).toBe(false)
    expect(fetchFacilityFocus).toHaveBeenCalled()
  })

  it('refreshes the current page after submission and clamps an empty last page', async () => {
    const store = useFacilityStore()
    store.pageData = { ...page, page: 2, total: 41 }
    store.formOpen = true
    vi.mocked(transitionFacilityAlert).mockResolvedValueOnce({} as never)
    vi.mocked(fetchFacilityAlerts).mockResolvedValueOnce({ ...page, page: 2, total: 40 })
    await store.transition('9223372036854775807', 'CONFIRMED', 'CLOSED', '已解决', 'RESOLVED')
    expect(fetchFacilityAlerts).toHaveBeenNthCalledWith(1, 'PENDING', '', 2, 20)
    expect(fetchFacilityAlerts).toHaveBeenNthCalledWith(2, 'PENDING', '', 1, 20)
    expect(store.formOpen).toBe(false)
  })
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setActivePinia(createPinia())
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    vi.mocked(fetchFacilityAlerts).mockResolvedValue(page)
  })

  afterEach(() => vi.useRealTimers())

  it('discards a report arriving after switching to focus', async () => {
    let finish!: (value: Awaited<ReturnType<typeof fetchFacilityHealthReport>>) => void
    vi.mocked(fetchFacilityHealthReport).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    vi.mocked(fetchFacilityFocus).mockResolvedValue([])
    const store = useFacilityStore()
    const old = store.showReport()
    await store.showFocus()
    finish({ generatedAt: '', activeAlertCount: 1, affectedFacilityCount: 1,
      warningCount: 1, severeCount: 0, emergencyCount: 0, pendingCount: 1, confirmedCount: 0,
      overallHealth: 'NO_ACTIVE_ALERT', overallHealthName: '', summary: 'old report', facilities: [] })
    await old
    expect(store.viewMode).toBe('focus')
    expect(store.focusItems).toEqual([])
    expect(store.report).toBeNull()
    expect(store.polling).toBe(false)
  })

  it('discards an old filter response and fetches the new status immediately', async () => {
    let finish!: (value: FacilityAlertPage) => void
    vi.mocked(fetchFacilityAlerts).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const store = useFacilityStore()
    const old = store.refresh()
    await store.selectStatus('CONFIRMED')
    finish({ ...page, total: 999 })
    await old
    expect(fetchFacilityAlerts).toHaveBeenLastCalledWith('CONFIRMED', '', 0, 20)
    expect(store.pageData?.total).toBe(0)
  })

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
      alertId: '42', facilityName: '闽江大桥', metricName: '主梁应变',
      alarmLevel: 'EMERGENCY', alarmLevelName: '紧急',
      collectTime: '2026-09-08T01:59:30Z', triggerTime: '2026-09-08T01:59:00Z',
      status: 'CONFIRMED', statusName: '处理中', remark: '【已确认】已派员',
      thresholdAssessment: '状态型异常', sourceConsistent: true,
    })
    const store = useFacilityStore()

    await store.transition('42', 'PENDING', 'CONFIRMED', '已派员')

    expect(transitionFacilityAlert).toHaveBeenCalledWith('42', {
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
