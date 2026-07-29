import type { ApiResponse } from '../types/traffic'
import type { DispatchPlan, EmergencyAlert } from '../types/dispatch'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function parseResponse<T>(response: Response, fallback: string): Promise<T> {
  const body = (await response.json()) as ApiResponse<T>
  if (!response.ok) {
    throw new Error(body.message || fallback)
  }
  return body.data
}

export async function fetchNextEmergency(): Promise<EmergencyAlert | null> {
  const response = await fetch(`${apiBaseUrl}/api/v1/emergency-events/pending/next`)
  return parseResponse<EmergencyAlert | null>(response, '紧急事件查询失败')
}

export async function generateEmergencyDispatch(eventId: string): Promise<DispatchPlan> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-events/${encodeURIComponent(eventId)}/dispatches`,
    { method: 'POST' },
  )
  return parseResponse<DispatchPlan>(response, '调度工单生成失败')
}

export async function markEmergencyNoDispatch(eventId: string, reason: string): Promise<void> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-events/${encodeURIComponent(eventId)}/no-dispatch`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason, confirmed: true }),
    },
  )
  await parseResponse<null>(response, '事件处置失败')
}
