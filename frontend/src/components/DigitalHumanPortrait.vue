<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  digitalHumanMouthAssets,
  digitalHumanPoseAssets,
  digitalHumanStateMeta,
  resolveDigitalHumanMouthWeights,
  type DigitalHumanMode,
  type DigitalHumanMouthShape,
  type DigitalHumanPose,
} from '../types/digitalHuman'

const props = withDefaults(defineProps<{
  mode: DigitalHumanMode
  speechLevel?: number
  previewSpeech?: boolean
  alt?: string
}>(), {
  speechLevel: 0,
  previewSpeech: false,
  alt: '路智通应急交通数字助手',
})

const emit = defineEmits<{
  'ready-change': [ready: boolean]
  'asset-error': [pose: DigitalHumanPose]
}>()

const poseEntries = Object.entries(digitalHumanPoseAssets) as Array<
  [DigitalHumanPose, (typeof digitalHumanPoseAssets)[DigitalHumanPose]]
>
const mouthEntries = Object.entries(digitalHumanMouthAssets) as Array<
  [DigitalHumanMouthShape, (typeof digitalHumanMouthAssets)[DigitalHumanMouthShape]]
>
const resolvedSources = reactive<Record<DigitalHumanPose, string>>({
  idle: digitalHumanPoseAssets.idle.webp,
  thinking: digitalHumanPoseAssets.thinking.webp,
  explaining: digitalHumanPoseAssets.explaining.webp,
})
const resolvedMouthSources = reactive<Record<DigitalHumanMouthShape, string>>({
  closed: digitalHumanMouthAssets.closed.webp,
  half: digitalHumanMouthAssets.half.webp,
  open: digitalHumanMouthAssets.open.webp,
})
const failedPoses = ref<DigitalHumanPose[]>([])
const mouthUnavailable = ref(false)
const ready = ref(false)

const requestedPose = computed(() => digitalHumanStateMeta[props.mode].pose)
const renderedPose = computed<DigitalHumanPose>(() => (
  failedPoses.value.includes(requestedPose.value) ? 'idle' : requestedPose.value
))
const usingFallback = computed(() => renderedPose.value !== requestedPose.value)
const idleUnavailable = computed(() => failedPoses.value.includes('idle'))
const previewingSpeech = computed(() => props.mode === 'speaking' && props.previewSpeech)
const mouthWeights = computed(() => resolveDigitalHumanMouthWeights(
  props.mode === 'speaking' ? props.speechLevel : 0,
))

function markAssetFailed(pose: DigitalHumanPose) {
  if (failedPoses.value.includes(pose)) return
  failedPoses.value = [...failedPoses.value, pose]
  emit('asset-error', pose)
}

