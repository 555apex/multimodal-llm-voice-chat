import { defineStore } from 'pinia'
import { fetchSpeechCapabilities, synthesizeSpeech } from '../api/speechApi'
import { splitSpeechText } from '../utils/speechText'
import type { SpeechCapabilities, SpeechPlaybackStatus } from '../types/speech'

let activeAudio: HTMLAudioElement | null = null
let activeObjectUrl = ''
let activeAbort: AbortController | null = null
let activePlaybackCompletion: {
  resolve: () => void
  reject: (reason: Error) => void
} | null = null
let playbackGeneration = 0
const audioCache = new Map<string, Blob>()

export const useSpeechStore = defineStore('speech', {
  state: () => ({
    capabilities: null as SpeechCapabilities | null,
    capabilitiesLoading: false,
    capabilityError: '',
    autoReadEnabled: false,
    playbackMessageId: '',
    playbackStatus: 'idle' as SpeechPlaybackStatus,
    playbackError: '',
  }),

  actions: {
    async loadCapabilities() {
      this.capabilitiesLoading = true
      this.capabilityError = ''
      try {
        this.capabilities = await fetchSpeechCapabilities()
      } catch (error) {
        this.capabilities = {
          asrAvailable: false,
          ttsAvailable: false,
          maxRecordingSeconds: 60,
          maxAudioBytes: 10 * 1024 * 1024,
        }
        this.capabilityError = error instanceof Error ? error.message : '语音服务不可用'
      } finally {
        this.capabilitiesLoading = false
      }
    },

    setAutoRead(enabled: boolean) {
      this.autoReadEnabled = enabled && Boolean(this.capabilities?.ttsAvailable)
      if (!this.autoReadEnabled) this.stop()
    },

    async toggleMessage(messageId: string, speechText: string) {
      if (this.playbackMessageId === messageId) {
        if (this.playbackStatus === 'playing') {
          activeAudio?.pause()
          this.playbackStatus = 'paused'
          return
        }
        if (this.playbackStatus === 'paused' && activeAudio) {
          try {
            await activeAudio.play()
            this.playbackStatus = 'playing'
          } catch {
            this.playbackStatus = 'failed'
            this.playbackError = '浏览器阻止了音频播放，请再次点击播放'
          }
          return
        }
        if (this.playbackStatus === 'loading') {
          this.stop()
          return
        }
      }
      await this.speak(messageId, speechText)
    },

    async speak(messageId: string, speechText: string) {
      const segments = splitSpeechText(speechText)
      if (!segments.length || !this.capabilities?.ttsAvailable) return
      this.stop(false)
      const generation = playbackGeneration
      activeAbort = new AbortController()
      this.playbackMessageId = messageId
      this.playbackStatus = 'loading'
      this.playbackError = ''

      try {
        let nextAudio = this.loadSegment(messageId, 0, segments[0], activeAbort.signal)
        for (let index = 0; index < segments.length; index += 1) {
          const blob = await nextAudio
          if (generation !== playbackGeneration) return
          nextAudio = index + 1 < segments.length
            ? this.loadSegment(messageId, index + 1, segments[index + 1], activeAbort.signal)
            : Promise.resolve(new Blob())
          await this.playBlob(blob, generation)
        }
        if (generation === playbackGeneration) {
          this.finishPlayback()
        }
      } catch (error) {
        if (generation !== playbackGeneration) return
        this.releaseActiveAudio()
        this.playbackStatus = 'failed'
        this.playbackError = error instanceof Error && error.name !== 'AbortError'
          ? error.message : '语音播放已取消'
      }
    },

    async loadSegment(
      messageId: string,
      index: number,
      text: string,
      signal: AbortSignal,
    ) {
      const key = `${messageId}:${index}:${text}`
      const cached = audioCache.get(key)
      if (cached) return cached
      const blob = await synthesizeSpeech(text, signal)
      audioCache.set(key, blob)
      return blob
    },

    playBlob(blob: Blob, generation: number) {
      return new Promise<void>((resolve, reject) => {
        if (generation !== playbackGeneration) {
          resolve()
          return
        }
        this.releaseActiveAudio()
        activeObjectUrl = URL.createObjectURL(blob)
        const audio = new Audio(activeObjectUrl)
        activeAudio = audio
        activePlaybackCompletion = { resolve, reject }
        audio.onended = () => {
          this.releaseActiveAudio()
        }
        audio.onerror = () => {
          this.releaseActiveAudio(new Error('合成音频无法播放'))
        }
        void audio.play().then(() => {
          if (generation === playbackGeneration) this.playbackStatus = 'playing'
        }).catch((error) => {
          this.releaseActiveAudio(
            error instanceof Error ? error : new Error('浏览器阻止了音频播放'),
          )
        })
      })
    },

    stop(clearError = true) {
      playbackGeneration += 1
      activeAbort?.abort()
      activeAbort = null
      this.releaseActiveAudio()
      this.playbackMessageId = ''
      this.playbackStatus = 'idle'
      if (clearError) this.playbackError = ''
    },

    finishPlayback() {
      activeAbort = null
      this.releaseActiveAudio()
      this.playbackMessageId = ''
      this.playbackStatus = 'idle'
    },

    releaseActiveAudio(error?: Error) {
      const completion = activePlaybackCompletion
      activePlaybackCompletion = null
      if (activeAudio) {
        activeAudio.onended = null
        activeAudio.onerror = null
        activeAudio.pause()
        activeAudio = null
      }
      if (activeObjectUrl) {
        URL.revokeObjectURL(activeObjectUrl)
        activeObjectUrl = ''
      }
      if (completion) {
        if (error) completion.reject(error)
        else completion.resolve()
      }
    },
  },
})
