import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TrafficResultPanel from './TrafficResultPanel.vue'
import type { TrafficQueryResult } from '../types/traffic'

const base: Omit<TrafficQueryResult, 'queryType' | 'title' | 'routeSummaries' | 'segments'> = {
  summary: '当前交通数据已汇总。请留意当前异常。',
  capacityRows: [],
  totalSegmentCount: 0,
  displayedSegmentCount: 0,
  truncated: false,
  source: 'MYSQL',
  acquiredAt: '2026-08-13T08:00:00Z',
  warnings: [],
  traceId: 'trace-1',
}

describe('TrafficResultPanel MySQL highway modes', () => {
  it('shows missing-hour semantics even in compact chat results', () => {
    const result: TrafficQueryResult = {
      ...base, queryType: 'VEHICLE_HOURLY_PATTERN', title: '福州市24小时规律',
      routeSummaries: [], segments: [],
      warnings: ['有22个小时源数据缺失，图表按项目规则补0；补0不代表实际无车。'],
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('补0不代表实际无车')
    expect(wrapper.find('.traffic-summary').exists()).toBe(false)
  })

  it('shows the qualitative trend only through the existing summary and adds no forecast column', () => {
    const result: TrafficQueryResult = {
      ...base,
      summary: '当前全省国省干线总体通行平稳。未来1至2小时，预计整体趋势基本稳定。',
      queryType: 'PROVINCE_OVERVIEW',
      title: '福建省国省道整体交通态势',
      routeSummaries: [{
        routeCode: 'G104', routeName: '北京-平潭', averageSpeedKmh: 89.34, status: 10, statusName: '畅通',
      }],
      segments: [],
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: false } })

    expect(wrapper.find('.traffic-summary').text()).toContain('未来1至2小时')
    expect(wrapper.findAll('th').map((cell) => cell.text())).toEqual(['路线', '名称', '均速', '状态'])
  })

  it('renders province overview with authoritative route values and five-level label', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'PROVINCE_OVERVIEW',
      title: '福建省国省道整体交通态势',
      routeSummaries: [{
        routeCode: 'G104', routeName: '北京-平潭', averageSpeedKmh: 89.34, status: 10, statusName: '畅通',
      }],
      segments: [],
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('G104')
    expect(wrapper.text()).toContain('北京-平潭')
    expect(wrapper.text()).toContain('89.34 km/h')
    expect(wrapper.text()).toContain('畅通')
  })

  it('renders abnormal top list with severity fixed to two decimals and truncation notice', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'PROVINCE_ABNORMAL',
      title: '福建省拥堵异常路段',
      routeSummaries: [],
      segments: [{
        routeCode: 'G316', routeName: '长乐-同仁', routeSection: 'FJ076→FJ085',
        distanceKm: 45, averageSpeedKmh: 19, status: 40, statusName: '重度拥堵', severity: 0.8,
      }],
      totalSegmentCount: 25,
      displayedSegmentCount: 10,
      truncated: true,
      warnings: [],
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('重度拥堵')
    expect(wrapper.text()).toContain('0.80')
    expect(wrapper.text()).toContain('共 25 条路段')
  })

  it('renders city-pair detail columns', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'CITY_PAIR',
      title: '宁德市—福州市交通情况',
      routeSummaries: [],
      segments: [{
        routeCode: 'G104', routeName: '北京-平潭', routeSection: 'FJ001→FJ002',
        distanceKm: 10, averageSpeedKmh: 35.2, status: 20, statusName: '轻度拥堵', severity: 0.21,
      }],
      totalSegmentCount: 1,
      displayedSegmentCount: 1,
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('路段')
    expect(wrapper.text()).toContain('均速')
    expect(wrapper.text()).toContain('拥堵指数')
    expect(wrapper.text()).toContain('轻度拥堵')
  })

  it('renders route-detail table using the same deterministic segment fields', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'ROUTE_DETAIL',
      title: 'G104 北京-平潭交通情况',
      routeSummaries: [],
      segments: [{
        routeCode: 'G104', routeName: '北京-平潭', routeSection: 'FJ010→FJ011',
        distanceKm: 8.5, averageSpeedKmh: 22, status: 30, statusName: '中度拥堵', severity: 0.58,
      }],
      totalSegmentCount: 1,
      displayedSegmentCount: 1,
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('G104')
    expect(wrapper.text()).toContain('FJ010→FJ011')
    expect(wrapper.text()).toContain('中度拥堵')
    expect(wrapper.text()).toContain('0.58')
  })

  it.each([
    ['CAPACITY_OVERVIEW' as const, '福建省国省道通行能力总览'],
    ['CAPACITY_BOTTLENECKS' as const, '福建省瓶颈路线排行'],
    ['CAPACITY_ROUTE_DETAIL' as const, 'G104 北京-平潭通行能力'],
  ])('renders %s capacity table with units, percentage and level', (queryType, title) => {
    const result: TrafficQueryResult = {
      ...base,
      queryType,
      title,
      routeSummaries: [],
      segments: [],
      capacityRows: [{
        routeCode: 'G104', routeName: '北京-平潭', actualCapacityVph: 320,
        designCapacityVph: 1920, utilizationRatio: 0.1667,
        capacityLevel: 'SEVERE_BOTTLENECK', capacityLevelName: '严重瓶颈',
      }],
      totalSegmentCount: 1,
      displayedSegmentCount: 1,
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('实际通行能力')
    expect(wrapper.text()).toContain('320.00 辆/小时')
    expect(wrapper.text()).toContain('1920.00 辆/小时')
    expect(wrapper.text()).toContain('16.67%')
    expect(wrapper.text()).toContain('严重瓶颈')
  })

  it('uses route wording for truncated bottleneck ranking', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'CAPACITY_BOTTLENECKS', title: '福建省瓶颈路线排行',
      routeSummaries: [], segments: [],
      capacityRows: [{
        routeCode: 'S201', routeName: '柘荣-霞浦', actualCapacityVph: 100,
        designCapacityVph: 1920, utilizationRatio: 0.2,
        capacityLevel: 'SEVERE_BOTTLENECK', capacityLevelName: '严重瓶颈',
      }],
      totalSegmentCount: 12, displayedSegmentCount: 10, truncated: true,
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })
    expect(wrapper.text()).toContain('共 12 条瓶颈路线')
    expect(wrapper.text()).not.toContain('条路段')
  })

  it('renders city-pair and route-level regional connection dimensions only', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'REGIONAL_TRAFFIC_OVERVIEW', title: '福州市、宁德市、南平市跨区域交通联系综合分析',
      routeSummaries: [], segments: [],
      regionalPairRows: [{ cityARegionCode: '350100', cityAName: '福州市', cityBRegionCode: '350900', cityBName: '宁德市', routeCount: 2, checkpointCount: 12, weeklyTotalFlow: 22400, dailyAverageFlow: 3200, averageSpeedKmh: 28.125 }],
      regionalChannelRows: [{ cityARegionCode: '350100', cityAName: '福州市', cityBRegionCode: '350900', cityBName: '宁德市', routeCode: 'G104', routeName: '北京-平潭', checkpointCount: 8, weeklyTotalFlow: 14000, dailyAverageFlow: 2000, averageSpeedKmh: 30.1 }],
      selectedRegions: [
        { regionCode: '350100', regionName: '福州市' },
        { regionCode: '350900', regionName: '宁德市' },
        { regionCode: '350700', regionName: '南平市' },
      ],
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('城市对交通联系压力（1个城市对）')
    expect(wrapper.text()).toContain('重要跨市路线通道（1条路线）')
    expect(wrapper.text()).toContain('福州市—宁德市')
    expect(wrapper.text()).toContain('22,400 辆')
    expect(wrapper.text()).toContain('3,200 辆/日')
    expect(wrapper.text()).toContain('28.13 km/h')
    expect(wrapper.text()).not.toContain('卡口数')
    expect(wrapper.text()).not.toContain('路线名称')
    expect(wrapper.text()).not.toContain('测试卡口')
  })

  it('uses Top5 wording when five city-pair rows are displayed', () => {
    const rows = Array.from({ length: 5 }, (_, index) => ({
      cityARegionCode: `350${index}00`, cityAName: `甲市${index}`, cityBRegionCode: `351${index}00`, cityBName: `乙市${index}`,
      routeCount: 1, checkpointCount: 2, weeklyTotalFlow: 5000 - index * 100,
      dailyAverageFlow: 700 - index, averageSpeedKmh: 40,
    }))
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'REGIONAL_PAIR_PRESSURE', title: '福建省九市城市对交通联系压力',
      routeSummaries: [], segments: [], regionalPairRows: rows,
      totalSegmentCount: 12, displayedSegmentCount: 5, truncated: true,
    }

    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })

    expect(wrapper.text()).toContain('城市对交通联系压力 Top5')
  })

  it('renders vehicle overview tables and all three chart templates', async () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'VEHICLE_PATTERN_OVERVIEW', title: '福州市交通运输特征分析', analysisCity: '福州市',
      routeSummaries: [], segments: [],
      vehicleStructureRows: [{ vehicleType: 'CAR', vehicleTypeName: '小型客车', weeklyVolume: 900, shareRatio: 0.75 }],
      vehicleTimeFeatureRows: [{ vehicleType: 'CAR', vehicleTypeName: '小型客车', peakHour: '10:00–10:59', peakVolume: 165, morningPeakRatio: 0.2, eveningPeakRatio: 0.1, characteristic: '全天分布相对分散' }],
      vehicleDayTypeRows: [{ vehicleType: 'CAR', vehicleTypeName: '小型客车', weekdayVolume: 750, weekendVolume: 150 }],
      hourlyVehicleSeries: Array.from({ length: 24 }, (_, hour) => ({ hour: `${String(hour).padStart(2, '0')}:00`, car: hour, bus: 0, truck: 0 })),
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('车型结构占比')
    expect(wrapper.text()).toContain('出行时间特征')
    expect(wrapper.text()).toContain('工作日5天合计')
    expect(wrapper.find('.vehicle-pie-chart').exists()).toBe(true)
    expect(wrapper.find('.vehicle-line-chart').exists()).toBe(true)
    expect(wrapper.find('.vehicle-bar-chart').exists()).toBe(true)
    expect(wrapper.findAll('.vehicle-chart-wide')).toHaveLength(3)
  })

  it('renders only the requested vehicle structure view', () => {
    const result: TrafficQueryResult = {
      ...base,
      queryType: 'VEHICLE_STRUCTURE', title: '厦门市车型结构分析', analysisCity: '厦门市',
      routeSummaries: [], segments: [],
      vehicleStructureRows: [{ vehicleType: 'TRUCK', vehicleTypeName: '大型货车', weeklyVolume: 100, shareRatio: 0.125 }],
    }
    const wrapper = mount(TrafficResultPanel, { props: { result, compact: true } })
    expect(wrapper.find('.vehicle-pie-chart').exists()).toBe(true)
    expect(wrapper.find('.vehicle-line-chart').exists()).toBe(false)
    expect(wrapper.find('.vehicle-bar-chart').exists()).toBe(false)
    expect(wrapper.text()).toContain('12.50%')
  })
})
