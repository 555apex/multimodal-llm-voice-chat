<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CongestionLevel, TrafficQueryResult } from '../types/traffic'

const props = defineProps<{ result: TrafficQueryResult; compact?: boolean }>()

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
</script>

<template>
  <section class="traffic-answer" :class="{ 'area-traffic-answer': isArea }" aria-live="polite">
    <header class="traffic-answer-header">
      <div>
        <p class="eyebrow">{{ isArea ? '区域交通态势' : '道路查询结果' }}</p>
        <h2>{{ isArea ? result.areaName : result.roadName }}</h2>
        <small v-if="isArea" class="scope-caption">查询范围：{{ areaScopeText }}</small>
      </div>
    </header>

    <p v-if="!compact && result.summary" class="traffic-summary">{{ result.summary }}</p>

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

    <div v-if="visibleSegments.length" class="traffic-table-wrap">
      <table class="traffic-table">
        <thead>
          <tr>
            <th scope="col">道路名称</th>
            <th scope="col">方向</th>
            <th scope="col">拥堵程度</th>
            <th scope="col">平均速度</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(segment, index) in visibleSegments"
            :key="`${segment.roadName}-${segment.direction}-${segment.polyline ?? index}`"
          >
            <td data-label="道路名称"><strong>{{ segment.roadName }}</strong></td>
            <td data-label="方向">{{ segment.direction || '方向未提供' }}</td>
            <td data-label="拥堵程度">
              <span class="level" :class="segment.congestionLevel.toLowerCase()">
                {{ levelText[segment.congestionLevel] }}
              </span>
            </td>
            <td data-label="平均速度" class="speed">
              {{ segment.averageSpeedKmh == null ? '速度未提供' : `${segment.averageSpeedKmh} km/h` }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="empty-result">
      {{ result.segments.length ? '没有符合当前筛选条件的道路。' : '本次查询没有返回道路分段。' }}
    </div>

    <nav v-if="isArea && filteredSegments.length > pageSize" class="area-pagination" aria-label="道路分页">
      <button :disabled="currentPage === 1" @click="currentPage--">上一页</button>
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button :disabled="currentPage === totalPages" @click="currentPage++">下一页</button>
    </nav>

  </section>
</template>
