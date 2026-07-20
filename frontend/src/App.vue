<script setup lang="ts">
import { storeToRefs } from 'pinia'
import TrafficQueryForm from './components/TrafficQueryForm.vue'
import TrafficResultPanel from './components/TrafficResultPanel.vue'
import { useTrafficStore } from './stores/traffic'
import type { TrafficQueryPayload } from './types/traffic'

const store = useTrafficStore()
const { loading, result, errorMessage, errorTraceId, lastTraceId } = storeToRefs(store)

function handleQuery(payload: TrafficQueryPayload) {
  void store.query(payload)
}
</script>

<template>
  <main class="page-shell">
    <section class="hero">
      <div class="brand-mark" aria-hidden="true">路</div>
      <div>
        <p class="eyebrow">福建应急交通 Agent · 一期教学工作流</p>
        <h1>实时交通路况查询</h1>
        <p class="hero-copy">
          页面提交结构化条件，由Agent固定工作流调用交通Tool，再使用规则或模型生成摘要。
        </p>
      </div>
    </section>

    <section class="query-card">
      <div class="flow-strip" aria-label="系统处理流程">
        <span>输入条件</span><i>→</i><span>Agent Skill</span><i>→</i><span>交通 Tool</span><i>→</i><span>结构化结果</span>
      </div>
      <TrafficQueryForm :loading="loading" @submit="handleQuery" />
    </section>

    <section v-if="errorMessage" class="error-card" role="alert">
      <strong>查询未完成</strong>
      <span>{{ errorMessage }}</span>
      <small v-if="errorTraceId">Trace ID：{{ errorTraceId }}</small>
    </section>

    <TrafficResultPanel v-if="result" :result="result" :trace-id="lastTraceId" />

    <section v-else-if="!errorMessage" class="placeholder-card">
      <div class="placeholder-icon">◎</div>
      <p>填写道路信息，体验第一条端到端交通查询闭环。</p>
      <small>默认使用Mock数据，不需要配置任何API密钥。</small>
    </section>
  </main>
</template>
