import { defineStore } from 'pinia'
import {
  fetchFacilityAlerts,
  fetchFacilityFocus,
  fetchFacilityHealthReport,
  transitionFacilityAlert,
} from '../api/facilityApi'
import type {
  AlarmLevel,
  FacilityAlertCounts,
  FacilityAlertPage,
  FacilityAlertResolution,
  FacilityAlertStatus,
  FacilityFocusItem,
  FacilityHealthReport,
} from '../types/facility'

export type FacilityViewMode = 'alerts' | 'report' | 'focus'
export type FacilityQueryStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'error'

const emptyCounts = (): FacilityAlertCounts => ({
  pending: 0,
  confirmed: 0,
  closed: 0,
  activeWarning: 0,
  activeSevere: 0,
  activeEmergency: 0,
})

export const useFacilityStore = defineStore('facility', {
  state: () => ({
    viewMode: 'alerts' as FacilityViewMode,
    selectedStatus: 'PENDING' as FacilityAlertStatus,
    selectedAlarmLevel: '' as AlarmLevel | '',
    pageData: null as FacilityAlertPage | null,
    report: null as FacilityHealthReport | null,
    focusItems: [] as FacilityFocusItem[],
    counts: emptyCounts(),
    polling: false,
    requestSequence: 0,
    actionBusy: false,
    queryStatus: 'idle' as FacilityQueryStatus,
    errorMessage: '',
    timerId: 0,
    formOpen: false,
  }),

  actions: {
    invalidateQuery() {
      this.requestSequence++
      this.polling = false
    },
    startPolling() {
      if (this.timerId) return
      void this.refresh()
      this.timerId = window.setInterval(() => {
        if (document.visibilityState === 'visible' && !this.formOpen) void this.refresh()
      }, 5000)
      document.addEventListener('visibilitychange', this.handleVisibility)
    },

    stopPolling() {
      this.invalidateQuery()
      if (this.timerId) window.clearInterval(this.timerId)
      this.timerId = 0
      document.removeEventListener('visibilitychange', this.handleVisibility)
    },

    handleVisibility() {
      if (document.visibilityState === 'visible' && !this.formOpen) void this.refresh()
    },

    async selectStatus(status: FacilityAlertStatus) {
      this.selectedStatus = status
      this.pageData = null
      this.viewMode = 'alerts'
      await this.loadAlerts(0)
    },

    async selectAlarmLevel(level: AlarmLevel | '') {
      this.selectedAlarmLevel = level
      this.pageData = null
      this.viewMode = 'alerts'
      await this.loadAlerts(0)
    },

    async showAlerts() {
      this.viewMode = 'alerts'
      await this.loadAlerts(this.pageData?.page ?? 0)
    },

    async showReport() {
      this.invalidateQuery()
      this.viewMode = 'report'
      await this.refresh()
    },

    async showFocus() {
      this.invalidateQuery()
      this.viewMode = 'focus'
      await this.refresh()
    },

    async refresh() {
      if (this.polling || this.actionBusy || this.formOpen) return
      if (this.viewMode === 'alerts') {
        await this.loadAlerts(this.pageData?.page ?? 0)
        return
      }
      this.polling = true
      const sequence = ++this.requestSequence
      const view = this.viewMode
      this.queryStatus = 'loading'
      try {
        const [countPage, content] = await Promise.all([
          fetchFacilityAlerts('PENDING', '', 0, 1),
          view === 'report' ? fetchFacilityHealthReport() : fetchFacilityFocus(10),
        ])
        if (sequence !== this.requestSequence || view !== this.viewMode) return
        this.counts = countPage.counts
        if (this.viewMode === 'report') this.report = content as FacilityHealthReport
        else this.focusItems = content as FacilityFocusItem[]
        const hasContent = this.viewMode === 'report'
          ? Boolean(this.report)
          : this.focusItems.length > 0
        this.queryStatus = hasContent ? 'ready' : 'empty'
        this.errorMessage = ''
      } catch (error) {
        if (sequence !== this.requestSequence) return
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '设施预警查询失败'
      } finally {
        if (sequence === this.requestSequence) this.polling = false
      }
    },

    async loadAlerts(page = 0) {
      if (this.actionBusy || this.formOpen) return
      const sequence = ++this.requestSequence
      const status = this.selectedStatus
      const level = this.selectedAlarmLevel
      this.polling = true
      if (!this.pageData) this.queryStatus = 'loading'
      try {
        const result = await fetchFacilityAlerts(
          status, level, page, 20,
        )
        if (sequence !== this.requestSequence || this.viewMode !== 'alerts'
          || status !== this.selectedStatus || level !== this.selectedAlarmLevel) return
        this.pageData = result
        this.counts = this.pageData.counts
        this.queryStatus = this.pageData.items.length ? 'ready' : 'empty'
        this.errorMessage = ''
      } catch (error) {
        if (sequence !== this.requestSequence) return
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '设施预警查询失败'
      } finally {
        if (sequence === this.requestSequence) this.polling = false
      }
    },

    async transition(
      alertId: number,
      expectedStatus: FacilityAlertStatus,
      targetStatus: FacilityAlertStatus,
      remark: string,
      resolutionType?: FacilityAlertResolution,
    ) {
      if (this.actionBusy) return
      this.invalidateQuery()
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await transitionFacilityAlert(alertId, {
          expectedStatus, targetStatus, resolutionType, remark,
        })
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '设施告警状态更新失败'
        throw error
      } finally {
        this.actionBusy = false
      }
      await this.loadAlerts(0)
    },
  },
})
