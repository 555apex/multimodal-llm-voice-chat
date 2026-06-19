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

  // 朗读开关 + 音量（移到 store 中，支持实时响应）
  const autoRead = ref(true)
  const volume = ref(0.8)

  const audioQueue = ref([])
  let currentAudio = null
  let nextIndex = 0
  let audioUnlocked = false

  // ── 实时响应：朗读开关关闭 → 立即停止当前音频 ──
  watch(autoRead, (val) => {
    if (!val && currentAudio) {
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

    // 方式1：解析 LLM 输出的 JSON 块
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

    // 方式2：从文本中提取已知路名 + 拥堵关键词，自动生成高亮
    const knownRoads = [
      // 福州
      { name: '福州绕城高速新店至桂湖段', lat: 26.12, lng: 119.30, keywords: ['福州绕城', '绕城高速', '新店', '桂湖'] },
      { name: '福州市五四路至五一路', lat: 26.08, lng: 119.30, keywords: ['五四路', '五一路'] },
      { name: '福州鼓屏路-华林路', lat: 26.09, lng: 119.30, keywords: ['鼓屏路', '华林路'] },
      { name: '福州杨桥路-西二环', lat: 26.08, lng: 119.28, keywords: ['杨桥路', '西二环', '二环'] },
      { name: 'G15沈海高速福州段', lat: 26.05, lng: 119.35, keywords: ['沈海高速'] },
      { name: 'G70福银高速福州至闽侯段', lat: 26.10, lng: 119.20, keywords: ['福银高速'] },
      // 厦门
      { name: 'G324嘉禾路厦门大桥至莲坂段', lat: 24.50, lng: 118.12, keywords: ['嘉禾路', 'G324', '福昆线'] },
      { name: '厦门海沧大桥', lat: 24.50, lng: 118.06, keywords: ['海沧大桥'] },
      { name: '厦门演武大桥', lat: 24.44, lng: 118.09, keywords: ['演武大桥'] },
      { name: '厦门成功大道', lat: 24.47, lng: 118.12, keywords: ['成功大道'] },
      { name: '厦门环岛南路', lat: 24.44, lng: 118.12, keywords: ['环岛南路', '环岛路'] },
      { name: '厦门厦禾路', lat: 24.46, lng: 118.10, keywords: ['厦禾路'] },
      { name: '厦门湖滨南路', lat: 24.46, lng: 118.10, keywords: ['湖滨南路'] },
      { name: '厦门鹭江道', lat: 24.45, lng: 118.08, keywords: ['鹭江道'] },
      { name: '厦门翔安隧道', lat: 24.51, lng: 118.20, keywords: ['翔安隧道'] },
      // 泉州
      { name: '泉州温陵路-刺桐路', lat: 24.89, lng: 118.60, keywords: ['温陵路', '刺桐路'] },
      { name: '泉州丰泽街-泉秀街', lat: 24.88, lng: 118.62, keywords: ['丰泽街', '泉秀街'] },
      { name: '泉州晋江机场周边', lat: 24.79, lng: 118.59, keywords: ['晋江机场'] },
      { name: 'G15沈海高速泉州段', lat: 24.88, lng: 118.66, keywords: ['泉州段', '泉厦', '沈海高速泉州'] },
      { name: 'G324国道泉州段', lat: 24.90, lng: 118.60, keywords: ['G324', '福昆线泉州'] },
      // 漳州
      { name: '漳州胜利路-水仙大街', lat: 24.51, lng: 117.65, keywords: ['胜利路', '水仙大街'] },
      { name: '漳州南昌路-延安北路', lat: 24.51, lng: 117.65, keywords: ['南昌路', '延安北路'] },
      { name: 'G15沈海高速漳州段', lat: 24.42, lng: 117.75, keywords: ['沈海高速漳州', '漳州段'] },
      { name: 'G76厦蓉高速漳州段', lat: 24.52, lng: 117.66, keywords: ['厦蓉高速'] },
      // 龙岩
      { name: '龙岩龙岩大道', lat: 25.09, lng: 117.02, keywords: ['龙岩大道'] },
      { name: 'G76厦蓉高速龙岩段', lat: 25.08, lng: 117.02, keywords: ['厦蓉高速龙岩', '龙岩段'] },
      { name: 'G319国道龙岩段', lat: 25.08, lng: 117.02, keywords: ['G319', '龙岩国道'] },
      // 三明
      { name: '三明列东街-新市路', lat: 26.26, lng: 117.64, keywords: ['列东街', '新市路'] },
      { name: 'G25长深高速三明段', lat: 26.25, lng: 117.63, keywords: ['长深高速'] },
      { name: 'G205国道三明段', lat: 26.27, lng: 117.63, keywords: ['G205'] },
      // 南平
      { name: '南平中山路-八一路', lat: 26.64, lng: 118.18, keywords: ['中山路南平', '八一路'] },
      { name: 'G25长深高速南平段', lat: 26.64, lng: 118.19, keywords: ['长深高速南平'] },
      { name: 'G70福银高速南平段', lat: 26.60, lng: 118.15, keywords: ['福银高速南平'] },
      // 宁德
      { name: '宁德蕉城路-闽东路', lat: 26.66, lng: 119.55, keywords: ['蕉城路', '闽东路'] },
      { name: 'G15沈海高速宁德段', lat: 26.66, lng: 119.54, keywords: ['沈海高速宁德', '宁德段'] },
      { name: 'G1514宁上高速', lat: 27.10, lng: 118.80, keywords: ['宁上高速'] },
      // 莆田
      { name: '莆田胜利路-学园路', lat: 25.45, lng: 119.01, keywords: ['胜利路莆田', '学园路'] },
      { name: '莆田荔城大道', lat: 25.45, lng: 119.01, keywords: ['荔城大道'] },
      { name: 'G15沈海高速莆田段', lat: 25.40, lng: 119.02, keywords: ['沈海高速莆田', '莆田段'] },
      // 福州补充
      { name: '福州乌山路-湖东路', lat: 26.08, lng: 119.30, keywords: ['乌山路', '湖东路'] },
      { name: '福州八一七路-东街', lat: 26.08, lng: 119.30, keywords: ['八一七路', '东街', '东街口'] },
      { name: '福州台江路-六一路', lat: 26.06, lng: 119.31, keywords: ['台江路', '六一路'] },
      { name: '福州福马路-金山大道', lat: 26.06, lng: 119.30, keywords: ['福马路', '金山大道'] },
      // 厦门补充
      { name: '厦门莲前东路-莲前西路', lat: 24.48, lng: 118.13, keywords: ['莲前东路', '莲前西路', '莲前'] },
      { name: '厦门湖里大道', lat: 24.52, lng: 118.11, keywords: ['湖里大道'] },
      { name: '厦门仙岳路', lat: 24.49, lng: 118.14, keywords: ['仙岳路'] },
      { name: '厦门吕岭路-金尚路', lat: 24.50, lng: 118.14, keywords: ['吕岭路', '金尚路'] },
      // 泉州补充
      { name: '泉州田安路-坪山路', lat: 24.89, lng: 118.62, keywords: ['田安路', '坪山路'] },
      // 漳州补充
      { name: '漳州丹霞路-新浦路', lat: 24.51, lng: 117.66, keywords: ['丹霞路', '新浦路'] },
      // 龙岩补充
      { name: '龙岩解放路-华莲路', lat: 25.08, lng: 117.02, keywords: ['解放路', '华莲路'] },
      // 三明补充
      { name: '三明麒麟山路-劲松路', lat: 26.26, lng: 117.64, keywords: ['麒麟山路', '劲松路'] },
      // 南平补充
      { name: '南平滨江路-马坑路', lat: 26.64, lng: 118.17, keywords: ['滨江路', '马坑路'] },
      // 宁德补充
      { name: '宁德福宁路-鹤峰路', lat: 26.65, lng: 119.55, keywords: ['福宁路', '鹤峰路'] },
      // 莆田补充
      { name: '莆田东圳路-文献路', lat: 25.45, lng: 119.00, keywords: ['东圳路', '文献路'] },
    ]

    const highlights = []
    const congestionWords = ['拥堵', '缓行', '排队', '通行缓慢', '车流量大', '饱和', '严重', '延误', '拥挤']
    const hazardWords = ['灾害', '隐患', '滑坡', '塌方', '泥石流', '预警', '监测异常']

    // 策略：先找到拥堵段落，再提取段落中的路名 → 路名所在段落含拥堵词 = 该路拥堵
    const paragraphs = text.split(/\n{2,}/)  // 空行分段
    const congestionParagraphs = paragraphs.filter(p =>
      congestionWords.some(w => p.includes(w))
    )
    const hazardParagraphs = paragraphs.filter(p =>
      hazardWords.some(w => p.includes(w))
    )

    knownRoads.forEach(road => {
      let found = false
      let type = 'construction'  // 默认施工类

      // 检查是否在拥堵段落中
      for (const kw of road.keywords) {
        for (const p of congestionParagraphs) {
          if (p.includes(kw)) { found = true; type = 'congestion'; break }
        }
        if (found) break
      }

      // 检查是否在灾害段落中
      if (!found) {
        for (const kw of road.keywords) {
          for (const p of hazardParagraphs) {
            if (p.includes(kw)) { found = true; type = 'hazard'; break }
          }
          if (found) break
        }
      }

      // 兜底：全文匹配
      if (!found) {
        for (const kw of road.keywords) {
          if (text.includes(kw)) { found = true; break }
        }
      }

      if (found) {
        highlights.push({ name: road.name, lat: road.lat, lng: road.lng, type })
      }
    })

    mapHighlight.value = highlights.length > 0 ? highlights : []
  }

  initSocket()

  return { messages, isProcessing, isSpeaking, audioCharPos, mapHighlight, autoRead, volume, sendTextMessage, sendAudioMessage }
})
