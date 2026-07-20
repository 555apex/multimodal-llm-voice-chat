<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { TrafficQueryPayload } from '../types/traffic'

defineProps<{ loading: boolean }>()
const emit = defineEmits<{ submit: [payload: TrafficQueryPayload] }>()

const form = reactive<TrafficQueryPayload>({
  areaCode: '350100',
  roadName: '五四路',
  direction: '南向北',
})
const validationMessage = ref('')

function submit() {
  if (!/^\d{6}$/.test(form.areaCode.trim())) {
    validationMessage.value = '行政区划代码必须是6位数字，例如福州市为350100。'
    return
  }
  if (!form.roadName.trim()) {
    validationMessage.value = '请输入道路名称。'
    return
  }
  validationMessage.value = ''
  emit('submit', { ...form })
}
</script>

<template>
  <form class="query-form" @submit.prevent="submit">
    <div class="field-grid">
      <label>
        <span>行政区划代码</span>
        <input v-model="form.areaCode" maxlength="6" inputmode="numeric" placeholder="例如 350100" />
      </label>
      <label>
        <span>道路名称</span>
        <input v-model="form.roadName" maxlength="100" placeholder="例如 五四路" />
      </label>
      <label>
        <span>行驶方向（选填）</span>
        <input v-model="form.direction" maxlength="50" placeholder="例如 南向北" />
      </label>
    </div>

    <p v-if="validationMessage" class="form-error">{{ validationMessage }}</p>

    <button type="submit" :disabled="loading">
      <span v-if="loading" class="spinner" aria-hidden="true"></span>
      {{ loading ? '正在查询…' : '查询实时路况' }}
    </button>
  </form>
</template>
