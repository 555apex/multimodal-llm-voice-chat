import { defineStore } from 'pinia'
import {
  fetchNextEmergency,
  generateEmergencyDispatch,
  markEmergencyNoDispatch,
} from '../api/emergencyApi'
import {
  decideCommandWorkflow,
  decideLevel1Workflow,
  fetchWorkflowDetail,
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

export const useEmergencyStore = defineStore('emergency', {
  state: () => ({
    selectedStage: 'LEVEL_1' as WorkflowStage,
    viewMode: 'inbox' as EmergencyViewMode,
    item: null as EmergencyWorkflowItem | null,
    counts: emptyCounts(),
    history: null as WorkflowHistoryPage | null,
    polling: false,
    requestSequence: 0,
    actionBusy: false,
    generationPending: false,
    operationStartedAt: 0,
    elapsedSeconds: 0,
    queryStatus: 'idle' as EmergencyQueryStatus,
    errorMessage: '',
    timerId: 0,
  }),

  getters: {
    totalPending(state): number {
      return state.counts.level1 + state.counts.level2 + state.counts.level3
    },
    serverGenerating(state): boolean {
      return !!state.item && ['GENERATING', 'REVISING'].includes(state.item.workflowStatus)
    },
  },

  actions: {
    invalidateQuery() {
      this.requestSequence++
      this.polling = false
    },
    startPolling() {
      if (this.timerId) return
      void this.refresh()
      let ticks = 0
      this.timerId = window.setInterval(() => {
        if (this.operationStartedAt) this.elapsedSeconds = Math.floor((Date.now() - this.operationStartedAt) / 1000)
        ticks++
        if (document.visibilityState === 'visible' && (this.actionBusy || this.serverGenerating || ticks % 5 === 0)) void this.refresh()
      }, 1000)
      document.addEventListener('visibilitychange', this.handleVisibility)
    },

    stopPolling() {
      this.invalidateQuery()
      if (this.timerId) window.clearInterval(this.timerId)
      this.timerId = 0
      document.removeEventListener('visibilitychange', this.handleVisibility)
    },

    handleVisibility() {
      if (document.visibilityState === 'visible') void this.refresh()
    },

    async selectStage(stage: WorkflowStage) {
      this.invalidateQuery()
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
      this.invalidateQuery()
      this.viewMode = 'inbox'
      await this.refresh()
    },

    async refresh() {
      if (this.polling || (this.actionBusy && !this.generationPending)) return
      if (this.viewMode === 'history') {
        await this.loadHistory(this.history?.page ?? 0)
        return
      }
      this.polling = true
      const sequence = ++this.requestSequence
      const stage = this.selectedStage
      if (!this.item) this.queryStatus = 'loading'
      try {
        if ((this.generationPending || this.serverGenerating) && this.item?.workflowId) {
          const id = this.item.workflowId
          const item = await fetchWorkflowDetail(id)
          if (sequence === this.requestSequence && this.item?.workflowId === id) {
            this.item = item
            this.queryStatus = 'ready'
            if (!this.actionBusy && !this.serverGenerating) this.operationStartedAt = 0
          }
          return
        }
        const inbox = await fetchWorkflowInbox(stage)
        if (sequence !== this.requestSequence || this.selectedStage !== stage || this.viewMode !== 'inbox') return
        this.item = inbox.item
        this.counts = inbox.counts
        this.queryStatus = inbox.item ? 'ready' : 'empty'
        this.errorMessage = ''
      } catch (error) {
        if (sequence !== this.requestSequence) return
        // 单次网络波动时保留当前待办，避免用户正在填写的表单消失。
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '应急工作流查询失败'
      } finally {
        if (sequence === this.requestSequence) this.polling = false
      }
    },

    async loadHistory(page = 0) {
      if (this.actionBusy) return
      this.viewMode = 'history'
      const sequence = ++this.requestSequence
      this.polling = true
      this.queryStatus = 'loading'
      try {
        const history = await fetchWorkflowHistory(page, 20)
        if (sequence !== this.requestSequence || this.viewMode !== 'history') return
        this.history = history
        this.queryStatus = this.history.items.length ? 'ready' : 'empty'
        this.errorMessage = ''
      } catch (error) {
        if (sequence !== this.requestSequence) return
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '流程记录查询失败'
      } finally {
        if (sequence === this.requestSequence) this.polling = false
      }
    },

    async generate() {
      if (!this.item || this.actionBusy || ['GENERATING', 'REVISING'].includes(this.item.workflowStatus)) return
      let failure = ''
      this.invalidateQuery()
      this.generationPending = true
      this.operationStartedAt = Date.now()
      this.elapsedSeconds = 0
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await generateEmergencyDispatch(this.item.event.eventId)
      } catch (error) {
        this.invalidateQuery()
        await this.refresh()
        if (this.item?.currentPlan?.status !== 'WAITING_APPROVAL' && this.item?.currentPlan?.status !== 'GENERATING') {
          failure = error instanceof Error ? error.message : '调度工单生成失败'
        }
      } finally {
        this.actionBusy = false
        this.generationPending = false
        if (!this.serverGenerating) this.operationStartedAt = 0
      }
      await this.refresh()
      if (failure) this.errorMessage = failure
    },

    async markNoDispatch(reason: string) {
      if (!this.item || this.actionBusy) return
      this.invalidateQuery()
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
      this.invalidateQuery()
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
      this.invalidateQuery()
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
      const before = this.item
      let failure = ''
      this.generationPending = true
      this.operationStartedAt = Date.now()
      this.elapsedSeconds = 0
      this.invalidateQuery()
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await operation()
        this.item = null
      } catch (error) {
        this.invalidateQuery()
        await this.refresh()
        if (!before || !this.item || this.item.workflowVersion <= before.workflowVersion) {
          failure = error instanceof Error ? error.message : '应急工作流操作失败'
        }
      } finally {
        this.actionBusy = false
        this.generationPending = false
        if (!this.serverGenerating) this.operationStartedAt = 0
      }
      await this.refresh()
      if (failure) this.errorMessage = failure
    },

    /** 旧接口只留给兼容测试或迁移诊断，不参与新版三级页面。 */
    async refreshLegacyPending() {
      return fetchNextEmergency()
    },
  },
})
