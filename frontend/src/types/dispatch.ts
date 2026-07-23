export type DispatchStatus =
  | 'DRAFT'
  | 'WAITING_APPROVAL'
  | 'APPROVED'
  | 'SUBMITTED'
  | 'REJECTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface EmergencyEvent {
  eventType: string
  city: string
  locationDescription: string
  severity: string
  description: string
}

export interface EmergencyResource {
  resourceId: string
  type: string
  name: string
  city: string
  capability: string
  available: boolean
}

export interface DispatchTask {
  sequence: number
  action: string
  responsibleUnit: string
  resourceId?: string
}

export interface WorkOrderReference {
  workOrderId: string
  status: string
  submittedAt: string
  mock: boolean
}

export interface DispatchPlan {
  planId: string
  event: EmergencyEvent
  summary: string
  tasks: DispatchTask[]
  resources: EmergencyResource[]
  warnings: string[]
  status: DispatchStatus
  version: number
  createdAt: string
  workOrder?: WorkOrderReference
  approvalRequired?: boolean
}
