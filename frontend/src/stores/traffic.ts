import { defineStore } from 'pinia'
import { queryRealtimeTraffic, TrafficApiError } from '../api/trafficApi'
import type { TrafficQueryPayload, TrafficQueryResult } from '../types/traffic'

interface TrafficState {
  loading: boolean
  result: TrafficQueryResult | null
  errorMessage: string
  errorTraceId: string
  lastTraceId: string
}

export const useTrafficStore = defineStore('traffic', {
  state: (): TrafficState => ({
    loading: false,
    result: null,
    errorMessage: '',
    errorTraceId: '',
    lastTraceId: '',
  }),

  actions: {
    async query(payload: TrafficQueryPayload) {
      this.loading = true
      this.errorMessage = ''
      this.errorTraceId = ''

      try {
        const response = await queryRealtimeTraffic(payload)
        this.result = response.data
        this.lastTraceId = response.traceId
      } catch (error) {
        this.result = null
        if (error instanceof TrafficApiError) {
          this.errorMessage = error.message
          this.errorTraceId = error.traceId ?? ''
        } else {
          this.errorMessage = '发生未预期的前端错误。'
        }
      } finally {
        this.loading = false
      }
    },
  },
})
