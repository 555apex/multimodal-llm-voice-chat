import type { ApiResponse } from '../types/traffic'
import type { SpeechCapabilities, SpeechTranscription } from '../types/speech'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
const speechErrors: Record<string, string> = {
  ASR_NO_SPEECH: '未识别到清晰语音，请靠近麦克风重试',
  ASR_INVALID_AUDIO: '录音格式无法解码，请重新录音',
  ASR_BUSY: '语音服务正忙，请稍后重试',
  ASR_TIMEOUT: '语音识别超时，请重新录音',
  ASR_UNAVAILABLE: '语音识别暂不可用，请稍后重试',
}

export class SpeechApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly traceId: string,
  ) {
    super(message)
    this.name = 'SpeechApiError'
  }
}

async function parseJson<T>(response: Response, fallback: string): Promise<T> {
  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(fallback)
  }
  if (!response.ok) {
    throw new SpeechApiError(
      speechErrors[body.code] || body.message || fallback,
      body.code,
      body.traceId,
    )
  }
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
  const controller = new AbortController()
  let timedOut = false
  const cancel = () => controller.abort()
  signal?.addEventListener('abort', cancel, { once: true })
  if (signal?.aborted) cancel()
  const timer = window.setTimeout(() => { timedOut = true; controller.abort() }, 100_000)
  try {
    const response = await fetch(`${apiBaseUrl}/api/v1/speech/transcriptions`, { method: 'POST', body: form, signal: controller.signal })
    return await parseJson<SpeechTranscription>(response, '语音识别失败')
  } catch (error) {
    if (timedOut) throw new Error('语音识别超时，请重新录音')
    throw error
  } finally { window.clearTimeout(timer); signal?.removeEventListener('abort', cancel) }
}

export async function synthesizeSpeech(text: string, signal?: AbortSignal): Promise<Blob> {
  const requestId = crypto.randomUUID()
  const cancel = () => { void fetch(`${apiBaseUrl}/api/v1/speech/syntheses/${requestId}`, { method: 'DELETE', keepalive: true }).catch(() => undefined) }
  if (signal?.aborted) throw new DOMException('Cancelled', 'AbortError')
  signal?.addEventListener('abort', cancel, { once: true })
  try {
  const response = await fetch(`${apiBaseUrl}/api/v1/speech/syntheses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'audio/mpeg', 'X-Speech-Request-Id': requestId },
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
  return await response.blob()
  } finally { signal?.removeEventListener('abort', cancel) }
}

function recordingFileName(contentType: string) {
  const normalized = contentType.toLowerCase()
  if (normalized.includes('mp4')) return 'recording.m4a'
  if (normalized.includes('ogg')) return 'recording.ogg'
  if (normalized.includes('wav')) return 'recording.wav'
  return 'recording.webm'
}
