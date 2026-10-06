export interface MaintenanceProject {
  projectCode: string
  routeCode: string
  routeName: string
  routeSection: string
  facilityType: string
  maintenanceType: string
  repairScale: string
  diseaseDescription: string
  recommendedAction: string
  urgency: string
  priority: number
  plannedStartDate: string
  durationDays: number
  contractor: string
  budgetWan: number
}

export interface MaintenanceProjectResult {
  title: string
  summary: string
  projects: MaintenanceProject[]
  sourceBatchId: string
  generatedAt: string
}

export interface MaintenanceSection {
  heading: string
  paragraphs: string[]
  rows: Record<string, unknown>[]
}

export interface MaintenanceChart {
  title: string
  unit: string
  labels: string[]
  values: number[]
}

export interface MaintenanceDocument {
  documentId: string
  documentType: string
  reportYear: number
  projectCode?: string
  title: string
  summary: string
  conclusion: string
  sections: MaintenanceSection[]
  charts: MaintenanceChart[]
  sourceBatchId: string
  version: number
  generatedAt: string
  downloadUrl: string
}
