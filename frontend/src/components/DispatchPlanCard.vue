<script setup lang="ts">
import type { DispatchPlan } from '../types/dispatch'

defineProps<{ plan: DispatchPlan; busy: boolean }>()
const emit = defineEmits<{ decide: [decision: 'APPROVE' | 'REJECT'] }>()

const statusText: Record<string, string> = {
  WAITING_APPROVAL: '等待人工确认',
  SUBMITTED: 'Mock工单已提交',
  REJECTED: '方案已驳回',
  FAILED: '执行失败',
}
</script>

<template>
  <section class="dispatch-card">
    <header>
      <div>
        <p>应急调度草案</p>
        <h3>{{ plan.event.eventType }} · {{ plan.event.locationDescription }}</h3>
      </div>
      <span class="status-pill" :class="plan.status.toLowerCase()">
        {{ statusText[plan.status] ?? plan.status }}
      </span>
    </header>

    <p class="dispatch-summary">{{ plan.summary }}</p>

    <div class="dispatch-section">
      <strong>执行任务</strong>
      <ol>
        <li v-for="task in plan.tasks" :key="task.sequence">
          <span>{{ task.action }}</span>
          <small>{{ task.responsibleUnit }}<template v-if="task.resourceId"> · {{ task.resourceId }}</template></small>
        </li>
      </ol>
    </div>

    <div class="dispatch-section">
      <strong>建议资源</strong>
      <div v-if="plan.resources.length" class="resource-tags">
        <span v-for="resource in plan.resources" :key="resource.resourceId">
          {{ resource.name }} · {{ resource.type }}
        </span>
      </div>
      <p v-else class="muted">当前Mock台账没有匹配的可用资源。</p>
    </div>

    <div v-if="plan.warnings.length" class="dispatch-warning">
      {{ plan.warnings.join(' · ') }}
    </div>

    <div v-if="plan.status === 'WAITING_APPROVAL'" class="approval-gate">
      <div><strong>人工确认闸门</strong><small>只有点击批准才会创建Mock工单</small></div>
      <div class="approval-actions">
        <button class="reject-button" :disabled="busy" @click="emit('decide', 'REJECT')">驳回</button>
        <button class="approve-button" :disabled="busy" @click="emit('decide', 'APPROVE')">
          {{ busy ? '处理中…' : '批准并下发' }}
        </button>
      </div>
    </div>

    <div v-if="plan.workOrder" class="work-order-result">
      <span>工单编号</span><strong>{{ plan.workOrder.workOrderId }}</strong><small>Mock · {{ plan.workOrder.status }}</small>
    </div>
  </section>
</template>
