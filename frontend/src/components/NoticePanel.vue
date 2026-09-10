<script setup lang="ts">
import { ref } from 'vue'
import { useEmergencyStore } from '../stores/emergency'
import { fetchWorkflowDetail, releaseWorkflowResources } from '../api/workflowApi'
import type { EmergencyWorkflowItem } from '../types/dispatch'
import WorkflowHistoryPanel from './WorkflowHistoryPanel.vue'

const store = useEmergencyStore()
const expanded = ref('')
const details = ref<Record<string, EmergencyWorkflowItem>>({})
const errors = ref<Record<string, string>>({})
const busy = ref<Record<string, boolean>>({})
const quickForms = ref<Record<string, { reason: string; confirming: boolean; version: number }>>({})
const attempts = new Map<string, { reason: string; version: number; key: string }>()
const names: Record<string, string> = {
  DT01: '崩塌（落石）', DT02: '滑坡', DT03: '泥石流', DT04: '沉陷与塌陷', DT05: '水毁',
  ET101: '拥堵', ET102: '明火（火灾）', ET103: '抛撒物', ET104: '设备故障',
  ET105: '占用应急车道', ET106: '交通事故', ET107: '异常停车', ET108: '浓雾',
  ET109: '路障', ET110: '施工', ET112: '道路积雪',
}
async function open(id: string) {
  if (expanded.value === id) { expanded.value = ''; return }
  expanded.value = id
  errors.value[id] = ''
  try { details.value[id] = await fetchWorkflowDetail(id) }
  catch (error) { errors.value[id] = error instanceof Error ? error.message : '详情加载失败，请收起后重试' }
}
async function prepareQuickRelease(id: string) {
  if (busy.value[id]) return
  busy.value[id] = true
  errors.value[id] = ''
  try {
    const latest = await fetchWorkflowDetail(id)
    details.value[id] = latest
    if (!latest.canReleaseResources) {
      errors.value[id] = latest.resourceReleaseUnavailableReason || '当前资源已归还或无需归还，请刷新列表。'
      return
    }
    quickForms.value[id] = { reason: '', confirming: false, version: latest.workflowVersion }
  } catch (error) {
    errors.value[id] = error instanceof Error ? error.message : '资源状态查询失败，请重试'
  } finally { busy.value[id] = false }
}
function confirmQuickRelease(id: string) {
  const form = quickForms.value[id]
  if (!form?.confirming || !form.reason.trim()) return
  void release(id, form.version, form.reason.trim())
}
async function release(id: string, version: number, reason: string) {
  if (busy.value[id]) return
  busy.value[id] = true
  errors.value[id] = ''
  let attempt = attempts.get(id)
  if (!attempt || attempt.reason !== reason || attempt.version !== version) {
    attempt = { reason, version, key: crypto.randomUUID() }
    attempts.set(id, attempt)
  }
  try {
    details.value[id] = await releaseWorkflowResources(id, reason, version, attempt.key)
    attempts.delete(id)
    delete quickForms.value[id]
    await store.loadNotices()
  } catch (error) {
    errors.value[id] = error instanceof Error ? error.message : '资源归还失败，请重试'
    if ((error as { status?: number }).status === 409) {
      try {
        details.value[id] = await fetchWorkflowDetail(id)
        const form = quickForms.value[id]
        if (form) {
          form.version = details.value[id]!.workflowVersion
          form.confirming = false
        }
        errors.value[id] += '。已刷新最新状态，请核对后重新确认。'
        attempts.delete(id)
      } catch { /* 保留最初错误与填写内容，允许网络恢复后重试。 */ }
    }
  } finally { busy.value[id] = false }
}
</script>

