import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TrafficResultPanel from './TrafficResultPanel.vue'
import type { TrafficQueryResult } from '../types/traffic'
import { createPinia, setActivePinia } from 'pinia'
import { useAgentStore } from '../stores/agent'

function result(): TrafficQueryResult {
  return {
    queryType: 'OD_OVERVIEW', title: '福州、厦门城市OD综合分析',
    summary: '已完成所选城市的卡口七日统计。福州市流量相对较高，重点通道详见下表。',
    routeSummaries: [], segments: [], capacityRows: [], periodDays: 7,
    totalSegmentCount: 0, displayedSegmentCount: 0, truncated: false,
    selectedRegions: [{ regionCode: '350100', regionName: '福州市' }, { regionCode: '350200', regionName: '厦门市' }],
    odCityFlowRows: [{ regionCode: '350100', regionName: '福州市', checkpointCount: 2,
      weeklyTotalFlow: 12000, dailyAverageFlow: 1700, averageSpeedKmh: 25.5 }],
    odChannelRows: Array.from({ length: 12 }, (_, i) => ({
      routeCode: 'G' + (100 + i), routeName: '测试路线', weeklyTotalFlow: 1000,
      carWeeklyFlow: 700, busWeeklyFlow: 100, truckWeeklyFlow: 200,
    })),
    source: 'MYSQL', acquiredAt: '2026-09-03T04:00:00Z', warnings: ['厦门市暂无卡口数据'], traceId: 'od',
  }
}

describe('城市OD七日统计', () => {
  it('renders exact table headings, numbers, scope and summary before both tables', () => {
    const wrapper = mount(TrafficResultPanel, { props: { result: result() } })
    expect(wrapper.findAll('h3').map(h => h.text())).toEqual(['城市区域流量不平衡', '城市交通关键OD通道'])
    expect(wrapper.find('.od-city-table').text()).toContain('12,000')
    expect(wrapper.find('.od-city-table').text()).toContain('1,700 辆/日')
    expect(wrapper.find('.od-city-table').text()).toContain('25.50 km/h')
    expect(wrapper.find('.od-scope').text()).toContain('福州市、厦门市')
    expect(wrapper.find('.od-warning').text()).toContain('厦门市暂无卡口数据')
    expect(wrapper.html().indexOf('traffic-summary')).toBeLessThan(wrapper.html().indexOf('od-city-table'))
    expect(wrapper.text()).not.toContain('Top5')
    expect(wrapper.findAll('canvas')).toHaveLength(0)
    expect(wrapper.find('.od-channel-table td[data-label="小型客车总流量"]').text()).toBe('700 辆')
  })

  it('expands all routes without changing totals, then resets for another result', async () => {
    const wrapper = mount(TrafficResultPanel, { props: { result: result() } })
    expect(wrapper.findAll('.od-channel-table tbody tr')).toHaveLength(10)
    await wrapper.find('.od-expand-button').trigger('click')
    expect(wrapper.findAll('.od-channel-table tbody tr')).toHaveLength(12)
    expect(wrapper.find('.od-expand-button').attributes('aria-expanded')).toBe('true')
    await wrapper.find('.od-expand-button').trigger('click')
    expect(wrapper.findAll('.od-channel-table tbody tr')).toHaveLength(10)
    await wrapper.setProps({ result: { ...result(), odChannelRows: result().odChannelRows?.slice(0, 2) } })
    expect(wrapper.find('.od-expand-button').exists()).toBe(false)
    expect(wrapper.text()).toContain('共2条路线')
  })

  it.each(['OD_CITY_FLOW', 'OD_KEY_CHANNELS'] as const)('selects only the requested table for %s', type => {
    const value = result()
    value.queryType = type
    if (type === 'OD_CITY_FLOW') value.odChannelRows = []
    else value.odCityFlowRows = []
    const wrapper = mount(TrafficResultPanel, { props: { result: value, compact: true } })
    expect(wrapper.findAll('table')).toHaveLength(1)
    expect(wrapper.find('.traffic-summary').exists()).toBe(false)
    expect(wrapper.find('.traffic-truncated').exists()).toBe(false)
  })

  it('renders zero and empty data without inventing congestion', () => {
    const value = result()
    value.odCityFlowRows![0].weeklyTotalFlow = 0
    value.odCityFlowRows![0].averageSpeedKmh = 0
    const wrapper = mount(TrafficResultPanel, { props: { result: value } })
    expect(wrapper.find('.od-city-table').text()).toContain('0.00 km/h')
    expect(wrapper.find('.od-city-table').text()).not.toContain('拥堵')
    const empty = mount(TrafficResultPanel, { props: { result: { ...value, odCityFlowRows: [], odChannelRows: [] } } })
    expect(empty.find('.empty-result').text()).toContain('暂无')
  })

  it('keeps OD tables out of the dedicated speech channel', () => {
    setActivePinia(createPinia())
    const store = useAgentStore()
    const id = 'od-speech'
    store.messages.push({ id, role: 'assistant', content: '', status: 'pending' })
    const data = result()
    store.applyEvent(id, { name: 'answer.delta', data: { content: data.summary } })
    store.applyEvent(id, { name: 'result.traffic', data })
    store.applyEvent(id, { name: 'answer.speech', data: { content: data.summary } })
    expect(store.messages.at(-1)?.speechText).toBe(data.summary)
    expect(store.messages.at(-1)?.traffic?.odChannelRows).toHaveLength(12)
    expect(store.messages.at(-1)?.speechText).not.toContain('700')
  })
})
