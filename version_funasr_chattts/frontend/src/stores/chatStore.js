import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { io } from 'socket.io-client'

export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref([])
  const isProcessing = ref(false)
  const isGenerating = ref(false)
  const isSpeaking = ref(false)
  const socket = ref(null)
  const currentAssistantMessage = ref('')

  // 音频播放状态
  const audioQueue = ref([])        // 当前回复的所有音频块
  const currentAudioIndex = ref(-1) // 当前播放索引 (-1 = 未播放)
  const currentAudioEl = ref(null)  // 当前播放的 Audio 元素
  const isAutoRead = ref(true)      // 朗读开关
  const volume = ref(0.8)           // 音量

  // 音量变化时实时调节当前播放音频
  watch(volume, (newVol) => {
    if (currentAudioEl.value) {
      currentAudioEl.value.volume = newVol
    }
  })

  // 重置音频队列
  function resetAudioQueue() {
    stopCurrentAudio()
    audioQueue.value = []
    currentAudioIndex.value = -1
  }

  // 停止当前播放
  function stopCurrentAudio() {
    if (currentAudioEl.value) {
      currentAudioEl.value.pause()
      currentAudioEl.value.currentTime = 0
      currentAudioEl.value.onended = null
      currentAudioEl.value.onerror = null
      currentAudioEl.value = null
    }
    isSpeaking.value = false
  }

  // 从指定索引开始播放
  function playFromIndex(index) {
    if (index >= audioQueue.value.length) {
      isSpeaking.value = false
      currentAudioEl.value = null
      return
    }

    const audio = audioQueue.value[index]
    currentAudioIndex.value = index
    currentAudioEl.value = audio
    audio.volume = volume.value
    isSpeaking.value = true

    audio.onended = () => playFromIndex(index + 1)
    audio.onerror = () => playFromIndex(index + 1)
    audio.play().catch(() => playFromIndex(index + 1))
  }

  // 切换朗读开关
  function toggleAutoRead() {
    isAutoRead.value = !isAutoRead.value
    if (!isAutoRead.value) {
      // 关闭：立即停止播放
      stopCurrentAudio()
    } else {
      // 开启：从当前位置继续播放，或从头开始
      if (audioQueue.value.length > 0) {
        const resumeIndex = Math.max(0, currentAudioIndex.value)
        playFromIndex(resumeIndex)
      }
    }
  }

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

    // 接收开场白
    socket.value.on('greeting', (data) => {
      console.log('收到开场白')
      messages.value.push({
        role: 'assistant',
        content: data.content,
        isGreeting: true
      })
    })

    // 接收功能调用通知
    socket.value.on('function_call', (data) => {
      console.log('功能调用:', data.function)
      functionCalls.value.push({
        function: data.function,
        config: data.config,
        timestamp: Date.now()
      })
    })

    // 接收 LLM 流式文本
    socket.value.on('text_chunk', (data) => {
      isGenerating.value = true
      currentAssistantMessage.value += data.content
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant') {
          messages.value[i].content = currentAssistantMessage.value
          break
        }
      }
    })

    // LLM 回复完成
    socket.value.on('text_complete', (data) => {
      isGenerating.value = false
      currentAssistantMessage.value = ''
    })

    // 接收流式 TTS 音频块
    socket.value.on('audio_chunk', (data) => {
      const audioSrc = `data:audio/${data.format};base64,${data.content}`
      const audio = new Audio(audioSrc)
      audio.volume = volume.value
      audioQueue.value.push(audio)

      // 如果朗读开启且当前没有播放，立即开始播放
      if (isAutoRead.value && !isSpeaking.value) {
        playFromIndex(audioQueue.value.length - 1)
      }
    })

    // 所有音频块发送完毕
    socket.value.on('audio_done', (data) => {
      isProcessing.value = false
    })

    // ASR 识别结果
    socket.value.on('asr_result', (data) => {
      messages.value.push({
        role: 'user',
        content: data.content,
        type: 'voice'
      })
      messages.value.push({
        role: 'assistant',
        content: ''
      })
      // 新回复开始，重置音频队列
      resetAudioQueue()
    })

    // 错误处理
    socket.value.on('error', (data) => {
      console.error('服务端错误:', data.content)
      isProcessing.value = false
      isGenerating.value = false
    })
  }

  // 发送文本消息
  const sendTextMessage = (text) => {
    if (!text.trim() || isProcessing.value) return

    messages.value.push({
      role: 'user',
      content: text
    })

    messages.value.push({
      role: 'assistant',
      content: ''
    })

    isProcessing.value = true
    isGenerating.value = true
    currentAssistantMessage.value = ''

    // 新回复开始，重置音频队列
    resetAudioQueue()

    socket.value.emit('message', {
      type: 'text',
      content: text,
    })
  }

  // 发送语音消息
  const sendAudioMessage = (audioData) => {
    if (isProcessing.value) return

    isProcessing.value = true
    isGenerating.value = true
    currentAssistantMessage.value = ''

    // 新回复开始，重置音频队列
    resetAudioQueue()

    socket.value.emit('message', {
      type: 'audio',
      content: audioData,
      format: 'wav',
    })
  }

  // 初始化
  initSocket()

  return {
    messages,
    isProcessing,
    isGenerating,
    isSpeaking,
    isAutoRead,
    volume,
    functionCalls,
    sendTextMessage,
    sendAudioMessage,
    toggleAutoRead,
    resetAudioQueue,
  }
})
