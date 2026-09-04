<script setup lang="ts">
import { computed } from 'vue'
import DigitalHumanPortrait from './DigitalHumanPortrait.vue'
import {
  digitalHumanStateMeta,
  type DigitalHumanMode,
  type DigitalHumanPose,
} from '../types/digitalHuman'

const props = withDefaults(defineProps<{
  state: DigitalHumanMode
  speechLevel?: number
  previewSpeech?: boolean
}>(), {
  speechLevel: 0,
  previewSpeech: false,
})
const emit = defineEmits<{
  'ready-change': [ready: boolean]
  'asset-error': [pose: DigitalHumanPose]
}>()

const stateMeta = computed(() => digitalHumanStateMeta[props.state])
</script>

<template>
  <aside class="prototype-human-panel" :data-state="state" :data-pose="stateMeta.pose">
    <div class="prototype-human-orbit" aria-hidden="true"><i></i><i></i><i></i></div>

    <DigitalHumanPortrait
      class="prototype-portrait-stage"
      :mode="state"
      :speech-level="speechLevel"
      :preview-speech="previewSpeech"
      @ready-change="emit('ready-change', $event)"
      @asset-error="emit('asset-error', $event)"
    />

    <div class="prototype-identity">
      <span class="prototype-online-dot" aria-hidden="true"></span>
      <div>
        <strong>路智通</strong>
        <small>公路交通智能助手 · 生产渲染器</small>
      </div>
    </div>

    <div class="prototype-state" aria-live="polite">
      <strong>{{ stateMeta.label }}</strong>
      <span>{{ stateMeta.detail }}</span>
    </div>
  </aside>
</template>

<style scoped>
.prototype-human-panel {
  position: relative;
  min-height: 0;
  padding: 26px 32px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  color: white;
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(3, 31, 45, .12), rgba(3, 26, 38, .82)),
    radial-gradient(circle at 50% 34%, #1f938f 0, #0d5865 36%, #082f43 78%);
  box-shadow: 0 26px 64px rgba(24, 61, 75, .24);
  transition: background-color .28s ease, box-shadow .28s ease;
}

.prototype-human-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: .18;
  background-image:
    linear-gradient(rgba(255, 255, 255, .24) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .24) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom, black, transparent 90%);
}

.prototype-human-panel::after {
  content: '';
  position: absolute;
  left: 10%;
  right: 10%;
  bottom: 140px;
  height: 55px;
  border-radius: 50%;
  background: rgba(36, 226, 197, .2);
  filter: blur(18px);
  transition: background .28s ease;
}

.prototype-human-orbit {
  position: absolute;
  top: 46%;
  left: 50%;
  width: min(420px, 86%);
  aspect-ratio: 1;
  transform: translate(-50%, -56%);
  border: 1px solid rgba(91, 243, 217, .34);
  border-radius: 50%;
  animation: prototype-orbit-turn 24s linear infinite;
}
.prototype-human-orbit::before,
.prototype-human-orbit::after {
  content: '';
  position: absolute;
  border: 1px dashed rgba(255, 255, 255, .18);
  border-radius: 50%;
}
.prototype-human-orbit::before { inset: 12%; }
.prototype-human-orbit::after { inset: 26%; }
.prototype-human-orbit i {
  position: absolute;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f7b35f;
  box-shadow: 0 0 14px #f7b35f;
}
.prototype-human-orbit i:nth-child(1) { top: 12%; left: 18%; }
.prototype-human-orbit i:nth-child(2) { right: 5%; top: 54%; }
.prototype-human-orbit i:nth-child(3) { left: 24%; bottom: 2%; }

.prototype-portrait-stage {
  position: relative;
  z-index: 2;
  width: min(470px, 100%);
  height: calc(100% - 136px);
  min-height: 430px;
}

.prototype-identity {
  position: relative;
  z-index: 3;
  width: 100%;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 11px;
  border: 1px solid rgba(255, 255, 255, .18);
  border-radius: 15px;
  background: rgba(3, 31, 44, .5);
  backdrop-filter: blur(15px);
}
.prototype-identity div { display: grid; }
.prototype-identity strong { font-size: 18px; }
.prototype-identity small { margin-top: 2px; color: #a9d6d7; }
.prototype-online-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #54e3b7;
  box-shadow: 0 0 12px #54e3b7;
}

.prototype-state {
  position: relative;
  z-index: 3;
  width: 100%;
  min-height: 52px;
  padding-top: 9px;
  display: grid;
  gap: 2px;
}
.prototype-state strong { color: #c9f3ed; font-size: 12px; letter-spacing: .12em; }
.prototype-state span { color: #9fd7d7; font-size: 11px; line-height: 1.45; }

.prototype-human-panel[data-state='listening'] .prototype-human-orbit {
  border-color: rgba(247, 179, 95, .82);
  animation-duration: 8s;
}
.prototype-human-panel[data-state='listening']::after { background: rgba(247, 179, 95, .28); }
.prototype-human-panel[data-state='thinking']::after { background: rgba(61, 181, 239, .32); }
.prototype-human-panel[data-state='answering']::after,
.prototype-human-panel[data-state='speaking']::after { background: rgba(36, 226, 197, .44); }
.prototype-human-panel[data-state='error'] {
  background-color: #4f3844;
  background-image: radial-gradient(circle at 50% 34%, #99615c, #4f3844 52%, #2a2938);
  box-shadow: 0 26px 64px rgba(83, 43, 47, .26);
}
.prototype-human-panel[data-state='error'] .prototype-human-orbit { animation-play-state: paused; }
.prototype-human-panel[data-state='error'] .prototype-online-dot {
  background: #f0a49c;
  box-shadow: 0 0 12px #f0a49c;
}

@keyframes prototype-orbit-turn {
  to { transform: translate(-50%, -56%) rotate(360deg); }
}

@media (max-width: 720px) {
  .prototype-human-panel { min-height: 620px; padding: 20px; }
  .prototype-portrait-stage { min-height: 420px; }
}

@media (prefers-reduced-motion: reduce) {
  .prototype-human-panel,
  .prototype-human-panel::after,
  .prototype-human-orbit {
    animation: none !important;
    transition-duration: .001ms !important;
  }
}
</style>
