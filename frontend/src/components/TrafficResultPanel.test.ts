import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TrafficResultPanel from './TrafficResultPanel.vue'
import type { TrafficQueryResult, TrafficSegment } from '../types/traffic'

function segment(index: number): TrafficSegment {
  return {
    roadName: index === 0 ? '成功大道' : `测试道路${index}`,
    direction: '北向南',
    congestionLevel: index % 3 === 0 ? 'CONGESTED' : 'SMOOTH',
    averageSpeedKmh: index % 3 === 0 ? 12 : 36,
    polyline: `118.${index},24.4;118.${index + 1},24.5`,
  }
}

const result: TrafficQueryResult = {
  queryScope: 'AREA_ALL',
  areaCode: '350203',
  areaName: '思明区',
  summary: '思明区整体交通态势已汇总。',
  summarySource: 'MODEL',
  segments: Array.from({ length: 55 }, (_, index) => segment(index)),
  evaluation: {
    totalSegments: 55, smoothSegments: 36, slowSegments: 0, congestedSegments: 19,
    unknownSegments: 0, smoothRatio: 36 / 55, slowRatio: 0,
    congestedRatio: 19 / 55, unknownRatio: 0, averageSpeedKmh: 27.7,
  },
  coverage: {
    totalTiles: 10, succeededTiles: 9, failedTiles: 1,
    coverageRatio: 0.9, complete: false,
  },
  source: 'AMAP', acquiredAt: '2026-07-21T08:00:00Z', freshness: 'FRESH',
  mock: false, warnings: ['PARTIAL_AREA_COVERAGE'], traceId: 'trace-area',
}

describe('TrafficResultPanel area mode', () => {
  it('shows aggregate metrics, coverage warning and paginated roads', async () => {
    const wrapper = mount(TrafficResultPanel, {
      props: { result, traceId: 'trace-area', compact: true },
    })

    expect(wrapper.text()).toContain('思明区')
    expect(wrapper.text()).toContain('数据覆盖率 90%')
    expect(wrapper.findAll('.segment')).toHaveLength(50)

    await wrapper.find('.area-pagination button:last-child').trigger('click')
    expect(wrapper.findAll('.segment')).toHaveLength(5)
  })

  it('filters by road name and congestion level', async () => {
    const wrapper = mount(TrafficResultPanel, {
      props: { result, traceId: 'trace-area', compact: true },
    })

    await wrapper.find('input[type="search"]').setValue('成功大道')
    expect(wrapper.findAll('.segment')).toHaveLength(1)
    expect(wrapper.text()).toContain('成功大道')

    await wrapper.find('input[type="search"]').setValue('')
    await wrapper.find('select').setValue('CONGESTED')
    expect(wrapper.findAll('.segment')).toHaveLength(19)
  })
})
