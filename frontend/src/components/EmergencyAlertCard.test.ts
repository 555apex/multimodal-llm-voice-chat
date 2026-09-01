import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmergencyAlertCard from './EmergencyAlertCard.vue'
import type { EmergencyWorkflowItem } from '../types/dispatch'

const item: EmergencyWorkflowItem = {
  currentStage: 'LEVEL_1',
  workflowStatus: 'WAITING_GENERATION',
  workflowVersion: 0,
  event: {
    eventId: '202607280000000001',
    customId: 'AGT20260728EVT000000000000000001',
    occurrenceTime: '2026-07-28T00:00:00Z',
    eventType: 'DT01',
    eventTypeName: '崩塌',
    description: '福州市某道路发生边坡崩塌',
  },
  timeline: [],
}

const plan = {
  planId: 'DP-1', event: item.event,
  suggestedResources: [{ resourceType: '抢险队伍', resourceName: '道路抢险人员', quantity: 1, unit: '组', purpose: '现场抢通' }],
  rescuePlan: '先警戒，再抢通。', status: 'WAITING_APPROVAL' as const, version: 1,
  createdAt: '2026-07-28T00:00:00Z', updatedAt: '2026-07-28T00:00:01Z',
}

describe('EmergencyAlertCard', () => {
  it('starts official dispatch generation from the level-one inbox', async () => {
    const wrapper = mount(EmergencyAlertCard, {
      props: { item, stage: 'LEVEL_1', busy: false, errorMessage: '' },
    })

    await wrapper.find('.generate-dispatch-button').trigger('click')

    expect(wrapper.emitted('generate')).toHaveLength(1)
    expect(wrapper.text()).toContain('一级现场处置')
  })

  it('requires a reason and a second confirmation before no-dispatch', async () => {
    const wrapper = mount(EmergencyAlertCard, {
      props: { item, stage: 'LEVEL_1', busy: false, errorMessage: '' },
    })

    await wrapper.find('.no-dispatch-button').trigger('click')
    await wrapper.find('#no-dispatch-reason').setValue('现场已经自行恢复')
    await wrapper.find('.danger-confirm-button').trigger('click')

    expect(wrapper.text()).toContain('请再次确认')
    expect(wrapper.emitted('noDispatch')).toBeUndefined()

    await wrapper.find('.danger-confirm-button').trigger('click')
    expect(wrapper.emitted('noDispatch')?.[0]).toEqual(['现场已经自行恢复'])
  })

  it('recovers a pre-upgrade plan into the three-level workflow before approval', async () => {
    const wrapper = mount(EmergencyAlertCard, {
      props: {
        item: { ...item, currentPlan: plan, workflowStatus: 'WAITING_GENERATION' },
        stage: 'LEVEL_1', busy: false, errorMessage: '',
      },
    })
    expect(wrapper.text()).toContain('纳入三级流程')
    expect(wrapper.find('.dispatch-card').exists()).toBe(false)
    await wrapper.find('.dispatch-generating-state button').trigger('click')
    expect(wrapper.emitted('generate')).toHaveLength(1)
  })

  it('requires a feasible structured review before level two can pass', async () => {
    const level2 = {
      ...item, workflowId: 'WF-1', currentStage: 'LEVEL_2' as const,
      workflowStatus: 'WAITING_LEVEL_2_REVIEW' as const, workflowVersion: 2,
      currentPlan: { ...plan, event: item.event },
    }
    const wrapper = mount(EmergencyAlertCard, {
      props: { item: level2, stage: 'LEVEL_2', busy: false, errorMessage: '' },
    })
    const selects = wrapper.findAll('.workflow-review-form select')
    const textareas = wrapper.findAll('.workflow-review-form textarea')
    await selects[1].setValue('NEEDS_ADJUSTMENT')
    await textareas[0].setValue('预计影响主线交通两小时')
    await textareas[2].setValue('建议补充资源')
    expect(wrapper.find('.approve-button').attributes('disabled')).toBeDefined()

    await wrapper.find('.reject-button').trigger('click')
    expect(wrapper.emitted('review')?.[0]?.[0]).toMatchObject({
      decision: 'REJECT', resourceFeasibility: 'NEEDS_ADJUSTMENT', comment: '建议补充资源',
    })

    await selects[1].setValue('FEASIBLE')
    await wrapper.find('.approve-button').trigger('click')
    expect(wrapper.emitted('review')?.[1]?.[0]).toMatchObject({
      decision: 'APPROVE', resourceFeasibility: 'FEASIBLE',
      impactAssessment: '预计影响主线交通两小时',
    })
  })

  it('shows level two opinion and supports final publish or return', async () => {
    const level3: EmergencyWorkflowItem = {
      ...item, workflowId: 'WF-1', currentStage: 'LEVEL_3',
      workflowStatus: 'WAITING_LEVEL_3_DECISION', workflowVersion: 3,
      currentPlan: { ...plan, event: item.event },
      professionalReview: {
        reviewId: 'PR-1', workflowId: 'WF-1', planId: 'DP-1', planVersion: 1,
        status: 'PASSED', eventSeverity: 'LARGER', resourceFeasibility: 'FEASIBLE',
        impactAssessment: '影响主线交通', coordinationRequirements: '协调交警',
        reviewOpinion: '方案可行', createdAt: '2026-07-28T00:00:00Z', updatedAt: '2026-07-28T00:00:01Z',
      },
    }
    const wrapper = mount(EmergencyAlertCard, {
      props: { item: level3, stage: 'LEVEL_3', busy: false, errorMessage: '' },
    })
    expect(wrapper.text()).toContain('三级省级决策')
    expect(wrapper.text()).toContain('省级最终批示')
    expect(wrapper.text()).toContain('方案可行')
    await wrapper.find('.approve-button').trigger('click')
    expect(wrapper.emitted('command')?.[0]).toEqual(['APPROVE', ''])

    await wrapper.find('.workflow-review-form textarea').setValue('补充夜间照明')
    await wrapper.find('.reject-button').trigger('click')
    expect(wrapper.emitted('command')?.[1]).toEqual(['REJECT', '补充夜间照明'])
  })
})
