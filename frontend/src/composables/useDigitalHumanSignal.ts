import { computed, onScopeDispose, ref, watch, type Ref } from 'vue'
import { digitalHumanStateMeta, type DigitalHumanMode, type DigitalHumanSignal } from '../types/digitalHuman'
import type { AgentMessage } from '../types/agent'
import type { SpeechPlaybackStatus } from '../types/speech'

export interface DigitalHumanSnapshot {
  recording: boolean
  playbackStatus: SpeechPlaybackStatus
  running: boolean
  hasAnswerDelta: boolean
  emergencyActionBusy: boolean
  errorActive: boolean
}

export interface DigitalHumanSources {
  recording: Ref<boolean>
  playbackStatus: Ref<SpeechPlaybackStatus>
  playbackAmplitude: Ref<number>
  running: Ref<boolean>
  lastAssistantMessage: Ref<AgentMessage | undefined>
  emergencyActionBusy: Ref<boolean>
}

export function resolveDigitalHumanMode(snapshot: DigitalHumanSnapshot): DigitalHumanMode {
  if (snapshot.recording) return 'listening'
  if (snapshot.playbackStatus === 'playing') return 'speaking'
  if (snapshot.running && snapshot.hasAnswerDelta) return 'answering'
  if (snapshot.running || snapshot.playbackStatus === 'loading' || snapshot.emergencyActionBusy) {
    return 'thinking'
  }
  if (snapshot.errorActive) return 'error'
  return 'idle'
}

export function useDigitalHumanSignal(sources: DigitalHumanSources) {
  const errorActive = ref(false)
  let errorTimer: number | undefined

  function clearErrorTimer() {
    if (errorTimer !== undefined) window.clearTimeout(errorTimer)
    errorTimer = undefined
  }

  watch(
    () => {
      const message = sources.lastAssistantMessage.value
      return message ? `${message.id}:${message.status}` : ''
    },
    () => {
      clearErrorTimer()
      if (sources.lastAssistantMessage.value?.status !== 'failed') {
        errorActive.value = false
        return
      }
      errorActive.value = true
      errorTimer = window.setTimeout(() => {
        errorActive.value = false
        errorTimer = undefined
      }, 3000)
    },
    { immediate: true },
  )

  onScopeDispose(clearErrorTimer)

  return computed<DigitalHumanSignal>(() => {
    const message = sources.lastAssistantMessage.value
    const mode = resolveDigitalHumanMode({
      recording: sources.recording.value,
      playbackStatus: sources.playbackStatus.value,
      running: sources.running.value,
      hasAnswerDelta: Boolean(message?.status === 'pending' && message.content.trim()),
      emergencyActionBusy: sources.emergencyActionBusy.value,
      errorActive: errorActive.value,
    })
    const speechLevel = mode === 'speaking'
      ? Math.min(1, Math.max(0, sources.playbackAmplitude.value))
      : 0
    return { mode, statusLabel: digitalHumanStateMeta[mode].label, speechLevel }
  })
}
