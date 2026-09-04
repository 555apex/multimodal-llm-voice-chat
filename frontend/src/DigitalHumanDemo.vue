<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import DigitalHumanPrototype from './components/DigitalHumanPrototype.vue'
import { useSpeechStore } from './stores/speech'
import {
  digitalHumanStateMeta,
  type DigitalHumanMode,
  type DigitalHumanPose,
} from './types/digitalHuman'

const DEMO_REPORT = '演示文案，非实时交通数据。本次仅展示路智通数字人的本地语音汇报、姿态切换与动态反馈能力。'
const AUTO_SEQUENCE: Array<{ mode: DigitalHumanMode; duration: number }> = [
  { mode: 'idle', duration: 1800 },
  { mode: 'listening', duration: 1800 },
  { mode: 'thinking', duration: 2400 },
  { mode: 'answering', duration: 2400 },
  { mode: 'speaking', duration: 3200 },
]

const speechStore = useSpeechStore()
const {
  capabilities,
  capabilitiesLoading,
  capabilityError,
  playbackStatus,
  playbackAmplitude,
  playbackError,
} = storeToRefs(speechStore)

const visualState = ref<DigitalHumanMode>('idle')
const assetsReady = ref(false)
const failedAssets = ref<DigitalHumanPose[]>([])
const autoRunning = ref(false)
const autoPaused = ref(false)
const sequenceIndex = ref(0)
const reportNotice = ref('')
let autoTimer: number | undefined

const states: DigitalHumanMode[] = ['idle', 'listening', 'thinking', 'answering', 'speaking', 'error']
const selectedState = computed<DigitalHumanMode>(() => {
  if (playbackStatus.value === 'loading') return 'thinking'
  if (playbackStatus.value === 'playing') return 'speaking'
  return visualState.value
})
const activeMeta = computed(() => digitalHumanStateMeta[selectedState.value])
const ttsBusy = computed(() => playbackStatus.value === 'loading' || playbackStatus.value === 'playing')

function clearAutoTimer() {
  if (autoTimer !== undefined) window.clearTimeout(autoTimer)
  autoTimer = undefined
}

function scheduleAutoStep() {
  clearAutoTimer()
  const step = AUTO_SEQUENCE[sequenceIndex.value]
  visualState.value = step.mode
  autoTimer = window.setTimeout(() => {
    sequenceIndex.value = (sequenceIndex.value + 1) % AUTO_SEQUENCE.length
    scheduleAutoStep()
  }, step.duration)
}

function startDemo() {
  if (autoRunning.value) return
  speechStore.stop()
  const resume = autoPaused.value
  autoRunning.value = true
  autoPaused.value = false
  reportNotice.value = ''
  if (!resume) sequenceIndex.value = 0
  scheduleAutoStep()
}

function pauseDemo() {
  if (!autoRunning.value) return
  clearAutoTimer()
  autoRunning.value = false
  autoPaused.value = true
}

function replayDemo() {
  clearAutoTimer()
  speechStore.stop()
  sequenceIndex.value = 0
  autoRunning.value = true
  autoPaused.value = false
  reportNotice.value = ''
  scheduleAutoStep()
}

function selectState(state: DigitalHumanMode) {
  clearAutoTimer()
  autoRunning.value = false
  autoPaused.value = false
  speechStore.stop()
  visualState.value = state
}

function handleAssetError(pose: DigitalHumanPose) {
  if (!failedAssets.value.includes(pose)) failedAssets.value.push(pose)
}

async function playReportDemo() {
  clearAutoTimer()
  autoRunning.value = false
  autoPaused.value = false
  reportNotice.value = ''
  if (!capabilities.value?.ttsAvailable) {
    reportNotice.value = capabilityError.value || '本地 TTS 当前不可用，姿态演示仍可继续。'
    return
  }
  visualState.value = 'thinking'
  await speechStore.speak('digital-human-demo-report', DEMO_REPORT)
  if (playbackStatus.value === 'failed') {
    reportNotice.value = playbackError.value || '浏览器未能播放音频，姿态演示仍可继续。'
  } else {
    visualState.value = 'idle'
  }
}

watch(playbackStatus, (status) => {
  if (status === 'failed') {
    reportNotice.value = playbackError.value || '本地 TTS 播放失败，姿态演示仍可继续。'
  }
})

onMounted(async () => {
  speechStore.setSurfaceActive(true)
  await speechStore.loadCapabilities()
})

onUnmounted(() => {
  clearAutoTimer()
  speechStore.stop()
  speechStore.setSurfaceActive(false)
})
</script>

