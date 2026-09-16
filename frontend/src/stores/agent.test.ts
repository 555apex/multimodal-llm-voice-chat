import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAgentStore } from './agent'
import { useSpeechStore } from './speech'
import type { TrafficQueryResult } from '../types/traffic'

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

  it('waits for completion and automatically reads the full multi-paragraph answer', () => {
    const store = useAgentStore()
    const speech = useSpeechStore()
    speech.capabilities = {
      asrAvailable: true,
      ttsAvailable: true,
      maxRecordingSeconds: 60,
      maxAudioBytes: 10 * 1024 * 1024,
    }
    const speak = vi.spyOn(speech, 'speak').mockResolvedValue()
    const messageId = crypto.randomUUID()
    store.messages.push({ id: messageId, role: 'assistant', content: '第一段。', status: 'pending' })

    store.applyEvent(messageId, {
      name: 'answer.speech',
      data: { content: '第一段摘要。' },
    })
    expect(speak).not.toHaveBeenCalled()

    store.applyEvent(messageId, { name: 'answer.delta', data: { content: '\n第二段完整内容。' } })
    store.applyEvent(messageId, { name: 'run.completed', data: { runId: 'run-full-speech' } })

    expect(speak).toHaveBeenCalledOnce()
    expect(speak).toHaveBeenCalledWith(messageId, '第一段。\n第二段完整内容。')
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

  it('keeps both city results from repeated traffic events in one answer', () => {
    const store = useAgentStore()
    const messageId = crypto.randomUUID()
    store.messages.push({ id: messageId, role: 'assistant', content: '', status: 'pending' })
    const result = (city: string): TrafficQueryResult => ({
      queryType: 'VEHICLE_STRUCTURE', title: `${city}车型结构`, analysisCity: city,
      summary: `${city}结果`, routeSummaries: [], segments: [], capacityRows: [],
      totalSegmentCount: 0, displayedSegmentCount: 0, truncated: false,
      source: 'MYSQL', acquiredAt: '2026-09-03T08:00:00Z', warnings: [], traceId: 'batch-1',
    })

    store.applyEvent(messageId, { name: 'result.traffic', data: result('福州市') })
    store.applyEvent(messageId, { name: 'result.traffic', data: result('厦门市') })
    store.applyEvent(messageId, { name: 'run.completed', data: { runId: 'run-1' } })

    expect(store.findMessage(messageId).trafficResults?.map(item => item.analysisCity))
      .toEqual(['福州市', '厦门市'])
    expect(sessionStorage.getItem('roadagent-chat-session-v5')).toContain('trafficResults')
  })
})
