import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NoticePanel from './NoticePanel.vue'
import { useEmergencyStore } from '../stores/emergency'
import { fetchWorkflowDetail, releaseWorkflowResources, fetchNotices } from '../api/workflowApi'
import type { EmergencyWorkflowItem } from '../types/dispatch'

vi.mock('../api/workflowApi', () => ({
  fetchWorkflowDetail: vi.fn(), releaseWorkflowResources: vi.fn(), fetchNotices: vi.fn(),
}))
const detail: EmergencyWorkflowItem = {
  workflowId: 'WF-1', workflowVersion: 5, workflowStatus: 'PUBLISHED', canReleaseResources: true,
  event: { eventId: 'INC-1', customId: 'INC-1', eventType: 'ET106',
    occurrenceTime: '2026-09-10T00:00:00Z', description: '事故详细描述仅在展开后显示' },
  timeline: [],
}
describe('NoticePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    setActivePinia(createPinia())
    useEmergencyStore().notices = { items: [{ workflowId: 'WF-1', eventId: 'INC-1',
      eventType: 'ET106', cityName: '福州', workflowStatus: 'PUBLISHED', completionStatus: 'PENDING' }],
      page: 0, size: 20, total: 1, pendingCount: 1, completedCount: 0 }
    vi.mocked(fetchWorkflowDetail).mockResolvedValue(detail)
  })
  it('releases from the collapsed summary with reason, confirmation and an idempotent retry', async () => {
    const store = useEmergencyStore()
    store.noticeLoading = true
    const wrapper = mount(NoticePanel)
    expect(wrapper.get('.quick-release-button').attributes('disabled')).toBeUndefined()
    await wrapper.get('.quick-release-button').trigger('click')
    await flushPromises()
    expect(fetchWorkflowDetail).toHaveBeenCalledWith('WF-1')
    expect(wrapper.find('.workflow-history-panel').exists()).toBe(false)
    expect(wrapper.get('.notice-summary-button').attributes('aria-expanded')).toBe('false')
    expect(releaseWorkflowResources).not.toHaveBeenCalled()
    expect(wrapper.get('.quick-release-actions button:last-child').attributes('disabled')).toBeDefined()
    await wrapper.get('.quick-release-form textarea').setValue('现场处置结束')
    await wrapper.get('.quick-release-actions button:last-child').trigger('click')
    expect(wrapper.text()).toContain('请再次确认')
    vi.mocked(releaseWorkflowResources).mockRejectedValueOnce(new Error('网络中断'))
    await wrapper.get('.quick-release-confirm').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络中断')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('现场处置结束')
    vi.mocked(releaseWorkflowResources).mockResolvedValue({ ...detail, canReleaseResources: false, resourcesReleased: true })
    vi.mocked(fetchNotices).mockResolvedValue({ items: [], page: 0, size: 20, total: 0, pendingCount: 0, completedCount: 1 })
    await wrapper.get('.quick-release-confirm').trigger('click')
    await flushPromises()
    const calls = vi.mocked(releaseWorkflowResources).mock.calls
    expect(calls[0].slice(0, 3)).toEqual(['WF-1', '现场处置结束', 5])
    expect(calls[0][3]).toBe(calls[1][3])
    expect(wrapper.text()).toContain('当前没有未办结记录')
  })

  it('shows exactly one entry point when expanding and collapsing, and cancels without submitting', async () => {
    const wrapper = mount(NoticePanel)
    expect(wrapper.findAll('.quick-release-button, .resource-release-control > button')).toHaveLength(1)
    await wrapper.get('.notice-summary-button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.quick-release-control').exists()).toBe(false)
    expect(wrapper.get('.resource-release-control > button').text()).toBe('归还全部资源')
    expect(wrapper.findAll('.quick-release-button, .resource-release-control > button')).toHaveLength(1)
    await wrapper.get('.notice-summary-button').trigger('click')
    expect(wrapper.find('.workflow-history-panel').exists()).toBe(false)
    expect(wrapper.findAll('.quick-release-button, .resource-release-control > button')).toHaveLength(1)
    await wrapper.get('.quick-release-button').trigger('click')
    await flushPromises()
    await wrapper.get('.quick-release-form textarea').setValue('取消本次操作')
    await wrapper.get('.quick-release-actions button:first-child').trigger('click')
    expect(wrapper.find('.quick-release-form').exists()).toBe(false)
    expect(releaseWorkflowResources).not.toHaveBeenCalled()
  })

  it('prevents release when the latest server state is already returned', async () => {
    vi.mocked(fetchWorkflowDetail).mockResolvedValue({ ...detail, canReleaseResources: false,
      resourceReleaseUnavailableReason: '资源已全部归还' })
    const wrapper = mount(NoticePanel)
    await wrapper.get('.quick-release-button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('资源已全部归还')
    expect(wrapper.find('.quick-release-form').exists()).toBe(false)
    expect(wrapper.find('.quick-release-button').exists()).toBe(false)
    expect(releaseWorkflowResources).not.toHaveBeenCalled()
  })

  it('does not show the summary action for completed or no-dispatch records', () => {
    const store = useEmergencyStore()
    store.notices!.items[0]!.completionStatus = 'COMPLETED'
    store.notices!.items[0]!.workflowStatus = 'NO_DISPATCH'
    const wrapper = mount(NoticePanel)
    expect(wrapper.find('.quick-release-control').exists()).toBe(false)
    expect(fetchWorkflowDetail).not.toHaveBeenCalled()
  })
  it('loads details only on expansion and preserves release input across polling and failed retry', async () => {
    const store = useEmergencyStore()
    const wrapper = mount(NoticePanel)
    expect(wrapper.text()).toContain('交通事故 · 福州')
    expect(wrapper.text()).not.toContain(detail.event.description)
    expect(fetchWorkflowDetail).not.toHaveBeenCalled()
    await wrapper.get('.notice-summary-button').trigger('click')
    await flushPromises()
    store.polling = true
    store.noticeLoading = true
    await flushPromises()
    expect(wrapper.get('.resource-release-control > button').attributes('disabled')).toBeUndefined()
    await wrapper.get('.resource-release-control > button').trigger('click')
    await wrapper.get('textarea').setValue('现场处置结束')
    await wrapper.get('.resource-release-form > div button:last-child').trigger('click')
    vi.mocked(releaseWorkflowResources).mockRejectedValueOnce(new Error('网络中断'))
    await wrapper.get('.approve-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络中断')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('现场处置结束')
    vi.mocked(releaseWorkflowResources).mockResolvedValue({ ...detail, canReleaseResources: false, resourcesReleased: true })
    vi.mocked(fetchNotices).mockResolvedValue({ items: [], page: 0, size: 20, total: 0, pendingCount: 0, completedCount: 1 })
    await wrapper.get('.approve-button').trigger('click')
    await flushPromises()
    expect(vi.mocked(releaseWorkflowResources).mock.calls[0][3])
      .toBe(vi.mocked(releaseWorkflowResources).mock.calls[1][3])
    expect(wrapper.text()).toContain('当前没有未办结记录')
    expect(store.notices?.completedCount).toBe(1)
  })
})
