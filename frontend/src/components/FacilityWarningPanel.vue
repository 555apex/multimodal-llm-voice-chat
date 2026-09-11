<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useFacilityStore } from '../stores/facility'
import type {
  AlarmLevel,
  FacilityAlert,
  FacilityAlertResolution,
  FacilityAlertStatus,
} from '../types/facility'

type ActionKind = 'confirm' | 'resolved' | 'ignored'

const store = useFacilityStore()
const {
  viewMode, selectedStatus, selectedAlarmLevel, pageData, report, focusItems,
  counts, polling, actionBusy, queryStatus, errorMessage, actionError,
} = storeToRefs(store)

const actionAlert = ref<FacilityAlert | null>(null)
const actionKind = ref<ActionKind>('confirm')
const actionRemark = ref('')
watch(() => [store.viewMode, store.selectedStatus, store.selectedAlarmLevel], () => cancelAction())

const totalPages = computed(() => Math.max(1, Math.ceil((pageData.value?.total ?? 0) / 20)))

const statusOptions: Array<{ value: FacilityAlertStatus; label: string }> = [
  { value: 'PENDING', label: '待确认' },
  { value: 'CONFIRMED', label: '处理中' },
  { value: 'CLOSED', label: '已结束' },
]

function formatTime(value?: string) {
  if (!value) return '暂无'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
}

function formatNumber(value?: number) {
  if (value === undefined || value === null) return '—'
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 6 }).format(value)
}

function actualText(alert: FacilityAlert) {
  return alert.actualStringValue ?? formatNumber(alert.actualValue)
}

function thresholdText(alert: FacilityAlert) {
  if (alert.thresholdMin !== undefined && alert.thresholdMax !== undefined) {
    return `${formatNumber(alert.thresholdMin)} ～ ${formatNumber(alert.thresholdMax)}`
  }
  if (alert.thresholdMin !== undefined) return `不低于 ${formatNumber(alert.thresholdMin)}`
  if (alert.thresholdMax !== undefined) return `不高于 ${formatNumber(alert.thresholdMax)}`
  return '状态型阈值'
}

function beginAction(alert: FacilityAlert, kind: ActionKind) {
  if (actionBusy.value) return
  store.actionError = ''
  actionAlert.value = alert
  actionKind.value = kind
  actionRemark.value = ''
  store.formOpen = true
}

function cancelAction() {
  actionAlert.value = null
  actionRemark.value = ''
  store.closeForm()
}

async function submitAction() {
  const alert = actionAlert.value
  const remark = actionRemark.value.trim()
  if (!alert || !remark || actionBusy.value) return
  let target: FacilityAlertStatus = 'CONFIRMED'
  let resolution: FacilityAlertResolution | undefined
  if (actionKind.value !== 'confirm') {
    target = 'CLOSED'
    resolution = actionKind.value === 'resolved' ? 'RESOLVED' : 'IGNORED'
  }
  try {
    await store.transition(alert.alertId, alert.status, target, remark, resolution)
    actionAlert.value = null
    actionRemark.value = ''
  } catch {
    if (!store.formOpen) actionAlert.value = null
  }
}

function selectStatus(event: Event) {
  void store.selectStatus((event.target as HTMLSelectElement).value as FacilityAlertStatus)
}

function selectAlarmLevel(event: Event) {
  void store.selectAlarmLevel((event.target as HTMLSelectElement).value as AlarmLevel | '')
}
</script>

