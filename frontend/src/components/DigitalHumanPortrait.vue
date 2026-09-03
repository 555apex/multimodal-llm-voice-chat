<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  digitalHumanPoseAssets,
  digitalHumanStateMeta,
  type DigitalHumanMode,
  type DigitalHumanPose,
} from '../types/digitalHuman'

const props = withDefaults(defineProps<{
  mode: DigitalHumanMode
  alt?: string
}>(), {
  alt: '路智通应急交通数字助手',
})

const emit = defineEmits<{
  'ready-change': [ready: boolean]
  'asset-error': [pose: DigitalHumanPose]
}>()

const poseEntries = Object.entries(digitalHumanPoseAssets) as Array<
  [DigitalHumanPose, (typeof digitalHumanPoseAssets)[DigitalHumanPose]]
>
const resolvedSources = reactive<Record<DigitalHumanPose, string>>({
  idle: digitalHumanPoseAssets.idle.webp,
  thinking: digitalHumanPoseAssets.thinking.webp,
  explaining: digitalHumanPoseAssets.explaining.webp,
})
const failedPoses = ref<DigitalHumanPose[]>([])
const ready = ref(false)

const requestedPose = computed(() => digitalHumanStateMeta[props.mode].pose)
const renderedPose = computed<DigitalHumanPose>(() => (
  failedPoses.value.includes(requestedPose.value) ? 'idle' : requestedPose.value
))
const usingFallback = computed(() => renderedPose.value !== requestedPose.value)
const idleUnavailable = computed(() => failedPoses.value.includes('idle'))

function markAssetFailed(pose: DigitalHumanPose) {
  if (failedPoses.value.includes(pose)) return
  failedPoses.value = [...failedPoses.value, pose]
  emit('asset-error', pose)
}

function loadSource(src: string) {
  return new Promise<boolean>((resolve) => {
    const image = new Image()
    image.onload = () => resolve(true)
    image.onerror = () => resolve(false)
    image.src = src
  })
}

async function preloadPose(pose: DigitalHumanPose) {
  const asset = digitalHumanPoseAssets[pose]
  if (await loadSource(asset.webp)) {
    resolvedSources[pose] = asset.webp
    return
  }
  if (await loadSource(asset.png)) {
    resolvedSources[pose] = asset.png
    return
  }
  markAssetFailed(pose)
}

async function handleRuntimeError(pose: DigitalHumanPose) {
  const asset = digitalHumanPoseAssets[pose]
  if (resolvedSources[pose] === asset.webp && await loadSource(asset.png)) {
    resolvedSources[pose] = asset.png
    return
  }
  markAssetFailed(pose)
}

onMounted(async () => {
  await Promise.all(poseEntries.map(([pose]) => preloadPose(pose)))
  ready.value = true
  emit('ready-change', true)
})
</script>

<template>
  <div
    class="digital-human-portrait-stage"
    :data-mode="mode"
    :data-pose="renderedPose"
    :data-fallback="usingFallback ? 'true' : 'false'"
    role="img"
    :aria-label="`${alt}，${digitalHumanStateMeta[mode].label}`"
    :aria-busy="!ready"
  >
    <span class="digital-human-aura" aria-hidden="true"></span>
    <span class="digital-human-scan" aria-hidden="true"></span>
    <img
      v-for="([pose]) in poseEntries"
      :key="pose"
      class="digital-human-portrait-layer"
      :class="{ 'is-active': pose === renderedPose && !idleUnavailable }"
      :data-pose="pose"
      :src="resolvedSources[pose]"
      alt=""
      draggable="false"
      @error="handleRuntimeError(pose)"
    />
    <div class="digital-human-speaking-wave" aria-hidden="true">
      <i></i><i></i><i></i><i></i>
    </div>
    <div v-if="!ready" class="digital-human-loading">正在预载人物姿态…</div>
    <div v-else-if="idleUnavailable" class="digital-human-loading error">人物资产暂时不可用</div>
    <span v-if="usingFallback" class="digital-human-sr-only">当前姿态加载失败，已回退待命图。</span>
  </div>
</template>

<style scoped>
.digital-human-portrait-stage {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  overflow: hidden;
  isolation: isolate;
}

.digital-human-aura {
  position: absolute;
  z-index: 0;
  left: 50%;
  bottom: 5%;
  width: 76%;
  aspect-ratio: 1;
  border: 1px solid rgba(81, 228, 220, .28);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(55, 222, 205, .2), transparent 68%);
  box-shadow: 0 0 26px rgba(45, 219, 207, .18);
  transform: translateX(-50%);
  transition: border-color 200ms ease, box-shadow 200ms ease, background 200ms ease;
}

.digital-human-scan {
  position: absolute;
  z-index: 3;
  left: 8%;
  right: 8%;
  top: 28%;
  height: 2px;
  opacity: 0;
  background: linear-gradient(90deg, transparent, rgba(99, 239, 224, .9), transparent);
  box-shadow: 0 0 14px rgba(99, 239, 224, .7);
  pointer-events: none;
}

