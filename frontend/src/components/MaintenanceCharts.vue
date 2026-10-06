<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'
import type { MaintenanceChart } from '../types/maintenance'

echarts.use([BarChart, GridComponent, TooltipComponent, SVGRenderer])
const props = defineProps<{ charts: MaintenanceChart[] }>()
const elements: HTMLElement[] = []
let instances: ECharts[] = []
let observer: ResizeObserver | undefined
function setElement(el: unknown) { if (el instanceof HTMLElement && !elements.includes(el)) elements.push(el) }
function render() {
  instances.forEach(item => item.dispose()); instances = []
  props.charts.forEach((chart, index) => {
    const el = elements[index]; if (!el) return
    const instance = echarts.init(el, undefined, { renderer: 'svg' }); instances.push(instance)
    instance.setOption({ tooltip: {}, grid: { left: 48, right: 18, top: 20, bottom: 58 },
      xAxis: { type: 'category', data: chart.labels, axisLabel: { color: '#b9d5e6', rotate: chart.labels.length > 5 ? 25 : 0 } },
      yAxis: { type: 'value', name: chart.unit, axisLabel: { color: '#b9d5e6' }, nameTextStyle: { color: '#b9d5e6' } },
      series: [{ type: 'bar', data: chart.values, itemStyle: { color: '#24b4ca', borderRadius: [5, 5, 0, 0] } }] })
  })
}
onMounted(() => { nextTick(render); observer = new ResizeObserver(() => instances.forEach(item => item.resize())); elements.forEach(el => observer?.observe(el)) }); watch(() => props.charts, () => nextTick(render), { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); instances.forEach(item => item.dispose()) })
</script>
<template><div class="chart-grid"><section v-for="chart in charts" :key="chart.title"><h4>{{ chart.title }}</h4><div :ref="setElement" class="chart"></div></section></div></template>
<style scoped>.chart-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,360px),1fr));gap:14px;min-width:0}.chart-grid section{min-width:0;overflow:hidden;padding:12px;background:#082d4c;border-radius:10px}.chart-grid h4{margin:0 0 8px;font-size:15px}.chart{width:100%;height:260px;min-width:0}@media(max-width:520px){.chart{height:230px}}</style>
