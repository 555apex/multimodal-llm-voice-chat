import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DispatchPlanCard from './DispatchPlanCard.vue'
import type { DispatchPlan } from '../types/dispatch'

const plan: DispatchPlan = {
  planId: 'DP-1',
  event: {
    eventType: '道路塌方', city: '福州', locationDescription: '五四路',
    severity: 'HIGH', description: '道路发生塌方',
  },
  summary: '建议先警戒再清障',
  tasks: [{ sequence: 1, action: '设置警戒', responsibleUnit: '属地单位' }],
  resources: [], warnings: [], status: 'WAITING_APPROVAL', version: 1,
  createdAt: '2026-07-21T00:00:00Z',
}

describe('DispatchPlanCard', () => {
  it('requires an explicit approval button click', async () => {
    const wrapper = mount(DispatchPlanCard, { props: { plan, busy: false } })

    await wrapper.find('.approve-button').trigger('click')

    expect(wrapper.emitted('decide')?.[0]).toEqual(['APPROVE'])
    expect(wrapper.text()).toContain('只有点击批准才会创建Mock工单')
  })
})
