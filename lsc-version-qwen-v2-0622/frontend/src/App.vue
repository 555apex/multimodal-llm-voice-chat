<template>
  <div class="app-container">
    <header class="app-header">
      <h1>福建省路面交通智能助手</h1>
    </header>

    <main class="app-main">
      <div class="chat-column">
        <CharacterAvatar :state="avatarState" />
        <ChatWindow :messages="messages" :autoRead="autoRead" :audioCharPos="audioCharPos" />
      </div>
      <div class="map-column">
        <TrafficMap :highlight="mapHighlight" :realtimeTraffic="trafficRoads" :trafficCenter="trafficCenter" :trafficRadius="trafficRadius" :trafficBounds="trafficBounds" :visible="true" />
      </div>
    </main>

    <footer class="app-footer">
      <AudioControls v-model:volume="volume" v-model:autoRead="autoRead" />
      <InputArea
        @send-text="handleSendText"
        @send-audio="handleSendAudio"
        :isProcessing="isProcessing"
      />
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useChatStore } from './stores/chatStore'
import CharacterAvatar from './components/CharacterAvatar.vue'
import ChatWindow from './components/ChatWindow.vue'
import InputArea from './components/InputArea.vue'
import AudioControls from './components/AudioControls.vue'
import TrafficMap from './components/TrafficMap.vue'

const chatStore = useChatStore()

const messages = computed(() => chatStore.messages)
const isProcessing = computed(() => chatStore.isProcessing)
const isSpeaking = computed(() => chatStore.isSpeaking)
const audioCharPos = computed(() => chatStore.audioCharPos)
const mapHighlight = computed(() => chatStore.mapHighlight)
const trafficRoads = computed(() => chatStore.trafficRoads)
const trafficCenter = computed(() => chatStore.trafficCenter)
const trafficRadius = computed(() => chatStore.trafficRadius)
const trafficBounds = computed(() => chatStore.trafficBounds)

const volume = computed({ get: () => chatStore.volume, set: (v) => chatStore.volume = v })
const autoRead = computed({ get: () => chatStore.autoRead, set: (v) => chatStore.autoRead = v })

const avatarState = computed(() => {
  if (isSpeaking.value) return 'speaking'
  if (isProcessing.value) return 'thinking'
  return 'idle'
})

const handleSendText = (text) => { chatStore.sendTextMessage(text) }
const handleSendAudio = (audioData) => { chatStore.sendAudioMessage(audioData) }
</script>

<style scoped>
.app-container {
  display: flex; flex-direction: column;
  height: 100vh; max-width: 1400px; margin: 0 auto; padding: 16px;
}

.app-header {
  padding: 12px 0; border-bottom: 1px solid #eee; flex-shrink: 0;
}

.app-header h1 { margin: 0; font-size: 1.3rem; color: #333; }

.app-main {
  flex: 1; overflow: hidden; display: flex; gap: 16px; padding: 16px 0; min-height: 0;
}

.chat-column {
  flex: 6; min-width: 0; display: flex; flex-direction: column; gap: 16px; overflow: hidden;
}

.map-column {
  flex: 4; min-width: 350px; overflow: hidden;
}

.app-footer {
  border-top: 1px solid #eee; padding-top: 12px; flex-shrink: 0;
}
</style>
