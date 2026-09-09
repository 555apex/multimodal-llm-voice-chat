<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { SpeechApiError, transcribeSpeech } from '../api/speechApi'
import { useSpeechStore } from '../stores/speech'

const props = withDefaults(defineProps<{
  active?: boolean
  disabled?: boolean
  available: boolean
  maxRecordingSeconds: number
  maxAudioBytes: number
}>(), { active: true, disabled: false })
const emit = defineEmits<{
  transcribed: [text: string]
  recordingChanged: [recording: boolean]
}>()

type RecorderStatus = 'idle' | 'recording' | 'transcribing' | 'error'

const speechStore = useSpeechStore()
const status = ref<RecorderStatus>('idle')
const elapsedSeconds = ref(0)
const errorMessage = ref('')
let recorder: MediaRecorder | null = null
let stream: MediaStream | null = null
let chunks: Blob[] = []
let recordedBytes = 0
let startedAt = 0
let timerId: number | undefined
let stopTimerId: number | undefined
let cancelled = false
let transcriptionAbort: AbortController | null = null

const label = computed(() => {
  if (status.value === 'recording') return `停止录音 ${elapsedSeconds.value}秒`
  if (status.value === 'transcribing') return '取消识别'
  if (status.value === 'error') return '重新录音'
  return '语音输入'
})

async function toggle() {
  if (status.value === 'recording') {
    stopRecording()
    return
  }
  if (status.value === 'transcribing') {
    transcriptionAbort?.abort()
    status.value = 'idle'
    return
  }
  await startRecording()
}

async function startRecording() {
  errorMessage.value = ''
  if (!props.available) {
    fail('语音识别服务尚未就绪')
    return
  }
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    fail('当前浏览器不支持录音，请使用最新版Chrome、Edge或Safari')
    return
  }
  speechStore.stop()
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    })
    const mimeType = chooseMimeType()
    recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
    chunks = []
    recordedBytes = 0
    cancelled = false
    recorder.ondataavailable = (event) => {
      if (!event.data.size) return
      chunks.push(event.data)
      recordedBytes += event.data.size
      if (recordedBytes > props.maxAudioBytes) stopRecording()
    }
    recorder.onerror = () => fail('录音过程中发生错误，请重试')
    recorder.onstop = () => void completeRecording()
    startedAt = Date.now()
    elapsedSeconds.value = 0
    status.value = 'recording'
    emit('recordingChanged', true)
    recorder.start(250)
    timerId = window.setInterval(() => {
      elapsedSeconds.value = Math.min(
        props.maxRecordingSeconds,
        Math.floor((Date.now() - startedAt) / 1000),
      )
    }, 250)
    stopTimerId = window.setTimeout(stopRecording, props.maxRecordingSeconds * 1000)
  } catch (error) {
    cleanupMedia()
    const denied = error instanceof DOMException && error.name === 'NotAllowedError'
    fail(denied ? '麦克风权限被拒绝，请在浏览器设置中允许访问' : '无法启动麦克风')
  }
}

function stopRecording() {
  if (recorder?.state === 'recording') recorder.stop()
}

function cancelRecording() {
  cancelled = true
  if (recorder?.state === 'recording') recorder.stop()
  transcriptionAbort?.abort()
  cleanupMedia()
  status.value = 'idle'
  emit('recordingChanged', false)
}

async function completeRecording() {
  const durationMs = Date.now() - startedAt
  const mimeType = recorder?.mimeType || chunks[0]?.type || 'audio/webm'
  const blob = new Blob(chunks, { type: mimeType })
  cleanupMedia()
  emit('recordingChanged', false)
  if (cancelled) {
    status.value = 'idle'
    return
  }
  if (!blob.size) {
    fail('没有录到声音，请重试')
    return
  }
  if (blob.size > props.maxAudioBytes) {
    fail('录音文件过大，请缩短录音时间')
    return
  }
  status.value = 'transcribing'
  transcriptionAbort = new AbortController()
  try {
    const result = await transcribeSpeech(blob, durationMs, transcriptionAbort.signal)
    emit('transcribed', result.text)
    status.value = 'idle'
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      status.value = 'idle'
      return
    }
    if (error instanceof SpeechApiError && error.code === 'ASR_NO_SPEECH') {
      errorMessage.value = ''
      status.value = 'idle'
      return
    }
    fail(error instanceof Error ? error.message : '语音识别失败')
  } finally {
    transcriptionAbort = null
  }
}

function cleanupMedia() {
  if (timerId !== undefined) window.clearInterval(timerId)
  if (stopTimerId !== undefined) window.clearTimeout(stopTimerId)
  timerId = undefined
  stopTimerId = undefined
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  recorder = null
  chunks = []
  recordedBytes = 0
}

function fail(message: string) {
  cleanupMedia()
  emit('recordingChanged', false)
  errorMessage.value = message
  status.value = 'error'
}

function chooseMimeType() {
  const candidates = [
    'audio/webm;codecs=opus',
    'audio/mp4;codecs=mp4a.40.2',
    'audio/mp4',
    'audio/webm',
    'audio/ogg;codecs=opus',
  ]
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? ''
}

watch(() => props.active, (active) => {
  if (!active && status.value !== 'idle') cancelRecording()
})

onUnmounted(cancelRecording)
</script>

<template>
  <div class="voice-input-control" :data-status="status">
    <button
      type="button"
      class="voice-input-button"
      :disabled="(disabled || !available) && status === 'idle'"
      :aria-label="label"
      :title="label"
      @click="toggle"
    >
      <span aria-hidden="true">{{ status === 'recording' ? '■' : status === 'transcribing' ? '…' : '🎙' }}</span>
    </button>
    <span v-if="status === 'recording'" class="voice-recording-actions">
      <button
        type="button"
        class="voice-input-finish"
        aria-label="停止录音并开始识别"
        @click="stopRecording"
      >
        停止并识别
      </button>
      <button type="button" class="voice-input-cancel" aria-label="取消本次录音" @click="cancelRecording">
        取消
      </button>
    </span>
    <span v-else-if="status === 'transcribing'" class="voice-recording-time">识别中</span>
    <small v-if="errorMessage" class="voice-input-error" role="alert">{{ errorMessage }}</small>
  </div>
</template>
