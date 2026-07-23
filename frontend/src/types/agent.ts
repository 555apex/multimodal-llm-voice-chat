import type { DispatchPlan } from './dispatch'
import type { TrafficQueryResult } from './traffic'

export type AgentRole = 'user' | 'assistant'
export type AgentMessageStatus = 'pending' | 'completed' | 'failed'

export interface AgentMessage {
  id: string
  role: AgentRole
  content: string
  status: AgentMessageStatus
  traffic?: TrafficQueryResult
  dispatch?: DispatchPlan
  errorMessage?: string
}

export interface AgentStage {
  stage: string
  label: string
}

export interface AgentToolProgress {
  tool: string
  totalTiles: number
  completedTiles: number
  failedTiles: number
}

export interface AgentEvent<T = unknown> {
  name: string
  data: T
}

export interface RunFailedData {
  code: string
  message: string
  traceId: string
}
