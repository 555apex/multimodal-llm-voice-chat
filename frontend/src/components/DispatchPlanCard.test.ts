import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DispatchPlanCard from './DispatchPlanCard.vue'
import type { DispatchPlan } from '../types/dispatch'

const plan: DispatchPlan = {
  planId: 'DP-1',
  event: {
    eventId: '202607280000000001',
    customId: 'EVT-1',
    occurrenceTime: '2026-07-21T00:00:00Z',
    eventType: 'DT01',
    description: '福州五四路发生塌方',
  },
  suggestedResources: [
    {
      resourceType: '抢险队伍',
      resourceName: '道路抢险人员',
      quantity: 1,
      unit: '组',
      purpose: '设置警戒并开展清障',
    },
  ],
  rescuePlan: '建议先警戒再清障',
  status: 'WAITING_APPROVAL',
  version: 1,
  createdAt: '2026-07-21T00:00:00Z',
  updatedAt: '2026-07-21T00:00:00Z',
}

describe('DispatchPlanCard', () => {
  it('requires an explicit approval button click', async () => {
    const wrapper = mount(DispatchPlanCard, { props: { plan, busy: false } })

    await wrapper.find('.approve-button').trigger('click')

    expect(wrapper.emitted('decide')?.[0]).toEqual(['APPROVE', ''])
    expect(wrapper.text()).toContain('资源由模型基于通用知识建议')
  })
})
