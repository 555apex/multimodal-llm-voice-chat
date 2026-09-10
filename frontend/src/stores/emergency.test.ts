import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { generateEmergencyDispatch, markEmergencyNoDispatch } from '../api/emergencyApi'
import {
  decideLevel1Workflow,
  fetchWorkflowHistory,
  fetchWorkflowInbox,
  releaseWorkflowResources,
  retryNextEventClassification,
  fetchNotices,
} from '../api/workflowApi'
import type { DispatchPlan, EmergencyWorkflowItem, WorkflowInbox } from '../types/dispatch'
import { useAgentStore } from './agent'
import { useEmergencyStore } from './emergency'

vi.mock('../api/emergencyApi', () => ({
  fetchNextEmergency: vi.fn(),
  generateEmergencyDispatch: vi.fn(),
  markEmergencyNoDispatch: vi.fn(),
}))

vi.mock('../api/workflowApi', () => ({
  fetchWorkflowInbox: vi.fn(),
  decideLevel1Workflow: vi.fn(),
  reviewEmergencyWorkflow: vi.fn(),
  decideCommandWorkflow: vi.fn(),
  fetchWorkflowHistory: vi.fn(),
  releaseWorkflowResources: vi.fn(),
  correctWorkflowEventType: vi.fn(),
  retryNextEventClassification: vi.fn(),
  fetchNotices: vi.fn(),
}))

const event = {
  eventId: '202607280000000001', customId: 'EVT-1',
  occurrenceTime: '2026-07-28T00:00:00Z', eventType: 'DT01', description: '边坡崩塌',
}

const waitingPlan: DispatchPlan = {
  planId: 'DP-1', event,
  suggestedResources: [{ resourceType: '抢险队伍', resourceName: '道路抢险人员', quantity: 1, unit: '组', purpose: '现场警戒' }],
  rescuePlan: '先警戒，再抢通。', status: 'WAITING_APPROVAL', version: 1,
  createdAt: '2026-07-28T00:00:00Z', updatedAt: '2026-07-28T00:00:01Z',
}

const item: EmergencyWorkflowItem = {
  workflowId: 'WF-1', currentStage: 'LEVEL_1',
  workflowStatus: 'WAITING_LEVEL_1_SUBMISSION', workflowVersion: 1,
  event, currentPlan: waitingPlan, timeline: [],
}

const inbox: WorkflowInbox = { item, counts: { level1: 2, level2: 1, level3: 1 } }

