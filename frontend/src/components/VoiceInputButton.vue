<script setup lang="ts">
import { useVoiceRecorder } from '../composables/useVoiceRecorder'
const props = withDefaults(defineProps<{
  active?: boolean; disabled?: boolean; available: boolean; maxRecordingSeconds: number; maxAudioBytes: number
}>(), { active: true, disabled: false })
const emit = defineEmits<{ transcribed: [text: string]; recordingChanged: [recording: boolean] }>()
const { status, label, elapsedSeconds, errorMessage, toggle, stopRecording, cancelRecording } = useVoiceRecorder(
  props, text => emit('transcribed', text), recording => emit('recordingChanged', recording),
)
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
      <span aria-hidden="true">{{ status === 'recording' ? '■' : (status === 'transcribing' || status === 'requesting') ? '…' : '🎙' }}</span>
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
    <span v-else-if="status === 'requesting'" class="voice-recording-time">等待麦克风授权，可点击取消</span>
    <span v-else-if="status === 'transcribing'" class="voice-recording-time">识别中</span>
    <small v-if="errorMessage" class="voice-input-error" role="alert">{{ errorMessage }}</small>
  </div>
</template>
