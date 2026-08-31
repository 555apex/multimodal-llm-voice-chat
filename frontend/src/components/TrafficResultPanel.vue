<script setup lang="ts">
import { computed } from 'vue'
import type { CapacityLevel, TrafficQueryResult, TrafficStatus } from '../types/traffic'
import VehiclePatternCharts from './VehiclePatternCharts.vue'

const props = defineProps<{ result: TrafficQueryResult; compact?: boolean }>()

const statusText: Record<TrafficStatus, string> = {
  10: '畅通', 20: '轻度拥堵', 30: '中度拥堵', 40: '重度拥堵', 50: '堵塞',
}
const statusClass: Record<TrafficStatus, string> = {
  10: 'smooth', 20: 'light_congestion', 30: 'moderate_congestion', 40: 'severe_congestion', 50: 'blocked',
}
const capacityClass: Record<CapacityLevel, string> = {
  NORMAL: 'capacity-normal', BOTTLENECK: 'capacity-bottleneck', SEVERE_BOTTLENECK: 'capacity-severe',
}

const overview = computed(() => props.result.queryType === 'PROVINCE_OVERVIEW')
const abnormal = computed(() => props.result.queryType === 'PROVINCE_ABNORMAL')
const capacityQuery = computed(() => props.result.queryType.startsWith('CAPACITY_'))
const regionalQuery = computed(() => [
  'REGIONAL_TRAFFIC_OVERVIEW', 'CHECKPOINT_PRESSURE', 'CITY_PRESSURE', 'ROUTE_PRESSURE',
].includes(props.result.queryType))
const vehicleQuery = computed(() => props.result.queryType.startsWith('VEHICLE_'))

const hubRows = computed(() => props.result.hubRows ?? [])
const regionRows = computed(() => props.result.regionPressureRows ?? [])
const routePressureRows = computed(() => props.result.routePressureRows ?? [])
const structureRows = computed(() => props.result.vehicleStructureRows ?? [])
const timeFeatureRows = computed(() => props.result.vehicleTimeFeatureRows ?? [])
const dayTypeRows = computed(() => props.result.vehicleDayTypeRows ?? [])
const hourlySeries = computed(() => props.result.hourlyVehicleSeries ?? [])
const regionScopeCount = computed(() => Math.max(
  props.result.selectedRegions?.length ?? 0,
  regionRows.value.length,
))
const hubSectionTitle = computed(() => rankedSectionTitle('高流量卡口枢纽', hubRows.value.length, 20, '个卡口'))
const regionSectionTitle = computed(() => regionRows.value.length >= 5 && regionScopeCount.value > 5
  ? '城市交通压力 Top5'
  : `城市交通压力（${regionRows.value.length}个城市）`)
const routeSectionTitle = computed(() => rankedSectionTitle('重点路线交通压力', routePressureRows.value.length, 10, '条路线'))
const eyebrow = computed(() => regionalQuery.value ? '福建区域交通压力'
  : vehicleQuery.value ? `${props.result.analysisCity ?? ''}车型出行特征` : '福建普通国省干线')
const hasDisplayData = computed(() => capacityQuery.value
  ? props.result.capacityRows.length > 0
  : regionalQuery.value
    ? hubRows.value.length + regionRows.value.length + routePressureRows.value.length > 0
    : vehicleQuery.value
      ? structureRows.value.length + timeFeatureRows.value.length + dayTypeRows.value.length + hourlySeries.value.length > 0
      : overview.value ? props.result.routeSummaries.length > 0 : props.result.segments.length > 0)

function formatNumber(value: number | null | undefined, digits = 2) {
  return value == null ? '未提供' : value.toFixed(digits)
}
function formatInteger(value: number | null | undefined) {
  return value == null ? '未提供' : value.toLocaleString('zh-CN')
}
function formatPercentage(value: number | null | undefined) {
  return value == null ? '未提供' : `${(value * 100).toFixed(2)}%`
}
function rankedSectionTitle(label: string, displayedCount: number, limit: number, unit: string) {
  return displayedCount >= limit ? `${label} Top${limit}` : `${label}（${displayedCount}${unit}）`
}
</script>

