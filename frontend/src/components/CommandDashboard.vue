<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

defineProps<{ agentOpen: boolean }>()
const emit = defineEmits<{ toggleAgent: [] }>()

const now = ref(new Date())
let timerId = 0

const dateText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  weekday: 'short',
}).format(now.value))

const timeText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
}).format(now.value))

onMounted(() => {
  timerId = window.setInterval(() => { now.value = new Date() }, 1000)
})

onUnmounted(() => {
  if (timerId) window.clearInterval(timerId)
})
</script>

<template>
  <section class="command-dashboard" aria-label="福建省路网应急与运行监测系统">
    <div class="command-map" aria-hidden="true"></div>
    <div class="command-vignette" aria-hidden="true"></div>
    <div class="command-grid" aria-hidden="true"></div>

    <header class="command-header">
      <div class="command-brand">
        <span class="command-brand-mark">路</span>
        <strong>路智通</strong>
        <i></i>
        <span>福建省路网应急与运行监测系统</span>
      </div>
      <div class="command-clock" aria-live="off">
        <span>{{ dateText }}</span>
        <strong>{{ timeText }}</strong>
      </div>
    </header>

    <div class="map-focus-label" aria-hidden="true">
      <span></span>
      <strong>福建路网态势</strong>
      <small>FUJIAN ROAD NETWORK</small>
    </div>

    <button
      type="button"
      class="assistant-orb"
      :class="{ active: agentOpen }"
      :aria-expanded="agentOpen"
      aria-controls="road-agent-drawer"
      :aria-label="agentOpen ? '收起路智通 AI 助手' : '打开路智通 AI 助手'"
      @click="emit('toggleAgent')"
    >
      <span class="orb-radar" aria-hidden="true"></span>
      <span class="orb-core" aria-hidden="true">
        <i></i><i></i><i></i>
      </span>
      <strong>路智通 AI 助手</strong>
      <small>{{ agentOpen ? '点击收起' : '点击咨询' }}</small>
    </button>

  </section>
</template>
