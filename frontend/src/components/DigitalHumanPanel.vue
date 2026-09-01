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
      <div class="human-state">
        {{ state === 'speaking' ? '正在朗读' : state === 'listening' ? '正在聆听或处理' : state === 'error' ? '处理异常' : '在线待命' }}
      </div>
    </div>
  </aside>
</template>