function markMouthFailed() {
  if (mouthUnavailable.value) return
  mouthUnavailable.value = true
  emit('asset-error', 'explaining')
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

async function preloadMouth(shape: DigitalHumanMouthShape) {
  const asset = digitalHumanMouthAssets[shape]
  if (await loadSource(asset.webp)) {
    resolvedMouthSources[shape] = asset.webp
    return
  }
  if (await loadSource(asset.png)) {
    resolvedMouthSources[shape] = asset.png
    return
  }
  markMouthFailed()
}

async function handleRuntimeError(pose: DigitalHumanPose) {
  const asset = digitalHumanPoseAssets[pose]
  if (resolvedSources[pose] === asset.webp && await loadSource(asset.png)) {
    resolvedSources[pose] = asset.png
    return
  }
  markAssetFailed(pose)
}

async function handleMouthRuntimeError(shape: DigitalHumanMouthShape) {
  const asset = digitalHumanMouthAssets[shape]
  if (resolvedMouthSources[shape] === asset.webp && await loadSource(asset.png)) {
    resolvedMouthSources[shape] = asset.png
    return
  }
  markMouthFailed()
}

function mouthStyle(shape: DigitalHumanMouthShape) {
  if (previewingSpeech.value) return undefined
  return { opacity: mouthWeights.value[shape].toFixed(4) }
}

onMounted(async () => {
  await Promise.all([
    ...poseEntries.map(([pose]) => preloadPose(pose)),
    ...mouthEntries.map(([shape]) => preloadMouth(shape)),
  ])
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
    :data-mouth-fallback="mouthUnavailable ? 'true' : 'false'"
    :data-preview-speech="previewingSpeech ? 'true' : 'false'"
    role="img"
    :aria-label="`${alt}，${digitalHumanStateMeta[mode].label}`"
    :aria-busy="!ready"
  >
    <span class="digital-human-aura" aria-hidden="true"></span>
    <span class="digital-human-scan" aria-hidden="true"></span>

    <div class="digital-human-state-motion">
      <div
        v-for="([pose]) in poseEntries"
        :key="pose"
        class="digital-human-portrait-layer"
        :class="{ 'is-active': pose === renderedPose && !idleUnavailable }"
        :data-pose="pose"
      >
        <div class="digital-human-pose-motion">
          <template v-if="pose === 'explaining'">
            <img
              v-if="mouthUnavailable"
              class="digital-human-portrait-image"
              :src="resolvedSources.explaining"
              alt=""
              draggable="false"
              @error="handleRuntimeError('explaining')"
            />
            <template v-else>
              <img
                v-for="([shape]) in mouthEntries"
                :key="shape"
                class="digital-human-portrait-image digital-human-mouth-frame"
                :class="`mouth-${shape}`"
                :data-mouth="shape"
                :src="resolvedMouthSources[shape]"
                :style="mouthStyle(shape)"
                alt=""
                draggable="false"
                @error="handleMouthRuntimeError(shape)"
              />
            </template>
          </template>
          <img
            v-else
            class="digital-human-portrait-image"
            :src="resolvedSources[pose]"
            alt=""
            draggable="false"
            @error="handleRuntimeError(pose)"
          />
        </div>
      </div>
    </div>

    <div class="digital-human-speaking-wave" aria-hidden="true">
      <i></i><i></i><i></i><i></i>
    </div>
    <div v-if="!ready" class="digital-human-loading">正在预载人物姿态…</div>
    <div v-else-if="idleUnavailable" class="digital-human-loading error">人物资产暂时不可用</div>
    <span v-if="usingFallback" class="digital-human-sr-only">当前姿态加载失败，已回退待命图。</span>
    <span v-if="mouthUnavailable" class="digital-human-sr-only">嘴型资产加载失败，已回退静态讲解图。</span>
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
  contain: layout paint;
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
  transition: border-color 320ms ease, box-shadow 320ms ease, background 320ms ease;
}

.digital-human-scan {
  position: absolute;
  z-index: 4;
  left: 8%;
  right: 8%;
  top: 28%;
  height: 2px;
  opacity: 0;
  background: linear-gradient(90deg, transparent, rgba(99, 239, 224, .9), transparent);
  box-shadow: 0 0 14px rgba(99, 239, 224, .7);
  pointer-events: none;
}

.digital-human-state-motion {
  position: absolute;
  z-index: 2;
  inset: 0;
  transform-origin: center bottom;
  transition: transform 360ms cubic-bezier(.22, 1, .36, 1);
}

.digital-human-portrait-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
  opacity: 0;
  transform: translateY(6px) scale(.985);
  transform-origin: center bottom;
  filter: blur(1.5px) drop-shadow(0 14px 22px rgba(0, 10, 29, .42));
  transition:
    opacity 600ms cubic-bezier(.22, 1, .36, 1),
    transform 600ms cubic-bezier(.22, 1, .36, 1),
    filter 400ms ease;
  pointer-events: none;
  will-change: opacity, transform;
}

.digital-human-portrait-layer.is-active {
  z-index: 2;
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0) drop-shadow(0 14px 22px rgba(0, 10, 29, .42));
}

.digital-human-pose-motion,
.digital-human-portrait-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.digital-human-pose-motion {
  transform-origin: center bottom;
}

.digital-human-portrait-image {
  object-fit: contain;
  object-position: bottom center;
  user-select: none;
}

.digital-human-portrait-layer[data-pose='idle'] .digital-human-pose-motion {
  animation: guardian-breathe 4.4s ease-in-out infinite;
}

.digital-human-portrait-layer[data-pose='thinking'] .digital-human-pose-motion {
  animation: guardian-thinking 3.4s ease-in-out infinite;
}