<template>
  <section class="notice-panel">
    <p v-if="store.noticeError" role="alert">{{ store.noticeError }}</p>
    <p v-if="!store.notices">{{ store.noticeLoading ? '正在加载通告…' : '暂无通告' }}</p>
    <p v-else-if="!store.notices.items.length">当前没有{{ store.completionStatus === 'PENDING' ? '未办结' : '已办结' }}记录</p>
    <article v-for="item in store.notices?.items" :key="item.workflowId" class="notice-summary">
      <button class="notice-summary-button" type="button" :aria-expanded="expanded === item.workflowId"
        @click="open(item.workflowId)">
        <strong>{{ names[item.eventType] || item.eventType }} · {{ item.cityName || item.place || '地点待核实' }}</strong>
        <span>{{ item.eventId }} · {{ item.workflowStatus === 'NO_DISPATCH' ? '无需调度' : item.completionStatus === 'PENDING' ? '未办结 · 待归还资源' : '已办结' }}</span>
        <small>{{ item.noticeNumber || '无需调度记录' }} · {{ item.publishedAt ? new Date(item.publishedAt).toLocaleString('zh-CN') : '' }}</small>
        <small>{{ expanded === item.workflowId ? '收起详情 ▴' : '查看详情 ▾' }}</small>
      </button>
      <div v-if="expanded !== item.workflowId && item.workflowStatus === 'PUBLISHED' && item.completionStatus === 'PENDING'"
        class="quick-release-control">
        <p v-if="errors[item.workflowId]" role="alert">{{ errors[item.workflowId] }}</p>
        <template v-if="details[item.workflowId]?.canReleaseResources !== false">
          <button v-if="!quickForms[item.workflowId]" type="button" class="quick-release-button"
            :disabled="!!busy[item.workflowId]" @click="prepareQuickRelease(item.workflowId)">
            {{ busy[item.workflowId] ? '正在处理…' : '归还全部资源' }}
          </button>
          <div v-else class="quick-release-form">
            <label>资源归还原因
              <textarea v-model="quickForms[item.workflowId]!.reason" rows="2" maxlength="500"
                :disabled="!!busy[item.workflowId]" placeholder="例如：现场处置结束，车辆和队伍已撤离归建。"
                @input="quickForms[item.workflowId]!.confirming = false"></textarea>
            </label>
            <p v-if="quickForms[item.workflowId]!.confirming">确认后将归还该工单的全部已调度资源，并移入已办结，请再次确认。</p>
            <div class="quick-release-actions">
              <button type="button" :disabled="!!busy[item.workflowId]"
                @click="delete quickForms[item.workflowId]">取消</button>
              <button v-if="!quickForms[item.workflowId]!.confirming" type="button"
                :disabled="!!busy[item.workflowId] || !quickForms[item.workflowId]!.reason.trim()"
                @click="quickForms[item.workflowId]!.confirming = true">继续</button>
              <button v-else type="button" class="quick-release-confirm"
                :disabled="!!busy[item.workflowId] || !quickForms[item.workflowId]!.reason.trim()"
                @click="confirmQuickRelease(item.workflowId)">确认全部归还</button>
            </div>
          </div>
        </template>
        <p v-else-if="!errors[item.workflowId]">{{ details[item.workflowId]?.resourceReleaseUnavailableReason || '资源已归还，无需重复操作' }}</p>
      </div>
      <template v-if="expanded === item.workflowId">
        <WorkflowHistoryPanel v-if="details[item.workflowId]"
          :history="{ items: [details[item.workflowId]!], total: 1, page: 0, size: 1 }"
          :busy="!!busy[item.workflowId]"
          :error-message="errors[item.workflowId]"
          @release-resources="release" />
        <p v-else>{{ errors[item.workflowId] || '正在加载详情…' }}</p>
      </template>
    </article>
    <nav v-if="store.notices && store.notices.total > store.notices.size">
      <button :disabled="store.noticePage === 0" @click="store.loadNotices(store.noticePage - 1)">上一页</button>
      <span>第 {{ store.noticePage + 1 }} 页 · 共 {{ store.notices.total }} 条</span>
      <button :disabled="(store.noticePage + 1) * store.notices.size >= store.notices.total"
        @click="store.loadNotices(store.noticePage + 1)">下一页</button>
    </nav>
  </section>
</template>

<style scoped>
.notice-panel { display: grid; gap: 12px; font-size: 15px; }
.notice-summary { border: 1px solid #2b536c; border-radius: 10px; overflow: hidden; }
.notice-summary-button { display: grid; gap: 7px; width: 100%; text-align: left; padding: 16px; color: inherit; background: #10283e; border: 0; cursor: pointer; }
.notice-summary-button strong { font-size: 17px; }
.notice-summary-button span { font-size: 14px; }
.notice-summary-button small { font-size: 13px; opacity: .8; }
.quick-release-control { padding: 0 16px 14px; background: #10283e; }
.quick-release-control button { padding: 8px 14px; border: 1px solid #3a8195; border-radius: 6px; color: #e7f7fc; background: #15516a; cursor: pointer; font-size: 14px; }
.quick-release-control button:disabled { opacity: .5; cursor: not-allowed; }
.quick-release-form label { display: grid; gap: 8px; }
.quick-release-form textarea { width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid #386279; border-radius: 6px; color: inherit; background: #0b2033; font: inherit; resize: vertical; }
.quick-release-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
nav { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
[role=alert] { color: #ffadad; }
</style>
