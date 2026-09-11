<script setup lang="ts">
import { ref } from 'vue'
import type { WorkflowHistoryPage } from '../types/dispatch'
import { rescuePlanForDisplay, resourceNameWithCityForDisplay } from '../utils/dispatchPlanText'

defineProps<{ history: WorkflowHistoryPage; busy: boolean; errorMessage?: string }>()
const emit = defineEmits<{
  page: [page: number]
  releaseResources: [workflowId: string, expectedVersion: number, reason: string]
}>()
const releaseWorkflowId = ref('')
const releaseReason = ref('')
const confirmingRelease = ref(false)

function openRelease(workflowId: string) {
  releaseWorkflowId.value = workflowId
  releaseReason.value = ''
  confirmingRelease.value = false
}

function cancelRelease() {
  releaseWorkflowId.value = ''
  releaseReason.value = ''
  confirmingRelease.value = false
}

function confirmRelease(workflowId: string, version: number) {
  const reason = releaseReason.value.trim()
  if (!reason) return
  emit('releaseResources', workflowId, version, reason)
}

function formatTime(value?: string) {
  if (!value) return '时间未知'
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value))
}

function actionText(type: string) {
  const labels: Record<string, string> = {
    GENERATION_STARTED: '开始生成方案', GENERATION_COMPLETED: '方案生成完成',
    GENERATION_FAILED: '方案生成失败', GENERATION_RETRIED: '重新生成方案',
    EVENT_TYPE_CORRECTED: '一级人工更正事件类型',
    LEVEL_1_SUBMITTED: '一级上报二级', LEVEL_1_RETURNED: '一级退回返工',
    LEVEL_2_PASSED: '二级复核通过', LEVEL_2_RETURNED: '二级退回一级',
    LEVEL_3_RETURNED: '省级退回一级', LEVEL_3_PUBLISHED: '省级批准并通告',
    RESOURCES_RELEASED: '已调度资源全部归还',
    NO_DISPATCH: '确认无需调度',
  }
  return labels[type] ?? type
}

</script>

<template>
  <section class="workflow-history-panel">
    <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>
    <article v-for="item in history.items" :key="item.workflowId" class="workflow-history-card">
      <header>
        <div><strong>{{ item.event.eventTypeName ?? item.event.eventType }}</strong>
          <small>{{ item.event.customId || item.event.eventId }}</small></div>
        <span>{{ item.workflowStatus === 'PUBLISHED' ? '已通告' : '无需调度' }}</span>
      </header>
      <p>{{ item.event.description }}</p>
      <dl class="notice-event-facts">
        <div><dt>发生时间</dt><dd>{{ formatTime(item.event.occurrenceTime) }}</dd></div>
        <div v-if="item.event.place || item.event.cityName"><dt>发生地点</dt><dd>{{ item.event.place || item.event.cityName }}</dd></div>
        <div v-if="item.event.sourceName"><dt>事件来源</dt><dd>{{ item.event.sourceName }}</dd></div>
        <div v-if="item.event.routeNo || item.event.routeName"><dt>路线</dt><dd>{{ item.event.routeNo }} {{ item.event.routeName }}</dd></div>
      </dl>
      <p v-if="item.workflowStatus === 'NO_DISPATCH' && item.terminalReason" class="no-dispatch-reason">
        无需调度原因：{{ item.terminalReason }}
      </p>
      <section v-if="item.commandDecision?.noticeSnapshot" class="notice-snapshot">
        <strong>{{ item.commandDecision.noticeSnapshot.title }}</strong>
        <small>通告编号：{{ item.commandDecision.noticeSnapshot.noticeNumber }} · {{ formatTime(item.commandDecision.noticeSnapshot.publishedAt) }}</small>
        <small v-if="item.commandDecision.noticeSnapshot.responsePlan">
          执行预案：{{ item.commandDecision.noticeSnapshot.responsePlan.eventTypeName }} ·
          {{ item.commandDecision.noticeSnapshot.responsePlan.planId }} v{{ item.commandDecision.noticeSnapshot.responsePlan.version }}
        </small>
        <p>{{ rescuePlanForDisplay(item.commandDecision.noticeSnapshot.rescuePlan) }}</p>
        <div v-if="item.commandDecision.noticeSnapshot.allocatedResources?.length" class="notice-resource-summary">
          <strong>正式调度资源</strong>
          <span v-for="resource in item.commandDecision.noticeSnapshot.allocatedResources" :key="resource.resourceId">
            {{ resourceNameWithCityForDisplay(resource.sourceCityName, resource.resourceName) }} {{ resource.quantity }}{{ resource.unit }}
          </span>
        </div>
        <div v-if="item.commandDecision.noticeSnapshot.resourceShortages?.length" class="notice-resource-shortage">
          通告包含 {{ item.commandDecision.noticeSnapshot.resourceShortages.length }} 项资源缺口
        </div>
        <dl><div><dt>二级专业意见</dt><dd>{{ item.commandDecision.noticeSnapshot.professionalOpinion }}</dd></div>
          <div><dt>省级批示</dt><dd>{{ item.commandDecision.noticeSnapshot.commandOpinion || '同意按方案执行' }}</dd></div></dl>
      </section>
      <p v-if="item.resourcesReleased" class="resource-release-result">该工单实际调度资源已全部归还库存。</p>
      <div v-else-if="item.workflowId && item.canReleaseResources"
        class="resource-release-control">
        <button v-if="releaseWorkflowId !== item.workflowId" type="button" :disabled="busy"
          @click="openRelease(item.workflowId)">归还全部资源</button>
        <div v-else class="resource-release-form">
          <label>资源归还原因<textarea v-model="releaseReason" rows="2" maxlength="500"
            :disabled="busy" placeholder="例如：现场处置完成，车辆和队伍已撤离归建。"></textarea></label>
          <p v-if="confirmingRelease">确认后将把该最终方案的全部已调度资源恢复为可用，请再次确认。</p>
          <div>
            <button type="button" :disabled="busy" @click="cancelRelease">取消</button>
            <button v-if="!confirmingRelease" type="button" :disabled="busy || !releaseReason.trim()"
              @click="confirmingRelease = true">继续</button>
            <button v-else type="button" class="approve-button" :disabled="busy"
              @click="confirmRelease(item.workflowId, item.workflowVersion)">确认全部归还</button>
          </div>
        </div>
      </div>
      <p v-else-if="item.workflowStatus === 'PUBLISHED'">{{ item.resourceReleaseUnavailableReason || '无需归还资源' }}</p>
      <details class="workflow-timeline"><summary>查看完整留痕（{{ item.timeline.length }}）</summary>
        <ol><li v-for="action in item.timeline" :key="action.actionId"><strong>{{ actionText(action.actionType) }}</strong>
          <time>{{ formatTime(action.createdAt) }}</time><p v-if="action.comment">{{ action.comment }}</p></li></ol>
      </details>
    </article>
    <div v-if="history.total > history.size" class="history-pagination">
      <button :disabled="busy || history.page === 0" @click="emit('page', history.page - 1)">上一页</button>
      <span>第 {{ history.page + 1 }} 页</span>
      <button :disabled="busy || (history.page + 1) * history.size >= history.total" @click="emit('page', history.page + 1)">下一页</button>
    </div>
  </section>
</template>
