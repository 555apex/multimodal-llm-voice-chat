import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import TrafficResultPanel from './TrafficResultPanel.vue'
import type { TrafficQueryResult } from '../types/traffic'
import { useAgentStore } from '../stores/agent'

const base = (): Omit<TrafficQueryResult, 'queryType' | 'title'> => ({
  summary: '福州市的跨市出行联系呈现清晰的目的地倾向结构。',
  routeSummaries: [], segments: [], capacityRows: [],
  totalSegmentCount: 0, displayedSegmentCount: 0, truncated: false,
  source: 'MYSQL', acquiredAt: '2026-09-07T04:00:00Z', warnings: [], traceId: 'od',
})

function destinationResult(): TrafficQueryResult {
  return {
    ...base(), queryType: 'OD_DESTINATION_TENDENCY', title: '福州市目的地联系倾向分析',
    selectedRegions: [{ regionCode: '350100', regionName: '福州市' }],
    odDestinationRows: [
      { analysisRegionCode: '350100', analysisCityName: '福州市', destinationRegionCode: '350900', destinationCityName: '宁德市', routeCount: 2, weeklyConnectionStrength: 700, tendencyRatio: 0.7 },
      { analysisRegionCode: '350100', analysisCityName: '福州市', destinationRegionCode: '350200', destinationCityName: '厦门市', routeCount: 1, weeklyConnectionStrength: 300, tendencyRatio: 0.3 },
    ],
    odMatrixRows: [],
  }
}

function matrixResult(): TrafficQueryResult {
  const selectedRegions = [
    { regionCode: '350100', regionName: '福州市' },
    { regionCode: '350200', regionName: '厦门市' },
    { regionCode: '350500', regionName: '泉州市' },
  ]
  return {
    ...base(), queryType: 'OD_CONNECTION_MATRIX', title: '三市目的地联系倾向矩阵', selectedRegions,
    odDestinationRows: [],
    odMatrixRows: [
      { analysisRegionCode: '350100', analysisCityName: '福州市', cells: [
        { destinationRegionCode: '350100', destinationCityName: '福州市' },
        { destinationRegionCode: '350200', destinationCityName: '厦门市', weeklyConnectionStrength: 300, tendencyRatio: 0.3 },
        { destinationRegionCode: '350500', destinationCityName: '泉州市' },
      ] },
      { analysisRegionCode: '350200', analysisCityName: '厦门市', cells: [
        { destinationRegionCode: '350100', destinationCityName: '福州市', weeklyConnectionStrength: 300, tendencyRatio: 0.75 },
        { destinationRegionCode: '350200', destinationCityName: '厦门市' },
        { destinationRegionCode: '350500', destinationCityName: '泉州市', weeklyConnectionStrength: 100, tendencyRatio: 0.25 },
      ] },
      { analysisRegionCode: '350500', analysisCityName: '泉州市', cells: [
        { destinationRegionCode: '350100', destinationCityName: '福州市' },
        { destinationRegionCode: '350200', destinationCityName: '厦门市', weeklyConnectionStrength: 100, tendencyRatio: 1 },
        { destinationRegionCode: '350500', destinationCityName: '泉州市' },
      ] },
    ],
  }
}

describe('城市目的地联系倾向', () => {
  it('单城市展示排名表且摘要在表格之前', () => {
    const wrapper = mount(TrafficResultPanel, { props: { result: destinationResult() } })
    expect(wrapper.find('h3').text()).toBe('目的地联系倾向')
    expect(wrapper.find('.od-destination-table').text()).toContain('宁德市')
    expect(wrapper.find('.od-destination-table').text()).toContain('700.00')
    expect(wrapper.find('.od-destination-table').text()).toContain('70.00%')
    expect(wrapper.find('.od-destination-table').text()).toContain('2')
    expect(wrapper.html().indexOf('traffic-summary')).toBeLessThan(wrapper.html().indexOf('od-destination-table'))
    expect(wrapper.text()).not.toContain('卡口')
  })

  it('多城市展示矩阵、百分比和空联系', () => {
    const wrapper = mount(TrafficResultPanel, { props: { result: matrixResult() } })
    expect(wrapper.find('h3').text()).toBe('城市目的地联系倾向矩阵')
    expect(wrapper.findAll('.od-matrix-table thead th')).toHaveLength(4)
    expect(wrapper.findAll('.od-matrix-table tbody tr')).toHaveLength(3)
    expect(wrapper.find('.od-matrix-table').text()).toContain('75.00%')
    expect(wrapper.find('.od-matrix-table').text()).toContain('—')
    expect(wrapper.find('.od-destination-table').exists()).toBe(false)
  })

  it('紧凑模式不重复展示摘要', () => {
    const wrapper = mount(TrafficResultPanel, { props: { result: destinationResult(), compact: true } })
    expect(wrapper.find('.traffic-summary').exists()).toBe(false)
    expect(wrapper.find('.od-destination-table').exists()).toBe(true)
  })

  it('空数据显示统一空状态', () => {
    const value = destinationResult()
    value.odDestinationRows = []
    const wrapper = mount(TrafficResultPanel, { props: { result: value } })
    expect(wrapper.find('.empty-result').text()).toContain('暂无')
  })

  it('语音通道只保留总结文本', () => {
    setActivePinia(createPinia())
    const store = useAgentStore()
    const id = 'od-speech'
    store.messages.push({ id, role: 'assistant', content: '', status: 'pending' })
    const data = matrixResult()
    store.applyEvent(id, { name: 'answer.delta', data: { content: data.summary } })
    store.applyEvent(id, { name: 'result.traffic', data })
    store.applyEvent(id, { name: 'answer.speech', data: { content: data.summary } })
    expect(store.messages.at(-1)?.speechText).toBe(data.summary)
    expect(store.messages.at(-1)?.traffic?.odMatrixRows).toHaveLength(3)
    expect(store.messages.at(-1)?.speechText).not.toContain('75.00%')
  })
})
