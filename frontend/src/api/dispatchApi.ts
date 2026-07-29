import type { ApiResponse } from '../types/traffic'
import type { DispatchPlan } from '../types/dispatch'

export async function decideDispatch(
  planId: string,
  decision: 'APPROVE' | 'REJECT',
  expectedVersion: number,
  idempotencyKey: string,
  comment = '',
): Promise<DispatchPlan> {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${apiBaseUrl}/api/v1/dispatches/${encodeURIComponent(planId)}/approvals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ decision, comment, expectedVersion, idempotencyKey }),
  })
  const body = (await response.json()) as ApiResponse<DispatchPlan>
  if (!response.ok) {
    throw new Error(body.message || '调度审批失败')
  }
  return body.data
}
