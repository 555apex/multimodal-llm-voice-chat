<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import ChatMessage from './ChatMessage.vue'
import DigitalHumanPanel from './DigitalHumanPanel.vue'
import EmergencyAlertCard from './EmergencyAlertCard.vue'
import FacilityWarningPanel from './FacilityWarningPanel.vue'
import WorkflowHistoryPanel from './WorkflowHistoryPanel.vue'
import VoiceInputButton from './VoiceInputButton.vue'
import { useAgentStore } from '../stores/agent'
import { useEmergencyStore } from '../stores/emergency'
import { useFacilityStore } from '../stores/facility'
import { useSpeechStore } from '../stores/speech'
import { useDigitalHumanSignal } from '../composables/useDigitalHumanSignal'
import type { WorkflowStage } from '../types/dispatch'

type AgentTab = 'chat' | 'emergency' | 'facilityWarning'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const agentStore = useAgentStore()
const { messages, running, stage, toolProgress, approvalBusyPlanId } = storeToRefs(agentStore)
const emergencyStore = useEmergencyStore()
const {
  item: emergencyItem,
  counts: emergencyCounts,
  selectedStage: emergencyStage,
  viewMode: emergencyViewMode,
  history: emergencyHistory,
  totalPending,
  actionBusy: emergencyActionBusy,
  queryStatus: emergencyQueryStatus,
  errorMessage: emergencyError,
} = storeToRefs(emergencyStore)
const facilityStore = useFacilityStore()
const { counts: facilityCounts, actionBusy: facilityActionBusy } = storeToRefs(facilityStore)
const speechStore = useSpeechStore()
const {
  capabilities: speechCapabilities,
  capabilitiesLoading: speechCapabilitiesLoading,
  capabilityError: speechCapabilityError,
  autoReadEnabled,
  playbackStatus,
  playbackAmplitude,
} = storeToRefs(speechStore)

const activeTab = ref<AgentTab>('chat')
const input = ref('')
const inputElement = ref<HTMLTextAreaElement>()
const messageList = ref<HTMLElement>()
const recording = ref(false)

const examples = [
  '福建省目前整体交通态势如何？',
  'G104 北京—平潭当前通行情况如何？',
  '福建省哪些国省道路段接近通行瓶颈？',
  '福州的出行主要联系哪些城市？',
]

const lastAssistantMessage = computed(() => [...messages.value].reverse()
  .find((message) => message.role === 'assistant'))
const emergencyProcessing = computed(() => emergencyActionBusy.value
  || ['GENERATING', 'REVISING'].includes(emergencyItem.value?.workflowStatus ?? ''))
const digitalHumanSignal = useDigitalHumanSignal({
  recording,
  playbackStatus,
  playbackAmplitude,
  running,
  lastAssistantMessage,
  emergencyActionBusy: emergencyProcessing,
  facilityActionBusy,
})

const pendingCount = computed(() => totalPending.value)
const facilityPendingCount = computed(() => facilityCounts.value.pending)

const progressText = computed(() => {
  if (!toolProgress.value) return stage.value?.label ?? 'Agent正在处理'
  const progress = toolProgress.value
  const failed = progress.failedTiles ? `，失败 ${progress.failedTiles} 片` : ''
  return `${stage.value?.label ?? '正在采集'}：${progress.completedTiles}/${progress.totalTiles} 片${failed}`
})

async function send() {
  const content = input.value.trim()
  if (!content || running.value || recording.value) return
  input.value = ''
  await agentStore.send(content)
}

async function insertTranscription(text: string) {
  const element = inputElement.value
  const start = element?.selectionStart ?? input.value.length
  const end = element?.selectionEnd ?? start
  const before = input.value.slice(0, start)
  const after = input.value.slice(end)
  const prefix = before && !/\s$/.test(before) ? ' ' : ''
  const suffix = after && !/^\s/.test(after) ? ' ' : ''
  input.value = `${before}${prefix}${text}${suffix}${after}`
  const caret = start + prefix.length + text.length + suffix.length
  await nextTick()
  element?.focus()
  element?.setSelectionRange(caret, caret)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void send()
  }
}

function useExample(example: string) {
  input.value = example
  void nextTick(() => inputElement.value?.focus())
}

function selectTab(tab: AgentTab) {
  activeTab.value = tab
}

