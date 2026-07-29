<script setup lang="ts">
import { ref, watch } from 'vue'
import type { EmergencyAlert } from '../types/dispatch'
import DispatchPlanCard from './DispatchPlanCard.vue'

const props = defineProps<{
  alert: EmergencyAlert
  busy: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  generate: []
  noDispatch: [reason: string]
  decide: [decision: 'APPROVE' | 'REJECT', comment: string]
}>()

const showingNoDispatch = ref(false)
const confirmingNoDispatch = ref(false)
const noDispatchReason = ref('')

watch(
  () => props.alert.event.eventId,
  () => {
    showingNoDispatch.value = false
    confirmingNoDispatch.value = false
    noDispatchReason.value = ''
  },
)

function prepareNoDispatchConfirmation() {
  if (!noDispatchReason.value.trim()) return
  confirmingNoDispatch.value = true
}

function confirmNoDispatch() {
  const reason = noDispatchReason.value.trim()
  if (!reason) return
  emit('noDispatch', reason)
}

function formatTime(value?: string) {
  if (!value) return '发生时间未知'
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
</script>

<template>
  <aside class="emergency-alert-card" aria-live="polite">
    <header class="emergency-alert-header">
      <div class="emergency-alert-title">
        <span class="emergency-alert-icon">!</span>
        <div>
          <strong>紧急事件告警</strong>
          <small>当前还有 {{ alert.pendingCount }} 条事件待处理</small>
        </div>
      </div>
      <span class="emergency-priority">最高优先级</span>
    </header>

    <div class="emergency-event-detail">
      <div>
        <strong>{{ alert.event.eventTypeName ?? alert.event.eventType }}</strong>
        <span>{{ alert.event.eventType }} · {{ formatTime(alert.event.occurrenceTime) }}</span>
      </div>
      <p>{{ alert.event.description }}</p>
      <small>事件编号：{{ alert.event.customId || alert.event.eventId }}</small>
    </div>

    <p v-if="errorMessage" class="emergency-action-error">{{ errorMessage }}</p>

    <div v-if="!alert.dispatch && !showingNoDispatch" class="emergency-alert-actions">
      <button class="no-dispatch-button" :disabled="busy" @click="showingNoDispatch = true">
        无需生成
      </button>
      <button class="generate-dispatch-button" :disabled="busy" @click="emit('generate')">
        {{ busy ? '正在生成…' : '生成调度工单' }}
      </button>
    </div>

    <div v-if="!alert.dispatch && showingNoDispatch" class="no-dispatch-form">
      <label for="no-dispatch-reason">无需生成调度工单的原因</label>
      <textarea
        id="no-dispatch-reason"
        v-model="noDispatchReason"
        rows="2"
        maxlength="500"
        :disabled="busy"
        placeholder="请填写判断依据，保存后该事件将停止提醒。"
      ></textarea>
      <template v-if="confirmingNoDispatch">
        <p class="no-dispatch-confirmation">
          确认后事件将标记为“无需调度”并停止自动提醒，请再次确认。
        </p>
        <div class="no-dispatch-form-actions">
          <button :disabled="busy" @click="confirmingNoDispatch = false">返回修改</button>
          <button class="danger-confirm-button" :disabled="busy" @click="confirmNoDispatch">
            {{ busy ? '保存中…' : '确认无需调度' }}
          </button>
        </div>
      </template>
      <div v-else class="no-dispatch-form-actions">
        <button :disabled="busy" @click="showingNoDispatch = false">取消</button>
        <button
          class="danger-confirm-button"
          :disabled="busy || !noDispatchReason.trim()"
          @click="prepareNoDispatchConfirmation"
        >
          继续
        </button>
      </div>
    </div>

    <div v-if="alert.dispatch?.status === 'GENERATING'" class="dispatch-generating-state">
      <span class="progress-dot"></span>
      大模型正在生成建议资源清单和救援方案，您可以继续使用聊天功能。
    </div>

    <div v-if="alert.dispatch?.status === 'FAILED'" class="dispatch-failed-state">
      <p>{{ alert.dispatch.errorMessage || '调度工单生成失败。' }}</p>
      <button :disabled="busy" @click="emit('generate')">
        {{ busy ? '重试中…' : '重新生成' }}
      </button>
    </div>

    <DispatchPlanCard
      v-if="alert.dispatch && !['GENERATING', 'FAILED'].includes(alert.dispatch.status)"
      :plan="alert.dispatch"
      :busy="busy"
      @decide="(decision, comment) => emit('decide', decision, comment)"
    />
  </aside>
</template>
