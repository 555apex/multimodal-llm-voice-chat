<template>
  <div class="audio-controls">
    <div class="control-item">
      <span class="control-icon">🔊</span>
      <input
        type="range"
        :value="chatStore.volume"
        @input="chatStore.volume = parseFloat($event.target.value)"
        min="0"
        max="1"
        step="0.1"
        class="volume-slider"
      />
      <span class="volume-value">{{ Math.round(chatStore.volume * 100) }}%</span>
    </div>

    <div class="control-item">
      <label class="toggle-label">
        <span class="control-icon">{{ chatStore.isAutoRead ? '🔊' : '🔇' }}</span>
        <span class="toggle-text">朗读:</span>
        <button
          class="toggle-btn"
          :class="{ active: chatStore.isAutoRead }"
          @click="chatStore.toggleAutoRead()"
        >
          {{ chatStore.isAutoRead ? '开' : '关' }}
        </button>
      </label>
    </div>
  </div>
</template>

<script setup>
import { useChatStore } from '../stores/chatStore'

const chatStore = useChatStore()
</script>

<style scoped>
.audio-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f8f8f8;
  border-radius: 10px;
  margin-bottom: 12px;
}

.control-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-icon {
  font-size: 18px;
}

.volume-slider {
  width: 120px;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: #ddd;
  border-radius: 2px;
  outline: none;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  background: #4a90d9;
  border-radius: 50%;
  cursor: pointer;
  transition: transform 0.2s;
}

.volume-slider::-webkit-slider-thumb:hover {
  transform: scale(1.2);
}

.volume-value {
  font-size: 12px;
  color: #666;
  min-width: 36px;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.toggle-text {
  font-size: 14px;
  color: #666;
}

.toggle-btn {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
  background: #e0e0e0;
  color: #666;
  transition: all 0.2s;
}

.toggle-btn.active {
  background: #4a90d9;
  color: white;
}

.toggle-btn:hover {
  opacity: 0.9;
}
</style>
