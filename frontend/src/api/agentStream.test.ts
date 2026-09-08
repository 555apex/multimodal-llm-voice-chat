import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamAgentMessage } from './agentApi'
import type { AgentEvent } from '../types/agent'

function stream(chunks: Uint8Array[]) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(chunk))
      controller.close()
    },
  }))))
}
afterEach(() => vi.unstubAllGlobals())
describe('SSE transport boundaries', () => {
  it('decodes UTF-8 and CRLF even when every byte is a separate chunk', async () => {
    const text = 'event: answer.delta\r\ndata: {"content":"福建"}\r\n\r\nevent: run.completed\r\ndata: {}\r\n\r\n'
    stream([...new TextEncoder().encode(text)].map((b) => new Uint8Array([b])))
    const events: AgentEvent[] = []
    await streamAgentMessage('test', 'test', (event) => events.push(event))
    expect(events.map((e) => e.name)).toEqual(['answer.delta', 'run.completed'])
    expect(events[0].data).toEqual({ content: '福建' })
  })
  it('rejects EOF before a terminal business event', async () => {
    stream([new TextEncoder().encode('event: answer.delta\ndata: {"content":"partial"}\n\n')])
    await expect(streamAgentMessage('test', 'test', () => {}))
      .rejects.toMatchObject({ code: 'AGENT_STREAM_INTERRUPTED' })
  })
  it('accepts run.failed as terminal without replaying the request', async () => {
    stream([new TextEncoder().encode('event: run.failed\ndata: {"message":"failure"}\n\n')])
    const onEvent = vi.fn()
    await streamAgentMessage('test', 'test', onEvent)
    expect(onEvent).toHaveBeenCalledOnce()
    expect(fetch).toHaveBeenCalledOnce()
  })
})
