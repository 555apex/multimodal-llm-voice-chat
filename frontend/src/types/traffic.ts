export type CongestionLevel = 'UNKNOWN' | 'SMOOTH' | 'SLOW' | 'CONGESTED'
export type SummarySource = 'MODEL' | 'RULE_FALLBACK'
export type Freshness = 'FRESH' | 'STALE' | 'UNKNOWN'

export interface TrafficQueryPayload {
  areaCode: string
  roadName: string
  direction?: string
}

export interface TrafficSegment {
  roadName: string
  direction: string
  congestionLevel: CongestionLevel
  averageSpeedKmh?: number
  polyline?: string
}

export interface TrafficQueryResult {
  areaCode: string
  roadName: string
  direction?: string
  summary: string
  summarySource: SummarySource
  segments: TrafficSegment[]
  source: string
  acquiredAt: string
  freshness: Freshness
  mock: boolean
  warnings: string[]
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
