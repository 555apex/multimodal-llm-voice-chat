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
  allocatedResources: [{
    resourceId: 'ER-XM-ROAD', resourceTypeCode: 'ROAD_RESCUE_TEAM',
    resourceTypeName: '公路抢险队伍', resourceName: '漳州市公路抢险队伍资源池',
    sourceCityCode: '350600', sourceCityName: '漳州', quantity: 1, unit: '组',
    purpose: '设置警戒并开展清障', estimatedDistanceKm: 0, dispatchScope: 'LOCAL',
  }],
  rescuePlan: '建议先警戒再清障\n\n数据库资源调度安排：\n- 同城调度：厦门公路抢险资源池 1组，城市级估算距离0公里。',
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
    expect(wrapper.text()).not.toContain('资源需求')
    expect(wrapper.text()).toContain('漳州市公路抢险队伍')
    expect(wrapper.text()).not.toContain('漳州市公路抢险队伍资源池')
    expect(wrapper.text()).toContain('资源来自数据库库存并已完成软占用')
    expect(wrapper.text()).toContain('调度城市：漳州')
    expect(wrapper.text()).toContain('建议先警戒再清障')
    expect(wrapper.text()).not.toContain('同城调度')
    expect(wrapper.text()).not.toContain('城市级估算')
    expect(wrapper.text()).not.toContain('数据库资源调度安排')
    expect(wrapper.findAll('.dispatch-section > strong').map(item => item.text())).toEqual([
      '救援方案', '实际匹配资源',
    ])
  })
})
