<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import AgentDrawer from './components/AgentDrawer.vue'
import CommandDashboard from './components/CommandDashboard.vue'
import { useEmergencyStore } from './stores/emergency'
import { useSpeechStore } from './stores/speech'

const agentOpen = ref(false)
const emergencyStore = useEmergencyStore()
const speechStore = useSpeechStore()

function toggleAgent() {
  agentOpen.value = !agentOpen.value
}

function closeAgent() {
  agentOpen.value = false
}

onMounted(() => {
  emergencyStore.startPolling()
  void speechStore.loadCapabilities()
})

onUnmounted(() => {
  emergencyStore.stopPolling()
  speechStore.stop()
})
</script>

<template>
  <main class="command-center-shell">
    <CommandDashboard :agent-open="agentOpen" @toggle-agent="toggleAgent" />
    <AgentDrawer :open="agentOpen" @close="closeAgent" />
  </main>
</template>
