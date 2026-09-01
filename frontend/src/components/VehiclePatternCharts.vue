<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import type {
  HourlyVehicleFlow,
  VehicleDayTypeRow,
  VehicleStructureRow,
} from '../types/traffic'

echarts.use([
  BarChart, LineChart, PieChart, GridComponent, LegendComponent,
  TitleComponent, TooltipComponent, SVGRenderer,
])

const props = defineProps<{
  structureRows: VehicleStructureRow[]
  hourlySeries: HourlyVehicleFlow[]
  dayTypeRows: VehicleDayTypeRow[]
}>()

const pieElement = ref<HTMLElement>()
const lineElement = ref<HTMLElement>()
const barElement = ref<HTMLElement>()
const instances: ECharts[] = []
let resizeObserver: ResizeObserver | undefined
const testEnvironment = typeof navigator !== 'undefined' && navigator.userAgent.includes('jsdom')

const colors = ['#5b7cfa', '#67c587', '#f2b94b']

function chart(element: HTMLElement | undefined, option: EChartsCoreOption) {
  if (!element) return
  const instance = echarts.init(element, undefined, { renderer: 'svg' })
  instance.setOption(option)
  instances.push(instance)
  resizeObserver?.observe(element)
}

function render() {
  if (testEnvironment) return
  instances.splice(0).forEach((instance) => instance.dispose())
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver?.disconnect()
    resizeObserver = new ResizeObserver(() => instances.forEach((instance) => instance.resize()))
  }
  if (props.structureRows.length) {
    chart(pieElement.value, {
      color: colors,
      tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 辆（{d}%）' },
      legend: {
        top: 2, left: 'center', itemWidth: 12, itemHeight: 7, itemGap: 18,
        textStyle: { color: '#8fb1c7', fontSize: 10 },
      },
      series: [{
        name: '车型占比', type: 'pie', radius: ['32%', '58%'], center: ['50%', '60%'],
        avoidLabelOverlap: true,
        data: props.structureRows.map((row) => ({ name: row.vehicleTypeName, value: row.weeklyVolume })),
        label: {
          show: true, position: 'outside', alignTo: 'edge', edgeDistance: 10, bleedMargin: 2,
          color: '#a9c3d3', formatter: '{b}  {d}%', fontSize: 10, lineHeight: 15,
        },
        labelLine: { show: true, length: 10, length2: 14, smooth: 0.2 },
        labelLayout: { hideOverlap: false, moveOverlap: 'shiftY' },
      }],
    })
  }
  if (props.hourlySeries.length) {
    chart(lineElement.value, {
      color: colors,
      tooltip: { trigger: 'axis' },
      legend: {
        top: 2, left: 'center', itemWidth: 12, itemHeight: 7, itemGap: 18,
        textStyle: { color: '#8fb1c7', fontSize: 10 },
      },
      grid: { left: 14, right: 14, top: 56, bottom: 30, containLabel: true },
      xAxis: {
        type: 'category', data: props.hourlySeries.map((row) => row.hour),
        axisLabel: { color: '#7395aa', interval: 3, fontSize: 9 },
        axisLine: { lineStyle: { color: '#315b78' } },
      },
      yAxis: {
        type: 'value', name: '流量（辆）', nameGap: 12,
        nameTextStyle: { color: '#7395aa', fontSize: 9, align: 'left' },
        axisLabel: { color: '#7395aa', fontSize: 9 }, splitLine: { lineStyle: { color: 'rgba(74,120,151,.22)' } },
      },
      series: [
        { name: '小型客车', type: 'line', smooth: true, symbolSize: 4, data: props.hourlySeries.map((row) => row.car) },
        { name: '中型客车', type: 'line', smooth: true, symbolSize: 4, data: props.hourlySeries.map((row) => row.bus) },
        { name: '大型货车', type: 'line', smooth: true, symbolSize: 4, data: props.hourlySeries.map((row) => row.truck) },
      ],
    })
  }
  if (props.dayTypeRows.length) {
    chart(barElement.value, {
      color: ['#5b7cfa', '#67c587'],
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: {
        top: 2, left: 'center', itemWidth: 12, itemHeight: 7, itemGap: 22,
        textStyle: { color: '#8fb1c7', fontSize: 10 },
      },
      grid: { left: 14, right: 14, top: 62, bottom: 30, containLabel: true },
      xAxis: {
        type: 'category', data: props.dayTypeRows.map((row) => row.vehicleTypeName),
        axisLabel: { color: '#7395aa', fontSize: 9 }, axisLine: { lineStyle: { color: '#315b78' } },
      },
      yAxis: {
        type: 'value', name: '流量（辆）', nameGap: 12,
        nameTextStyle: { color: '#7395aa', fontSize: 9, align: 'left' },
        axisLabel: { color: '#7395aa', fontSize: 9 }, splitLine: { lineStyle: { color: 'rgba(74,120,151,.22)' } },
      },
      series: [
        { name: '工作日5天合计', type: 'bar', data: props.dayTypeRows.map((row) => row.weekdayVolume) },
        { name: '周末2天合计', type: 'bar', data: props.dayTypeRows.map((row) => row.weekendVolume) },
      ],
    })
  }
}

onMounted(() => void nextTick(render))
watch(() => [props.structureRows, props.hourlySeries, props.dayTypeRows], () => void nextTick(render), { deep: true })
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  instances.splice(0).forEach((instance) => instance.dispose())
})
</script>

<template>
  <div class="vehicle-chart-grid">
    <section v-if="structureRows.length" class="vehicle-chart-card vehicle-chart-wide">
      <h3>车型占比分布</h3>
      <div ref="pieElement" class="vehicle-chart vehicle-pie-chart" role="img" aria-label="车型占比分布饼图"></div>
    </section>
    <section v-if="hourlySeries.length" class="vehicle-chart-card vehicle-chart-wide">
      <h3>24小时分车型出行规律</h3>
      <div ref="lineElement" class="vehicle-chart vehicle-line-chart" role="img" aria-label="24小时分车型出行规律折线图"></div>
    </section>
    <section v-if="dayTypeRows.length" class="vehicle-chart-card vehicle-chart-wide">
      <h3>工作日与周末出行对比</h3>
      <div ref="barElement" class="vehicle-chart vehicle-bar-chart" role="img" aria-label="工作日与周末出行对比柱状图"></div>
    </section>
  </div>
</template>
