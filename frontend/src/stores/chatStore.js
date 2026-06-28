import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { io } from 'socket.io-client'

export const useChatStore = defineStore('chat', () => {
  const messages = ref([])
  const isProcessing = ref(false)
  const isSpeaking = ref(false)
  const socket = ref(null)
  const currentAssistantMessage = ref('')
  const audioCharPos = ref(-1)
  const mapHighlight = ref([])
  const trafficRoads = ref([])
  const trafficCenter = ref(null)
  const trafficRadius = ref(0)
  const trafficBounds = ref(null)

  // 朗读开关 + 音量（移到 store 中，支持实时响应）
  const autoRead = ref(true)
  const volume = ref(0.8)

  const audioQueue = ref([])
  let currentAudio = null
  let nextIndex = 0
  let audioUnlocked = false

  // ── 朗读开关：关闭时停止播放 + 清空队列，打开时可恢复 ──
  watch(autoRead, (val) => {
    if (!val) {
      stopAudio()
    }
  })

  // ── 实时响应：音量调节 → 立即生效 ──
  watch(volume, (val) => {
    if (currentAudio) {
      currentAudio.volume = val
    }
  })

  const unlockAudio = () => {
    if (audioUnlocked) return
    try {
      const a = new Audio('data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=')
      a.volume = 0.01
      a.play().then(() => { a.pause(); a.currentTime = 0; audioUnlocked = true }).catch(() => {})
    } catch (e) {}
  }

  const stopAudio = () => {
    if (currentAudio) { currentAudio.pause(); currentAudio = null }
    audioQueue.value = []
    nextIndex = 0
    isSpeaking.value = false
    audioCharPos.value = -1
  }

  const tryPlayNext = () => {
    const item = audioQueue.value.find(a => a.index === nextIndex)
    if (!item) { isSpeaking.value = false; currentAudio = null; return }
    audioQueue.value = audioQueue.value.filter(a => a.index !== nextIndex)
    nextIndex++

    // 更新滚动位置到当前段起始处
    audioCharPos.value = item.char_pos || 0
    isSpeaking.value = true
    currentAudio = new Audio(item.url)
    currentAudio.volume = volume.value  // 应用当前音量
    currentAudio.onended = () => { currentAudio = null; tryPlayNext() }
    currentAudio.onerror = () => { currentAudio = null; tryPlayNext() }
    currentAudio.play().catch(() => { currentAudio = null; tryPlayNext() })
  }

  const initSocket = () => {
    socket.value = io('/', { transports: ['websocket', 'polling'] })

    socket.value.on('connect', () => console.log('WS 已连接'))
    socket.value.on('disconnect', () => console.log('WS 已断开'))

    socket.value.on('greeting', (data) => {
      messages.value.push({ role: 'assistant', content: data.content, isGreeting: true })
    })

    socket.value.on('text_chunk', (data) => {
      currentAssistantMessage.value += data.content
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant') {
          messages.value[i].content = currentAssistantMessage.value
          break
        }
      }
    })

    socket.value.on('text_complete', (data) => {
      // 用后端的干净版本（无 JSON）覆盖流式输出中的最后一条消息
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant') {
          messages.value[i].content = data.content
          break
        }
      }
      isProcessing.value = false
      currentAssistantMessage.value = ''
      parseMapHighlight(data.content)
    })

    socket.value.on('audio', (data) => {
      if (!autoRead.value) return  // 朗读已关闭，拒绝新音频入队
      if (!data.url) return
      if (audioQueue.value.some(a => a.index === data.index)) return
      audioQueue.value.push({ url: data.url, index: data.index, char_pos: data.char_pos || 0 })
      audioQueue.value.sort((a, b) => a.index - b.index)
      if (!currentAudio) tryPlayNext()
    })

    socket.value.on('asr_result', (data) => {
      stopAudio()
      messages.value.push({ role: 'user', content: data.content, type: 'voice' })
      messages.value.push({ role: 'assistant', content: '' })
    })

    socket.value.on('error', (data) => {
      console.error('服务端错误:', data.content)
      isProcessing.value = false
    })

    socket.value.on('traffic_data', (data) => {
      console.log('[路况] 收到实时数据:', data.summary)
      trafficRoads.value = data.roads || []
      trafficCenter.value = data.center || null
      trafficRadius.value = data.query_radius || 0
      trafficBounds.value = data.bounds || null
      mapHighlight.value = data.highlights || []
    })

    // LLM 回复中提到的路名 → 合并进地图高亮
    socket.value.on('highlight_update', (data) => {
      if (!data.roads || data.roads.length === 0) return
      const existing = new Map(mapHighlight.value.map(h => [h.name, h]))
      data.roads.forEach(r => {
        if (!existing.has(r.name)) {
          existing.set(r.name, r)
        }
      })
      mapHighlight.value = Array.from(existing.values())
    })

  }

  const sendTextMessage = (text) => {
    if (!text.trim() || isProcessing.value) return
    unlockAudio(); stopAudio()
    messages.value.push({ role: 'user', content: text })
    messages.value.push({ role: 'assistant', content: '' })
    isProcessing.value = true
    currentAssistantMessage.value = ''
    socket.value.emit('message', { type: 'text', content: text, auto_read: autoRead.value, volume: volume.value })
  }

  const sendAudioMessage = (audioData) => {
    if (isProcessing.value) return
    unlockAudio(); stopAudio()
    isProcessing.value = true
    currentAssistantMessage.value = ''
    socket.value.emit('message', { type: 'audio', content: audioData, format: 'wav', auto_read: autoRead.value, volume: volume.value })
  }

  const parseMapHighlight = (text) => {
    if (!text) { mapHighlight.value = []; return }

    try {
      const m = text.match(/```json\s*([\s\S]*?)```/)
      if (m) {
        const parsed = JSON.parse(m[1])
        if (parsed.map_highlight) {
          mapHighlight.value = parsed.map_highlight
          return
        }
      }
      const m2 = text.match(/\{"map_highlight"\s*:\s*(\[[\s\S]*?\])\s*\}/)
      if (m2) {
        const parsed = JSON.parse('{"map_highlight":' + m2[1] + '}')
        mapHighlight.value = parsed.map_highlight
        return
      }
    } catch (e) {}

    mapHighlight.value = []
  }

  initSocket()

  return { messages, isProcessing, isSpeaking, audioCharPos, mapHighlight, trafficRoads, trafficCenter, trafficRadius, trafficBounds, autoRead, volume, sendTextMessage, sendAudioMessage }
})
