export type FacilityAlertStatus = 'PENDING' | 'CONFIRMED' | 'CLOSED'
export type AlarmLevel = 'WARNING' | 'SEVERE' | 'EMERGENCY'
export type FacilityAlertResolution = 'RESOLVED' | 'IGNORED'
export type FacilityHealthState = 'NO_ACTIVE_ALERT' | 'ATTENTION' | 'ABNORMAL' | 'DANGER'

export interface FacilityAlert {
  alertId: string
  facilityName: string
  metricName: string
  actualValue?: number
  actualStringValue?: string
  thresholdMin?: number
  thresholdMax?: number
  alarmLevel: AlarmLevel
  alarmLevelName: string
  collectTime: string
  triggerTime: string
  status: FacilityAlertStatus
  statusName: string
  remark?: string
  thresholdAssessment: string
  sourceConsistent: boolean
}

export interface FacilityAlertCounts {
  pending: number
  confirmed: number
  closed: number
  activeWarning: number
  activeSevere: number
  activeEmergency: number
  dataAsOf?: string
}

export interface FacilityAlertPage {
  items: FacilityAlert[]
  page: number
  size: number
  total: number
  counts: FacilityAlertCounts
}

export interface FacilityFocusItem {
  facilityName: string
  healthState: FacilityHealthState
  healthStateName: string
  highestAlarmLevel: AlarmLevel
  highestAlarmLevelName: string
  activeAlertCount: number
  pendingCount: number
  confirmedCount: number
  metricNames: string[]
  oldestTriggerTime: string
  latestCollectTime: string
  focusReason: string
}

export interface FacilityHealthReport {
  generatedAt: string
  dataAsOf?: string
  activeAlertCount: number
  affectedFacilityCount: number
  warningCount: number
  severeCount: number
  emergencyCount: number
  pendingCount: number
  confirmedCount: number
  overallHealth: FacilityHealthState
  overallHealthName: string
  summary: string
  facilities: FacilityFocusItem[]
}

export interface FacilityAlertTransition {
  expectedStatus: FacilityAlertStatus
  targetStatus: FacilityAlertStatus
  resolutionType?: FacilityAlertResolution
  remark: string
}
