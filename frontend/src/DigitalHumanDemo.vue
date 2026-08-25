<script setup lang="ts">
import { computed, ref } from 'vue'
import DigitalHumanPrototype from './components/DigitalHumanPrototype.vue'
import {
  prototypeStateMeta,
  type PrototypeDigitalHumanPose,
  type PrototypeDigitalHumanState,
} from './types/digitalHumanPrototype'

const selectedState = ref<PrototypeDigitalHumanState>('idle')
const assetsReady = ref(false)
const failedAssets = ref<PrototypeDigitalHumanPose[]>([])

const states: PrototypeDigitalHumanState[] = ['idle', 'listening', 'speaking', 'error']
const activeMeta = computed(() => prototypeStateMeta[selectedState.value])

function handleAssetError(pose: PrototypeDigitalHumanPose) {
  if (!failedAssets.value.includes(pose)) failedAssets.value.push(pose)
}
</script>

<template>
  <main class="prototype-demo-page">
    <header class="prototype-demo-header">
      <div class="prototype-demo-brand">
        <span>路</span>
        <div>
          <strong>“路智通”轻量姿态交互原型</strong>
          <small>图 1 · 桌面评审页 · 不连接真实业务状态</small>
        </div>
      </div>
      <a href="/">返回业务页面</a>
    </header>

    <section class="prototype-demo-layout">
      <DigitalHumanPrototype
        :state="selectedState"
        @ready-change="assetsReady = $event"
        @asset-error="handleAssetError"
      />

      <section class="prototype-control-panel" aria-labelledby="prototype-control-title">
        <p class="prototype-eyebrow">STATE PROTOTYPE</p>
        <h1 id="prototype-control-title">手动切换数字人状态</h1>
        <p class="prototype-lead">
          使用三张透明姿态图覆盖现有四状态。原型中的 listening 同时表达聆听、研判和处理中。
        </p>

        <div class="prototype-state-buttons" role="group" aria-label="数字人状态">
          <button
            v-for="state in states"
            :key="state"
            type="button"
            :class="{ active: selectedState === state }"
            :aria-pressed="selectedState === state"
            :disabled="!assetsReady"
            @click="selectedState = state"
          >
            <code>{{ state }}</code>
            <span>{{ prototypeStateMeta[state].label }}</span>
          </button>
        </div>

        <article class="prototype-current-state" :data-state="selectedState">
          <div>
            <span>当前状态</span>
            <code>{{ selectedState }}</code>
          </div>
          <dl>
            <div><dt>人物姿态</dt><dd>{{ activeMeta.poseLabel }}</dd></div>
            <div><dt>状态文案</dt><dd>{{ activeMeta.label }}</dd></div>
            <div><dt>动态反馈</dt><dd>{{ activeMeta.detail }}</dd></div>
          </dl>
        </article>

        <div class="prototype-architecture-note">
          <strong>本原型的技术边界</strong>
          <ul>
            <li>Vue 状态切换与 200ms 图片交叉淡入</li>
            <li>CSS 呼吸、轨道、声波和异常色</li>
            <li>不包含口型、眨眼、音频或 Agent 状态接入</li>
          </ul>
        </div>

        <p v-if="!assetsReady" class="prototype-load-status" aria-live="polite">正在预载三张姿态图…</p>
        <p v-else-if="failedAssets.length" class="prototype-load-status error" aria-live="polite">
          {{ failedAssets.join('、') }} 姿态加载失败，组件将自动回退到待命图。
        </p>
        <p v-else class="prototype-load-status ready" aria-live="polite">三张姿态图已加载，可以开始评审。</p>
      </section>
    </section>
  </main>
</template>
