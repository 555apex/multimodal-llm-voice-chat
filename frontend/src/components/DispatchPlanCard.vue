<script setup lang="ts">
import { ref } from 'vue'
import type { DispatchPlan } from '../types/dispatch'
import { rescuePlanForDisplay, resourceNameForDisplay } from '../utils/dispatchPlanText'

withDefaults(defineProps<{
  plan: DispatchPlan
  busy: boolean
  actionsEnabled?: boolean
}>(), { actionsEnabled: true })
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
        <small v-if="plan.responsePlanId" class="response-plan-reference">
          执行预案：{{ plan.responsePlanName || plan.event.eventType }} · {{ plan.responsePlanId }} v{{ plan.responsePlanVersion }}
        </small>
      </div>
      <span class="status-pill" :class="plan.status.toLowerCase()">
        {{ statusText[plan.status] ?? plan.status }}
      </span>
    </header>

    <div class="dispatch-section dispatch-plan-section">
      <strong>救援方案</strong>
      <p class="dispatch-plan-text">{{ rescuePlanForDisplay(plan.rescuePlan) || '正在生成救援方案…' }}</p>
    </div>

    <div class="dispatch-section allocated-resource-section">
      <strong>实际匹配资源</strong>
      <div v-if="plan.allocatedResources?.length" class="suggested-resource-list allocated-resource-list">
        <article v-for="resource in plan.allocatedResources" :key="resource.resourceId">
          <div>
            <strong>{{ resourceNameForDisplay(resource.resourceName) }}</strong>
            <span>{{ resource.resourceTypeName }} · {{ resource.resourceTypeCode }}</span>
          </div>
          <b>{{ resource.quantity }} {{ resource.unit }}</b>
          <p>{{ resource.purpose }}</p>
          <small :class="resource.dispatchScope === 'CROSS_CITY' ? 'cross-city' : 'local-city'">
            调度城市：{{ resource.sourceCityName }}
          </small>
        </article>
      </div>
      <p v-else class="muted">当前没有匹配到可调度资源。</p>
      <small class="resource-disclaimer">资源来自数据库库存并已完成软占用。</small>
    </div>

    <div v-if="plan.resourceShortages?.length" class="resource-shortage-panel">
      <strong>资源缺口</strong>
      <article v-for="shortage in plan.resourceShortages" :key="shortage.resourceTypeCode">
        <span>{{ shortage.resourceTypeName }}</span>
        <b>需求 {{ shortage.requiredQuantity }} {{ shortage.unit }} · 已匹配 {{ shortage.allocatedQuantity }} · 缺口 {{ shortage.shortageQuantity }}</b>
        <small>{{ shortage.reason }}</small>
      </article>
    </div>

    <div v-if="plan.errorMessage" class="dispatch-warning">{{ plan.errorMessage }}</div>

    <div v-if="actionsEnabled && plan.status === 'WAITING_APPROVAL'" class="approval-gate">
      <div><strong>一级现场确认</strong><small>确认后事件和方案将上报市交通应急办复核</small></div>
      <div class="approval-actions">
        <button class="reject-button" :disabled="busy" @click="rejecting = true">退回AI返工</button>
        <button class="approve-button" :disabled="busy" @click="emit('decide', 'APPROVE', '')">
          {{ busy ? '处理中…' : '确认并上报二级' }}
        </button>
      </div>
    </div>

    <div v-if="rejecting" class="rejection-editor">
      <label for="dispatch-rejection">请说明方案需要修改的问题</label>
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
