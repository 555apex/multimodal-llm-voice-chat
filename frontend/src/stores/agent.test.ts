import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAgentStore } from './agent'

describe('agent store', () => {
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

  it('persists the MySQL highway traffic result', () => {
    const store = useAgentStore()
    store.messages.push({
      id: crypto.randomUUID(), role: 'assistant', content: '区域路况', status: 'completed',
      traffic: {
        queryType: 'ROUTE_DETAIL', title: 'G104 交通情况', summary: '当前状态已汇总。请留意通行时间。',
        routeSummaries: [], capacityRows: [], source: 'MYSQL', acquiredAt: '2026-08-13T08:00:00Z',
        totalSegmentCount: 1, displayedSegmentCount: 1, truncated: false,
        warnings: [], traceId: 'trace-1',
        segments: [{
          routeCode: 'G104', routeName: '北京-平潭', routeSection: 'FJ001→FJ002',
          distanceKm: 10, averageSpeedKmh: 20, status: 20, statusName: '轻度拥堵', severity: 0.3,
        }],
      },
    })

    store.persist()

    expect(sessionStorage.getItem('roadagent-chat-session-v5')).toContain('G104')
    expect(store.messages.at(-1)?.traffic?.segments[0].routeSection).toBe('FJ001→FJ002')
  })
})
