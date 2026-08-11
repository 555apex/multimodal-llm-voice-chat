import { defineStore } from 'pinia'
import { streamAgentMessage } from '../api/agentApi'
import { decideDispatch } from '../api/dispatchApi'
import { useSpeechStore } from './speech'
import type { AgentEvent, AgentMessage, AgentStage, AgentToolProgress, RunFailedData } from '../types/agent'
import type { DispatchPlan } from '../types/dispatch'
import type { TrafficQueryResult } from '../types/traffic'

const SESSION_KEY = 'roadagent-chat-session-v2'

interface StoredSession {
  conversationId: string
  messages: AgentMessage[]
}

function initialSession(): StoredSession {
  const saved = sessionStorage.getItem(SESSION_KEY)
  if (saved) {
    try {
      return JSON.parse(saved) as StoredSession
    } catch {
      sessionStorage.removeItem(SESSION_KEY)
    }
  }
  return {
    conversationId: crypto.randomUUID(),
    messages: [
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '你好，我可以查询福州、厦门、泉州的实时道路交通。正式应急调度请通过顶部红色告警卡处理。',
        status: 'completed',
      },
    ],
  }
}

const restored = initialSession()

export const useAgentStore = defineStore('agent', {
  state: () => ({
    conversationId: restored.conversationId,
    messages: restored.messages as AgentMessage[],
    running: false,
    stage: null as AgentStage | null,
    toolProgress: null as AgentToolProgress | null,
    approvalBusyPlanId: '',
  }),

  actions: {
    async send(message: string) {
      if (this.running || !message.trim()) return
      useSpeechStore().stop()
      this.running = true
      this.stage = { stage: 'CONNECTING', label: '正在连接Agent' }
      this.toolProgress = null
      const userMessage: AgentMessage = {
        id: crypto.randomUUID(), role: 'user', content: message.trim(), status: 'completed',
      }
      const assistantMessage: AgentMessage = {
        id: crypto.randomUUID(), role: 'assistant', content: '', status: 'pending',
      }
      this.messages.push(userMessage, assistantMessage)
      this.persist()

      try {
        await streamAgentMessage(this.conversationId, userMessage.content, (event) => {
          this.applyEvent(assistantMessage.id, event)
        })
      } catch (error) {
        const target = this.findMessage(assistantMessage.id)
        target.content = ''
        target.status = 'failed'
        target.errorMessage = error instanceof Error ? error.message : '流式请求失败'
      } finally {
        this.running = false
        this.stage = null
        this.toolProgress = null
        this.persist()
      }
    },

    applyEvent(messageId: string, event: AgentEvent) {
      const message = this.findMessage(messageId)
      switch (event.name) {
        case 'stage.changed':
          this.stage = event.data as AgentStage
          break
        case 'tool.progress':
          this.toolProgress = event.data as AgentToolProgress
          break
        case 'answer.delta':
          message.content += (event.data as { content: string }).content
          break
        case 'answer.speech':
          message.speechText = (event.data as { content: string }).content
          break
        case 'result.traffic':
          message.traffic = event.data as TrafficQueryResult
          break
        case 'result.dispatch':
          message.dispatch = event.data as DispatchPlan
          break
        case 'run.completed':
          message.status = 'completed'
          if (message.speechText && useSpeechStore().autoReadEnabled) {
            void useSpeechStore().speak(message.id, message.speechText)
          }
          break
        case 'run.failed': {
          const failed = event.data as RunFailedData
          // 按约定丢弃模型已生成的半截内容，整次请求显示失败。
          message.content = ''
          message.traffic = undefined
          message.dispatch = undefined
          message.speechText = undefined
          message.status = 'failed'
          message.errorMessage = `${failed.message}（${failed.code}）`
          break
        }
      }
      this.persist()
    },

    async decide(messageId: string, decision: 'APPROVE' | 'REJECT', comment = '') {
      const message = this.findMessage(messageId)
      if (!message.dispatch || this.approvalBusyPlanId) return
      const plan = message.dispatch
      this.approvalBusyPlanId = plan.planId
      try {
        message.dispatch = await decideDispatch(
          plan.planId,
          decision,
          plan.version,
          `${plan.planId}-${crypto.randomUUID()}`,
          comment,
        )
      } catch (error) {
        message.errorMessage = error instanceof Error ? error.message : '审批失败'
      } finally {
        this.approvalBusyPlanId = ''
        this.persist()
      }
    },

    reset() {
      useSpeechStore().stop()
      this.conversationId = crypto.randomUUID()
      this.messages = [
        {
          id: crypto.randomUUID(), role: 'assistant',
          content: '已开始新会话。请告诉我需要查询的城市、道路或行政区。',
          status: 'completed',
        },
      ]
      this.stage = null
      this.toolProgress = null
      this.persist()
    },

    findMessage(id: string) {
      const message = this.messages.find((item) => item.id === id)
      if (!message) throw new Error('消息不存在')
      return message
    },

    persist() {
      // 区域路况可能包含数百条坐标折线，不应长期写入sessionStorage。
      // 当前页面内的Pinia状态仍保留完整路段，只在持久化副本中剔除坐标。
      const messages = this.messages.map((message) => ({
        ...message,
        traffic: message.traffic ? {
          ...message.traffic,
          segments: message.traffic.segments.map((segment) => ({ ...segment, polyline: undefined })),
        } : undefined,
      }))
      sessionStorage.setItem(SESSION_KEY, JSON.stringify({
        conversationId: this.conversationId,
        messages,
      }))
    },
  },
})
