<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type {
  EmergencyWorkflowItem,
  EventSeverity,
  ProfessionalReviewInput,
  ResourceFeasibility,
  WorkflowStage,
} from '../types/dispatch'
import DispatchPlanCard from './DispatchPlanCard.vue'

const props = defineProps<{
  item: EmergencyWorkflowItem
  stage: WorkflowStage
  busy: boolean
  elapsedSeconds?: number
  errorMessage: string
}>()

const emit = defineEmits<{
  generate: []
  noDispatch: [reason: string]
  level1: [decision: 'SUBMIT' | 'REJECT', comment: string]
  review: [input: ProfessionalReviewInput]
  command: [decision: 'APPROVE' | 'REJECT', comment: string]
  correctType: [eventType: string, reason: string]
}>()

const eventTypes = [
  ['DT01', '崩塌（落石）'], ['DT02', '滑坡（坡体位移）'], ['DT03', '泥石流'],
  ['DT04', '沉陷与塌陷'], ['DT05', '水毁'], ['ET101', '拥堵'], ['ET102', '明火（火灾）'],
  ['ET103', '抛撒物'], ['ET104', '设备故障'], ['ET105', '占用应急车道'],
  ['ET106', '交通事故'], ['ET107', '异常停车'], ['ET108', '浓雾检测'], ['ET109', '路障'],
  ['ET110', '施工'], ['ET112', '道路积雪'],
] as const

const showingNoDispatch = ref(false)
const confirmingNoDispatch = ref(false)
const noDispatchReason = ref('')
const severity = ref<EventSeverity>('GENERAL')
const feasibility = ref<ResourceFeasibility>(
  props.item.currentPlan?.resourceShortages?.length ? 'FEASIBLE_WITH_GAP' : 'FEASIBLE',
)
const impactAssessment = ref('')
const coordinationRequirements = ref('')
const reviewComment = ref('')
const commandComment = ref('')
const showingTypeCorrection = ref(false)
const correctedType = ref(props.item.event.eventType)
const correctionReason = ref('')

const isLevel1 = computed(() => props.stage === 'LEVEL_1')
const isLevel2 = computed(() => props.stage === 'LEVEL_2')
const isLevel3 = computed(() => props.stage === 'LEVEL_3')
const plan = computed(() => props.item.currentPlan)
const hasShortage = computed(() => Boolean(plan.value?.resourceShortages?.length))
const generationBusy = computed(() =>
  ['GENERATING', 'REVISING'].includes(props.item.workflowStatus)
  || plan.value?.status === 'GENERATING',
)
const generationFailed = computed(() =>
  props.item.workflowStatus === 'GENERATION_FAILED' || plan.value?.status === 'FAILED',
)
const reviewReady = computed(() =>
  Boolean(impactAssessment.value.trim() && reviewComment.value.trim())
  && (hasShortage.value
    ? feasibility.value === 'FEASIBLE_WITH_GAP' && Boolean(coordinationRequirements.value.trim())
    : feasibility.value === 'FEASIBLE'),
)

watch(
  () => `${props.item.event.eventId}-${props.item.workflowVersion}-${props.stage}`,
  () => {
    showingNoDispatch.value = false
    confirmingNoDispatch.value = false
    noDispatchReason.value = ''
    severity.value = 'GENERAL'
    feasibility.value = props.item.currentPlan?.resourceShortages?.length
      ? 'FEASIBLE_WITH_GAP' : 'FEASIBLE'
    impactAssessment.value = ''
    coordinationRequirements.value = ''
    reviewComment.value = ''
    commandComment.value = ''
    showingTypeCorrection.value = false
    correctedType.value = props.item.event.eventType
    correctionReason.value = ''
  },
)

function prepareNoDispatchConfirmation() {
  if (noDispatchReason.value.trim()) confirmingNoDispatch.value = true
}

function confirmNoDispatch() {
  const reason = noDispatchReason.value.trim()
  if (reason) emit('noDispatch', reason)
}