.digital-human-portrait-layer[data-pose='explaining'] .digital-human-pose-motion {
  animation: guardian-explaining 4s ease-in-out infinite;
}

.digital-human-portrait-layer:not(.is-active) .digital-human-pose-motion {
  animation-play-state: paused;
}

.digital-human-mouth-frame {
  opacity: 0;
  transition: opacity 70ms linear;
  will-change: opacity;
}

.digital-human-mouth-frame.mouth-closed { opacity: 1; }

.digital-human-portrait-stage[data-mode='listening'] .digital-human-state-motion {
  transform: translateY(1px) rotate(-.35deg) scale(1.004);
}

.digital-human-portrait-stage[data-mode='thinking'] .digital-human-state-motion {
  transform: translateY(-2px);
}

.digital-human-portrait-stage[data-mode='speaking'] .digital-human-state-motion {
  transform: translateY(-1px) scale(1.002);
}

.digital-human-portrait-stage[data-mode='listening'] .digital-human-aura {
  border-color: rgba(249, 171, 71, .78);
  background: radial-gradient(circle, rgba(249, 171, 71, .24), transparent 68%);
  box-shadow: 0 0 28px rgba(249, 171, 71, .36);
  animation: guardian-listen-ring 1.4s ease-in-out infinite;
}

.digital-human-portrait-stage[data-mode='thinking'] .digital-human-scan {
  opacity: .8;
  animation: guardian-scan 2.4s ease-in-out infinite;
}

.digital-human-portrait-stage[data-preview-speech='true'] .mouth-closed {
  animation: guardian-mouth-closed-preview .72s ease-in-out infinite;
}

.digital-human-portrait-stage[data-preview-speech='true'] .mouth-half {
  animation: guardian-mouth-half-preview .72s ease-in-out infinite;
}

.digital-human-portrait-stage[data-preview-speech='true'] .mouth-open {
  animation: guardian-mouth-open-preview .72s ease-in-out infinite;
}

.digital-human-speaking-wave {
  position: absolute;
  z-index: 5;
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



.digital-human-loading {
  position: absolute;
  z-index: 6;
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
@keyframes guardian-thinking {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
@keyframes guardian-explaining {
  0%, 100% { transform: translateY(0) rotate(-.2deg); }
  50% { transform: translateY(-2px) rotate(.2deg); }
}
@keyframes guardian-listen-ring {
  50% { transform: translateX(-50%) scale(1.05); opacity: .74; }
}
@keyframes guardian-scan {
  0%, 100% { transform: translateY(-30px); opacity: 0; }
  35%, 70% { opacity: .82; }
  60% { transform: translateY(82px); }
}
@keyframes guardian-mouth-closed-preview {
  0%, 16%, 100% { opacity: 1; }
  36%, 72% { opacity: 0; }
}
@keyframes guardian-mouth-half-preview {
  0%, 100% { opacity: 0; }
  18%, 46%, 76% { opacity: 1; }
  62% { opacity: .22; }
}
@keyframes guardian-mouth-open-preview {
  0%, 38%, 84%, 100% { opacity: 0; }
  60%, 72% { opacity: 1; }
}
@keyframes guardian-voice-wave { to { height: 27px; } }

@media (prefers-reduced-motion: reduce) {
  .digital-human-aura,
  .digital-human-scan,
  .digital-human-state-motion,
  .digital-human-pose-motion,
  .digital-human-speaking-wave,
  .digital-human-speaking-wave i {
    animation: none !important;
    transition-duration: .001ms !important;
  }

  .digital-human-portrait-layer {
    animation: none !important;
    transform: none !important;
    filter: drop-shadow(0 14px 22px rgba(0, 10, 29, .42)) !important;
    transition: opacity 120ms ease !important;
  }

  .digital-human-portrait-stage[data-mode='speaking'] .mouth-closed,
  .digital-human-portrait-stage[data-mode='speaking'] .mouth-open {
    opacity: 0 !important;
    animation: none !important;
  }

  .digital-human-portrait-stage[data-mode='speaking'] .mouth-half {
    opacity: 1 !important;
    animation: none !important;
  }
}
</style>
