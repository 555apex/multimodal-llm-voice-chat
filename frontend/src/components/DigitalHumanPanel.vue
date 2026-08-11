<script setup lang="ts">
defineProps<{ state: 'idle' | 'listening' | 'speaking' | 'error' }>()
</script>

<template>
  <aside class="human-panel" :data-state="state">
    <div class="human-orbit" aria-hidden="true"><i></i><i></i><i></i></div>
    <div class="human-portrait-wrap">
      <img
        class="human-portrait"
        src="/emergency-agent-avatar.png"
        alt="应急交通数字助手"
      />
      <div class="human-speaking-wave" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
    </div>
    <div class="human-identity">
      <span class="online-dot"></span>
      <div>
        <strong>路智通</strong>
        <small>应急交通数字助手</small>
      </div>
    </div>
    <div class="human-state">
      {{ state === 'speaking' ? '正在朗读' : state === 'listening' ? '正在聆听或处理' : state === 'error' ? '处理异常' : '在线待命' }}
    </div>
  </aside>
</template>
