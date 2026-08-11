import type { ApiResponse } from '../types/traffic'
import type { SpeechCapabilities, SpeechTranscription } from '../types/speech'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function parseJson<T>(response: Response, fallback: string): Promise<T> {
  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(fallback)
  }
  if (!response.ok) throw new Error(body.message || fallback)
  return body.data
}

export async function fetchSpeechCapabilities(): Promise<SpeechCapabilities> {
  const response = await fetch(`${apiBaseUrl}/api/v1/speech/capabilities`)
  return parseJson<SpeechCapabilities>(response, '无法查询语音服务状态')
}

export async function transcribeSpeech(
  audio: Blob,
  durationMs: number,
  signal?: AbortSignal,
): Promise<SpeechTranscription> {
  const form = new FormData()
  form.append('audio', audio, recordingFileName(audio.type))
  form.append('durationMs', Math.max(1, Math.round(durationMs)).toString())
  const response = await fetch(`${apiBaseUrl}/api/v1/speech/transcriptions`, {
    method: 'POST',
    body: form,
    signal,
  })
  return parseJson<SpeechTranscription>(response, '语音识别失败')
}

export async function synthesizeSpeech(text: string, signal?: AbortSignal): Promise<Blob> {
  const response = await fetch(`${apiBaseUrl}/api/v1/speech/syntheses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'audio/mpeg' },
    body: JSON.stringify({ text }),
    signal,
  })
  if (!response.ok) {
    let message = '语音合成失败'
    try {
      const body = (await response.json()) as ApiResponse<null>
      message = body.message || message
    } catch { /* 非JSON错误响应使用统一提示 */ }
    throw new Error(message)
  }
  return response.blob()
}

function recordingFileName(contentType: string) {
  const normalized = contentType.toLowerCase()
  if (normalized.includes('mp4')) return 'recording.m4a'
  if (normalized.includes('ogg')) return 'recording.ogg'
  if (normalized.includes('wav')) return 'recording.wav'
  return 'recording.webm'
}
