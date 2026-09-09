import type { ApiResponse } from '../types/traffic'
import type {
  AlarmLevel,
  FacilityAlert,
  FacilityAlertPage,
  FacilityAlertStatus,
  FacilityAlertTransition,
  FacilityFocusItem,
  FacilityHealthReport,
} from '../types/facility'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function parseResponse<T>(response: Response, fallback: string): Promise<T> {
  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(fallback)
  }
  if (!response.ok) throw Object.assign(new Error(body.message || fallback), { status: response.status, code: body.code })
  return body.data
}

export async function fetchFacilityAlerts(
  status: FacilityAlertStatus,
  alarmLevel: AlarmLevel | '',
  page = 0,
  size = 20,
): Promise<FacilityAlertPage> {
  const params = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (alarmLevel) params.set('alarmLevel', alarmLevel)
  const response = await fetch(`${apiBaseUrl}/api/v1/facility-alerts?${params}`)
  return parseResponse<FacilityAlertPage>(response, '设施告警查询失败')
}

export async function fetchFacilityHealthReport(): Promise<FacilityHealthReport> {
  const response = await fetch(`${apiBaseUrl}/api/v1/facility-alerts/health-report`)
  return parseResponse<FacilityHealthReport>(response, '设施健康状态报告生成失败')
}

export async function fetchFacilityFocus(limit = 10): Promise<FacilityFocusItem[]> {
  const response = await fetch(`${apiBaseUrl}/api/v1/facility-alerts/focus?limit=${limit}`)
  return parseResponse<FacilityFocusItem[]>(response, '重点关注对象查询失败')
}

export async function transitionFacilityAlert(
  alertId: string,
  transition: FacilityAlertTransition,
): Promise<FacilityAlert> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/facility-alerts/${encodeURIComponent(String(alertId))}/status-transitions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(transition),
    },
  )
  return parseResponse<FacilityAlert>(response, '设施告警状态更新失败')
}
