<template>
  <div class="character-avatar">
    <div class="avatar-container" :class="state">
      <!-- 光环效果 -->
      <div class="avatar-glow"></div>
      <!-- 人物图片 -->
      <img
        :src="avatarSrc"
        alt="卡通人物"
        class="avatar-image"
      />
      <!-- 口型叠加层（说话时显示） -->
      <div class="mouth-overlay" v-if="state === 'speaking'">
        <div class="mouth-shape"></div>
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
import avatarSrc from '/avatar/fig1.jpg'

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
  width: 200px;
  height: 200px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 光环 */
.avatar-glow {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(74,144,217,0.15) 0%, transparent 70%);
  transition: all 0.4s ease;
}

/* 人物图片 */
.avatar-image {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #e8e8e8;
  position: relative;
  z-index: 1;
  transition: all 0.3s ease;
}

/* 口型叠加层 */
.mouth-overlay {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
}

.mouth-shape {
  width: 30px;
  height: 14px;
  background: rgba(255, 150, 150, 0.5);
  border-radius: 50%;
  animation: mouthMove 0.3s infinite alternate;
}

/* ===== 空闲态 ===== */
.avatar-container.idle .avatar-image {
  animation: idleFloat 3s ease-in-out infinite;
}

@keyframes idleFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}

.avatar-container.idle .avatar-glow {
  animation: glowPulse 3s ease-in-out infinite;
}

/* ===== 思考态 ===== */
.avatar-container.thinking .avatar-image {
  animation: thinkingPulse 1.5s ease-in-out infinite;
  border-color: #ffc107;
}

.avatar-container.thinking .avatar-glow {
  background: radial-gradient(circle, rgba(255, 193, 7, 0.2) 0%, transparent 70%);
  animation: glowThink 1s ease-in-out infinite;
}

@keyframes thinkingPulse {
  0%, 100% { transform: scale(1); }
  25% { transform: scale(1.03) rotate(-1deg); }
  75% { transform: scale(1.03) rotate(1deg); }
}

@keyframes glowThink {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.08); opacity: 1; }
}

/* ===== 说话态 ===== */
.avatar-container.speaking .avatar-image {
  animation: speakingBounce 0.4s ease-in-out infinite;
  border-color: #4caf50;
}

.avatar-container.speaking .avatar-glow {
  background: radial-gradient(circle, rgba(76, 175, 80, 0.25) 0%, transparent 70%);
  animation: glowSpeak 0.4s ease-in-out infinite;
}

@keyframes speakingBounce {
  0%, 100% { transform: scale(1) translateY(0); }
  50% { transform: scale(1.04) translateY(-4px); }
}

@keyframes glowSpeak {
  0%, 100% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
}

@keyframes mouthMove {
  0% { transform: scaleY(0.3); border-radius: 50%; }
  100% { transform: scaleY(1); border-radius: 50% 50% 0 0; }
}

/* ===== 状态文字 ===== */
.state-text {
  margin-top: 16px;
  font-size: 14px;
  color: #888;
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
