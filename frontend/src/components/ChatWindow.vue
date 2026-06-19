<template>
  <div class="chat-window" ref="chatContainer" @scroll="onScroll">
    <div class="messages-list">
      <div v-for="(msg, i) in messages" :key="i" class="message-item" :class="msg.role">
        <div class="message-avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
        <div class="message-content">
          <div class="message-role">
            {{ msg.role === 'user' ? '用户' : '助手' }}
            <span v-if="msg.type === 'voice'" class="voice-badge">🎤 语音</span>
          </div>
          <div class="message-text" v-html="renderContent(msg)"></div>
        </div>
      </div>
    </div>

    <!-- 回到底部按钮（用户上滑后出现）-->
    <button v-if="showScrollBtn" class="scroll-bottom-btn" @click="gotoBottom">
      ↓ 回到底部
    </button>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  autoRead: { type: Boolean, default: true },
  audioCharPos: { type: Number, default: 0 }
})

const chatContainer = ref(null)
const userScrolledUp = ref(false)
const showScrollBtn = ref(false)
let programmaticScroll = false  // 程序化滚动标志，防止误判用户介入

const isAtBottom = () => {
  const el = chatContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 50
}

const scrollToBottom = () => {
  const el = chatContainer.value
  if (!el) return
  programmaticScroll = true
  el.scrollTop = el.scrollHeight
}

const scrollToRatio = (ratio) => {
  const el = chatContainer.value
  if (!el) return
  const msgs = el.querySelectorAll('.message-item.assistant')
  const lastMsg = msgs[msgs.length - 1]
  if (!lastMsg) { scrollToBottom(); return }
  const msgTop = lastMsg.offsetTop
  const msgHeight = lastMsg.offsetHeight
  const target = msgTop + ratio * msgHeight - el.clientHeight * 0.3
  programmaticScroll = true
  el.scrollTop = Math.max(0, Math.min(target, el.scrollHeight - el.clientHeight))
}

const renderContent = (msg) => {
  if (!msg.content) return '<span class="typing-cursor">|</span>'
  let html = msg.content
  // 转义 HTML 防 XSS
  html = html.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  // **粗体**
  html = html.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
  // 编号列表
  html = html.replace(/^(\d+)\.\s\*\*(.+?)\*\*/gm, '<div><b>$1. $2</b></div>')
  html = html.replace(/^(\d+)\.\s(.+)/gm, '<div>$1. $2</div>')
  // 无序列表
  html = html.replace(/^[-*]\s(.+)/gm, '<div>• $1</div>')
  // 换行
  html = html.replace(/\n/g, '<br>')
  return html
}

const gotoBottom = () => {
  userScrolledUp.value = false
  showScrollBtn.value = false
  nextTick(scrollToBottom)
}

// ── 用户滚动检测 ──

let scrollTimer = null
const onScroll = () => {
  // 程序触发的滚动，忽略
  if (programmaticScroll) { programmaticScroll = false; return }
  clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    if (isAtBottom()) {
      userScrolledUp.value = false
      showScrollBtn.value = false
    } else {
      userScrolledUp.value = true
      showScrollBtn.value = true
    }
  }, 150)
}

// ── 新对话：重置状态，滚到底 ──

watch(() => props.messages.length, () => {
  userScrolledUp.value = false
  showScrollBtn.value = false
  nextTick(scrollToBottom)
})

// ── 内容更新时的滚动策略 ──

watch(
  () => {
    // 只监听最后一条消息的内容变化
    const last = props.messages[props.messages.length - 1]
    return last?.content ?? ''
  },
  () => {
    if (userScrolledUp.value) return  // 用户上滑了，不打扰

    if (props.autoRead) {
      // 朗读模式：不跟文字更新（跟音频走）
    } else {
      // 非朗读模式：文字更新就跟到底
      nextTick(scrollToBottom)
    }
  }
)

// ── 朗读模式：音频进度驱动滚动 ──

watch(() => props.audioCharPos, (pos) => {
  if (!props.autoRead) return
  if (pos < 0) return                  // 初始值 -1，跳过
  if (userScrolledUp.value) return

  const last = props.messages[props.messages.length - 1]
  if (!last || last.role !== 'assistant' || !last.content) return
  nextTick(() => scrollToRatio(pos / last.content.length))
})
</script>

<style scoped>
.chat-window {
  flex: 1; overflow-y: auto; padding: 16px; background: white;
  border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.06);
  overflow-anchor: none; position: relative;
}
.messages-list { display: flex; flex-direction: column; gap: 16px; }
.message-item { display: flex; gap: 12px; padding: 12px; border-radius: 8px; }
.message-item.user { background-color: #f0f7ff; }
.message-item.assistant { background-color: #f9f9f9; }
.message-avatar { font-size: 24px; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.message-content { flex: 1; min-width: 0; }
.message-role { font-size: 12px; color: #999; margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
.voice-badge { font-size: 11px; background: #e8f4fd; color: #4a90d9; padding: 1px 6px; border-radius: 4px; }
.message-text { font-size: 14px; line-height: 1.6; word-wrap: break-word; white-space: pre-wrap; }
.typing-cursor { display: inline-block; animation: blink .8s infinite; color: #4a90d9; font-weight: bold; }
@keyframes blink { 0%,50%{opacity:1} 51%,100%{opacity:0} }

/* 回到底部按钮 */
.scroll-bottom-btn {
  position: sticky; bottom: 12px; left: 50%; transform: translateX(-50%);
  padding: 8px 20px; background: #4a90d9; color: white; border-radius: 20px;
  font-size: 13px; box-shadow: 0 2px 8px rgba(74,144,217,.3);
  cursor: pointer; z-index: 10; transition: all .2s;
}
.scroll-bottom-btn:hover { background: #357abd; transform: translateX(-50%) scale(1.05); }
</style>
