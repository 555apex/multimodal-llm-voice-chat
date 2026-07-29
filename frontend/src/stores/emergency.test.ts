import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { decideDispatch } from '../api/dispatchApi'
import {
  fetchNextEmergency,
  generateEmergencyDispatch,
  markEmergencyNoDispatch,
} from '../api/emergencyApi'
import type { DispatchPlan, EmergencyAlert } from '../types/dispatch'
import { useAgentStore } from './agent'
import { useEmergencyStore } from './emergency'

vi.mock('../api/emergencyApi', () => ({
  fetchNextEmergency: vi.fn(),
  generateEmergencyDispatch: vi.fn(),
  markEmergencyNoDispatch: vi.fn(),
}))

vi.mock('../api/dispatchApi', () => ({
  decideDispatch: vi.fn(),
}))

const event = {
  eventId: '202607280000000001',
  customId: 'AGT20260728EVT000000000000000001',
  occurrenceTime: '2026-07-28T00:00:00Z',
  eventType: 'DT01',
  description: '边坡崩塌',
}

const waitingPlan: DispatchPlan = {
  planId: 'DP-1',
  event,
  suggestedResources: [{
    resourceType: '抢险队伍',
    resourceName: '道路抢险人员',
    quantity: 1,
    unit: '组',
    purpose: '现场警戒',
  }],
  rescuePlan: '先警戒，再抢通。',
  status: 'WAITING_APPROVAL',
  version: 1,
  createdAt: '2026-07-28T00:00:00Z',
  updatedAt: '2026-07-28T00:00:01Z',
}

const alert: EmergencyAlert = { event, pendingCount: 2 }

describe('emergency store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    sessionStorage.clear()
    setActivePinia(createPinia())
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls every five seconds only while the page is visible', async () => {
    vi.mocked(fetchNextEmergency).mockResolvedValue(alert)
    const store = useEmergencyStore()

    store.startPolling()
    await vi.waitFor(() => expect(fetchNextEmergency).toHaveBeenCalledTimes(1))
    await vi.advanceTimersByTimeAsync(5000)
    expect(fetchNextEmergency).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    })
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchNextEmergency).toHaveBeenCalledTimes(2)
    store.stopPolling()
  })

  it('generates a formal order without occupying the chat running state', async () => {
    vi.mocked(generateEmergencyDispatch).mockResolvedValue(waitingPlan)
    const agentStore = useAgentStore()
    const emergencyStore = useEmergencyStore()
    agentStore.running = true
    emergencyStore.alert = alert

    await emergencyStore.generate()

    expect(agentStore.running).toBe(true)
    expect(emergencyStore.alert?.dispatch).toEqual(waitingPlan)
  })

  it('switches to the next event after approval', async () => {
    const nextAlert: EmergencyAlert = {
      event: { ...event, eventId: '202607280000000002', customId: 'EVT-2' },
      pendingCount: 1,
    }
    vi.mocked(decideDispatch).mockResolvedValue({ ...waitingPlan, status: 'APPROVED' })
    vi.mocked(fetchNextEmergency).mockResolvedValue(nextAlert)
    const store = useEmergencyStore()
    store.alert = { ...alert, dispatch: waitingPlan }

    await store.decide('APPROVE')

    expect(store.alert?.event.eventId).toBe('202607280000000002')
    expect(fetchNextEmergency).toHaveBeenCalledTimes(1)
  })

  it('submits no-dispatch through the dedicated workflow and loads the next event', async () => {
    const nextAlert: EmergencyAlert = {
      event: { ...event, eventId: '202607280000000003', customId: 'EVT-3' },
      pendingCount: 1,
    }
    vi.mocked(markEmergencyNoDispatch).mockResolvedValue()
    vi.mocked(fetchNextEmergency).mockResolvedValue(nextAlert)
    const store = useEmergencyStore()
    store.alert = alert

    await store.markNoDispatch('现场已经自行恢复')

    expect(markEmergencyNoDispatch).toHaveBeenCalledWith(
      '202607280000000001',
      '现场已经自行恢复',
    )
    expect(store.alert?.event.eventId).toBe('202607280000000003')
  })
})
