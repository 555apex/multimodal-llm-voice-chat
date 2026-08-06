<script setup lang="ts">
import type { AgentMessage } from '../types/agent'
import DispatchPlanCard from './DispatchPlanCard.vue'
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

      <TrafficResultPanel
        v-if="message.traffic"
        :result="message.traffic"
        compact
      />
      <DispatchPlanCard
        v-if="message.dispatch"
        :plan="message.dispatch"
        :busy="approvalBusyPlanId === message.dispatch.planId"
        @decide="(decision, comment) => emit('decide', message.id, decision, comment)"
      />
    </div>
  </article>
</template>
