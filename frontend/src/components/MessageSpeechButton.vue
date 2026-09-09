<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSpeechStore } from '../stores/speech'

const props = defineProps<{ messageId: string; speechText: string }>()
const store = useSpeechStore()
const { capabilities, playbackMessageId, playbackStatus, playbackError } = storeToRefs(store)

const isCurrent = computed(() => playbackMessageId.value === props.messageId)
const label = computed(() => {
  if (!isCurrent.value) return '朗读'
  if (playbackStatus.value === 'playing') return '暂停'
  if (playbackStatus.value === 'paused') return '继续'
  if (playbackStatus.value === 'loading') return '停止'
  if (playbackStatus.value === 'failed') return '重试'
  return '朗读'
})
const icon = computed(() => {
  if (!isCurrent.value) return '🔊'
  if (playbackStatus.value === 'playing') return '⏸'
  if (playbackStatus.value === 'paused') return '▶'
  if (playbackStatus.value === 'loading') return '■'
  return '🔊'
})
</script>

<template>
  <div class="message-speech-control">
    <button
      type="button"
      :disabled="!capabilities?.ttsAvailable"
      :aria-label="`${label}这条回答`"
      @click="store.toggleMessage(messageId, speechText)"
    >
      <span aria-hidden="true">{{ icon }}</span>{{ label }}
    </button>
    <button v-if="isCurrent && (playbackStatus === 'playing' || playbackStatus === 'paused')"
      type="button" aria-label="停止朗读" @click="store.stop()">停止</button>
    <small v-if="isCurrent && playbackError">{{ playbackError }}</small>
  </div>
</template>
