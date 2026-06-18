<template>
  <div class="input-area">
    <div class="input-row">
      <textarea
        v-model="inputText"
        placeholder="请输入消息..."
        @keydown.enter.exact.prevent="handleSend"
        :disabled="isProcessing"
        rows="1"
        ref="textareaRef"
      ></textarea>

      <button
        class="voice-btn"
        :class="{ recording: isRecording }"
        @click="toggleRecording"
        :disabled="isProcessing"
        :title="isRecording ? '点击停止录音' : '点击开始录音'"
      >
        🎤
      </button>

      <button
        class="send-btn"
        @click="handleSend"
        :disabled="!inputText.trim() || isProcessing"
      >
        发送
      </button>
    </div>

    <div v-if="isRecording" class="recording-indicator">
      <span class="recording-dot"></span>
      录音中... 再次点击麦克风停止
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  isProcessing: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send-text', 'send-audio'])

const inputText = ref('')
const textareaRef = ref(null)
const isRecording = ref(false)
const mediaRecorder = ref(null)
const audioChunks = ref([])

// 发送文本消息
const handleSend = () => {
  if (inputText.value.trim() && !props.isProcessing) {
    emit('send-text', inputText.value.trim())
    inputText.value = ''
    // 重置 textarea 高度
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  }
}

// 切换录音状态
const toggleRecording = async () => {
  if (isRecording.value) {
    stopRecording()
  } else {
    await startRecording()
  }
}

// 开始录音
const startRecording = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder.value = new MediaRecorder(stream, {
      mimeType: 'audio/webm;codecs=opus'
    })
    audioChunks.value = []

    mediaRecorder.value.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.value.push(event.data)
      }
    }

    mediaRecorder.value.onstop = () => {
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })
      convertToWav(audioBlob)
      // 停止所有音轨
      stream.getTracks().forEach(track => track.stop())
    }

    mediaRecorder.value.start()
    isRecording.value = true
  } catch (error) {
    console.error('无法访问麦克风:', error)
    alert('无法访问麦克风，请检查权限设置')
  }
}

// 停止录音
const stopRecording = () => {
  if (mediaRecorder.value && isRecording.value) {
    mediaRecorder.value.stop()
    isRecording.value = false
  }
}

// 转换为 WAV 格式并发送
const convertToWav = async (audioBlob) => {
  try {
    const arrayBuffer = await audioBlob.arrayBuffer()
    const audioContext = new (window.AudioContext || window.webkitAudioContext)()
    const audioBuffer = await audioContext.decodeAudioData(arrayBuffer)

    // 转换为 WAV
    const wavBuffer = audioBufferToWav(audioBuffer)
    const base64Audio = arrayBufferToBase64(wavBuffer)

    emit('send-audio', base64Audio)
  } catch (error) {
    console.error('音频转换失败:', error)
    alert('音频处理失败，请重试')
  }
}

// AudioBuffer 转 WAV
const audioBufferToWav = (buffer) => {
  const numChannels = buffer.numberOfChannels
  const sampleRate = buffer.sampleRate
  const format = 1 // PCM
  const bitDepth = 16

  const bytesPerSample = bitDepth / 8
  const blockAlign = numChannels * bytesPerSample
  const dataSize = buffer.length * blockAlign
  const headerSize = 44
  const totalSize = headerSize + dataSize

  const arrayBuffer = new ArrayBuffer(totalSize)
  const view = new DataView(arrayBuffer)

  // WAV 文件头
  writeString(view, 0, 'RIFF')
  view.setUint32(4, totalSize - 8, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, format, true)
  view.setUint16(22, numChannels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * blockAlign, true)
  view.setUint16(32, blockAlign, true)
  view.setUint16(34, bitDepth, true)
  writeString(view, 36, 'data')
  view.setUint32(40, dataSize, true)

  // 写入音频数据
  const offset = 44
  const channelData = []
  for (let i = 0; i < numChannels; i++) {
    channelData.push(buffer.getChannelData(i))
  }

  for (let i = 0; i < buffer.length; i++) {
    for (let channel = 0; channel < numChannels; channel++) {
      const sample = Math.max(-1, Math.min(1, channelData[channel][i]))
      const intSample = sample < 0 ? sample * 0x8000 : sample * 0x7FFF
      view.setInt16(offset + (i * blockAlign) + (channel * bytesPerSample), intSample, true)
    }
  }

  return arrayBuffer
}

const writeString = (view, offset, string) => {
  for (let i = 0; i < string.length; i++) {
    view.setUint8(offset + i, string.charCodeAt(i))
  }
}

const arrayBufferToBase64 = (buffer) => {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

// 自动调整 textarea 高度
const adjustTextareaHeight = () => {
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    textareaRef.value.style.height = Math.min(textareaRef.value.scrollHeight, 120) + 'px'
  }
}

onMounted(() => {
  if (textareaRef.value) {
    textareaRef.value.addEventListener('input', adjustTextareaHeight)
  }
})

onUnmounted(() => {
  if (textareaRef.value) {
    textareaRef.value.removeEventListener('input', adjustTextareaHeight)
  }
  // 确保停止录音
  if (isRecording.value) {
    stopRecording()
  }
})
</script>

<style scoped>
.input-area {
  padding: 8px 0;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

textarea {
  flex: 1;
  resize: none;
  min-height: 40px;
  max-height: 120px;
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 14px;
  line-height: 1.4;
  transition: border-color 0.2s;
}

textarea:focus {
  border-color: #4a90d9;
}

textarea:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.voice-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #f0f0f0;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.voice-btn:hover:not(:disabled) {
  background: #e0e0e0;
}

.voice-btn.recording {
  background: #ff4444;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(255, 68, 68, 0.4); }
  50% { transform: scale(1.05); box-shadow: 0 0 0 10px rgba(255, 68, 68, 0); }
}

.send-btn {
  height: 44px;
  padding: 0 20px;
  background: #4a90d9;
  color: white;
  border-radius: 22px;
  font-size: 14px;
  font-weight: 500;
  transition: background-color 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #357abd;
}

.send-btn:disabled {
  background: #ccc;
}

.recording-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-top: 8px;
  background: #fff3f3;
  border-radius: 8px;
  font-size: 13px;
  color: #ff4444;
}

.recording-dot {
  width: 8px;
  height: 8px;
  background: #ff4444;
  border-radius: 50%;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
