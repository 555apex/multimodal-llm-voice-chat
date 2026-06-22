<template>
  <div class="character-avatar">
    <div class="avatar-container" :class="state">
      <!-- 人物图片 -->
      <img
        :src="avatarSrc"
        alt="交警卡通人物"
        class="avatar-image"
      />
      <!-- 口型动画层 -->
      <div class="mouth-animation" v-if="state === 'speaking'">
        <div class="mouth-moving"></div>
      </div>
    </div>
    <!-- 状态提示 -->
    <div class="state-text">
      <span v-if="state === 'speaking'">🔊 正在说话...</span>
      <span v-else-if="state === 'thinking'">💭 思考中...</span>
      <span v-else>👋 你好！随时问我问题</span>
    </div>
  </div>
</template>

<script setup>
import avatarSrc from '/avatar/police.svg'

defineProps({
  state: {
    type: String,
    default: 'idle', // idle, thinking, speaking
    validator: (value) => ['idle', 'thinking', 'speaking'].includes(value)
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
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 人物图片 */
.avatar-image {
  width: 120px;
  height: 160px;
  object-fit: contain;
  position: relative;
  z-index: 1;
  transition: all 0.3s ease;
}

/* 口型动画层 - 覆盖在嘴巴位置 */
.mouth-animation {
  position: absolute;
  top: 32%;  /* 嘴巴位置 */
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  width: 20px;
  height: 15px;
  overflow: hidden;
}

.mouth-moving {
  width: 16px;
  height: 10px;
  background: #c0392b;
  border-radius: 50%;
  animation: mouthSpeak 0.3s infinite alternate;
  margin: 0 auto;
}

@keyframes mouthSpeak {
  0% {
    transform: scaleY(0.3);
    border-radius: 50%;
  }
  100% {
    transform: scaleY(1);
    border-radius: 50% 50% 30% 30%;
  }
}

/* ===== 空闲态 ===== */
.avatar-container.idle .avatar-image {
  animation: idleFloat 3s ease-in-out infinite;
}

@keyframes idleFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}

/* ===== 思考态 ===== */
.avatar-container.thinking .avatar-image {
  animation: thinkingPulse 1.5s ease-in-out infinite;
  filter: drop-shadow(0 0 15px rgba(255, 193, 7, 0.5));
}

@keyframes thinkingPulse {
  0%, 100% { transform: scale(1); }
  25% { transform: scale(1.02) rotate(-1deg); }
  75% { transform: scale(1.02) rotate(1deg); }
}

/* ===== 说话态 ===== */
.avatar-container.speaking .avatar-image {
  animation: speakingBounce 0.4s ease-in-out infinite;
  filter: drop-shadow(0 0 15px rgba(76, 175, 80, 0.5));
}

@keyframes speakingBounce {
  0%, 100% { transform: scale(1) translateY(0); }
  50% { transform: scale(1.02) translateY(-3px); }
}

/* ===== 状态文字 ===== */
.state-text {
  margin-top: 12px;
  font-size: 14px;
  color: #666;
  min-height: 24px;
  text-align: center;
  transition: all 0.3s ease;
}

.avatar-container.thinking + .state-text {
  color: #ff9800;
}

.avatar-container.speaking + .state-text {
  color: #4caf50;
}
</style>