.digital-human-portrait-layer {
  position: absolute;
  z-index: 2;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: bottom center;
  opacity: 0;
  visibility: hidden;
  transform-origin: center bottom;
  filter: drop-shadow(0 14px 22px rgba(0, 10, 29, .42));
  transition: opacity 200ms ease, visibility 0s linear 200ms;
  user-select: none;
}

.digital-human-portrait-layer.is-active {
  opacity: 1;
  visibility: visible;
  animation: guardian-breathe 4.4s ease-in-out infinite;
  transition-delay: 0s;
}

.digital-human-portrait-stage[data-mode='listening'] .digital-human-aura {
  border-color: rgba(249, 171, 71, .78);
  background: radial-gradient(circle, rgba(249, 171, 71, .24), transparent 68%);
  box-shadow: 0 0 28px rgba(249, 171, 71, .36);
  animation: guardian-listen-ring 1.4s ease-in-out infinite;
}

.digital-human-portrait-stage[data-mode='listening'] .digital-human-portrait-layer.is-active {
  animation: guardian-listen 2.2s ease-in-out infinite;
}

.digital-human-portrait-stage[data-mode='thinking'] .digital-human-portrait-layer.is-active {
  animation: guardian-thinking 3.4s ease-in-out infinite;
}

.digital-human-portrait-stage[data-mode='thinking'] .digital-human-scan {
  opacity: .8;
  animation: guardian-scan 2.4s ease-in-out infinite;
}

.digital-human-portrait-stage[data-mode='answering'] .digital-human-portrait-layer.is-active {
  animation: guardian-nod 2.6s ease-in-out infinite;
}

.digital-human-portrait-stage[data-mode='speaking'] .digital-human-portrait-layer.is-active {
  animation: guardian-speaking 2.1s ease-in-out infinite;
}

.digital-human-speaking-wave {
  position: absolute;
  z-index: 4;
  top: 42%;
  right: 3%;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 3px;
  opacity: 0;
  transition: opacity 200ms ease;
}

.digital-human-speaking-wave i {
  width: 3px;
  height: 9px;
  border-radius: 4px;
  background: #70f0df;
  box-shadow: 0 0 8px rgba(112, 240, 223, .65);
  animation: guardian-voice-wave .8s ease-in-out infinite alternate;
}
.digital-human-speaking-wave i:nth-child(2) { animation-delay: .15s; }
.digital-human-speaking-wave i:nth-child(3) { animation-delay: .3s; }
.digital-human-speaking-wave i:nth-child(4) { animation-delay: .45s; }
.digital-human-portrait-stage[data-mode='speaking'] .digital-human-speaking-wave { opacity: 1; }

.digital-human-portrait-stage[data-mode='error'] .digital-human-aura {
  border-color: rgba(236, 108, 99, .7);
  background: radial-gradient(circle, rgba(236, 108, 99, .2), transparent 68%);
  box-shadow: 0 0 24px rgba(236, 108, 99, .28);
}

.digital-human-portrait-stage[data-mode='error'] .digital-human-portrait-layer.is-active,
.digital-human-portrait-stage[data-mode='error'] .digital-human-aura {
  animation: none;
}

.digital-human-loading {
  position: absolute;
  z-index: 5;
  left: 50%;
  top: 50%;
  min-width: 152px;
  padding: 8px 10px;
  color: #c8e6e5;
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 10px;
  background: rgba(3, 31, 44, .72);
  transform: translate(-50%, -50%);
  text-align: center;
  font-size: 11px;
}
.digital-human-loading.error { color: #ffd5d1; }

.digital-human-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@keyframes guardian-breathe {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-3px) scale(1.008); }
}
@keyframes guardian-listen {
  0%, 100% { transform: translateY(0) rotate(0); }
  50% { transform: translateY(1px) rotate(-.7deg) scale(1.008); }
}
@keyframes guardian-listen-ring {
  50% { transform: translateX(-50%) scale(1.05); opacity: .74; }
}
@keyframes guardian-thinking {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
@keyframes guardian-scan {
  0%, 100% { transform: translateY(-30px); opacity: 0; }
  35%, 70% { opacity: .82; }
  60% { transform: translateY(82px); }
}
@keyframes guardian-nod {
  0%, 58%, 100% { transform: translateY(0) rotate(0); }
  68% { transform: translateY(2px) rotate(.7deg); }
  78% { transform: translateY(0) rotate(0); }
}
@keyframes guardian-speaking {
  0%, 100% { transform: translateY(0) rotate(-.35deg); }
  50% { transform: translateY(-2px) rotate(.35deg); }
}
@keyframes guardian-voice-wave { to { height: 27px; } }

@media (prefers-reduced-motion: reduce) {
  .digital-human-aura,
  .digital-human-scan,
  .digital-human-portrait-layer,
  .digital-human-portrait-layer.is-active,
  .digital-human-speaking-wave,
  .digital-human-speaking-wave i {
    animation: none !important;
    transition-duration: .001ms !important;
  }
}
</style>
