import { defineStore } from 'pinia'
import { decideDispatch } from '../api/dispatchApi'
import {
  fetchNextEmergency,
  generateEmergencyDispatch,
  markEmergencyNoDispatch,
} from '../api/emergencyApi'
import type { EmergencyAlert } from '../types/dispatch'

export type EmergencyQueryStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'error'

export const useEmergencyStore = defineStore('emergency', {
  state: () => ({
    alert: null as EmergencyAlert | null,
    polling: false,
    actionBusy: false,
    queryStatus: 'idle' as EmergencyQueryStatus,
    errorMessage: '',
    timerId: 0,
  }),

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

    async refresh() {
      if (this.polling || this.actionBusy) return
      this.polling = true
      if (!this.alert) this.queryStatus = 'loading'
      try {
        const nextAlert = await fetchNextEmergency()
        this.alert = nextAlert
        this.queryStatus = nextAlert ? 'ready' : 'empty'
        this.errorMessage = ''
      } catch (error) {
        // 轮询失败时保留当前告警，避免一次网络抖动让待处理工单从页面消失。
        this.queryStatus = 'error'
        this.errorMessage = error instanceof Error ? error.message : '紧急事件查询失败'
      } finally {
        this.polling = false
      }
    },

    async generate() {
      if (!this.alert || this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      try {
        this.alert.dispatch = await generateEmergencyDispatch(this.alert.event.eventId)
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '调度工单生成失败'
        await this.refreshAfterAction()
      } finally {
        this.actionBusy = false
      }
    },

    async markNoDispatch(reason: string) {
      if (!this.alert || this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      try {
        await markEmergencyNoDispatch(this.alert.event.eventId, reason)
        this.alert = null
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '事件处置失败'
      } finally {
        this.actionBusy = false
      }
      await this.refresh()
    },

    async decide(decision: 'APPROVE' | 'REJECT', comment = '') {
      if (!this.alert?.dispatch || this.actionBusy) return
      this.actionBusy = true
      this.errorMessage = ''
      const plan = this.alert.dispatch
      try {
        const updated = await decideDispatch(
          plan.planId,
          decision,
          plan.version,
          `${plan.planId}-${plan.version}-${crypto.randomUUID()}`,
          comment,
        )
        if (decision === 'APPROVE') {
          this.alert = null
        } else {
          this.alert.dispatch = updated
        }
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '工单审批失败'
        await this.refreshAfterAction()
      } finally {
        this.actionBusy = false
      }
      if (decision === 'APPROVE') await this.refresh()
    },

    async refreshAfterAction() {
      this.actionBusy = false
      await this.refresh()
      this.actionBusy = true
    },
  },
})
