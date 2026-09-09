import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchWorkflowInbox } from './api/workflowApi'
import { fetchFacilityAlerts } from './api/facilityApi'
import { fetchSpeechCapabilities } from './api/speechApi'
import App from './App.vue'
import { useEmergencyStore } from './stores/emergency'
import { useSpeechStore } from './stores/speech'

vi.mock('./api/emergencyApi', () => ({
  fetchNextEmergency: vi.fn(),
  generateEmergencyDispatch: vi.fn(),
  markEmergencyNoDispatch: vi.fn(),
}))

vi.mock('./api/workflowApi', () => ({
  fetchWorkflowInbox: vi.fn(),
  decideLevel1Workflow: vi.fn(),
  reviewEmergencyWorkflow: vi.fn(),
  decideCommandWorkflow: vi.fn(),
  fetchWorkflowHistory: vi.fn(),
}))

vi.mock('./api/speechApi', () => ({
  fetchSpeechCapabilities: vi.fn(),
  synthesizeSpeech: vi.fn(),
  transcribeSpeech: vi.fn(),
}))

vi.mock('./api/facilityApi', () => ({
  fetchFacilityAlerts: vi.fn(),
  fetchFacilityHealthReport: vi.fn(),
  fetchFacilityFocus: vi.fn(),
  transitionFacilityAlert: vi.fn(),
}))

const workflowItem = {
  workflowStatus: 'WAITING_GENERATION' as const,
  workflowVersion: 0,
  event: {
    eventId: '202608130000000001',
    customId: 'EVT-1',
    eventType: 'DT01',
    eventTypeName: '崩塌',
    description: '福州市某道路发生边坡崩塌',
  },
  timeline: [],
}

function mountApp() {
  const pinia = createPinia()
  return { wrapper: mount(App, { global: { plugins: [pinia] } }), pinia }
}