<template>
  <section class="traffic-answer mysql-traffic-answer" aria-live="polite">
    <header class="traffic-answer-header">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ result.title }}</h2>
        <small class="scope-caption">数据时间：{{ new Date(result.acquiredAt).toLocaleString('zh-CN') }}</small>
      </div>
    </header>
    <p v-if="!compact && result.summary" class="traffic-summary">{{ result.summary }}</p>

    <template v-if="regionalQuery">
      <section v-if="hubRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">{{ hubSectionTitle }}</h3>
        <div class="traffic-table-wrap">
          <table class="traffic-table hub-pressure-table">
            <thead><tr><th>卡口编号</th><th>所在路线</th><th>路线名称</th><th>均速</th><th>日均流量</th></tr></thead>
            <tbody><tr v-for="row in hubRows" :key="row.checkpointNo">
              <td data-label="卡口编号"><strong>{{ row.checkpointNo }}</strong></td>
              <td data-label="所在路线">{{ row.routeCode }}</td><td data-label="路线名称">{{ row.routeName }}</td>
              <td data-label="均速">{{ formatNumber(row.averageSpeedKmh) }} km/h</td>
              <td data-label="日均流量"><strong>{{ formatInteger(row.dailyAverageFlow) }}</strong> 辆/日</td>
            </tr></tbody>
          </table>
        </div>
      </section>
      <section v-if="regionRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">{{ regionSectionTitle }}</h3>
        <div class="traffic-table-wrap">
          <table class="traffic-table region-pressure-table">
            <thead><tr><th>区域</th><th>活跃卡口数</th><th>日总流量</th><th>交通枢纽占比</th><th>解读</th></tr></thead>
            <tbody><tr v-for="row in regionRows" :key="row.regionCode">
              <td data-label="区域"><strong>{{ row.regionName }}</strong></td><td data-label="活跃卡口数">{{ row.activeHubCount }}</td>
              <td data-label="日总流量">{{ formatInteger(row.totalDailyFlow) }} 辆/日</td>
              <td data-label="交通枢纽占比">{{ formatPercentage(row.hubShareRatio) }}</td>
              <td data-label="解读" class="row-interpretation">{{ row.interpretation }}</td>
            </tr></tbody>
          </table>
        </div>
      </section>
      <section v-if="routePressureRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">{{ routeSectionTitle }}</h3>
        <div class="traffic-table-wrap">
          <table class="traffic-table route-pressure-table">
            <thead><tr><th>路线编号</th><th>路线名称</th><th>日总流量</th><th>卡口数</th><th>均速</th></tr></thead>
            <tbody><tr v-for="row in routePressureRows" :key="row.routeCode">
              <td data-label="路线编号"><strong>{{ row.routeCode }}</strong></td><td data-label="路线名称">{{ row.routeName }}</td>
              <td data-label="日总流量">{{ formatInteger(row.totalDailyFlow) }} 辆/日</td><td data-label="卡口数">{{ row.checkpointCount }}</td>
              <td data-label="均速">{{ formatNumber(row.averageSpeedKmh) }} km/h</td>
            </tr></tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else-if="vehicleQuery">
      <section v-if="structureRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">车型结构占比</h3>
        <div class="traffic-table-wrap"><table class="traffic-table vehicle-structure-table">
          <thead><tr><th>车型</th><th>一周通行量</th><th>车型占比</th></tr></thead>
          <tbody><tr v-for="row in structureRows" :key="row.vehicleType">
            <td data-label="车型"><strong>{{ row.vehicleTypeName }}</strong></td><td data-label="一周通行量">{{ formatInteger(row.weeklyVolume) }} 辆</td>
            <td data-label="车型占比">{{ formatPercentage(row.shareRatio) }}</td>
          </tr></tbody>
        </table></div>
      </section>
      <section v-if="timeFeatureRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">出行时间特征</h3>
        <div class="traffic-table-wrap"><table class="traffic-table vehicle-time-table">
          <thead><tr><th>车型</th><th>最高峰时段</th><th>峰值流量</th><th>早高峰占比</th><th>晚高峰占比</th><th>典型特征</th></tr></thead>
          <tbody><tr v-for="row in timeFeatureRows" :key="row.vehicleType">
            <td data-label="车型"><strong>{{ row.vehicleTypeName }}</strong></td><td data-label="最高峰时段">{{ row.peakHour }}</td>
            <td data-label="峰值流量">{{ formatInteger(row.peakVolume) }} 辆</td><td data-label="早高峰占比">{{ formatPercentage(row.morningPeakRatio) }}</td>
            <td data-label="晚高峰占比">{{ formatPercentage(row.eveningPeakRatio) }}</td><td data-label="典型特征" class="row-interpretation">{{ row.characteristic }}</td>
          </tr></tbody>
        </table></div>
      </section>
      <section v-if="dayTypeRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">工作日与周末车型通行量</h3>
        <div class="traffic-table-wrap"><table class="traffic-table vehicle-daytype-table">
          <thead><tr><th>车型</th><th>工作日5天合计</th><th>周末2天合计</th></tr></thead>
          <tbody><tr v-for="row in dayTypeRows" :key="row.vehicleType">
            <td data-label="车型"><strong>{{ row.vehicleTypeName }}</strong></td><td data-label="工作日5天合计">{{ formatInteger(row.weekdayVolume) }} 辆</td>
            <td data-label="周末2天合计">{{ formatInteger(row.weekendVolume) }} 辆</td>
          </tr></tbody>
        </table></div>
      </section>
      <VehiclePatternCharts :structure-rows="structureRows" :hourly-series="hourlySeries" :day-type-rows="dayTypeRows" />
    </template>

    <div v-else-if="capacityQuery && result.capacityRows.length" class="traffic-table-wrap">
      <table class="traffic-table capacity-table">
        <thead><tr><th>评估等级</th><th>路线</th><th>名称</th><th>实际通行能力</th><th>设计通行能力</th><th>通行能力利用率</th></tr></thead>
        <tbody><tr v-for="row in result.capacityRows" :key="row.routeCode">
          <td data-label="评估等级"><span class="level" :class="capacityClass[row.capacityLevel]"><i class="status-dot" aria-hidden="true"></i>{{ row.capacityLevelName }}</span></td>
          <td data-label="路线"><strong>{{ row.routeCode }}</strong></td><td data-label="名称">{{ row.routeName }}</td>
          <td data-label="实际通行能力" class="capacity-value">{{ formatNumber(row.actualCapacityVph) }} 辆/小时</td>
          <td data-label="设计通行能力" class="capacity-value">{{ formatNumber(row.designCapacityVph) }} 辆/小时</td>
          <td data-label="通行能力利用率"><strong>{{ formatPercentage(row.utilizationRatio) }}</strong></td>
        </tr></tbody>
      </table>
    </div>
    <div v-else-if="overview && result.routeSummaries.length" class="traffic-table-wrap">
      <table class="traffic-table"><thead><tr><th>路线</th><th>名称</th><th>均速</th><th>状态</th></tr></thead>
        <tbody><tr v-for="route in result.routeSummaries" :key="route.routeCode">
          <td data-label="路线"><strong>{{ route.routeCode }}</strong></td><td data-label="名称">{{ route.routeName }}</td>
          <td data-label="均速" class="speed">{{ formatNumber(route.averageSpeedKmh) }} km/h</td>
          <td data-label="状态"><span class="level" :class="statusClass[route.status]"><i class="status-dot" aria-hidden="true"></i>{{ statusText[route.status] }}</span></td>
        </tr></tbody></table>
    </div>
    <div v-else-if="result.segments.length" class="traffic-table-wrap">
      <table class="traffic-table"><thead>
        <tr v-if="abnormal"><th>拥堵程度</th><th>路线</th><th>路段</th><th>距离</th><th>拥堵指数</th></tr>
        <tr v-else><th>路线</th><th>名称</th><th>路段</th><th>状态</th><th>均速</th><th>距离</th><th>拥堵指数</th></tr>
      </thead><tbody><tr v-for="segment in result.segments" :key="`${segment.routeCode}-${segment.routeSection}`">
        <template v-if="abnormal">
          <td data-label="拥堵程度"><span class="level" :class="statusClass[segment.status]"><i class="status-dot" aria-hidden="true"></i>{{ statusText[segment.status] }}</span></td>
          <td data-label="路线"><strong>{{ segment.routeCode }}</strong></td><td data-label="路段">{{ segment.routeSection }}</td>
          <td data-label="距离">{{ formatNumber(segment.distanceKm) }} km</td><td data-label="拥堵指数">{{ formatNumber(segment.severity) }}</td>
        </template><template v-else>
          <td data-label="路线"><strong>{{ segment.routeCode }}</strong></td><td data-label="名称">{{ segment.routeName }}</td><td data-label="路段">{{ segment.routeSection }}</td>
          <td data-label="状态"><span class="level" :class="statusClass[segment.status]"><i class="status-dot" aria-hidden="true"></i>{{ statusText[segment.status] }}</span></td>
          <td data-label="均速" class="speed">{{ formatNumber(segment.averageSpeedKmh) }} km/h</td><td data-label="距离">{{ formatNumber(segment.distanceKm) }} km</td>
          <td data-label="拥堵指数">{{ formatNumber(segment.severity) }}</td>
        </template>
      </tr></tbody></table>
    </div>

    <div v-if="!hasDisplayData" class="empty-result">
      {{ capacityQuery
        ? (result.queryType === 'CAPACITY_BOTTLENECKS' ? '当前没有通行能力利用率低于80%的瓶颈路线。' : '本次查询没有返回可展示的通行能力数据。')
        : regionalQuery ? '本次查询范围内没有可展示的卡口交通压力数据。'
          : vehicleQuery ? '该城市暂无可展示的车型出行特征数据。'
            : (abnormal ? '当前没有status≥20的拥堵异常路段。' : '本次查询没有返回可展示的交通数据。') }}
    </div>
    <p v-if="result.truncated && !regionalQuery && !vehicleQuery" class="traffic-truncated">
      <template v-if="capacityQuery">共 {{ result.totalSegmentCount }} 条瓶颈路线，当前展示利用率最低的前 {{ result.displayedSegmentCount }} 条。</template>
      <template v-else>共 {{ result.totalSegmentCount }} 条路段，当前展示拥堵程度较高的前 {{ result.displayedSegmentCount }} 条。</template>
    </p>
  </section>
</template>
