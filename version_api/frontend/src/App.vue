<template>
  <div class="app-container">
    <header class="app-header">
      <h1>语音聊天系统</h1>
    </header>

    <main class="app-main">
      <CharacterAvatar :state="avatarState" />
      <ChatWindow :messages="messages" />
    </main>

    <footer class="app-footer">
      <AudioControls
        v-model:volume="volume"
        v-model:autoRead="autoRead"
      />
      <InputArea
        @send-text="handleSendText"
        @send-audio="handleSendAudio"
        :isProcessing="isProcessing"
      />
    </footer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useChatStore } from './stores/chatStore'
import CharacterAvatar from './components/CharacterAvatar.vue'
import ChatWindow from './components/ChatWindow.vue'
import InputArea from './components/InputArea.vue'
import AudioControls from './components/AudioControls.vue'

const chatStore = useChatStore()

const messages = computed(() => chatStore.messages)
const isProcessing = computed(() => chatStore.isProcessing)
const isSpeaking = computed(() => chatStore.isSpeaking)
const volume = ref(0.8)
const autoRead = ref(true)

const avatarState = computed(() => {
  if (isSpeaking.value) return 'speaking'
  if (isProcessing.value) return 'thinking'
  return 'idle'
})

const handleSendText = (text) => {
  chatStore.sendTextMessage(text, autoRead.value, volume.value)
}

const handleSendAudio = (audioData) => {
  chatStore.sendAudioMessage(audioData, autoRead.value, volume.value)
}
</script>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 800px;
  margin: 0 auto;
  padding: 16px;
}

.app-header {
  text-align: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}

.app-header h1 {
  margin: 0;
  font-size: 1.5rem;
  color: #333;
}

.app-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 0;
}

.app-footer {
  border-top: 1px solid #eee;
  padding-top: 12px;
}
</style>
