<script setup lang="ts">
import type { AgentMessage } from '../types/agent'
import DispatchPlanCard from './DispatchPlanCard.vue'
import MessageSpeechButton from './MessageSpeechButton.vue'
import TrafficResultPanel from './TrafficResultPanel.vue'

defineProps<{ message: AgentMessage; approvalBusyPlanId: string }>()
const emit = defineEmits<{
  decide: [messageId: string, decision: 'APPROVE' | 'REJECT', comment: string]
}>()
</script>

<template>
  <article class="chat-message" :class="message.role">
    <div class="message-avatar" aria-hidden="true">{{ message.role === 'user' ? '我' : '路' }}</div>
    <div class="message-body">
      <div class="message-bubble" :class="{ failed: message.status === 'failed' }">
        <p v-if="message.content">{{ message.content }}</p>
        <p v-else-if="message.status === 'pending'" class="thinking-dots"><i></i><i></i><i></i></p>
        <p v-if="message.errorMessage" class="message-error">{{ message.errorMessage }}</p>
      </div>
      <MessageSpeechButton
        v-if="message.role === 'assistant' && message.status === 'completed' && message.speechText"
        :message-id="message.id"
        :speech-text="message.speechText"
      />

      <template v-if="message.trafficResults?.length">
        <TrafficResultPanel
          v-for="result in message.trafficResults"
          :key="`${result.traceId}-${result.analysisCity ?? result.title}`"
          :result="result"
          compact
        />
      </template>
      <TrafficResultPanel v-else-if="message.traffic" :result="message.traffic" compact />
      <DispatchPlanCard
        v-if="message.dispatch"
        :plan="message.dispatch"
        :busy="approvalBusyPlanId === message.dispatch.planId"
        @decide="(decision, comment) => emit('decide', message.id, decision, comment)"
      />
    </div>
  </article>
</template>
