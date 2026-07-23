export type CongestionLevel = 'UNKNOWN' | 'SMOOTH' | 'SLOW' | 'CONGESTED'
export type SummarySource = 'MODEL'
export type Freshness = 'FRESH' | 'STALE' | 'UNKNOWN'
export type TrafficQueryScope = 'ROAD' | 'AREA_ALL' | 'AREA_MAJOR'

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

export interface TrafficEvaluation {
  totalSegments: number
  smoothSegments: number
  slowSegments: number
  congestedSegments: number
  unknownSegments: number
  smoothRatio: number
  slowRatio: number
  congestedRatio: number
  unknownRatio: number
  averageSpeedKmh?: number
}

export interface TrafficCoverage {
  totalTiles: number
  succeededTiles: number
  failedTiles: number
  coverageRatio: number
  complete: boolean
}

export interface TrafficQueryResult {
  /** 旧会话数据可能没有该字段，界面会按ROAD兼容。 */
  queryScope?: TrafficQueryScope
  areaCode: string
  areaName?: string
  roadName?: string
  direction?: string
  summary: string
  summarySource: SummarySource
  segments: TrafficSegment[]
  evaluation?: TrafficEvaluation
  coverage?: TrafficCoverage
  source: string
  acquiredAt: string
  freshness: Freshness
  mock: boolean
  warnings: string[]
  traceId: string
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
