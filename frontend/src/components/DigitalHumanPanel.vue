<script setup lang="ts">
import DigitalHumanPortrait from './DigitalHumanPortrait.vue'
import type { DigitalHumanSignal } from '../types/digitalHuman'

defineProps<{ signal: DigitalHumanSignal }>()
</script>

<template>
  <aside class="human-panel" :data-state="signal.mode">
    <div class="human-orbit" aria-hidden="true"><i></i><i></i><i></i></div>
    <div class="human-portrait-wrap">
      <DigitalHumanPortrait class="human-digital-portrait" :mode="signal.mode" />
    </div>
    <div class="human-details">
      <div class="human-identity">
        <span class="online-dot"></span>
        <div>
          <strong>路智通</strong>
          <small>应急交通数字助手</small>
        </div>
      </div>
      <p class="human-capability-title">我可以为您提供</p>
      <ul class="human-capabilities">
        <li><span aria-hidden="true">◆</span>实时路况问询</li>
        <li><span aria-hidden="true">◆</span>区域交通研判</li>
        <li><span aria-hidden="true">◆</span>应急工单辅助</li>
        <li><span aria-hidden="true">◆</span>语音智能交互</li>
      </ul>
      <div class="human-state" aria-live="polite">{{ signal.statusLabel }}</div>
    </div>
  </aside>
</template>
