import type { AgentEvent } from '../types/agent'

export class AgentStreamError extends Error {
  constructor(message: string, public readonly code = 'AGENT_STREAM_ERROR') {
    super(message)
    this.name = 'AgentStreamError'
  }
}

/**
 * POST请求也可以接收SSE。这里手动解析event/data行，便于发送JSON请求体。
 */
export async function streamAgentMessage(
  conversationId: string,
  message: string,
  onEvent: (event: AgentEvent) => void,
  signal?: AbortSignal,
) {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(
    `${apiBaseUrl}/api/v1/conversations/${encodeURIComponent(conversationId)}/messages/stream`,
    {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ message }),
      signal,
    },
  )

  if (!response.ok || !response.body) {
    throw new AgentStreamError(`无法建立Agent流式连接（HTTP ${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal = false
  try {
    while (!terminal) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      buffer = consumeFrames(buffer, (event) => {
        if (terminal) return
        terminal = event.name === 'run.completed' || event.name === 'run.failed'
        onEvent(event)
      }, done)
      if (done) break
    }
    if (!terminal) throw new AgentStreamError('回答连接已中断，请重新提交问题', 'AGENT_STREAM_INTERRUPTED')
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}

/** 返回尚未组成完整事件的尾部文本。 */
export function consumeFrames(
  input: string,
  onEvent: (event: AgentEvent) => void,
  flush = false,
) {
  // Normalize after appending: CR and LF may arrive in different network chunks.
  let buffer = input.replaceAll('\r\n', '\n')
  let boundary = buffer.indexOf('\n\n')
  while (boundary >= 0) {
    parseFrame(buffer.slice(0, boundary), onEvent)
    buffer = buffer.slice(boundary + 2)
    boundary = buffer.indexOf('\n\n')
  }
  if (flush && buffer.trim()) {
    parseFrame(buffer, onEvent)
    return ''
  }
  return buffer
}

function parseFrame(frame: string, onEvent: (event: AgentEvent) => void) {
  let name = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return
  const rawData = dataLines.join('\n')
  try {
    onEvent({ name, data: JSON.parse(rawData) as unknown })
  } catch {
    throw new AgentStreamError(`无法解析SSE事件：${name}`, 'INVALID_SSE_DATA')
  }
}
