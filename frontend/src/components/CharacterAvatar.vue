<template>
  <div class="character-avatar">
    <div class="avatar-container" :class="state">
      <!-- 头部 -->
      <div class="head">
        <!-- 眼睛 -->
        <div class="eyes">
          <div class="eye left"></div>
          <div class="eye right"></div>
        </div>
        <!-- 嘴巴 -->
        <div class="mouth" :class="mouthClass"></div>
      </div>
      <!-- 身体 -->
      <div class="body"></div>
    </div>
    <!-- 状态文字 -->
    <div class="state-text">{{ stateText }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  state: {
    type: String,
    default: 'idle', // idle, thinking, speaking
    validator: (value) => ['idle', 'thinking', 'speaking'].includes(value)
  }
})

const mouthClass = computed(() => {
  switch (props.state) {
    case 'speaking': return 'speaking'
    case 'thinking': return 'thinking'
    default: return 'idle'
  }
})

const stateText = computed(() => {
  switch (props.state) {
    case 'speaking': return '正在说话...'
    case 'thinking': return '思考中...'
    default: return ''
  }
})
</script>

<style scoped>
.character-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
}

.avatar-container {
  width: 120px;
  height: 160px;
  position: relative;
}

/* 头部 */
.head {
  width: 100px;
  height: 100px;
  background: #4a90d9;
  border-radius: 50%;
  position: relative;
  margin: 0 auto;
  box-shadow: 0 4px 12px rgba(74, 144, 217, 0.3);
}

/* 眼睛 */
.eyes {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding-top: 32px;
}

.eye {
  width: 14px;
  height: 14px;
  background: white;
  border-radius: 50%;
  position: relative;
}

.eye::after {
  content: '';
  width: 8px;
  height: 8px;
  background: #333;
  border-radius: 50%;
  position: absolute;
  top: 3px;
  left: 3px;
}

/* 眨眼动画 */
@keyframes blink {
  0%, 90%, 100% { transform: scaleY(1); }
  95% { transform: scaleY(0.1); }
}

.eye {
  animation: blink 3s infinite;
}

/* 嘴巴 */
.mouth {
  width: 30px;
  height: 10px;
  background: #2c5aa0;
  border-radius: 0 0 15px 15px;
  margin: 16px auto 0;
  transition: all 0.15s ease;
}

.mouth.idle {
  height: 6px;
  border-radius: 0 0 10px 10px;
}

.mouth.speaking {
  animation: speak 0.3s infinite alternate;
}

.mouth.thinking {
  height: 4px;
  border-radius: 2px;
}

@keyframes speak {
  0% {
    height: 8px;
    border-radius: 0 0 10px 10px;
  }
  50% {
    height: 16px;
    border-radius: 0 0 15px 15px;
  }
  100% {
    height: 10px;
    border-radius: 0 0 12px 12px;
  }
}

/* 身体 */
.body {
  width: 60px;
  height: 50px;
  background: #4a90d9;
  border-radius: 10px 10px 0 0;
  margin: -5px auto 0;
}

/* 思考动画 */
.avatar-container.thinking .head {
  animation: think 2s infinite;
}

@keyframes think {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}

/* 说话动画 */
.avatar-container.speaking .head {
  animation: speakBounce 0.5s infinite;
}

@keyframes speakBounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

/* 状态文字 */
.state-text {
  margin-top: 12px;
  font-size: 14px;
  color: #666;
  min-height: 20px;
}

/* 思考中的省略号动画 */
.avatar-container.thinking .state-text::after {
  content: '';
  animation: dots 1.5s infinite;
}

@keyframes dots {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
}
</style>
