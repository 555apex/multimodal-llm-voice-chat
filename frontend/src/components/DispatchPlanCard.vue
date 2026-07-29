<script setup lang="ts">
import { ref } from 'vue'
import type { DispatchPlan } from '../types/dispatch'

defineProps<{ plan: DispatchPlan; busy: boolean }>()
const emit = defineEmits<{
  decide: [decision: 'APPROVE' | 'REJECT', comment: string]
}>()

const rejecting = ref(false)
const rejectionComment = ref('')

const statusText: Record<string, string> = {
  GENERATING: '正在生成',
  WAITING_APPROVAL: '等待人工审批',
  APPROVED: '审批通过',
  REJECTED: '已驳回',
  FAILED: '生成失败',
}

function submitReject() {
  const comment = rejectionComment.value.trim()
  if (!comment) return
  emit('decide', 'REJECT', comment)
  rejecting.value = false
  rejectionComment.value = ''
}
</script>

<template>
  <section class="dispatch-card">
    <header>
      <div>
        <p>应急调度工单 · 第 {{ plan.version }} 版</p>
        <h3>{{ plan.event.eventType }} · {{ plan.event.description }}</h3>
      </div>
      <span class="status-pill" :class="plan.status.toLowerCase()">
        {{ statusText[plan.status] ?? plan.status }}
      </span>
    </header>

    <div class="dispatch-section">
      <strong>建议救援资源</strong>
      <div v-if="plan.suggestedResources.length" class="suggested-resource-list">
        <article v-for="resource in plan.suggestedResources" :key="`${resource.resourceName}-${resource.purpose}`">
          <div>
            <strong>{{ resource.resourceName }}</strong>
            <span>{{ resource.resourceType }}</span>
          </div>
          <b>{{ resource.quantity }} {{ resource.unit }}</b>
          <p>{{ resource.purpose }}</p>
        </article>
      </div>
      <p v-else class="muted">尚未生成建议资源清单。</p>
      <small class="resource-disclaimer">资源由模型基于通用知识建议，不代表真实库存或当前可用性。</small>
    </div>

    <div class="dispatch-section">
      <strong>救援方案</strong>
      <p class="dispatch-plan-text">{{ plan.rescuePlan || '正在生成救援方案…' }}</p>
    </div>

    <div v-if="plan.errorMessage" class="dispatch-warning">{{ plan.errorMessage }}</div>

    <div v-if="plan.status === 'WAITING_APPROVAL'" class="approval-gate">
      <div><strong>人工审批</strong><small>审批通过后事件才会标记为已处理</small></div>
      <div class="approval-actions">
        <button class="reject-button" :disabled="busy" @click="rejecting = true">驳回返工</button>
        <button class="approve-button" :disabled="busy" @click="emit('decide', 'APPROVE', '')">
          {{ busy ? '处理中…' : '批准工单' }}
        </button>
      </div>
    </div>

    <div v-if="rejecting" class="rejection-editor">
      <label for="dispatch-rejection">请说明工单需要修改的问题</label>
      <textarea
        id="dispatch-rejection"
        v-model="rejectionComment"
        maxlength="500"
        rows="3"
        placeholder="例如：救援车辆数量不足，需要补充夜间照明和现场警戒方案。"
      ></textarea>
      <div>
        <button :disabled="busy" @click="rejecting = false">取消</button>
        <button
          class="reject-confirm-button"
          :disabled="busy || !rejectionComment.trim()"
          @click="submitReject"
        >
          {{ busy ? '返工中…' : '提交返工' }}
        </button>
      </div>
    </div>
  </section>
</template>