<template>
  <main class="prototype-demo-page">
    <header class="prototype-demo-header">
      <div class="prototype-demo-brand">
        <span>路</span>
        <div>
          <strong>“路智通”数字人动态演示</strong>
          <small>六状态生产渲染 · 与主 Agent 共用人物资产和状态映射</small>
        </div>
      </div>
      <a href="/">返回业务页面</a>
    </header>

    <section class="prototype-demo-layout">
      <DigitalHumanPrototype
        :state="selectedState"
        :speech-level="playbackStatus === 'playing' ? playbackAmplitude : 0"
        :preview-speech="selectedState === 'speaking' && playbackStatus !== 'playing'"
        @ready-change="assetsReady = $event"
        @asset-error="handleAssetError"
      />

      <section class="prototype-control-panel" aria-labelledby="prototype-control-title">
        <p class="prototype-eyebrow">DIGITAL HUMAN RUNTIME</p>
        <h1 id="prototype-control-title">动态姿态与本地语音汇报</h1>
        <p class="prototype-lead">
          三张透明人物姿态映射六种业务状态。自动演示依次呈现待命、聆听、研判、文字讲解和语音汇报。
        </p>

        <div class="prototype-playback-controls" role="group" aria-label="自动演示控制">
          <button type="button" :disabled="!assetsReady || autoRunning" @click="startDemo">开始演示</button>
          <button type="button" :disabled="!autoRunning" @click="pauseDemo">暂停</button>
          <button type="button" :disabled="!assetsReady" @click="replayDemo">重新播放</button>
          <span aria-live="polite">{{ autoRunning ? '自动演示中' : autoPaused ? '演示已暂停' : '手动模式' }}</span>
        </div>

        <div class="prototype-state-buttons" role="group" aria-label="数字人状态">
          <button
            v-for="state in states"
            :key="state"
            type="button"
            :class="{ active: selectedState === state }"
            :aria-pressed="selectedState === state"
            :disabled="!assetsReady"
            @click="selectState(state)"
          >
            <code>{{ state }}</code>
            <span>{{ digitalHumanStateMeta[state].label }}</span>
          </button>
        </div>

        <article class="prototype-current-state" :data-state="selectedState">
          <div>
            <span>当前状态</span>
            <code>{{ selectedState }}</code>
          </div>
          <dl>
            <div><dt>人物姿态</dt><dd>{{ activeMeta.poseLabel }}</dd></div>
            <div><dt>状态文案</dt><dd>{{ activeMeta.label }}</dd></div>
            <div><dt>动态反馈</dt><dd>{{ activeMeta.detail }}</dd></div>
          </dl>
        </article>

        <article class="prototype-report-demo">
          <header>
            <div><strong>本地 TTS 汇报演示</strong><small>演示文案 · 非实时交通数据</small></div>
            <span :class="{ ready: capabilities?.ttsAvailable }">
              {{ capabilitiesLoading ? '检测中' : capabilities?.ttsAvailable ? 'TTS 可用' : 'TTS 不可用' }}
            </span>
          </header>
          <p>{{ DEMO_REPORT }}</p>
          <button
            type="button"
            :disabled="capabilitiesLoading || !capabilities?.ttsAvailable || ttsBusy"
            @click="playReportDemo"
          >
            {{ ttsBusy ? '正在播放…' : '播放汇报演示' }}
          </button>
          <small v-if="reportNotice" class="prototype-report-notice" role="alert">{{ reportNotice }}</small>
        </article>

        <div class="prototype-architecture-note">
          <strong>当前实现边界</strong>
          <ul>
            <li>三姿态 600ms 可中断缓动过渡，含呼吸、扫描光效和声波</li>
            <li>真实 TTS 振幅驱动闭嘴、半开、张开三档嘴型连续混合</li>
            <li>汇报演示调用与主页面相同的 DGX 本地 TTS 接口</li>
            <li>不做汉字或音素级嘴型对应，分析器不可用时自动降级</li>
          </ul>
        </div>

        <p v-if="!assetsReady" class="prototype-load-status" aria-live="polite">正在预载三张姿态图…</p>
        <p v-else-if="failedAssets.length" class="prototype-load-status error" aria-live="polite">
          {{ failedAssets.join('、') }} 姿态加载失败，组件将自动回退到待命图。
        </p>
        <p v-else class="prototype-load-status ready" aria-live="polite">三张姿态图已加载，可以开始演示。</p>
      </section>
    </section>
  </main>
</template>
