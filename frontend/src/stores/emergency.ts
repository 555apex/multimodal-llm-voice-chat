import { defineStore } from 'pinia'
import {
  fetchNextEmergency,
  generateEmergencyDispatch,
  markEmergencyNoDispatch,
} from '../api/emergencyApi'
import {
  decideCommandWorkflow,
  decideLevel1Workflow,
  fetchWorkflowHistory,
  fetchWorkflowInbox,
  reviewEmergencyWorkflow,
  releaseWorkflowResources,
  correctWorkflowEventType,
  retryNextEventClassification,
} from '../api/workflowApi'
import type {
  EmergencyWorkflowItem,
  ProfessionalReviewInput,
  WorkflowCounts,
  WorkflowHistoryPage,
  WorkflowStage,
} from '../types/dispatch'

export type EmergencyQueryStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'error'
export type EmergencyViewMode = 'inbox' | 'history'

const emptyCounts = (): WorkflowCounts => ({
  level1: 0, level2: 0, level3: 0, pendingClassification: 0, classificationFailed: 0,
})

const STALE_GENERATION_MILLIS = 120_000

function staleGeneration(item: EmergencyWorkflowItem | null) {
  if (!item?.currentPlan?.updatedAt) return false
  const generating = ['GENERATING', 'REVISING'].includes(item.workflowStatus)
    || item.currentPlan.status === 'GENERATING'
  const updatedAt = Date.parse(item.currentPlan.updatedAt)
  return generating && Number.isFinite(updatedAt)
    && Date.now() - updatedAt >= STALE_GENERATION_MILLIS
}

function generationKey(item: EmergencyWorkflowItem) {
  return `${item.currentPlan?.planId ?? item.event.eventId}:${item.currentPlan?.version ?? 0}`
}

export const useEmergencyStore = defineStore('emergency', {
  state: () => ({
    selectedStage: 'LEVEL_1' as WorkflowStage,
    viewMode: 'inbox' as EmergencyViewMode,
    item: null as EmergencyWorkflowItem | null,
    counts: emptyCounts(),
    history: null as WorkflowHistoryPage | null,
    polling: false,
    actionBusy: false,
    queryStatus: 'idle' as EmergencyQueryStatus,
    errorMessage: '',
    timerId: 0,
    automaticRecoveryAttempts: {} as Record<string, boolean>,
  }),

  getters: {
    totalPending(state): number {
      return state.counts.level1 + state.counts.level2 + state.counts.level3
    },
  },

  actions: {
    startPolling() {
      if (this.timerId) return
      void this.refresh()
      this.timerId = window.setInterval(() => {
        if (document.visibilityState === 'visible') void this.refresh()
      }, 5000)
      document.addEventListener('visibilitychange', this.handleVisibility)
    },

    stopPolling() {
      if (this.timerId) window.clearInterval(this.timerId)
      this.timerId = 0
      document.removeEventListener('visibilitychange', this.handleVisibility)
    },

    handleVisibility() {
      if (document.visibilityState === 'visible') void this.refresh()
    },

    async selectStage(stage: WorkflowStage) {
      this.selectedStage = stage
      this.viewMode = 'inbox'
      this.item = null
      await this.refresh()
    },

    async showHistory() {
      this.viewMode = 'history'
      await this.loadHistory()
    },

    async showInbox() {
      this.viewMode = 'inbox'
      await this.refresh()
    },

    async refresh() {
      if (this.polling || this.actionBusy) return
      if (this.viewMode === 'history') {
        await this.loadHistory()
        return
      }
      this.polling = true
      if (!this.item) this.queryStatus = 'loading'
      let shouldRecover = false
      try {
        const inbox = await fetchWorkflowInbox(this.selectedStage)
        this.item = inbox.item
        this.counts = inbox.counts
        this.queryStatus = inbox.item ? 'ready' : 'empty'
        this.errorMessage = ''
        if (staleGeneration(inbox.item)) {
          const key = generationKey(inbox.item!)
          if (!this.automaticRecoveryAttempts[key]) {
            this.automaticRecoveryAttempts[key] = true
            shouldRecover = true
          }
        }
      } catch (error) {
        // 单次网络波动时保留当前待办，避免用户正在填写的表单消失。
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '应急工作流查询失败'
      } finally {
        this.polling = false
      }
      if (shouldRecover) await this.generate()
    },

    async loadHistory(page = 0) {
      if (this.polling || this.actionBusy) return
      this.polling = true
      this.queryStatus = 'loading'
      try {
        this.history = await fetchWorkflowHistory(page, 20)
        this.queryStatus = this.history.items.length ? 'ready' : 'empty'
        this.errorMessage = ''
      } catch (error) {
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '流程记录查询失败'
      } finally {
        this.polling = false
      }
    },

    async generate() {
      if (!this.item || this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await generateEmergencyDispatch(this.item.event.eventId)
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '调度工单生成失败'
      } finally {
        this.actionBusy = false
      }
      await this.refresh()
    },

    async markNoDispatch(reason: string) {
      if (!this.item || this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await markEmergencyNoDispatch(this.item.event.eventId, reason)
        this.item = null
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '事件处置失败'
      } finally {
        this.actionBusy = false
      }
      await this.refresh()
    },

    async decideLevel1(decision: 'SUBMIT' | 'REJECT', comment = '') {
      if (!this.item?.workflowId || this.actionBusy) return
      await this.runWorkflowAction(async () => {
        await decideLevel1Workflow(
          this.item!.workflowId!, decision, comment, this.item!.workflowVersion,
        )
      })
    },

    async correctEventType(eventType: string, reason: string) {
      if (!this.item?.workflowId || this.actionBusy) return
      await this.runWorkflowAction(async () => {
        await correctWorkflowEventType(
          this.item!.workflowId!, eventType, reason, this.item!.workflowVersion,
        )
      })
    },

    async retryClassification() {
      if (this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await retryNextEventClassification()
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '事件类型识别重试失败'
      } finally {
        this.actionBusy = false
      }
      await this.refresh()
    },

    async review(input: ProfessionalReviewInput) {
      if (!this.item?.workflowId || this.actionBusy) return
      await this.runWorkflowAction(async () => {
        await reviewEmergencyWorkflow(
          this.item!.workflowId!, input, this.item!.workflowVersion,
        )
      })
    },

    async decideCommand(decision: 'APPROVE' | 'REJECT', comment = '') {
      if (!this.item?.workflowId || this.actionBusy) return
      await this.runWorkflowAction(async () => {
        await decideCommandWorkflow(
          this.item!.workflowId!, decision, comment, this.item!.workflowVersion,
        )
      })
    },

    async releaseResources(workflowId: string, expectedVersion: number, reason: string) {
      if (this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await releaseWorkflowResources(workflowId, reason, expectedVersion)
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '已调度资源归还失败'
      } finally {
        this.actionBusy = false
      }
      await this.loadHistory(this.history?.page ?? 0)
    },

    async runWorkflowAction(operation: () => Promise<void>) {
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await operation()
        this.item = null
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '应急工作流操作失败'
      } finally {
        this.actionBusy = false
      }
      await this.refresh()
    },

    /** 旧接口只留给兼容测试或迁移诊断，不参与新版三级页面。 */
    async refreshLegacyPending() {
      return fetchNextEmergency()
    },
  },
})
