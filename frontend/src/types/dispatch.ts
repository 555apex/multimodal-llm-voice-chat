export type DispatchStatus =
  | 'GENERATING'
  | 'WAITING_APPROVAL'
  | 'REJECTED'
  | 'APPROVED'
  | 'FAILED'

export interface EmergencyEvent {
  eventId: string
  customId: string
  occurrenceTime?: string
  eventType: string
  eventTypeName?: string
  description: string
}

export interface SuggestedResource {
  resourceType: string
  resourceName: string
  quantity: number
  unit: string
  purpose: string
}

export interface DispatchPlan {
  planId: string
  event: EmergencyEvent
  suggestedResources: SuggestedResource[]
  rescuePlan: string
  status: DispatchStatus
  version: number
  createdAt: string
  updatedAt: string
  rejectionReason?: string
  errorMessage?: string
  approvalRequired?: boolean
}

export interface EmergencyAlert {
  event: EmergencyEvent
  dispatch?: DispatchPlan
  pendingCount: number
}
