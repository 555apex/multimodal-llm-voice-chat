import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { WorkflowHistoryPage } from '../types/dispatch'
import WorkflowHistoryPanel from './WorkflowHistoryPanel.vue'

const history: WorkflowHistoryPage = {
  page: 0, size: 20, total: 1,
  items: [{
    workflowId: 'WF-1', currentStage: null, workflowStatus: 'PUBLISHED', workflowVersion: 4,
    event: {
      eventId: '202607280000000001', customId: 'EVT-1',
      occurrenceTime: '2026-08-19T00:00:00Z', eventType: 'DT01', description: '边坡崩塌',
    },
    currentPlan: {
      planId: 'DP-1', event: {
        eventId: '202607280000000001', customId: 'EVT-1',
        occurrenceTime: '2026-08-19T00:00:00Z', eventType: 'DT01', description: '边坡崩塌',
      },
      suggestedResources: [{ resourceType: '公路抢险队伍', resourceName: '福州抢险资源池', quantity: 1, unit: '组', purpose: '道路抢通' }],
      allocatedResources: [{
        resourceId: 'ER-FZ-ROAD', resourceTypeCode: 'ROAD_RESCUE_TEAM',
        resourceTypeName: '公路抢险队伍', resourceName: '福州抢险资源池',
        sourceCityCode: '350100', sourceCityName: '福州', quantity: 1, unit: '组',
        purpose: '道路抢通', estimatedDistanceKm: 0, dispatchScope: 'LOCAL',
      }],
      rescuePlan: '设置警戒并开展抢通。', status: 'APPROVED', version: 1,
      createdAt: '2026-08-19T00:00:00Z', updatedAt: '2026-08-19T00:10:00Z',
    },
    commandDecision: {
      decisionId: 'CD-1', workflowId: 'WF-1', reviewId: 'PR-1', planId: 'DP-1', planVersion: 1,
      status: 'PUBLISHED', decisionOpinion: '同意发布',
      noticeSnapshot: {
        noticeNumber: 'NT-1', title: '应急处置通告',
        event: {
          eventId: '202607280000000001', customId: 'EVT-1',
          occurrenceTime: '2026-08-19T00:00:00Z', eventType: 'DT01', description: '边坡崩塌',
        },
        planId: 'DP-1', planVersion: 1, suggestedResources: [],
        allocatedResources: [{
          resourceId: 'ER-XM-EXCAVATOR', resourceTypeCode: 'EXCAVATOR',
          resourceTypeName: '挖掘机', resourceName: '厦门市挖掘机资源池',
          sourceCityCode: '350200', sourceCityName: '厦门', quantity: 2, unit: '台',
          purpose: '道路抢通', estimatedDistanceKm: 0, dispatchScope: 'LOCAL',
        }],
        rescuePlan: '设置警戒并开展抢通。\n\n数据库资源调度安排：\n- 同城调度：福州抢险资源池1组。', eventSeverity: 'LARGER',
        impactAssessment: '影响主线交通', professionalOpinion: '方案可行',
        commandOpinion: '同意发布', publishedAt: '2026-08-19T00:10:00Z',
      },
      createdAt: '2026-08-19T00:05:00Z', updatedAt: '2026-08-19T00:10:00Z',
    },
    timeline: [{
      actionId: 'AC-1', workflowId: 'WF-1', actionType: 'LEVEL_3_PUBLISHED',
      fromStage: 'LEVEL_3', toStage: null,
      fromStatus: 'WAITING_LEVEL_3_DECISION', toStatus: 'PUBLISHED',
      planId: 'DP-1', planVersion: 1, comment: '同意发布',
      idempotencyKey: 'idem-1', createdAt: '2026-08-19T00:10:00Z',
    }],
  }],
}

describe('WorkflowHistoryPanel', () => {
  it('renders the immutable notice and timeline as read-only content', () => {
    const wrapper = mount(WorkflowHistoryPanel, { props: { history, busy: false } })
    expect(wrapper.text()).toContain('应急处置通告')
    expect(wrapper.text()).toContain('NT-1')
    expect(wrapper.text()).toContain('省级批准并通告')
    expect(wrapper.text()).toContain('省级批示')
    expect(wrapper.text()).toContain('设置警戒并开展抢通。')
    expect(wrapper.text()).toContain('厦门市挖掘机 2台')
    expect(wrapper.text()).not.toContain('厦门市挖掘机资源池')
    expect(wrapper.text()).not.toContain('厦门厦门市挖掘机资源池')
    expect(wrapper.text()).not.toContain('数据库资源调度安排')
    expect(wrapper.find('textarea').exists()).toBe(false)
  })

  it('requires a reason and a second confirmation before full release', async () => {
    const wrapper = mount(WorkflowHistoryPanel, { props: { history, busy: false } })
    await wrapper.get('.resource-release-control > button').trigger('click')
    await wrapper.get('.resource-release-form textarea').setValue('现场处置完成，资源归队')
    await wrapper.get('.resource-release-form > div button:last-child').trigger('click')
    expect(wrapper.text()).toContain('请再次确认')
    await wrapper.get('.resource-release-form .approve-button').trigger('click')
    expect(wrapper.emitted('releaseResources')?.[0]).toEqual([
      'WF-1', 4, '现场处置完成，资源归队',
    ])
  })

  it('shows the saved no-dispatch reason and type-correction audit label', () => {
    const noDispatch: WorkflowHistoryPage = {
      ...history,
      items: [{
        ...history.items[0],
        workflowStatus: 'NO_DISPATCH',
        terminalReason: '现场已恢复通行，无需调集资源',
        commandDecision: undefined,
        timeline: [{
          ...history.items[0].timeline[0],
          actionType: 'EVENT_TYPE_CORRECTED',
          comment: '由拥堵更正为交通事故',
        }],
      }],
    }
    const wrapper = mount(WorkflowHistoryPanel, { props: { history: noDispatch, busy: false } })
    expect(wrapper.text()).toContain('无需调度原因：现场已恢复通行，无需调集资源')
    expect(wrapper.text()).toContain('一级人工更正事件类型')
  })
})
