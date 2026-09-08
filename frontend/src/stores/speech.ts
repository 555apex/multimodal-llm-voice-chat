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

type CapturableAudio = HTMLAudioElement & { captureStream?: () => MediaStream }
type AudioContextConstructor = new () => AudioContext

let amplitudeFrame: number | undefined
let amplitudeCancelFrame: ((handle: number) => void) | undefined
let amplitudeContext: AudioContext | null = null
let amplitudeSource: MediaStreamAudioSourceNode | null = null
let amplitudeStream: MediaStream | null = null

function scheduleAmplitudeFrame(callback: FrameRequestCallback) {
  if (typeof window.requestAnimationFrame === 'function') {
    amplitudeCancelFrame = (handle) => window.cancelAnimationFrame(handle)
    amplitudeFrame = window.requestAnimationFrame(callback)
    return
  }
  amplitudeCancelFrame = (handle) => window.clearTimeout(handle)
  amplitudeFrame = window.setTimeout(() => callback(performance.now()), 33)
}

function stopAmplitudeTracking(update: (level: number) => void) {
  if (amplitudeFrame !== undefined) amplitudeCancelFrame?.(amplitudeFrame)
  amplitudeFrame = undefined
  amplitudeCancelFrame = undefined
  amplitudeSource?.disconnect()
  amplitudeSource = null
  amplitudeStream?.getTracks().forEach((track) => track.stop())
  amplitudeStream = null
  const context = amplitudeContext
  amplitudeContext = null
  if (context && context.state !== 'closed') void context.close().catch(() => undefined)
  update(0)
}

function startFallbackAmplitude(audio: HTMLAudioElement, update: (level: number) => void) {
  const startedAt = performance.now()
  const tick: FrameRequestCallback = (timestamp) => {
    if (activeAudio !== audio || audio.paused) {
      update(0)
      return
    }
    const currentTime = Number.isFinite(audio.currentTime)
      ? audio.currentTime
      : (timestamp - startedAt) / 1000
    const carrier = Math.abs(Math.sin(currentTime * 19.1))
    const variation = 0.72 + 0.28 * Math.abs(Math.sin(currentTime * 7.3 + 0.8))
    update(0.12 + 0.5 * carrier * variation)
    scheduleAmplitudeFrame(tick)
  }
  scheduleAmplitudeFrame(tick)
}

function startAmplitudeTracking(audio: HTMLAudioElement, update: (level: number) => void) {
  stopAmplitudeTracking(update)
  const capture = (audio as CapturableAudio).captureStream
  const AudioContextClass = window.AudioContext
    ?? (window as typeof window & { webkitAudioContext?: AudioContextConstructor }).webkitAudioContext
  if (!capture || !AudioContextClass) {
    startFallbackAmplitude(audio, update)
    return
  }

  try {
    const context = new AudioContextClass()
    const stream = capture.call(audio)
    const source = context.createMediaStreamSource(stream)
    const analyser = context.createAnalyser()
    analyser.fftSize = 256
    analyser.smoothingTimeConstant = 0.65
    source.connect(analyser)
    amplitudeContext = context
    amplitudeSource = source
    amplitudeStream = stream
    const samples = new Uint8Array(analyser.fftSize)
    let envelope = 0
    let lastUpdate = 0

    const tick: FrameRequestCallback = (timestamp) => {
      if (activeAudio !== audio || audio.paused) {
        update(0)
        return
      }
      if (timestamp - lastUpdate >= 33) {
        analyser.getByteTimeDomainData(samples)
        let squareSum = 0
        for (const sample of samples) {
          const normalized = (sample - 128) / 128
          squareSum += normalized * normalized
        }
        const rms = Math.sqrt(squareSum / samples.length)
        const target = Math.min(1, Math.max(0, (rms - 0.025) / 0.18))
        envelope += (target - envelope) * (target > envelope ? 0.55 : 0.22)
        if (target === 0 && envelope < 0.012) envelope = 0
        update(envelope)
        lastUpdate = timestamp
      }
      scheduleAmplitudeFrame(tick)
    }

    void context.resume().catch(() => {
      if (activeAudio === audio) {
        stopAmplitudeTracking(update)
        startFallbackAmplitude(audio, update)
      }
    })
    scheduleAmplitudeFrame(tick)
  } catch {
    stopAmplitudeTracking(update)
    startFallbackAmplitude(audio, update)
  }
}

export const useSpeechStore = defineStore('speech', {
  state: () => ({
    capabilities: null as SpeechCapabilities | null,
    capabilitiesLoading: false,
    capabilityError: '',
    autoReadEnabled: false,
    surfaceActive: true,
    playbackMessageId: '',
    playbackStatus: 'idle' as SpeechPlaybackStatus,
    playbackAmplitude: 0,
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

    setSurfaceActive(active: boolean) {
      this.surfaceActive = active
      if (!active) this.stop()
    },

    async toggleMessage(messageId: string, speechText: string) {
      if (this.playbackMessageId === messageId) {
        if (this.playbackStatus === 'playing') {
          activeAudio?.pause()
          stopAmplitudeTracking((level) => { this.playbackAmplitude = level })
          this.playbackStatus = 'paused'
          return
        }
        if (this.playbackStatus === 'paused' && activeAudio) {
          try {
            await activeAudio.play()
            this.playbackStatus = 'playing'
            startAmplitudeTracking(activeAudio, (level) => { this.playbackAmplitude = level })
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
      if (!segments.length || !this.capabilities?.ttsAvailable || !this.surfaceActive) return
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
          if (generation === playbackGeneration) {
            this.playbackStatus = 'playing'
            startAmplitudeTracking(audio, (level) => { this.playbackAmplitude = level })
          }
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
      stopAmplitudeTracking((level) => { this.playbackAmplitude = level })
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
