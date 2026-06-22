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
          <div v-if="msg.role === 'assistant' && !msg.content" class="message-text">
            <span class="typing-cursor">|</span>
          </div>
          <div v-else class="message-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { marked } from 'marked'
import markedKatex from 'marked-katex-extension'
import 'katex/dist/katex.min.css'

// 配置 marked + KaTeX
marked.use(markedKatex({
  throwOnError: false,
  output: 'html',
}))

marked.setOptions({
  breaks: true,
  gfm: true,
})

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

const chatContainer = ref(null)

// 渲染 markdown（含数学公式）
function renderMarkdown(content) {
  if (!content) return ''
  try {
    return marked.parse(content)
  } catch (e) {
    // 解析失败时返回原文
    return content
  }
}

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

/* Markdown 渲染样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 12px 0 6px 0;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-body :deep(h1) { font-size: 1.4em; }
.markdown-body :deep(h2) { font-size: 1.2em; }
.markdown-body :deep(h3) { font-size: 1.1em; }

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
}

.markdown-body :deep(code) {
  background: #f0f0f0;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: 'Consolas', 'Monaco', monospace;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  border-radius: 0;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #4a90d9;
  margin: 8px 0;
  padding: 4px 12px;
  color: #666;
  background: #f8f9fa;
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 6px 0;
  padding-left: 24px;
}

.markdown-body :deep(li) {
  margin: 3px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #ddd;
  padding: 6px 10px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f5f5f5;
  font-weight: 600;
}

.markdown-body :deep(a) {
  color: #4a90d9;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 12px 0;
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

/* KaTeX 数学公式样式 */
.markdown-body :deep(.katex) {
  font-size: 1.1em;
}

.markdown-body :deep(.katex-display) {
  margin: 12px 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.markdown-body :deep(.katex-display > .katex) {
  font-size: 1.2em;
}
</style>
