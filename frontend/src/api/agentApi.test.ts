import { describe, expect, it } from 'vitest'
import { consumeFrames } from './agentApi'
import type { AgentEvent } from '../types/agent'

describe('consumeFrames', () => {
  it('parses fragmented SSE frames in order', () => {
    const events: AgentEvent[] = []
    let buffer = consumeFrames('event:answer.delta\ndata:{"content":"五', (event) => events.push(event))
    buffer = consumeFrames(`${buffer}四路"}\n\nevent:run.completed\ndata:{"runId":"1"}\n\n`,
      (event) => events.push(event))

    expect(buffer).toBe('')
    expect(events.map((event) => event.name)).toEqual(['answer.delta', 'run.completed'])
    expect(events[0].data).toEqual({ content: '五四路' })
  })
})
