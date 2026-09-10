import type { ApiResponse } from '../types/traffic'
import type {
  EmergencyWorkflowItem,
  ProfessionalReviewInput,
  WorkflowHistoryPage,
  WorkflowInbox,
  WorkflowStage,
  NoticePage,
} from '../types/dispatch'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function parseResponse<T>(response: Response, fallback: string): Promise<T> {
  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(fallback)
  }
  if (!response.ok) throw Object.assign(new Error(body.message || fallback), { status: response.status })
  return body.data
}

function actionBody(
  expectedWorkflowVersion: number,
  fields: Record<string, unknown>,
) {
  return JSON.stringify({
    ...fields,
    expectedWorkflowVersion,
    idempotencyKey: fields.idempotencyKey ?? crypto.randomUUID(),
  })
}

export async function fetchWorkflowInbox(stage: WorkflowStage): Promise<WorkflowInbox> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/inbox?stage=${encodeURIComponent(stage)}`,
  )
  return parseResponse<WorkflowInbox>(response, '应急工作流待办查询失败')
}

export async function decideLevel1Workflow(
  workflowId: string,
  decision: 'SUBMIT' | 'REJECT',
  comment: string,
  expectedWorkflowVersion: number,
): Promise<EmergencyWorkflowItem> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/${encodeURIComponent(workflowId)}/level-1-decisions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: actionBody(expectedWorkflowVersion, { decision, comment }),
    },
  )
  return parseResponse<EmergencyWorkflowItem>(response, '一级应急处置提交失败')
}

export async function reviewEmergencyWorkflow(
  workflowId: string,
  input: ProfessionalReviewInput,
  expectedWorkflowVersion: number,
): Promise<EmergencyWorkflowItem> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/${encodeURIComponent(workflowId)}/professional-reviews`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: actionBody(expectedWorkflowVersion, { ...input }),
    },
  )
  return parseResponse<EmergencyWorkflowItem>(response, '二级专业复核提交失败')
}

export async function decideCommandWorkflow(
  workflowId: string,
  decision: 'APPROVE' | 'REJECT',
  comment: string,
  expectedWorkflowVersion: number,
): Promise<EmergencyWorkflowItem> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/${encodeURIComponent(workflowId)}/command-decisions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: actionBody(expectedWorkflowVersion, { decision, comment }),
    },
  )
  return parseResponse<EmergencyWorkflowItem>(response, '三级省级决策提交失败')
}

export async function fetchWorkflowHistory(
  page = 0,
  size = 20,
): Promise<WorkflowHistoryPage> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/history?page=${page}&size=${size}`,
  )
  return parseResponse<WorkflowHistoryPage>(response, '应急流程记录查询失败')
}

export async function fetchWorkflowDetail(workflowId: string): Promise<EmergencyWorkflowItem> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/${encodeURIComponent(workflowId)}`,
  )
  return parseResponse<EmergencyWorkflowItem>(response, '应急工作流详情查询失败')
}

export async function releaseWorkflowResources(
  workflowId: string,
  reason: string,
  expectedWorkflowVersion: number,
  idempotencyKey?: string,
): Promise<EmergencyWorkflowItem> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/${encodeURIComponent(workflowId)}/resource-releases`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: actionBody(expectedWorkflowVersion, { reason, idempotencyKey }),
    },
  )
  return parseResponse<EmergencyWorkflowItem>(response, '已调度资源归还失败')
}

export async function fetchNotices(completionStatus: 'PENDING' | 'COMPLETED', page = 0): Promise<NoticePage> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/notices?completionStatus=${completionStatus}&page=${page}&size=20`,
  )
  return parseResponse<NoticePage>(response, '下发通告查询失败')
}

export async function correctWorkflowEventType(
  workflowId: string,
  eventType: string,
  reason: string,
  expectedWorkflowVersion: number,
): Promise<EmergencyWorkflowItem> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-workflows/${encodeURIComponent(workflowId)}/event-type-corrections`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: actionBody(expectedWorkflowVersion, { eventType, reason }),
    },
  )
  return parseResponse<EmergencyWorkflowItem>(response, '事件类型更正失败')
}

export async function retryNextEventClassification(): Promise<boolean> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/emergency-events/classification-retries/next`,
    { method: 'POST' },
  )
  return parseResponse<boolean>(response, '事件类型识别重试失败')
}
