<script setup lang="ts">
import type { CongestionLevel, TrafficQueryResult } from '../types/traffic'

defineProps<{ result: TrafficQueryResult; traceId: string }>()

const levelText: Record<CongestionLevel, string> = {
  UNKNOWN: '未知',
  SMOOTH: '畅通',
  SLOW: '缓行',
  CONGESTED: '拥堵',
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}
</script>

<template>
  <section class="result-card" aria-live="polite">
    <header class="result-header">
      <div>
        <p class="eyebrow">查询结果</p>
        <h2>{{ result.roadName }}</h2>
      </div>
      <div class="badges">
        <span class="badge" :class="result.mock ? 'mock' : 'real'">
          {{ result.mock ? 'Mock数据' : result.source }}
        </span>
        <span class="badge neutral">
          {{ result.summarySource === 'MODEL' ? '模型摘要' : '规则摘要' }}
        </span>
      </div>
    </header>

    <p class="summary">{{ result.summary }}</p>

    <div v-if="result.warnings.length" class="warning-box">
      <strong>数据提示</strong>
      <span>{{ result.warnings.join(' · ') }}</span>
    </div>

    <div v-if="result.segments.length" class="segment-list">
      <article v-for="(segment, index) in result.segments" :key="`${segment.roadName}-${index}`" class="segment">
        <div>
          <strong>{{ segment.direction }}</strong>
          <span>{{ segment.roadName }}</span>
        </div>
        <span class="level" :class="segment.congestionLevel.toLowerCase()">
          {{ levelText[segment.congestionLevel] }}
        </span>
        <span class="speed">
          {{ segment.averageSpeedKmh == null ? '速度未提供' : `${segment.averageSpeedKmh} km/h` }}
        </span>
      </article>
    </div>

    <div v-else class="empty-result">本次查询没有返回道路分段。</div>

    <footer class="metadata">
      <span>获取时间：{{ formatTime(result.acquiredAt) }}</span>
      <span>新鲜度：{{ result.freshness }}</span>
      <span>Trace ID：{{ traceId }}</span>
    </footer>
  </section>
</template>
