import { computed, onUnmounted, ref, watch } from 'vue'
import { transcribeSpeech } from '../api/speechApi'
import { useSpeechStore } from '../stores/speech'

interface Options { active: boolean; disabled: boolean; available: boolean; maxRecordingSeconds: number; maxAudioBytes: number }
interface Session {
  media?: MediaStream; recorder?: MediaRecorder; chunks: Blob[]; bytes: number
  startedAt: number; stoppedAt?: number; submitted: boolean; cancelled: boolean
  timer?: number; limitTimer?: number; abort?: AbortController
}

export function useVoiceRecorder(props: Options, transcribed: (text: string) => void, recordingChanged: (recording: boolean) => void) {
  const speech = useSpeechStore()
  const status = ref<'idle' | 'requesting' | 'recording' | 'transcribing' | 'error'>('idle')
  const elapsedSeconds = ref(0)
  const errorMessage = ref('')
  let current: Session | undefined
  let disposed = false
  const valid = (s: Session) => current === s && !s.cancelled && !disposed && props.active
  const label = computed(() => status.value === 'requesting' ? '取消麦克风申请'
    : status.value === 'recording' ? `停止录音 ${elapsedSeconds.value}秒`
      : status.value === 'transcribing' ? '取消识别' : status.value === 'error' ? '重新录音' : '语音输入')

  function releaseMedia(s: Session) {
    window.clearInterval(s.timer); window.clearTimeout(s.limitTimer)
    s.timer = undefined; s.limitTimer = undefined
    if (s.recorder) {
      s.recorder.onstop = null; s.recorder.ondataavailable = null; s.recorder.onerror = null
      if (s.recorder.state === 'recording') s.recorder.stop()
    }
    s.media?.getTracks().forEach(track => track.stop())
    s.media = undefined; s.recorder = undefined; s.chunks = []; s.bytes = 0
  }
  function fail(s: Session, message: string) {
    if (!valid(s)) return
    s.cancelled = true; s.abort?.abort(); releaseMedia(s); current = undefined
    recordingChanged(false); errorMessage.value = message; status.value = 'error'
  }
  function cancelRecording() {
    const s = current; current = undefined
    if (s) { s.cancelled = true; s.abort?.abort(); releaseMedia(s) }
    status.value = 'idle'; errorMessage.value = ''; recordingChanged(false)
  }
  function stopRecording() {
    const s = current
    if (!s || !valid(s) || s.stoppedAt !== undefined || s.recorder?.state !== 'recording') return
    // Measure at the stop request, not the delayed MediaRecorder onstop callback.
    s.stoppedAt = Math.min(Date.now(), s.startedAt + props.maxRecordingSeconds * 1000)
    window.clearInterval(s.timer); window.clearTimeout(s.limitTimer)
    s.recorder.stop()
  }
  async function complete(s: Session) {
    if (!valid(s) || s.submitted) return
    s.submitted = true
    const duration = Math.max(1, Math.min(props.maxRecordingSeconds * 1000, (s.stoppedAt ?? Date.now()) - s.startedAt))
    const blob = new Blob(s.chunks, { type: s.recorder?.mimeType || s.chunks[0]?.type || 'audio/webm' })
    releaseMedia(s); recordingChanged(false)
    if (!blob.size) { fail(s, '没有录到声音，请重试'); return }
    if (blob.size > props.maxAudioBytes) { fail(s, '录音文件过大，请缩短录音时间'); return }
    status.value = 'transcribing'; s.abort = new AbortController()
    try {
      const result = await transcribeSpeech(blob, duration, s.abort.signal)
      if (!valid(s)) return
      current = undefined; status.value = 'idle'; transcribed(result.text)
    } catch (error) {
      if (!valid(s)) return
      fail(s, error instanceof Error ? error.message : '语音识别失败，请重试')
    } finally { s.abort = undefined }
  }
  async function start() {
    if (current || disposed || !props.active || props.disabled) return
    const s: Session = { chunks: [], bytes: 0, startedAt: 0, submitted: false, cancelled: false }
    current = s; errorMessage.value = ''; status.value = 'requesting'
    if (!props.available) { fail(s, '语音识别服务尚未就绪'); return }
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      fail(s, '当前浏览器不支持录音，请使用最新版Chrome、Edge或Safari'); return
    }
    speech.stop()
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
      if (!valid(s)) { media.getTracks().forEach(track => track.stop()); return }
      s.media = media
      const mimeType = ['audio/webm;codecs=opus', 'audio/mp4;codecs=mp4a.40.2', 'audio/mp4', 'audio/webm', 'audio/ogg;codecs=opus']
        .find(type => MediaRecorder.isTypeSupported(type))
      s.recorder = mimeType ? new MediaRecorder(media, { mimeType }) : new MediaRecorder(media)
      s.recorder.ondataavailable = event => {
        if (!valid(s) || s.submitted || !event.data.size) return
        s.chunks.push(event.data); s.bytes += event.data.size
        if (s.bytes > props.maxAudioBytes) stopRecording()
      }
      s.recorder.onerror = () => fail(s, '录音过程中发生错误，请重试')
      s.recorder.onstop = () => void complete(s)
      s.startedAt = Date.now(); elapsedSeconds.value = 0
      s.recorder.start(250); status.value = 'recording'; recordingChanged(true)
      s.timer = window.setInterval(() => {
        if (valid(s)) elapsedSeconds.value = Math.min(props.maxRecordingSeconds, Math.floor((Date.now() - s.startedAt) / 1000))
      }, 250)
      s.limitTimer = window.setTimeout(() => { if (valid(s)) stopRecording() }, props.maxRecordingSeconds * 1000)
    } catch (error) {
      fail(s, error instanceof DOMException && error.name === 'NotAllowedError'
        ? '麦克风权限被拒绝，请在浏览器设置中允许访问' : '无法启动麦克风，请检查设备是否被占用')
    }
  }
  async function toggle() {
    if (status.value === 'requesting' || status.value === 'transcribing') { cancelRecording(); return }
    if (status.value === 'recording') { stopRecording(); return }
    await start()
  }
  watch(() => props.active, active => { if (!active) cancelRecording() })
  onUnmounted(() => { cancelRecording(); disposed = true })
  return { status, label, elapsedSeconds, errorMessage, toggle, stopRecording, cancelRecording }
}
