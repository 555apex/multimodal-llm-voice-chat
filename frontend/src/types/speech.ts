export interface SpeechCapabilities {
  asrAvailable: boolean
  ttsAvailable: boolean
  ttsStreamingAvailable?: boolean
  asrModel?: string
  ttsVoice?: string
  maxRecordingSeconds: number
  maxAudioBytes: number
}

export interface SpeechTranscription {
  text: string
  language: string
  durationMs: number
}

export type SpeechPlaybackStatus = 'idle' | 'loading' | 'playing' | 'paused' | 'failed'
