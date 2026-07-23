<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import ChatMessage from './components/ChatMessage.vue'
import DigitalHumanPanel from './components/DigitalHumanPanel.vue'
import { useAgentStore } from './stores/agent'

const store = useAgentStore()
const { messages, running, stage, toolProgress, approvalBusyPlanId } = storeToRefs(store)
const input = ref('')
const messageList = ref<HTMLElement>()

const avatarState = computed(() => {
  if (messages.value.at(-1)?.status === 'failed') return 'error'
  if (!running.value) return 'idle'
  return stage.value?.stage === 'ANSWERING' ? 'speaking' : 'listening'
})

const examples = [
  '福州五四路现在堵吗？',
  '厦门市思明区的交通情况如何？',
  '思明区交通要道现在通行情况如何？',
  '福州五四路发生塌方，请生成应急调度方案。',
]

const progressText = computed(() => {
  if (!toolProgress.value) return stage.value?.label ?? 'Agent正在处理'
  const progress = toolProgress.value
  const failed = progress.failedTiles ? `，失败 ${progress.failedTiles} 片` : ''
  return `${stage.value?.label ?? '正在采集'}：${progress.completedTiles}/${progress.totalTiles} 片${failed}`
})

async function send() {
  const content = input.value.trim()
  if (!content || running.value) return
  input.value = ''
  await store.send(content)
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
          <textarea
            v-model="input"
            rows="2"
            maxlength="1000"
            :disabled="running"
            placeholder="询问路况，或描述需要调度的公路应急事件…"
            @keydown="handleKeydown"
          ></textarea>
          <button type="submit" :disabled="running || !input.trim()">
            <span>{{ running ? '生成中' : '发送' }}</span><i aria-hidden="true">↗</i>
          </button>
        </form>
        <p class="composer-hint">Enter发送 · Shift + Enter换行 · 调度下发必须点击人工确认按钮</p>
      </section>
    </section>
  </main>
</template>
