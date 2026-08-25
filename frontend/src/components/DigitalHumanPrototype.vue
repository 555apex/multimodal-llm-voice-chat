<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  prototypePoseAssets,
  prototypeStateMeta,
  type PrototypeDigitalHumanPose,
  type PrototypeDigitalHumanState,
} from '../types/digitalHumanPrototype'

const props = defineProps<{ state: PrototypeDigitalHumanState }>()
const emit = defineEmits<{
  'ready-change': [ready: boolean]
  'asset-error': [pose: PrototypeDigitalHumanPose]
}>()

const poseEntries = Object.entries(prototypePoseAssets) as Array<
  [PrototypeDigitalHumanPose, string]
>
const failedPoses = ref<PrototypeDigitalHumanPose[]>([])
const ready = ref(false)

const stateMeta = computed(() => prototypeStateMeta[props.state])
const requestedPose = computed(() => stateMeta.value.pose)
const renderedPose = computed<PrototypeDigitalHumanPose>(() => (
  failedPoses.value.includes(requestedPose.value) ? 'idle' : requestedPose.value
))
const usingFallback = computed(() => renderedPose.value !== requestedPose.value)
const idleUnavailable = computed(() => failedPoses.value.includes('idle'))

function markAssetFailed(pose: PrototypeDigitalHumanPose) {
  if (failedPoses.value.includes(pose)) return
  failedPoses.value = [...failedPoses.value, pose]
  emit('asset-error', pose)
}

function preloadAsset(pose: PrototypeDigitalHumanPose, src: string) {
  return new Promise<void>((resolve) => {
    const image = new Image()
    image.onload = () => resolve()
    image.onerror = () => {
      markAssetFailed(pose)
      resolve()
    }
    image.src = src
  })
}

onMounted(async () => {
  await Promise.all(poseEntries.map(([pose, src]) => preloadAsset(pose, src)))
  ready.value = true
  emit('ready-change', true)
})
</script>

<template>
  <aside
    class="prototype-human-panel"
    :data-state="state"
    :data-pose="renderedPose"
    :data-fallback="usingFallback ? 'true' : 'false'"
  >
    <div class="prototype-human-orbit" aria-hidden="true"><i></i><i></i><i></i></div>

    <div class="prototype-portrait-stage" aria-hidden="true">
      <img
        v-for="([pose, src]) in poseEntries"
        :key="pose"
        class="prototype-portrait-layer"
        :class="{ 'is-active': pose === renderedPose && !idleUnavailable }"
        :data-pose="pose"
        :src="src"
        alt=""
        draggable="false"
        @error="markAssetFailed(pose)"
      />
      <div class="prototype-speaking-wave"><i></i><i></i><i></i><i></i></div>
      <div v-if="!ready" class="prototype-loading">正在预载姿态资产…</div>
      <div v-else-if="idleUnavailable" class="prototype-loading error">人物资产暂时不可用</div>
    </div>

    <div class="prototype-identity">
      <span class="prototype-online-dot" aria-hidden="true"></span>
      <div>
        <strong>路智通</strong>
        <small>公路交通智能助手 · 姿态原型</small>
      </div>
    </div>

    <div class="prototype-state" aria-live="polite">
      <strong>{{ stateMeta.label }}</strong>
      <span>{{ stateMeta.detail }}</span>
      <small v-if="usingFallback">姿态加载失败，已回退至待命图</small>
    </div>
  </aside>
</template>

<style scoped>
.prototype-human-panel {
  position: relative;
  min-height: 0;
  padding: 30px 32px 26px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  color: white;
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(3, 31, 45, .16), rgba(3, 26, 38, .8)),
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
  bottom: 142px;
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
  height: calc(100% - 142px);
  min-height: 470px;
}

.prototype-portrait-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: bottom center;
  opacity: 0;
  visibility: hidden;
  transform-origin: center bottom;
  filter: drop-shadow(0 20px 34px rgba(0, 15, 26, .5));
  transition: opacity 200ms ease, visibility 0s linear 200ms;
  user-select: none;
}

.prototype-portrait-layer.is-active {
  opacity: 1;
  visibility: visible;
  animation: prototype-human-breathe 4.4s ease-in-out infinite;
  transition-delay: 0s;
}

.prototype-speaking-wave {
  position: absolute;
  top: 43%;
  right: 8%;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 200ms ease;
}

.prototype-speaking-wave i {
  width: 3px;
  height: 10px;
  border-radius: 4px;
  background: #70f0d6;
  animation: prototype-voice-wave .8s ease-in-out infinite alternate;
}
.prototype-speaking-wave i:nth-child(2) { animation-delay: .15s; }
.prototype-speaking-wave i:nth-child(3) { animation-delay: .3s; }
.prototype-speaking-wave i:nth-child(4) { animation-delay: .45s; }

.prototype-loading {
  position: absolute;
  left: 50%;
  top: 50%;
  min-width: 180px;
  padding: 9px 12px;
  color: #c8e6e5;
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 10px;
  background: rgba(3, 31, 44, .64);
  transform: translate(-50%, -50%);
  text-align: center;
  font-size: 12px;
}
.prototype-loading.error { color: #ffd5d1; }

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
  min-height: 54px;
  padding-top: 9px;
  display: grid;
  gap: 2px;
}
.prototype-state strong { color: #c9f3ed; font-size: 12px; letter-spacing: .12em; }
.prototype-state span { color: #9fd7d7; font-size: 11px; line-height: 1.45; }
.prototype-state small { color: #ffd09a; font-size: 10px; }

.prototype-human-panel[data-state='listening'] .prototype-human-orbit {
  border-color: rgba(247, 179, 95, .82);
  animation-duration: 8s;
}
.prototype-human-panel[data-state='listening']::after { background: rgba(247, 179, 95, .26); }
.prototype-human-panel[data-state='speaking'] .prototype-speaking-wave { opacity: 1; }
.prototype-human-panel[data-state='speaking']::after { background: rgba(36, 226, 197, .44); }
.prototype-human-panel[data-state='error'] {
  background-color: #4f3844;
  background-image: radial-gradient(circle at 50% 34%, #99615c, #4f3844 52%, #2a2938);
  box-shadow: 0 26px 64px rgba(83, 43, 47, .26);
}
.prototype-human-panel[data-state='error'] .prototype-portrait-layer.is-active { animation: none; }
.prototype-human-panel[data-state='error'] .prototype-human-orbit { animation-play-state: paused; }
.prototype-human-panel[data-state='error'] .prototype-online-dot {
  background: #f0a49c;
  box-shadow: 0 0 12px #f0a49c;
}

@keyframes prototype-orbit-turn {
  to { transform: translate(-50%, -56%) rotate(360deg); }
}
@keyframes prototype-human-breathe {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-4px) scale(1.008); }
}
@keyframes prototype-voice-wave {
  to { height: 31px; }
}

@media (prefers-reduced-motion: reduce) {
  .prototype-human-panel,
  .prototype-human-panel::after,
  .prototype-human-orbit,
  .prototype-portrait-layer,
  .prototype-portrait-layer.is-active,
  .prototype-speaking-wave,
  .prototype-speaking-wave i {
    animation: none !important;
    transition-duration: .001ms !important;
  }
}
</style>
