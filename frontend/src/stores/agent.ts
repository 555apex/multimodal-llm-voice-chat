import { defineStore } from 'pinia'
import { streamAgentMessage } from '../api/agentApi'
import { decideDispatch } from '../api/dispatchApi'
import { useSpeechStore } from './speech'
import type { AgentEvent, AgentMessage, AgentStage, AgentToolProgress, RunFailedData } from '../types/agent'
import type { DispatchPlan } from '../types/dispatch'
import type { TrafficQueryResult } from '../types/traffic'

const SESSION_KEY = 'roadagent-chat-session-v5'
const WELCOME_MESSAGE = '您好，我是路智通，专注于福建省路网运行监测与应急处置。\n\n我可以研判福建普通国省干线交通态势和短时趋势，查询拥堵路段、指定路线状态、通行能力与瓶颈路线；也可以分析高流量卡口、城市和路线交通压力，以及福州、厦门的车型结构、24小时出行规律和工作日周末特征，还支持多城市OD七日统计、区域流量差异和关键通道分车型流量分析，同时提供应急调度辅助与语音交互。'

interface StoredSession {
  conversationId: string
  messages: AgentMessage[]
}

function initialSession(): StoredSession {
  const saved = sessionStorage.getItem(SESSION_KEY)
  if (saved) {
    try {
      const session = JSON.parse(saved) as StoredSession
      const firstMessage = session.messages?.[0]
      if (firstMessage?.role === 'assistant'
        && (firstMessage.content.includes('您好，我是路智通')
          || firstMessage.content.includes('新会话已开始'))) {
        firstMessage.content = WELCOME_MESSAGE
      }
      return session
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
        content: WELCOME_MESSAGE,
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
          if (message.speechText
            && useSpeechStore().autoReadEnabled
            && useSpeechStore().surfaceActive) {
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
          content: WELCOME_MESSAGE,
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
      sessionStorage.setItem(SESSION_KEY, JSON.stringify({
        conversationId: this.conversationId,
        messages: this.messages,
      }))
    },
  },
})
