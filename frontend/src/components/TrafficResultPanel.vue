<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CongestionLevel, TrafficQueryResult } from '../types/traffic'

const props = defineProps<{ result: TrafficQueryResult; traceId: string; compact?: boolean }>()

const levelText: Record<CongestionLevel, string> = {
  UNKNOWN: '未知',
  SMOOTH: '畅通',
  SLOW: '缓行',
  CONGESTED: '拥堵',
}

const pageSize = 50
const roadSearch = ref('')
const levelFilter = ref<'ALL' | CongestionLevel>('ALL')
const currentPage = ref(1)
const isArea = computed(() => (props.result.queryScope ?? 'ROAD') !== 'ROAD')
const areaScopeText = computed(() => props.result.queryScope === 'AREA_MAJOR' ? '主要道路' : '全体道路')

const filteredSegments = computed(() => {
  const keyword = roadSearch.value.trim().toLowerCase()
  return props.result.segments.filter((segment) => {
    const matchesRoad = !keyword || segment.roadName.toLowerCase().includes(keyword)
    const matchesLevel = levelFilter.value === 'ALL' || segment.congestionLevel === levelFilter.value
    return matchesRoad && matchesLevel
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredSegments.value.length / pageSize)))
const visibleSegments = computed(() => {
  if (!isArea.value) return props.result.segments
  const start = (currentPage.value - 1) * pageSize
  return filteredSegments.value.slice(start, start + pageSize)
})

watch([roadSearch, levelFilter], () => { currentPage.value = 1 })
watch(totalPages, (pages) => {
  if (currentPage.value > pages) currentPage.value = pages
})

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function formatRatio(value?: number) {
  return `${Math.round((value ?? 0) * 100)}%`
}
</script>

<template>
  <section class="result-card" :class="{ 'area-result-card': isArea }" aria-live="polite">
    <header class="result-header">
      <div>
        <p class="eyebrow">{{ isArea ? '区域交通态势' : '道路查询结果' }}</p>
        <h2>{{ isArea ? result.areaName : result.roadName }}</h2>
        <small v-if="isArea" class="scope-caption">查询范围：{{ areaScopeText }}</small>
      </div>
      <div class="badges">
        <span class="badge" :class="result.mock ? 'mock' : 'real'">
          {{ result.mock ? 'Mock数据' : result.source }}
        </span>
        <span class="badge neutral">模型回答</span>
      </div>
    </header>

    <p v-if="!compact" class="summary">{{ result.summary }}</p>

    <div v-if="isArea && result.evaluation" class="area-metrics">
      <article class="area-metric primary">
        <strong>{{ result.evaluation.totalSegments }}</strong><span>去重路段</span>
      </article>
      <article class="area-metric smooth">
        <strong>{{ result.evaluation.smoothSegments }}</strong><span>畅通 {{ formatRatio(result.evaluation.smoothRatio) }}</span>
      </article>
      <article class="area-metric slow">
        <strong>{{ result.evaluation.slowSegments }}</strong><span>缓行 {{ formatRatio(result.evaluation.slowRatio) }}</span>
      </article>
      <article class="area-metric congested">
        <strong>{{ result.evaluation.congestedSegments }}</strong><span>拥堵 {{ formatRatio(result.evaluation.congestedRatio) }}</span>
      </article>
      <article class="area-metric">
        <strong>{{ result.evaluation.averageSpeedKmh == null ? '--' : result.evaluation.averageSpeedKmh.toFixed(1) }}</strong>
        <span>平均速度 km/h</span>
      </article>
    </div>

    <div v-if="isArea && result.coverage" class="coverage-card" :class="{ partial: !result.coverage.complete }">
      <div>
        <strong>数据覆盖率 {{ formatRatio(result.coverage.coverageRatio) }}</strong>
        <span>成功 {{ result.coverage.succeededTiles }}/{{ result.coverage.totalTiles }} 个分片</span>
      </div>
      <div class="coverage-track" aria-hidden="true">
        <i :style="{ width: formatRatio(result.coverage.coverageRatio) }"></i>
      </div>
      <p v-if="!result.coverage.complete">
        有 {{ result.coverage.failedTiles }} 个分片未取得数据，当前结果不代表全区完整态势。
      </p>
    </div>

    <div v-if="result.warnings.length" class="warning-box">
      <strong>数据提示</strong>
      <span>{{ result.warnings.join(' · ') }}</span>
    </div>

    <div v-if="isArea && result.segments.length" class="area-list-tools">
      <label>
        <span class="sr-only">搜索道路名称</span>
        <input v-model="roadSearch" type="search" placeholder="搜索道路名称" />
      </label>
      <label>
        <span class="sr-only">筛选拥堵等级</span>
        <select v-model="levelFilter">
          <option value="ALL">全部状态</option>
          <option value="CONGESTED">拥堵</option>
          <option value="SLOW">缓行</option>
          <option value="SMOOTH">畅通</option>
          <option value="UNKNOWN">未知</option>
        </select>
      </label>
      <span>已筛选 {{ filteredSegments.length }} 条</span>
    </div>

    <div v-if="visibleSegments.length" class="segment-list">
      <article
        v-for="(segment, index) in visibleSegments"
        :key="`${segment.roadName}-${segment.direction}-${segment.polyline ?? index}`"
        class="segment"
      >
        <div>
          <strong>{{ segment.roadName }}</strong>
          <span>{{ segment.direction || '方向未提供' }}</span>
        </div>
        <span class="level" :class="segment.congestionLevel.toLowerCase()">
          {{ levelText[segment.congestionLevel] }}
        </span>
        <span class="speed">
          {{ segment.averageSpeedKmh == null ? '速度未提供' : `${segment.averageSpeedKmh} km/h` }}
        </span>
      </article>
    </div>

    <div v-else class="empty-result">
      {{ result.segments.length ? '没有符合当前筛选条件的道路。' : '本次查询没有返回道路分段。' }}
    </div>

    <nav v-if="isArea && filteredSegments.length > pageSize" class="area-pagination" aria-label="道路分页">
      <button :disabled="currentPage === 1" @click="currentPage--">上一页</button>
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button :disabled="currentPage === totalPages" @click="currentPage++">下一页</button>
    </nav>

    <footer class="metadata">
      <span>获取时间：{{ formatTime(result.acquiredAt) }}</span>
      <span>新鲜度：{{ result.freshness }}</span>
      <span>Trace ID：{{ traceId }}</span>
    </footer>
  </section>
</template>
