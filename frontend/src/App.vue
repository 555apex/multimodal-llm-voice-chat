<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import ChatMessage from './components/ChatMessage.vue'
import DigitalHumanPanel from './components/DigitalHumanPanel.vue'
import EmergencyAlertCard from './components/EmergencyAlertCard.vue'
import VoiceInputButton from './components/VoiceInputButton.vue'
import { useAgentStore } from './stores/agent'
import { useEmergencyStore } from './stores/emergency'
import { useSpeechStore } from './stores/speech'

const store = useAgentStore()
const { messages, running, stage, toolProgress, approvalBusyPlanId } = storeToRefs(store)
const emergencyStore = useEmergencyStore()
const {
  alert: emergencyAlert,
  actionBusy: emergencyActionBusy,
  queryStatus: emergencyQueryStatus,
  errorMessage: emergencyError,
} = storeToRefs(emergencyStore)
const input = ref('')
const inputElement = ref<HTMLTextAreaElement>()
const messageList = ref<HTMLElement>()
const recording = ref(false)
const speechStore = useSpeechStore()
const {
  capabilities: speechCapabilities,
  capabilitiesLoading: speechCapabilitiesLoading,
  capabilityError: speechCapabilityError,
  autoReadEnabled,
  playbackStatus,
} = storeToRefs(speechStore)

const avatarState = computed(() => {
  if (messages.value.at(-1)?.status === 'failed') return 'error'
  if (recording.value) return 'listening'
  if (playbackStatus.value === 'playing') return 'speaking'
  if (!running.value) return 'idle'
  return 'listening'
})

const examples = [
  '福州五四路现在堵吗？',
  '厦门市思明区的交通情况如何？',
  '思明区交通要道现在通行情况如何？',
]

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
  await store.send(content)
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
}

watch(
  () => messages.value.map((message) => `${message.content.length}-${message.status}`).join('|'),
  async () => {
    await nextTick()
    messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
  },
)

onMounted(() => {
  emergencyStore.startPolling()
  void speechStore.loadCapabilities()
})
onUnmounted(() => {
  emergencyStore.stopPolling()
  speechStore.stop()
})
</script>

<template>
  <main class="agent-page">
    <header class="topbar">
      <div class="brand">
        <span class="brand-symbol">路</span>
        <div><strong>福建应急交通 Agent</strong><small>真实路况 · 智能研判 · 安全调度</small></div>
      </div>
      <button class="new-chat-button" :disabled="running" @click="store.reset">新建会话</button>
    </header>

    <section class="agent-workspace">
      <DigitalHumanPanel :state="avatarState" />

      <section class="chat-panel">
        <EmergencyAlertCard
          v-if="emergencyAlert"
          :alert="emergencyAlert"
          :busy="emergencyActionBusy"
          :error-message="emergencyError"
          @generate="emergencyStore.generate"
          @no-dispatch="emergencyStore.markNoDispatch"
          @decide="emergencyStore.decide"
        />
        <div v-else-if="emergencyError" class="emergency-query-error">
          紧急事件告警暂时无法加载：{{ emergencyError }}
          <button @click="emergencyStore.refresh">重新查询</button>
        </div>
        <div v-else-if="emergencyQueryStatus === 'loading'" class="emergency-query-status" aria-live="polite">
          <span class="progress-dot"></span>
          正在查询待处理事件…
        </div>
        <div v-else-if="emergencyQueryStatus === 'empty'" class="emergency-query-status empty" aria-live="polite">
          <span>当前没有待处理事件</span>
          <button @click="emergencyStore.refresh">重新查询</button>
        </div>

        <div ref="messageList" class="message-list">
          <ChatMessage
            v-for="message in messages"
            :key="message.id"
            :message="message"
            :approval-busy-plan-id="approvalBusyPlanId"
            @decide="store.decide"
          />
        </div>

        <div v-if="messages.length <= 1" class="example-prompts">
          <button v-for="example in examples" :key="example" @click="useExample(example)">
            {{ example }}
          </button>
        </div>

        <div class="agent-progress" :class="{ visible: running }" aria-live="polite">
          <span class="progress-dot"></span>{{ progressText }}
        </div>

        <form class="chat-composer" @submit.prevent="send">
          <VoiceInputButton
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
            placeholder="询问道路或行政区实时路况…"
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
            Enter发送 · Shift + Enter换行 · 正式调度请使用顶部告警卡
          </p>
        </div>
      </section>
    </section>
  </main>
</template>