describe('command dashboard shell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    sessionStorage.clear()
    vi.mocked(fetchWorkflowInbox).mockResolvedValue({
      item: workflowItem,
      counts: { level1: 17, level2: 0, level3: 0 },
    })
    vi.mocked(fetchSpeechCapabilities).mockResolvedValue({
      asrAvailable: true,
      ttsAvailable: true,
      maxRecordingSeconds: 60,
      maxAudioBytes: 10485760,
    })
    vi.mocked(fetchFacilityAlerts).mockResolvedValue({
      items: [{
        alertId: '42',
        facilityName: '闽江大桥',
        metricName: '主梁应变',
        actualValue: 12.5,
        thresholdMax: 10,
        alarmLevel: 'EMERGENCY',
        alarmLevelName: '紧急',
        collectTime: '2026-09-08T01:59:30Z',
        triggerTime: '2026-09-08T01:59:00Z',
        status: 'PENDING',
        statusName: '待确认',
        thresholdAssessment: '超过上限',
        sourceConsistent: true,
      }],
      page: 0,
      size: 20,
      total: 1,
      counts: {
        pending: 6,
        confirmed: 1,
        closed: 2,
        activeWarning: 3,
        activeSevere: 2,
        activeEmergency: 2,
      },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts with the dashboard visible and the agent drawer closed', async () => {
    const { wrapper } = mountApp()
    await flushPromises()

    expect(wrapper.find('.command-dashboard').exists()).toBe(true)
    expect(wrapper.get('.agent-drawer').classes()).not.toContain('open')
    expect(wrapper.get('.assistant-orb').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.command-footer-note').exists()).toBe(false)
    expect(fetchWorkflowInbox).toHaveBeenCalledOnce()

    wrapper.unmount()
  })

  it('opens from the orb and closes from the titlebar or Escape', async () => {
    const { wrapper } = mountApp()
    await wrapper.get('.assistant-orb').trigger('click')
    expect(wrapper.get('.agent-drawer').classes()).toContain('open')

    await wrapper.get('.drawer-close').trigger('click')
    expect(wrapper.get('.agent-drawer').classes()).not.toContain('open')

    await wrapper.get('.assistant-orb').trigger('click')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.agent-drawer').classes()).not.toContain('open')

    wrapper.unmount()
  })

  it('keeps pending events on the emergency tab without forcing a tab change', async () => {
    const { wrapper } = mountApp()
    await flushPromises()
    await wrapper.get('.assistant-orb').trigger('click')

    const tabs = wrapper.findAll('.agent-mode-tabs button')
    expect(tabs[0].classes()).toContain('active')
    expect(tabs[1].text()).toContain('17')
    expect(wrapper.find('.emergency-tab-badge').exists()).toBe(true)
    expect(wrapper.get('.drawer-chat').isVisible()).toBe(true)

    await tabs[1].trigger('click')
    expect(tabs[1].classes()).toContain('active')
    expect(wrapper.get('.emergency-workspace').isVisible()).toBe(true)

    wrapper.unmount()
  })

  it('opens facility warnings as the third function and shows pending abnormal facts', async () => {
    const { wrapper } = mountApp()
    await flushPromises()
    await wrapper.get('.assistant-orb').trigger('click')

    const tabs = wrapper.findAll('.agent-mode-tabs button')
    expect(tabs).toHaveLength(3)
    expect(tabs[2].text()).toContain('设施预警')
    expect(tabs[2].text()).toContain('6')
    await tabs[2].trigger('click')

    expect(wrapper.get('.facility-warning-workspace').isVisible()).toBe(true)
    expect(wrapper.get('.facility-alert-card').text()).toContain('闽江大桥')
    expect(wrapper.get('.facility-alert-card').text()).toContain('主梁应变')
    expect(wrapper.get('.facility-alert-card').text()).toContain('超过上限')

    wrapper.unmount()
  })

  it('introduces the agent and shows a concise capability list', async () => {
    const { wrapper } = mountApp()
    await wrapper.get('.assistant-orb').trigger('click')

    expect(wrapper.get('.message-list').text()).toContain('我是路智通')
    expect(wrapper.get('.human-capabilities').text()).toContain('实时路况问询')
    expect(wrapper.get('.human-capabilities').text()).toContain('区域交通研判')
    expect(wrapper.get('.human-capabilities').text()).toContain('应急处置与设施预警')
    expect(wrapper.get('.human-capabilities').text()).toContain('语音智能交互')

    wrapper.unmount()
  })

  it('shows only four representative example questions', async () => {
    const { wrapper } = mountApp()
    await wrapper.get('.assistant-orb').trigger('click')

    const examples = wrapper.findAll('.example-prompts button')
    expect(examples).toHaveLength(4)
    expect(examples.map((button) => button.text())).toEqual([
      '福建省目前整体交通态势如何？',
      'G104 北京—平潭当前通行情况如何？',
      '福建省哪些国省道路段接近通行瓶颈？',
      '福州的出行主要联系哪些城市？',
    ])

    wrapper.unmount()
  })

  it('keeps polling while closed and stops speech on close or emergency switch', async () => {
    const { wrapper, pinia } = mountApp()
    await flushPromises()
    const emergencyStore = useEmergencyStore(pinia)
    const speechStore = useSpeechStore(pinia)
    const stop = vi.spyOn(speechStore, 'stop')

    expect(emergencyStore.timerId).not.toBe(0)
    await vi.advanceTimersByTimeAsync(5000)
    expect(fetchWorkflowInbox).toHaveBeenCalledTimes(2)

    await wrapper.get('.assistant-orb').trigger('click')
    await wrapper.findAll('.agent-mode-tabs button')[1].trigger('click')
    expect(stop).toHaveBeenCalled()

    await wrapper.get('.drawer-close').trigger('click')
    expect(stop).toHaveBeenCalledTimes(2)
    expect(emergencyStore.timerId).not.toBe(0)

    wrapper.unmount()
  })

  it('preserves the input draft and selected tab after closing the drawer', async () => {
    const { wrapper } = mountApp()
    await wrapper.get('.assistant-orb').trigger('click')
    await wrapper.get('.chat-composer textarea').setValue('尚未发送的路况问题')
    await wrapper.findAll('.agent-mode-tabs button')[1].trigger('click')
    await wrapper.get('.drawer-close').trigger('click')
    await wrapper.get('.assistant-orb').trigger('click')

    expect(wrapper.findAll('.agent-mode-tabs button')[1].classes()).toContain('active')
    await wrapper.findAll('.agent-mode-tabs button')[0].trigger('click')
    expect((wrapper.get('.chat-composer textarea').element as HTMLTextAreaElement).value)
      .toBe('尚未发送的路况问题')

    wrapper.unmount()
  })
})
