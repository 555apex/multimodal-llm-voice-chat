<script setup lang="ts">
import type { MaintenanceProjectResult } from '../types/maintenance'
defineProps<{ result: MaintenanceProjectResult }>()

function urgencyClass(value: string) {
  return value === '紧急' ? 'urgent' : value === '较高' ? 'high' : 'normal'
}
</script>

<template>
  <section class="maintenance-panel" aria-label="养护工程项目库">
    <header><p class="eyebrow">养护决策支持</p><h2>{{ result.title }}</h2></header>
    <div class="table-wrap"><table>
      <thead><tr><th>项目编号</th><th>路线</th><th>路段</th><th>养护对象</th><th>类型</th><th>紧急程度</th><th>计划时间</th><th>工期</th><th>预算</th></tr></thead>
      <tbody><tr v-for="project in result.projects" :key="project.projectCode">
        <td><strong>{{ project.projectCode }}</strong></td><td>{{ project.routeCode }} {{ project.routeName }}</td>
        <td>{{ project.routeSection }}</td><td>{{ project.facilityType }}</td><td>{{ project.maintenanceType }}</td>
        <td><span class="urgency" :class="urgencyClass(project.urgency)">{{ project.urgency }}</span></td>
        <td>{{ project.plannedStartDate }}</td><td>{{ project.durationDays }}天</td><td>{{ project.budgetWan.toFixed(2) }}万元</td>
      </tr></tbody>
    </table></div>
  </section>
</template>

<style scoped>
.maintenance-panel{margin-top:12px;padding:18px;border:1px solid #176286;border-radius:14px;background:#062746;color:#dceefa}
.eyebrow{margin:0 0 4px;color:#44d8db;font-size:12px}.maintenance-panel h2{margin:0 0 14px;font-size:20px}.table-wrap{overflow:auto}
table{width:100%;min-width:1050px;border-collapse:collapse;font-size:13px}th,td{padding:10px;border-bottom:1px solid #174b6b;text-align:left;vertical-align:middle}th{background:#0d4267;color:#cceeff;white-space:nowrap}
.urgency{display:inline-block;padding:3px 9px;border-radius:12px;font-weight:700}.urgent{background:#8f2630;color:#fff}.high{background:#8a6719;color:#fff}.normal{background:#236b5b;color:#fff}
</style>