function submitReview(decision: 'APPROVE' | 'REJECT') {
  const comment = reviewComment.value.trim()
  if (!comment) return
  if (decision === 'APPROVE' && !reviewReady.value) return
  emit('review', {
    decision,
    eventSeverity: severity.value,
    resourceFeasibility: decision === 'APPROVE' ? feasibility.value : 'NEEDS_ADJUSTMENT',
    impactAssessment: impactAssessment.value.trim(),
    coordinationRequirements: coordinationRequirements.value.trim(),
    comment,
  })
}

function submitCommand(decision: 'APPROVE' | 'REJECT') {
  const comment = commandComment.value.trim()
  if ((decision === 'REJECT' || (decision === 'APPROVE' && hasShortage.value)) && !comment) return
  emit('command', decision, comment)
}

function submitTypeCorrection() {
  const reason = correctionReason.value.trim()
  if (reason && correctedType.value !== props.item.event.eventType) {
    emit('correctType', correctedType.value, reason)
  }
}

function actionText(type: string) {
  const labels: Record<string, string> = {
    GENERATION_STARTED: '开始生成应急方案',
    GENERATION_COMPLETED: '应急方案生成完成',
    GENERATION_FAILED: '方案生成失败',
    GENERATION_RETRIED: '重新生成方案',
    EVENT_TYPE_CORRECTED: '一级人工更正事件类型',
    LEVEL_1_SUBMITTED: '一级确认并上报二级',
    LEVEL_1_RETURNED: '一级退回AI返工',
    LEVEL_2_PASSED: '二级专业复核通过',
    LEVEL_2_RETURNED: '二级退回一级返工',
    LEVEL_3_RETURNED: '省级退回一级返工',
    LEVEL_3_PUBLISHED: '省级批准并形成通告',
    RESOURCES_RELEASED: '已调度资源全部归还',
    NO_DISPATCH: '确认无需调度',
  }
  return labels[type] ?? type
}

function formatTime(value?: string) {
  if (!value) return '时间未知'
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value))
}

function canRecoverGeneration() {
  const updatedAt = props.item.currentPlan?.updatedAt
  if (!updatedAt || !generationBusy.value) return false
  const parsed = Date.parse(updatedAt)
  return Number.isFinite(parsed) && Date.now() - parsed >= 600_000
}
</script>

