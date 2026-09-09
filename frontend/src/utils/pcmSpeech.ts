import { consumeFrames } from '../api/agentApi'

export class PcmSpeechPlayer {
  private context = new AudioContext({ sampleRate: 24000 })
  private node?: AudioWorkletNode
  private closed = false
  private queuedSamples = 0
  private capacityWaiters: Array<() => void> = []
  private resolveDone!: () => void
  readonly done = new Promise<void>((resolve) => { this.resolveDone = resolve })
  constructor(private update: (event: { type: string; level?: number; starvedMs?: number }) => void) {}
  async open() {
    await this.context.audioWorklet.addModule('/audio/pcm-player.js')
    if (this.closed) return
    this.node = new AudioWorkletNode(this.context, 'road-pcm-player', { outputChannelCount: [1] })
    this.node.port.onmessage = ({ data }) => {
      if (this.closed) return
      if (data.type === 'consumed') {
        this.queuedSamples = Math.max(0, this.queuedSamples - data.samples)
        if (this.queuedSamples < 24000 * 12) this.capacityWaiters.splice(0).forEach(resolve => resolve())
        return
      }
      this.update(data)
      if (data.type === 'ended') this.resolveDone()
    }
    this.node.connect(this.context.destination)
    await this.context.resume()
    if (this.context.state !== 'running') throw new Error('请点击朗读以允许浏览器播放音频')
  }
  async push(base64: string) {
    // Bound paused playback memory and propagate backpressure to the HTTP reader.
    while (!this.closed && this.queuedSamples >= 24000 * 12) await new Promise<void>(resolve => this.capacityWaiters.push(resolve))
    if (this.closed) return
    if (base64.length > 640000) throw new Error('语音分块过大')
    const raw = atob(base64)
    if (raw.length % 2) throw new Error('语音采样数据不完整')
    const samples = new Float32Array(raw.length / 2)
    for (let i = 0; i < samples.length; i++) {
      const n = raw.charCodeAt(i * 2) | (raw.charCodeAt(i * 2 + 1) << 8)
      samples[i] = (n >= 32768 ? n - 65536 : n) / 32768
    }
    this.node?.port.postMessage({ type: 'chunk', samples }, [samples.buffer])
    this.queuedSamples += raw.length / 2
  }
  end() { this.node?.port.postMessage({ type: 'end' }) }
  pause() { this.node?.port.postMessage({ type: 'pause' }) }
  resume() { this.node?.port.postMessage({ type: 'resume' }) }
  close() {
    this.closed = true
    this.capacityWaiters.splice(0).forEach(resolve => resolve())
    this.node?.disconnect()
    void this.context.close().catch(() => undefined)
    this.resolveDone()
  }
}

export async function receiveSpeech(text: string, player: Pick<PcmSpeechPlayer, 'push'>, signal: AbortSignal) {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1/speech/syntheses/stream`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ text }), signal,
  })
  if (!response.ok || !response.body) throw new Error(`语音请求失败（${response.status}）`)
  const reader = response.body.getReader(), decoder = new TextDecoder()
  let buffer = '', completed = false, started = false, sequence = 0
  try {
    while (!completed) {
      const { value, done } = await reader.read()
      const chunks: string[] = []
      buffer += decoder.decode(value, { stream: !done })
      buffer = consumeFrames(buffer, event => {
        if (completed) throw new Error('语音结束后仍收到音频事件')
        const data = event.data as { sampleRate?: number; channels?: number; format?: string; sequence?: number; chunks?: number; pcm?: string; message?: string }
        if (event.name === 'audio.start') {
          if (started || data.sampleRate !== 24000 || data.channels !== 1 || data.format !== 's16le') throw new Error('语音格式不支持')
          started = true
        } else if (event.name === 'audio.chunk') {
          if (!started || data.sequence !== sequence++ || !data.pcm) throw new Error('语音分块顺序异常')
          chunks.push(data.pcm)
        } else if (event.name === 'audio.completed') {
          if (!started || sequence < 1 || data.chunks !== sequence) throw new Error('语音数据不完整')
          completed = true
        } else if (event.name === 'audio.failed') throw new Error(data.message || '语音合成中断')
      }, done)
      for (const chunk of chunks) await player.push(chunk)
      if (done) break
    }
    if (!completed) throw new Error('语音连接中断，请重新朗读')
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}
