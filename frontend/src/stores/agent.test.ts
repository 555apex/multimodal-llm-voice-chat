import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAgentStore } from './agent'

describe('agent store area progress', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('records area tile progress from SSE', () => {
    const store = useAgentStore()
    const messageId = crypto.randomUUID()
    store.messages.push({ id: messageId, role: 'assistant', content: '', status: 'pending' })

    store.applyEvent(messageId, {
      name: 'tool.progress',
      data: { tool: 'query_area_traffic', totalTiles: 10, completedTiles: 4, failedTiles: 1 },
    })

    expect(store.toolProgress).toEqual({
      tool: 'query_area_traffic', totalTiles: 10, completedTiles: 4, failedTiles: 1,
    })
  })

  it('stores the dedicated speakable answer from SSE', () => {
    const store = useAgentStore()
    const messageId = crypto.randomUUID()
    store.messages.push({ id: messageId, role: 'assistant', content: '完整回答', status: 'pending' })

    store.applyEvent(messageId, {
      name: 'answer.speech',
      data: { content: '精简朗读摘要。详细数据请查看页面。' },
    })

    expect(store.messages.at(-1)?.speechText).toBe('精简朗读摘要。详细数据请查看页面。')
  })

  it('does not persist traffic polylines to session storage', () => {
    const store = useAgentStore()
    store.messages.push({
      id: crypto.randomUUID(), role: 'assistant', content: '区域路况', status: 'completed',
      traffic: {
        queryScope: 'AREA_ALL', areaCode: '350203', areaName: '思明区', summary: '正常',
        summarySource: 'MODEL', source: 'AMAP', acquiredAt: '2026-07-21T08:00:00Z',
        freshness: 'FRESH', mock: false, warnings: [], traceId: 'trace-1',
        segments: [{
          roadName: '成功大道', direction: '北向南', congestionLevel: 'SLOW',
          averageSpeedKmh: 20, polyline: '118.1,24.4;118.2,24.5',
        }],
      },
    })

    store.persist()

    expect(sessionStorage.getItem('roadagent-chat-session-v2')).not.toContain('118.1,24.4')
    expect(store.messages.at(-1)?.traffic?.segments[0].polyline).toContain('118.1')
  })
})