describe('emergency workflow store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    sessionStorage.clear()
    setActivePinia(createPinia())
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    vi.mocked(fetchWorkflowInbox).mockResolvedValue(inbox)
  })

  afterEach(() => vi.useRealTimers())

  it('polls the selected stage every five seconds only while visible', async () => {
    const store = useEmergencyStore()
    store.startPolling()
    await vi.waitFor(() => expect(fetchWorkflowInbox).toHaveBeenCalledTimes(1))
    await vi.advanceTimersByTimeAsync(5000)
    expect(fetchWorkflowInbox).toHaveBeenCalledTimes(2)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchWorkflowInbox).toHaveBeenCalledTimes(2)
    store.stopPolling()
  })

  it('generates a formal plan without occupying the chat running state', async () => {
    vi.mocked(generateEmergencyDispatch).mockResolvedValue(waitingPlan)
    const agentStore = useAgentStore()
    const store = useEmergencyStore()
    agentStore.running = true
    store.item = { ...item, workflowId: undefined, currentPlan: undefined, workflowVersion: 0 }
    await store.generate()
    expect(agentStore.running).toBe(true)
    expect(generateEmergencyDispatch).toHaveBeenCalledWith(event.eventId)
    expect(fetchWorkflowInbox).toHaveBeenCalled()
  })

  it('automatically retries one stale generation when the page observes it', async () => {
    vi.setSystemTime(new Date('2026-07-28T00:10:00Z'))
    const stuckItem: EmergencyWorkflowItem = {
      ...item,
      workflowStatus: 'GENERATING',
      currentPlan: {
        ...waitingPlan,
        status: 'GENERATING',
        updatedAt: '2026-07-28T00:00:00Z',
      },
    }
    vi.mocked(fetchWorkflowInbox)
      .mockResolvedValueOnce({ ...inbox, item: stuckItem })
      .mockResolvedValueOnce(inbox)
    vi.mocked(generateEmergencyDispatch).mockResolvedValue(waitingPlan)
    const store = useEmergencyStore()

    await store.refresh()

    expect(generateEmergencyDispatch).toHaveBeenCalledTimes(1)
    expect(generateEmergencyDispatch).toHaveBeenCalledWith(event.eventId)
    expect(fetchWorkflowInbox).toHaveBeenCalledTimes(2)
  })

  it('does not retry a generation before the backend stale timeout', async () => {
    vi.setSystemTime(new Date('2026-07-28T00:01:00Z'))
    vi.mocked(fetchWorkflowInbox).mockResolvedValue({
      ...inbox,
      item: {
        ...item,
        workflowStatus: 'GENERATING',
        currentPlan: { ...waitingPlan, status: 'GENERATING', updatedAt: '2026-07-28T00:00:00Z' },
      },
    })
    const store = useEmergencyStore()

    await store.refresh()

    expect(generateEmergencyDispatch).not.toHaveBeenCalled()
  })

  it('switches between the three pending queues', async () => {
    const store = useEmergencyStore()
    await store.selectStage('LEVEL_2')
    expect(store.selectedStage).toBe('LEVEL_2')
    expect(fetchWorkflowInbox).toHaveBeenCalledWith('LEVEL_2')
  })

  it('submits level one and refreshes the current queue', async () => {
    vi.mocked(decideLevel1Workflow).mockResolvedValue(item)
    const store = useEmergencyStore()
    store.item = item
    await store.decideLevel1('SUBMIT')
    expect(decideLevel1Workflow).toHaveBeenCalledWith('WF-1', 'SUBMIT', '', 1)
    expect(fetchWorkflowInbox).toHaveBeenCalled()
  })

  it('keeps no-dispatch as a level-one-only path', async () => {
    vi.mocked(markEmergencyNoDispatch).mockResolvedValue()
    const store = useEmergencyStore()
    store.item = { ...item, workflowId: undefined, currentPlan: undefined }
    await store.markNoDispatch('现场已经自行恢复')
    expect(markEmergencyNoDispatch).toHaveBeenCalledWith(event.eventId, '现场已经自行恢复')
  })

  it('loads completed workflow history independently from pending queues', async () => {
    vi.mocked(fetchNotices).mockResolvedValue({ items: [], page: 0, size: 20, total: 1, pendingCount: 1, completedCount: 0 })
    const store = useEmergencyStore()
    await store.showHistory()
    expect(store.viewMode).toBe('history')
    expect(store.notices?.total).toBe(1)
    expect(fetchNotices).toHaveBeenCalledWith('PENDING', 0)
  })

  it('keeps the selected notice page during polling and discards stale filter responses', async () => {
    const store = useEmergencyStore()
    const result = { items: [{ workflowId: 'WF-N', eventId: 'N', eventType: 'ET106',
      workflowStatus: 'PUBLISHED' as const, completionStatus: 'COMPLETED' as const }],
      page: 2, size: 20, total: 45, pendingCount: 1, completedCount: 45 }
    vi.mocked(fetchNotices).mockResolvedValue(result)
    store.viewMode = 'history'
    await store.loadNotices(2)
    await store.refresh()
    expect(fetchNotices).toHaveBeenLastCalledWith('PENDING', 2)
    let resolveOld!: (value: typeof result) => void
    vi.mocked(fetchNotices).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const old = store.loadNotices(2)
    await store.selectCompletion('COMPLETED')
    resolveOld({ ...result, total: 999 })
    await old
    expect(store.notices?.total).toBe(45)
    expect(store.completionStatus).toBe('COMPLETED')
    expect(store.noticePage).toBe(0)
    expect(store.noticeLoading).toBe(false)
  })

  it('releases a published plan and refreshes its history page', async () => {
    vi.mocked(releaseWorkflowResources).mockResolvedValue({ ...item, resourcesReleased: true })
    vi.mocked(fetchWorkflowHistory).mockResolvedValue({ items: [], page: 2, size: 20, total: 0 })
    const store = useEmergencyStore()
    store.viewMode = 'history'
    store.history = { items: [item], page: 2, size: 20, total: 41 }

    await store.releaseResources('WF-1', 4, '演练完成后资源归队')

    expect(releaseWorkflowResources).toHaveBeenCalledWith('WF-1', '演练完成后资源归队', 4)
    expect(fetchWorkflowHistory).toHaveBeenCalledWith(2, 20)
  })

  it('lets the operator immediately retry one failed classification', async () => {
    vi.mocked(retryNextEventClassification).mockResolvedValue(true)
    const store = useEmergencyStore()

    await store.retryClassification()

    expect(retryNextEventClassification).toHaveBeenCalledTimes(1)
    expect(fetchWorkflowInbox).toHaveBeenCalledWith('LEVEL_1')
  })
})
