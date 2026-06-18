import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { io } from 'socket.io-client'

export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref([])
  const isProcessing = ref(false)
  const isSpeaking = ref(false)
  const socket = ref(null)
  const currentAssistantMessage = ref('')

  // 初始化 WebSocket 连接
  const initSocket = () => {
    socket.value = io('/', {
      transports: ['websocket', 'polling']
    })

    socket.value.on('connect', () => {
      console.log('WebSocket 已连接')
    })

    socket.value.on('disconnect', () => {
      console.log('WebSocket 已断开')
    })

    // 接收 LLM 流式文本
    socket.value.on('text_chunk', (data) => {
      currentAssistantMessage.value += data.content
      // 找到最后一条助手消息并更新
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant') {
          messages.value[i].content = currentAssistantMessage.value
          break
        }
      }
    })

    // LLM 回复完成
    socket.value.on('text_complete', (data) => {
      isProcessing.value = false
      currentAssistantMessage.value = ''
    })

    // 接收 TTS 音频
    socket.value.on('audio', (data) => {
      playAudio(data.content, data.format)
    })

    // ASR 识别结果
    socket.value.on('asr_result', (data) => {
      // 将识别结果显示为用户消息
      messages.value.push({
        role: 'user',
        content: data.content,
        type: 'voice'
      })
      // 添加助手消息占位符用于流式输出
      messages.value.push({
        role: 'assistant',
        content: ''
      })
    })

    // 错误处理
    socket.value.on('error', (data) => {
      console.error('服务端错误:', data.content)
      isProcessing.value = false
    })
  }

  // 发送文本消息
  const sendTextMessage = (text, autoRead, volume) => {
    if (!text.trim() || isProcessing.value) return

    // 添加用户消息
    messages.value.push({
      role: 'user',
      content: text
    })

    // 添加助手消息占位
    messages.value.push({
      role: 'assistant',
      content: ''
    })

    isProcessing.value = true
    currentAssistantMessage.value = ''

    // 发送到后端
    socket.value.emit('message', {
      type: 'text',
      content: text,
      auto_read: autoRead,
      volume: volume
    })
  }

  // 发送语音消息
  const sendAudioMessage = (audioData, autoRead, volume) => {
    if (isProcessing.value) return

    isProcessing.value = true
    currentAssistantMessage.value = ''

    // 注意：助手消息占位符会在收到 asr_result 后添加
    socket.value.emit('message', {
      type: 'audio',
      content: audioData,
      format: 'wav',
      auto_read: autoRead,
      volume: volume
    })
  }

  // 播放音频
  const playAudio = (base64Audio, format) => {
    isSpeaking.value = true
    const audioSrc = `data:audio/${format};base64,${base64Audio}`
    const audio = new Audio(audioSrc)

    audio.onended = () => {
      isSpeaking.value = false
    }

    audio.onerror = () => {
      isSpeaking.value = false
      console.error('音频播放失败')
    }

    audio.play().catch(err => {
      isSpeaking.value = false
      console.error('音频播放失败:', err)
    })
  }

  // 初始化
  initSocket()

  return {
    messages,
    isProcessing,
    isSpeaking,
    sendTextMessage,
    sendAudioMessage
  }
})
