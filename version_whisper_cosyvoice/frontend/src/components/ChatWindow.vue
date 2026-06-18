<template>
  <div class="chat-window" ref="chatContainer">
    <div class="messages-list">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message-item"
        :class="msg.role"
      >
        <div class="message-avatar">
          {{ msg.role === 'user' ? '🧑' : '🤖' }}
        </div>
        <div class="message-content">
          <div class="message-role">
            {{ msg.role === 'user' ? '用户' : '助手' }}
            <span v-if="msg.type === 'voice'" class="voice-badge">🎤 语音</span>
          </div>
          <div class="message-text">
            <span v-if="msg.role === 'assistant' && !msg.content" class="typing-cursor">|</span>
            {{ msg.content }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

const chatContainer = ref(null)

// 自动滚动到底部
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  }
)

// 监听内容变化也要滚动
watch(
  () => props.messages.map(m => m.content).join(''),
  async () => {
    await nextTick()
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  }
)
</script>

<style scoped>
.chat-window {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.message-item.user {
  background-color: #f0f7ff;
}

.message-item.assistant {
  background-color: #f9f9f9;
}

.message-avatar {
  font-size: 24px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-role {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.voice-badge {
  font-size: 11px;
  background: #e8f4fd;
  color: #4a90d9;
  padding: 1px 6px;
  border-radius: 4px;
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.typing-cursor {
  display: inline-block;
  animation: blink 0.8s infinite;
  color: #4a90d9;
  font-weight: bold;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
