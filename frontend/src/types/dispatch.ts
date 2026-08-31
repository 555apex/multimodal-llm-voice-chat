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
  cityCode?: string
  cityName?: string
}

export interface SuggestedResource {
  resourceType: string
  resourceName: string
  quantity: number
  unit: string
  purpose: string
}

export interface ResourceRequirement {
  resourceTypeCode: string
  resourceTypeName: string
  quantity: number
  unit: string
  purpose: string
}

export interface AllocatedResource {
  resourceId: string
  resourceTypeCode: string
  resourceTypeName: string
  resourceName: string
  sourceCityCode: string
  sourceCityName: string
  quantity: number
  unit: string
  purpose: string
  estimatedDistanceKm: number
  dispatchScope: 'LOCAL' | 'CROSS_CITY'
}

export interface ResourceShortage {
  resourceTypeCode: string
  resourceTypeName: string
  requiredQuantity: number
  allocatedQuantity: number
  shortageQuantity: number
  unit: string
  reason: string
}

export interface DispatchPlan {
  planId: string
  event: EmergencyEvent
  suggestedResources: SuggestedResource[]
  resourceRequirements?: ResourceRequirement[]
  allocatedResources?: AllocatedResource[]
  resourceShortages?: ResourceShortage[]
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

export type WorkflowStage = 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3'

export type WorkflowStatus =
  | 'WAITING_GENERATION'
  | 'GENERATING'
  | 'WAITING_LEVEL_1_SUBMISSION'
  | 'WAITING_LEVEL_2_REVIEW'
  | 'WAITING_LEVEL_3_DECISION'
  | 'REVISING'
  | 'GENERATION_FAILED'
  | 'PUBLISHED'
  | 'NO_DISPATCH'

export type EventSeverity = 'GENERAL' | 'LARGER' | 'MAJOR' | 'ESPECIALLY_MAJOR'
export type ResourceFeasibility = 'FEASIBLE' | 'FEASIBLE_WITH_GAP' | 'NEEDS_ADJUSTMENT'

export interface ProfessionalReview {
  reviewId: string
  workflowId: string
  planId: string
  planVersion: number
  status: 'PENDING' | 'PASSED' | 'RETURNED'
  eventSeverity?: EventSeverity
  resourceFeasibility?: ResourceFeasibility
  impactAssessment?: string
  coordinationRequirements?: string
  reviewOpinion?: string
  createdAt: string
  updatedAt: string
}

export interface NoticeSnapshot {
  noticeNumber: string
  title: string
  event: EmergencyEvent
  planId: string
  planVersion: number
  suggestedResources: SuggestedResource[]
  resourceRequirements?: ResourceRequirement[]
  allocatedResources?: AllocatedResource[]
  resourceShortages?: ResourceShortage[]
  rescuePlan: string
  eventSeverity: EventSeverity
  impactAssessment: string
  coordinationRequirements?: string
  professionalOpinion: string
  commandOpinion?: string
  publishedAt: string
}

export interface CommandDecisionRecord {
  decisionId: string
  workflowId: string
  reviewId: string
  planId: string
  planVersion: number
  status: 'PENDING' | 'RETURNED' | 'PUBLISHED'
  decisionOpinion?: string
  noticeSnapshot?: NoticeSnapshot
  createdAt: string
  updatedAt: string
}

export interface WorkflowAction {
  actionId: string
  workflowId: string
  actionType: string
  fromStage?: WorkflowStage | null
  toStage?: WorkflowStage | null
  fromStatus?: WorkflowStatus
  toStatus: WorkflowStatus
  planId?: string
  planVersion: number
  comment?: string
  idempotencyKey: string
  createdAt: string
}

export interface EmergencyWorkflowItem {
  workflowId?: string
  currentStage?: WorkflowStage | null
  workflowStatus: WorkflowStatus
  workflowVersion: number
  event: EmergencyEvent
  currentPlan?: DispatchPlan
  professionalReview?: ProfessionalReview
  commandDecision?: CommandDecisionRecord
  timeline: WorkflowAction[]
  resourcesReleased?: boolean
}

export interface WorkflowCounts {
  level1: number
  level2: number
  level3: number
}

export interface WorkflowInbox {
  item: EmergencyWorkflowItem | null
  counts: WorkflowCounts
}

export interface WorkflowHistoryPage {
  items: EmergencyWorkflowItem[]
  page: number
  size: number
  total: number
}

export interface ProfessionalReviewInput {
  decision: 'APPROVE' | 'REJECT'
  eventSeverity?: EventSeverity
  resourceFeasibility?: ResourceFeasibility
  impactAssessment?: string
  coordinationRequirements?: string
  comment: string
}
