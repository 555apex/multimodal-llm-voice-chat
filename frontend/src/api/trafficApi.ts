import type { ApiResponse, TrafficQueryPayload, TrafficQueryResult } from '../types/traffic'

export class TrafficApiError extends Error {
  constructor(
    message: string,
    public readonly code = 'NETWORK_ERROR',
    public readonly traceId?: string,
  ) {
    super(message)
    this.name = 'TrafficApiError'
  }
}

export async function queryRealtimeTraffic(
  payload: TrafficQueryPayload,
): Promise<ApiResponse<TrafficQueryResult>> {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  let response: Response

  try {
    response = await fetch(`${apiBaseUrl}/api/v1/traffic/queries`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        areaCode: payload.areaCode.trim(),
        roadName: payload.roadName.trim(),
        direction: payload.direction?.trim() || null,
      }),
    })
  } catch {
    throw new TrafficApiError('无法连接后端服务，请确认Spring Boot已启动。')
  }

  const body = (await response.json()) as ApiResponse<TrafficQueryResult>
  if (!response.ok) {
    throw new TrafficApiError(body.message || '交通查询失败', body.code, body.traceId)
  }
  return body
}
