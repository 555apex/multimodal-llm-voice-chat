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
const catalog = computed(() => props.result.queryType === 'ROUTE_CATALOG')
const catalogRows = computed(() => {
  const rows = props.result.segments.map(row => ({
    routeCode: row.routeCode, routeName: row.routeName, routeSection: row.routeSection,
  }))
  const codesWithSegments = new Set(rows.map(row => row.routeCode))
  for (const route of props.result.routeSummaries) {
    if (!codesWithSegments.has(route.routeCode)) {
      rows.push({ routeCode: route.routeCode, routeName: route.routeName, routeSection: '暂无路段数据' })
    }
  }
  return rows.sort((a, b) => a.routeCode.localeCompare(b.routeCode) || a.routeSection.localeCompare(b.routeSection))
})
const abnormal = computed(() => props.result.queryType === 'PROVINCE_ABNORMAL')
const capacityQuery = computed(() => props.result.queryType.startsWith('CAPACITY_'))
const regionalQuery = computed(() => [
  'REGIONAL_TRAFFIC_OVERVIEW', 'REGIONAL_PAIR_PRESSURE', 'REGIONAL_KEY_CHANNELS',
].includes(props.result.queryType))
const vehicleQuery = computed(() => props.result.queryType.startsWith('VEHICLE_'))
const odQuery = computed(() => props.result.queryType.startsWith('OD_'))
const odDestinationRows = computed(() => props.result.odDestinationRows ?? [])
const odMatrixRows = computed(() => props.result.odMatrixRows ?? [])
const odScope = computed(() => props.result.selectedRegions?.map(row => row.regionName).join('、') || '福建省')

const regionalPairRows = computed(() => props.result.regionalPairRows ?? [])
const regionalChannelRows = computed(() => props.result.regionalChannelRows ?? [])
const structureRows = computed(() => props.result.vehicleStructureRows ?? [])
const timeFeatureRows = computed(() => props.result.vehicleTimeFeatureRows ?? [])
const dayTypeRows = computed(() => props.result.vehicleDayTypeRows ?? [])
const hourlySeries = computed(() => props.result.hourlyVehicleSeries ?? [])
const pairSectionTitle = computed(() => rankedSectionTitle('城市对交通联系压力', regionalPairRows.value.length, 5, '个城市对'))
const channelSectionTitle = computed(() => rankedSectionTitle('重要跨市路线通道', regionalChannelRows.value.length, 10, '条路线'))
const eyebrow = computed(() => odQuery.value ? '城市目的地联系倾向' : regionalQuery.value ? '福建跨区域交通联系'
  : vehicleQuery.value ? `${props.result.analysisCity ?? ''}车型出行特征` : '福建普通国省干线')