<template>
  <section class="facility-warning-panel" aria-label="设施预警">
    <header class="facility-warning-toolbar">
      <div class="facility-warning-filters">
        <select :value="selectedStatus" :disabled="actionBusy" aria-label="告警处理状态" @change="selectStatus">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}（{{ counts[option.value === 'PENDING' ? 'pending' : option.value === 'CONFIRMED' ? 'confirmed' : 'closed'] }}）
          </option>
        </select>
        <select :value="selectedAlarmLevel" :disabled="actionBusy" aria-label="告警等级" @change="selectAlarmLevel">
          <option value="">全部等级</option>
          <option value="EMERGENCY">紧急</option>
          <option value="SEVERE">严重</option>
          <option value="WARNING">警告</option>
        </select>
      </div>
      <div class="facility-warning-views">
        <button type="button" :disabled="actionBusy" :class="{ active: viewMode === 'alerts' }" @click="store.showAlerts">预警清单</button>
        <button type="button" :disabled="actionBusy" :class="{ active: viewMode === 'report' }" @click="store.showReport">健康报告</button>
        <button type="button" :disabled="actionBusy" :class="{ active: viewMode === 'focus' }" @click="store.showFocus">重点关注</button>
      </div>
    </header>

    <div class="facility-warning-scroll">
      <div v-if="errorMessage && !pageData && !report && !focusItems.length" class="facility-query-state error" role="alert">
        <strong>设施预警暂时无法加载</strong>
        <p>{{ errorMessage }}</p>
        <button type="button" @click="store.refresh">重新查询</button>
      </div>

      <div v-else-if="queryStatus === 'loading' && !pageData && !report" class="facility-query-state">
        <span class="progress-dot"></span><strong>正在查询设施告警</strong>
      </div>

      <template v-else-if="viewMode === 'alerts'">
        <div v-if="pageData?.items.length" class="facility-alert-list">
          <article v-for="alert in pageData.items" :key="alert.alertId" class="facility-alert-card" :data-level="alert.alarmLevel">
            <header>
              <div>
                <span class="facility-alarm-level">{{ alert.alarmLevelName }}</span>
                <strong>{{ alert.facilityName }}</strong>
              </div>
              <span class="facility-status" :data-status="alert.status">{{ alert.statusName }}</span>
            </header>
            <div class="facility-alert-fact">
              <div><small>异常指标</small><strong>{{ alert.metricName }}</strong></div>
              <div><small>监测结果</small><strong>{{ actualText(alert) }}</strong></div>
              <div><small>预警阈值</small><strong>{{ thresholdText(alert) }}</strong></div>
            </div>
            <p class="facility-threshold-assessment" :class="{ inconsistent: !alert.sourceConsistent }">
              {{ alert.thresholdAssessment }}<span v-if="!alert.sourceConsistent"> · 源数据待核验</span>
            </p>
            <div class="facility-alert-times">
              <span>采集 {{ formatTime(alert.collectTime) }}</span>
              <span>触发 {{ formatTime(alert.triggerTime) }}</span>
            </div>
            <p v-if="alert.remark" class="facility-alert-remark">{{ alert.remark }}</p>

            <div v-if="alert.status !== 'CLOSED'" class="facility-alert-actions">
              <button v-if="alert.status === 'PENDING'" type="button" class="facility-confirm-button" :disabled="actionBusy" @click="beginAction(alert, 'confirm')">
                确认并处置
              </button>
              <template v-else>
                <button type="button" class="facility-resolve-button" :disabled="actionBusy" @click="beginAction(alert, 'resolved')">异常已消除</button>
                <button type="button" class="facility-ignore-button" :disabled="actionBusy" @click="beginAction(alert, 'ignored')">确认忽略</button>
              </template>
            </div>

            <form v-if="actionAlert?.alertId === alert.alertId" class="facility-action-form" @submit.prevent="submitAction">
              <label>
                {{ actionKind === 'confirm' ? '处理说明' : actionKind === 'resolved' ? '处置结果' : '忽略原因' }}
                <textarea v-model="actionRemark" rows="3" maxlength="200" required></textarea>
              </label>
              <p v-if="actionError" class="facility-action-error" role="alert">{{ actionError }}</p>
              <p v-if="actionKind !== 'confirm'">提交后告警将结束且不能在本页面恢复。</p>
              <div>
                <button type="button" :disabled="actionBusy" @click="cancelAction">取消</button>
                <button type="submit" :disabled="actionBusy || !actionRemark.trim()">{{ actionBusy ? '提交中' : '确认提交' }}</button>
              </div>
            </form>
          </article>
        </div>
        <div v-else class="facility-query-state empty">
          <strong>当前筛选条件下没有告警</strong>
          <p>系统会在页面可见时每5秒更新一次。</p>
        </div>
        <footer v-if="pageData && pageData.total > 20" class="facility-pagination">
          <button type="button" :disabled="pageData.page <= 0 || polling" @click="store.loadAlerts(pageData.page - 1)">上一页</button>
          <span>第 {{ pageData.page + 1 }} / {{ totalPages }} 页</span>
          <button type="button" :disabled="pageData.page + 1 >= totalPages || polling" @click="store.loadAlerts(pageData.page + 1)">下一页</button>
        </footer>
      </template>

      <article v-else-if="viewMode === 'report' && report" class="facility-health-report">
        <header>
          <div><small>基础设施健康状态报告</small><strong>{{ report.overallHealthName }}</strong></div>
          <time>数据截至 {{ formatTime(report.dataAsOf) }}</time>
        </header>
        <p>{{ report.summary }}</p>
        <div class="facility-report-counts">
          <span><strong>{{ report.activeAlertCount }}</strong>活动告警</span>
          <span><strong>{{ report.affectedFacilityCount }}</strong>涉及设施</span>
          <span class="emergency"><strong>{{ report.emergencyCount }}</strong>紧急</span>
          <span class="severe"><strong>{{ report.severeCount }}</strong>严重</span>
          <span><strong>{{ report.warningCount }}</strong>警告</span>
        </div>
        <section>
          <h3>逐设施状态</h3>
          <div v-if="report.facilities.length" class="facility-report-list">
            <div v-for="item in report.facilities" :key="item.facilityName">
              <strong>{{ item.facilityName }}</strong>
              <span>{{ item.healthStateName }} · {{ item.activeAlertCount }}项异常</span>
              <small>{{ item.metricNames.join('、') }}</small>
            </div>
          </div>
          <p v-else class="facility-report-empty">当前没有待确认或处理中的异常记录。</p>
        </section>
      </article>

      <section v-else-if="viewMode === 'focus'" class="facility-focus-list">
        <header><strong>重点关注对象</strong><small>按最高风险、活动告警数量和持续时间排序</small></header>
        <article v-for="(item, index) in focusItems" :key="item.facilityName">
          <b>{{ index + 1 }}</b>
          <div>
            <header><strong>{{ item.facilityName }}</strong><span>{{ item.highestAlarmLevelName }}</span></header>
            <p>{{ item.focusReason }}</p>
            <small>{{ item.metricNames.join('、') }} · 最早触发 {{ formatTime(item.oldestTriggerTime) }}</small>
          </div>
        </article>
        <div v-if="!focusItems.length" class="facility-query-state empty">
          <strong>当前没有重点关注对象</strong>
          <p>告警表中暂无待确认或处理中的记录。</p>
        </div>
      </section>
    </div>
  </section>
</template>