function selectEmergencyStage(event: Event) {
  const stage = (event.target as HTMLSelectElement).value as WorkflowStage
  void emergencyStore.selectStage(stage)
}

function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close')
}

watch(
  () => messages.value.map((message) => `${message.content.length}-${message.status}`).join('|'),
  async () => {
    await nextTick()
    messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
  },
)

watch(
  [() => props.open, activeTab],
  ([open, tab]) => {
    speechStore.setSurfaceActive(Boolean(open && tab === 'chat'))
  },
  { immediate: true },
)

onMounted(() => window.addEventListener('keydown', handleEscape))
onUnmounted(() => window.removeEventListener('keydown', handleEscape))
</script>

<template>
  <aside
    id="road-agent-drawer"
    class="agent-drawer"
    :class="{ open }"
    :aria-hidden="!open"
    :inert="!open"
    aria-label="路智通 AI 助手"
  >
    <header class="drawer-titlebar">
      <div>
        <span class="drawer-title-icon" aria-hidden="true">路</span>
        <div><strong>路智通数字人助手</strong><small>ROAD NETWORK INTELLIGENT AGENT</small></div>
      </div>
      <button type="button" class="drawer-close" aria-label="关闭路智通 AI 助手" @click="emit('close')">×</button>
    </header>

    <DigitalHumanPanel :signal="digitalHumanSignal" />

    <nav class="agent-mode-tabs" aria-label="Agent功能切换">
      <button
        type="button"
        :class="{ active: activeTab === 'chat' }"
        :aria-selected="activeTab === 'chat'"
        role="tab"
        @click="selectTab('chat')"
      >
        <span aria-hidden="true">✦</span>智能问答
      </button>
      <button
        type="button"
        :class="{ active: activeTab === 'emergency' }"
        :aria-selected="activeTab === 'emergency'"
        role="tab"
        @click="selectTab('emergency')"
      >
        <span aria-hidden="true">!</span>应急处置
        <i v-if="pendingCount" class="emergency-tab-badge" :aria-label="`${pendingCount} 条待处理事件`">
          {{ pendingCount > 99 ? '99+' : pendingCount }}
        </i>
      </button>
      <button
        type="button"
        :class="{ active: activeTab === 'facilityWarning' }"
        :aria-selected="activeTab === 'facilityWarning'"
        role="tab"
        @click="selectTab('facilityWarning')"
      >
        <span aria-hidden="true">⌁</span>设施预警
        <i v-if="facilityPendingCount" class="emergency-tab-badge facility-tab-badge"
          :aria-label="`${facilityPendingCount} 条待确认设施告警`">
          {{ facilityPendingCount > 99 ? '99+' : facilityPendingCount }}
        </i>
      </button>
    </nav>

    <div class="drawer-content">
      <section v-show="activeTab === 'chat'" class="chat-panel drawer-chat" role="tabpanel">
        <div class="chat-section-toolbar">
          <div><strong>智能问答</strong><small>路况查询 · 专业研判</small></div>
          <button type="button" :disabled="running" @click="agentStore.reset">新建会话</button>
        </div>

        <div ref="messageList" class="message-list">
          <ChatMessage
            v-for="message in messages"
            :key="message.id"
            :message="message"
            :approval-busy-plan-id="approvalBusyPlanId"
            @decide="agentStore.decide"
          />
        </div>

        <div v-if="messages.length <= 1" class="example-prompts">
          <button v-for="example in examples" :key="example" type="button" @click="useExample(example)">
            {{ example }}
          </button>
        </div>

        <div class="agent-progress" :class="{ visible: running }" aria-live="polite">
          <span class="progress-dot"></span>{{ progressText }}
        </div>

        <form class="chat-composer" @submit.prevent="send">
          <VoiceInputButton
            :active="open && activeTab === 'chat'"
            :disabled="running || recording"
            :available="Boolean(speechCapabilities?.asrAvailable)"
            :max-recording-seconds="speechCapabilities?.maxRecordingSeconds ?? 60"
            :max-audio-bytes="speechCapabilities?.maxAudioBytes ?? 10485760"
            @transcribed="insertTranscription"
            @recording-changed="recording = $event"
          />
          <textarea
            ref="inputElement"
            v-model="input"
            rows="2"
            maxlength="1000"
            :disabled="running"
            placeholder="请输入您的问题…"
            @keydown="handleKeydown"
          ></textarea>
          <button type="submit" :disabled="running || recording || !input.trim()">
            <span>{{ running ? '生成中' : '发送' }}</span><i aria-hidden="true">↗</i>
          </button>
        </form>
        <div class="composer-options">
          <label class="speech-auto-toggle" :class="{ unavailable: !speechCapabilities?.ttsAvailable }">
            <input
              type="checkbox"
              :checked="autoReadEnabled"
              :disabled="speechCapabilitiesLoading || !speechCapabilities?.ttsAvailable"
              @change="speechStore.setAutoRead(($event.target as HTMLInputElement).checked)"
            />
            <span>语音回答</span>
          </label>
          <p class="composer-hint">
            <span v-if="speechCapabilityError">语音服务未就绪 · </span>
            Enter发送 · Shift + Enter换行
          </p>
        </div>
      </section>

      <section v-show="activeTab === 'emergency'" class="emergency-workspace" role="tabpanel">
        <div class="workflow-stage-toolbar">
          <label>事件处理层次
            <select :value="emergencyStage" :disabled="emergencyActionBusy" @change="selectEmergencyStage">
              <option value="LEVEL_1">一级现场处置（{{ emergencyCounts.level1 }}）</option>
              <option value="LEVEL_2">二级专业复核（{{ emergencyCounts.level2 }}）</option>
              <option value="LEVEL_3">三级省级决策（{{ emergencyCounts.level3 }}）</option>
            </select>
          </label>
          <div>
            <button type="button" :class="{ active: emergencyViewMode === 'inbox' }" @click="emergencyStore.showInbox">待办</button>
            <button type="button" :class="{ active: emergencyViewMode === 'history' }" @click="emergencyStore.showHistory">流程记录</button>
          </div>
        </div>

        <p v-if="emergencyCounts.pendingClassification" class="classification-status-banner">
          <span>待自动识别 {{ emergencyCounts.pendingClassification }} 条
            <template v-if="emergencyCounts.classificationFailed">
              · 其中 {{ emergencyCounts.classificationFailed }} 条上次识别失败
            </template>
          </span>
          <button v-if="emergencyCounts.classificationFailed" type="button"
            :disabled="emergencyActionBusy" @click="emergencyStore.retryClassification">
            立即重试一条
          </button>
        </p>

        <div class="emergency-workspace-scroll">
          <EmergencyAlertCard
            v-if="emergencyViewMode === 'inbox' && emergencyItem"
            :item="emergencyItem"
            :stage="emergencyStage"
            :busy="emergencyActionBusy"
            :elapsed-seconds="emergencyStore.operationStartedAt ? emergencyStore.elapsedSeconds : undefined"
            :error-message="emergencyError"
            @generate="emergencyStore.generate"
            @no-dispatch="emergencyStore.markNoDispatch"
            @level1="emergencyStore.decideLevel1"
            @correct-type="emergencyStore.correctEventType"
            @review="emergencyStore.review"
            @command="emergencyStore.decideCommand"
          />
          <WorkflowHistoryPanel
            v-else-if="emergencyViewMode === 'history' && emergencyHistory"
            :history="emergencyHistory"
            :busy="emergencyActionBusy || emergencyStore.polling"
            @page="emergencyStore.loadHistory"
            @release-resources="emergencyStore.releaseResources"
          />
          <div v-else-if="emergencyError" class="emergency-query-state error" role="alert">
            <span aria-hidden="true">!</span>
            <div><strong>待处理事件暂时无法加载</strong><p>{{ emergencyError }}</p></div>
            <button type="button" @click="emergencyStore.refresh">重新查询</button>
          </div>
          <div v-else-if="emergencyQueryStatus === 'loading'" class="emergency-query-state" aria-live="polite">
            <span class="progress-dot"></span>
            <div><strong>正在查询待处理事件</strong><p>请稍候…</p></div>
          </div>
          <div v-else class="emergency-query-state empty" aria-live="polite">
            <span aria-hidden="true">✓</span>
            <div><strong>{{ emergencyViewMode === 'history' ? '当前没有已办结记录' : '当前层次没有待处理事件' }}</strong><p>系统会继续在后台自动查询</p></div>
            <button type="button" @click="emergencyStore.refresh">重新查询</button>
          </div>
        </div>
      </section>
      <section v-show="activeTab === 'facilityWarning'" class="facility-warning-workspace" role="tabpanel">
        <FacilityWarningPanel />
      </section>
    </div>
  </aside>
</template>