const hasDisplayData = computed(() => catalog.value ? catalogRows.value.length > 0 : odQuery.value ? odDestinationRows.value.length + odMatrixRows.value.length > 0 : capacityQuery.value
  ? props.result.capacityRows.length > 0
  : regionalQuery.value
    ? regionalPairRows.value.length + regionalChannelRows.value.length > 0
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
function matrixCellStyle(value: number | null | undefined) {
  if (value == null) return undefined
  return { backgroundColor: `rgba(37, 180, 202, ${Math.min(0.72, 0.08 + value * 1.8)})` }
}
</script>

<template>
  <section class="traffic-answer mysql-traffic-answer" aria-live="polite">
    <header class="traffic-answer-header">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ result.title }}</h2>
        <small class="scope-caption">数据时间：{{ new Date(result.acquiredAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</small>
      </div>
    </header>
    <p v-if="!compact && result.summary" class="traffic-summary">{{ result.summary }}</p>
    <p v-for="warning in (odQuery ? [] : result.warnings)" :key="warning" class="scope-caption od-warning">{{ warning }}</p>

    <template v-if="odQuery">
      <p class="scope-caption od-scope">分析范围：{{ odScope }} · 当前7日城市联系结构</p>
      <section v-if="odDestinationRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">目的地联系倾向</h3>
        <p class="scope-caption">共{{ odDestinationRows.length }}个关联目的地，按联系倾向从高到低排列。</p>
        <div class="traffic-table-wrap">
          <table class="traffic-table od-destination-table">
            <thead><tr><th>分析城市</th><th>关联目的地</th><th>跨市路线数</th><th>7日联系强度</th><th>目的地联系倾向占比</th></tr></thead>
            <tbody><tr v-for="row in odDestinationRows" :key="`${row.analysisRegionCode}-${row.destinationRegionCode}`">
              <td data-label="分析城市"><strong>{{ row.analysisCityName }}</strong></td>
              <td data-label="关联目的地">{{ row.destinationCityName }}</td>
              <td data-label="跨市路线数">{{ row.routeCount }}</td>
              <td data-label="7日联系强度">{{ formatNumber(row.weeklyConnectionStrength) }}</td>
              <td data-label="目的地联系倾向占比"><strong>{{ formatPercentage(row.tendencyRatio) }}</strong></td>
            </tr></tbody>
          </table>
        </div>
      </section>
      <section v-if="odMatrixRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">城市目的地联系倾向矩阵</h3>
        <p class="scope-caption">矩阵按分析城市展示其目的地联系倾向占比；“—”表示当前没有可形成的城市联系。</p>
        <div class="traffic-table-wrap">
          <table class="traffic-table od-matrix-table">
            <thead><tr><th>分析城市</th><th v-for="region in result.selectedRegions" :key="region.regionCode">{{ region.regionName }}</th></tr></thead>
            <tbody><tr v-for="row in odMatrixRows" :key="row.analysisRegionCode">
              <td data-label="分析城市"><strong>{{ row.analysisCityName }}</strong></td>
              <td v-for="cell in row.cells" :key="cell.destinationRegionCode" :data-label="cell.destinationCityName"
                class="od-matrix-cell" :style="matrixCellStyle(cell.tendencyRatio)"
                :title="cell.weeklyConnectionStrength == null ? '暂无联系' : `7日联系强度 ${formatNumber(cell.weeklyConnectionStrength)}`">
                {{ cell.tendencyRatio == null ? '—' : formatPercentage(cell.tendencyRatio) }}
              </td>
            </tr></tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else-if="regionalQuery">
      <section v-if="regionalPairRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">{{ pairSectionTitle }}</h3>
        <p v-if="(result.totalRegionalPairCount ?? 0) > regionalPairRows.length" class="scope-caption">
          共{{ result.totalRegionalPairCount }}个城市对，当前展示7日总流量较高的前{{ regionalPairRows.length }}个。
        </p>
        <div class="traffic-table-wrap">
          <table class="traffic-table regional-pair-table">
            <thead><tr><th>城市联系</th><th>跨市路线数</th><th>总流量（7天）</th><th>日均流量</th><th>平均车速</th></tr></thead>
            <tbody><tr v-for="row in regionalPairRows" :key="`${row.cityARegionCode}-${row.cityBRegionCode}`">
              <td data-label="城市联系"><strong>{{ row.cityAName }}—{{ row.cityBName }}</strong></td>
              <td data-label="跨市路线数">{{ row.routeCount }}</td>
              <td data-label="总流量（7天）">{{ formatInteger(row.weeklyTotalFlow) }} 辆</td>
              <td data-label="日均流量">{{ formatInteger(row.dailyAverageFlow) }} 辆/日</td>
              <td data-label="平均车速">{{ formatNumber(row.averageSpeedKmh) }} km/h</td>
            </tr></tbody>
          </table>
        </div>
      </section>
      <section v-if="regionalChannelRows.length" class="traffic-data-section">
        <h3 class="traffic-section-heading">{{ channelSectionTitle }}</h3>
        <p v-if="(result.totalRegionalChannelCount ?? 0) > regionalChannelRows.length" class="scope-caption">
          共{{ result.totalRegionalChannelCount }}条跨市路线，当前展示7日总流量较高的前{{ regionalChannelRows.length }}条。
        </p>
        <div class="traffic-table-wrap">
          <table class="traffic-table regional-channel-table">
            <thead><tr><th>城市联系</th><th>路线编号</th><th>总流量（7天）</th><th>日均流量</th><th>平均车速</th></tr></thead>
            <tbody><tr v-for="row in regionalChannelRows" :key="`${row.cityARegionCode}-${row.cityBRegionCode}-${row.routeCode}`">
              <td data-label="城市联系"><strong>{{ row.cityAName }}—{{ row.cityBName }}</strong></td>
              <td data-label="路线编号"><strong>{{ row.routeCode }}</strong></td>
              <td data-label="总流量（7天）">{{ formatInteger(row.weeklyTotalFlow) }} 辆</td>
              <td data-label="日均流量">{{ formatInteger(row.dailyAverageFlow) }} 辆/日</td>
              <td data-label="平均车速">{{ formatNumber(row.averageSpeedKmh) }} km/h</td>
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

    <div v-else-if="catalog && catalogRows.length" class="traffic-table-wrap">
      <table class="traffic-table">
        <thead><tr><th>路线编号</th><th>路线名称</th><th>路段名称</th></tr></thead>
        <tbody><tr v-for="row in catalogRows" :key="`${row.routeCode}-${row.routeSection}`">
          <td data-label="路线编号"><strong>{{ row.routeCode }}</strong></td>
          <td data-label="路线名称">{{ row.routeName }}</td>
          <td data-label="路段名称">{{ row.routeSection }}</td>
        </tr></tbody>
      </table>
    </div>
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
      {{ odQuery ? '所选范围暂无可展示的城市目的地联系数据。' : capacityQuery
        ? (result.queryType === 'CAPACITY_BOTTLENECKS' ? '当前没有通行能力利用率达到15%的瓶颈路线。' : '本次查询没有返回可展示的通行能力数据。')
        : regionalQuery ? '本次查询范围内没有可展示的跨市路线卡口数据。'
          : vehicleQuery ? '该城市暂无可展示的车型出行特征数据。'
            : (abnormal ? '当前没有status≥20的拥堵异常路段。' : '本次查询没有返回可展示的交通数据。') }}
    </div>
    <p v-if="result.truncated && !regionalQuery && !vehicleQuery && !odQuery" class="traffic-truncated">
      <template v-if="capacityQuery">共 {{ result.totalSegmentCount }} 条瓶颈路线，当前展示利用率最高的前 {{ result.displayedSegmentCount }} 条。</template>
      <template v-else>共 {{ result.totalSegmentCount }} 条路段，当前展示拥堵程度较高的前 {{ result.displayedSegmentCount }} 条。</template>
    </p>
  </section>
</template>

<style scoped>
.od-expand-button { margin-top: 10px; padding: 7px 14px; border: 1px solid #376b8b; border-radius: 6px; background: #103d5c; color: #c0e1f2; cursor: pointer; }
.od-expand-button:focus-visible { outline: 2px solid #64d4ff; outline-offset: 2px; }
.od-warning { color: #d1ad72; }
.od-city-table { min-width: 500px; }
.od-channel-table { min-width: 690px; }
@media (max-width: 800px) {
  .od-city-table, .od-channel-table { display: table; }
  .od-city-table thead, .od-channel-table thead { display: table-header-group; }
  .od-city-table tbody, .od-channel-table tbody { display: table-row-group; }
  .od-city-table tr, .od-channel-table tr { display: table-row; padding: 0; }
  .od-city-table td, .od-channel-table td { display: table-cell; width: auto; padding: 8px; white-space: nowrap; }
  .od-city-table td::before, .od-channel-table td::before { display: none; }
}
</style>