<template>
  <aside class="emergency-alert-card" aria-live="polite">
    <header class="emergency-alert-header">
      <div class="emergency-alert-title">
        <span class="emergency-alert-icon">!</span>
        <div>
          <strong>{{ stage === 'LEVEL_1' ? '一级现场处置' : stage === 'LEVEL_2' ? '二级专业复核' : '三级省级决策' }}</strong>
          <small>{{ item.workflowStatus }}</small>
        </div>
      </div>
    </header>

    <div class="emergency-event-detail">
      <div>
        <strong>{{ item.event.eventTypeName ?? item.event.eventType }}</strong>
        <span>{{ item.event.eventType }} · {{ formatTime(item.event.occurrenceTime) }}</span>
      </div>
      <p>{{ item.event.description }}</p>
      <dl class="incident-source-fields">
        <div><dt>事件编号</dt><dd>{{ item.event.customId || item.event.eventId }}</dd></div>
        <div v-if="item.event.sourceName || item.event.sourceOrgName"><dt>事件来源</dt><dd>{{ item.event.sourceName || item.event.sourceOrgName }}</dd></div>
        <div v-if="item.event.place"><dt>发生地点</dt><dd>{{ item.event.place }}</dd></div>
        <div v-if="item.event.routeNo || item.event.routeName"><dt>路线</dt><dd>{{ [item.event.routeNo, item.event.routeName].filter(Boolean).join(' · ') }}</dd></div>
        <div v-if="item.event.cityName"><dt>调度城市</dt><dd>{{ item.event.cityName }}</dd></div>
      </dl>
    </div>

    <p v-if="errorMessage" class="emergency-action-error">{{ errorMessage }}</p>

    <template v-if="isLevel1">
      <div v-if="!plan && !showingNoDispatch" class="emergency-alert-actions">
        <button class="no-dispatch-button" :disabled="busy" @click="showingNoDispatch = true">
          无需生成
        </button>
        <button class="generate-dispatch-button" :disabled="busy" @click="emit('generate')">
          {{ busy ? '正在生成…' : '生成调度方案' }}
        </button>
      </div>

      <div v-if="!plan && showingNoDispatch" class="no-dispatch-form">
        <label for="no-dispatch-reason">无需生成调度方案的原因</label>
        <textarea id="no-dispatch-reason" v-model="noDispatchReason" rows="2" maxlength="500"
          :disabled="busy" placeholder="请填写判断依据，保存后该事件将办结。"></textarea>
        <template v-if="confirmingNoDispatch">
          <p class="no-dispatch-confirmation">确认后事件将标记为“无需调度”，请再次确认。</p>
          <div class="no-dispatch-form-actions">
            <button :disabled="busy" @click="confirmingNoDispatch = false">返回修改</button>
            <button class="danger-confirm-button" :disabled="busy" @click="confirmNoDispatch">确认无需调度</button>
          </div>
        </template>
        <div v-else class="no-dispatch-form-actions">
          <button :disabled="busy" @click="showingNoDispatch = false">取消</button>
          <button class="danger-confirm-button" :disabled="busy || !noDispatchReason.trim()"
            @click="prepareNoDispatchConfirmation">继续</button>
        </div>
      </div>

      <div v-if="generationBusy || (busy && elapsedSeconds !== undefined)" class="dispatch-generating-state">
        <span>已等待 {{ elapsedSeconds ?? 0 }} 秒。</span>
        <span class="progress-dot"></span>大模型正在提出受限资源需求，系统将按库存和距离完成匹配，您可以继续使用聊天功能。
        <span>若服务中断，系统会在十分钟后自动尝试恢复。</span>
        <button v-if="canRecoverGeneration()" type="button" class="recover-generation-button"
          :disabled="busy" @click="emit('generate')">{{ busy ? '恢复中…' : '恢复生成' }}</button>
      </div>

      <div v-if="generationFailed && !busy" class="dispatch-failed-state">
        <p>{{ plan?.errorMessage || '调度方案生成失败。' }}</p>
        <button :disabled="busy" @click="emit('generate')">{{ busy ? '重试中…' : '重新生成' }}</button>
      </div>

      <div v-if="plan && !item.workflowId" class="dispatch-generating-state">
        检测到升级前生成的旧版方案，需先建立三级流程记录才能继续上报。
        <button :disabled="busy" @click="emit('generate')">
          {{ busy ? '恢复中…' : '纳入三级流程' }}
        </button>
      </div>

      <DispatchPlanCard v-if="plan && item.workflowId && !generationBusy && !generationFailed"
        :plan="plan" :busy="busy"
        @decide="(decision, comment) => emit('level1', decision === 'APPROVE' ? 'SUBMIT' : 'REJECT', comment)" />

      <div v-if="plan && item.workflowId && !generationBusy && !generationFailed" class="event-type-correction">
        <button v-if="!showingTypeCorrection" type="button" :disabled="busy"
          @click="showingTypeCorrection = true">更正事件类型并重新生成</button>
        <template v-else>
          <label>更正后类型
            <select v-model="correctedType" :disabled="busy">
              <option v-for="entry in eventTypes" :key="entry[0]" :value="entry[0]">
                {{ entry[0] }} · {{ entry[1] }}
              </option>
            </select>
          </label>
          <label>更正原因
            <textarea v-model="correctionReason" rows="2" maxlength="500" :disabled="busy"
              placeholder="说明误分类依据，旧方案和资源占用将留痕后释放。"></textarea>
          </label>
          <div class="no-dispatch-form-actions">
            <button type="button" :disabled="busy" @click="showingTypeCorrection = false">取消</button>
            <button type="button" class="danger-confirm-button"
              :disabled="busy || correctedType === item.event.eventType || !correctionReason.trim()"
              @click="submitTypeCorrection">确认更正并重新生成</button>
          </div>
        </template>
      </div>
    </template>

    <template v-if="isLevel2">
      <DispatchPlanCard v-if="plan" :plan="plan" :busy="busy" :actions-enabled="false" />
      <section class="workflow-review-form">
        <header><strong>市交通应急办专业会商表</strong><small>请人工填写专业复核结论，提交后全程留痕</small></header>
        <label>事件初判等级
          <select v-model="severity" :disabled="busy">
            <option value="GENERAL">一般</option><option value="LARGER">较大</option>
            <option value="MAJOR">重大</option><option value="ESPECIALLY_MAJOR">特别重大</option>
          </select>
        </label>
        <label>资源建议可行性
          <select v-model="feasibility" :disabled="busy">
            <option v-if="!hasShortage" value="FEASIBLE">可行</option>
            <option v-if="hasShortage" value="FEASIBLE_WITH_GAP">有缺口但可执行</option>
            <option value="NEEDS_ADJUSTMENT">需要调整</option>
          </select>
        </label>
        <p v-if="hasShortage" class="resource-review-warning">当前方案存在资源缺口。若仍决定通过，必须说明跨部门协调、替代措施或风险控制依据。</p>
        <label>影响研判<textarea v-model="impactAssessment" rows="3" maxlength="1000"
          :disabled="busy" placeholder="说明事件影响、发展趋势和处置重点。"></textarea></label>
        <label>协同要求{{ hasShortage ? '（必填）' : '（可选）' }}<textarea v-model="coordinationRequirements" rows="2" maxlength="1000"
          :disabled="busy" placeholder="说明需要协调的专业力量或工作要求。"></textarea></label>
        <label>专业意见<textarea v-model="reviewComment" rows="3" maxlength="500"
          :disabled="busy" placeholder="通过或退回均需填写明确意见。"></textarea></label>
        <div class="workflow-decision-actions">
          <button class="reject-button" :disabled="busy || !reviewComment.trim()" @click="submitReview('REJECT')">退回一级返工</button>
          <button class="approve-button" :disabled="busy || !reviewReady" @click="submitReview('APPROVE')">复核通过并上报三级</button>
        </div>
      </section>
    </template>

    <template v-if="isLevel3">
      <DispatchPlanCard v-if="plan" :plan="plan" :busy="busy" :actions-enabled="false" />
      <section v-if="item.professionalReview" class="professional-review-summary">
        <strong>二级专业复核意见</strong>
        <dl>
          <div><dt>事件等级</dt><dd>{{ item.professionalReview.eventSeverity }}</dd></div>
          <div><dt>资源可行性</dt><dd>{{ item.professionalReview.resourceFeasibility }}</dd></div>
          <div><dt>影响研判</dt><dd>{{ item.professionalReview.impactAssessment }}</dd></div>
          <div><dt>协同要求</dt><dd>{{ item.professionalReview.coordinationRequirements || '无' }}</dd></div>
          <div><dt>专业意见</dt><dd>{{ item.professionalReview.reviewOpinion }}</dd></div>
        </dl>
      </section>
      <section class="workflow-review-form">
        <header><strong>省级最终批示</strong><small>{{ hasShortage ? '当前方案存在资源缺口，批准时必须填写批示' : '批准后将冻结方案并生成本地正式通告' }}</small></header>
        <label>省级批示<textarea v-model="commandComment" rows="3" maxlength="500"
          :disabled="busy" :placeholder="hasShortage ? '当前存在资源缺口，批准或退回都必须填写明确批示。' : '批准时可选；退回时必须填写具体修改意见。'"></textarea></label>
        <div class="workflow-decision-actions">
          <button class="reject-button" :disabled="busy || !commandComment.trim()" @click="submitCommand('REJECT')">退回一级返工</button>
          <button class="approve-button" :disabled="busy || (hasShortage && !commandComment.trim())" @click="submitCommand('APPROVE')">最终批准并通告</button>
        </div>
      </section>
    </template>

    <details v-if="item.timeline.length" class="workflow-timeline">
      <summary>流程留痕（{{ item.timeline.length }}）</summary>
      <ol><li v-for="action in item.timeline" :key="action.actionId">
        <strong>{{ actionText(action.actionType) }}</strong><time>{{ formatTime(action.createdAt) }}</time>
        <p v-if="action.comment">{{ action.comment }}</p>
      </li></ol>
    </details>
  </aside>
</template>
