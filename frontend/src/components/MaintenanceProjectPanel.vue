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
    <div class="project-grid">
      <article v-for="project in result.projects" :key="project.projectCode" class="project-card">
        <div class="card-heading"><strong>{{ project.routeCode }} · {{ project.routeName }}</strong><span class="urgency" :class="urgencyClass(project.urgency)">{{ project.urgency }}</span></div>
        <p class="section-name">{{ project.routeSection }}</p>
        <dl><div><dt>项目编号</dt><dd>{{ project.projectCode }}</dd></div><div><dt>养护对象</dt><dd>{{ project.facilityType }}</dd></div>
          <div><dt>养护类型</dt><dd>{{ project.maintenanceType }}</dd></div><div><dt>计划开始时间</dt><dd>{{ project.plannedStartDate }}</dd></div>
          <div><dt>预计工期</dt><dd>{{ project.durationDays }}天</dd></div><div><dt>计划预算</dt><dd>{{ project.budgetWan.toFixed(2) }}万元</dd></div></dl>
      </article>
    </div>
  </section>
</template>

<style scoped>
.maintenance-panel{margin-top:12px;padding:18px;border:1px solid #176286;border-radius:14px;background:#062746;color:#dceefa}
.eyebrow{margin:0 0 4px;color:#44d8db;font-size:13px}.maintenance-panel h2{margin:0 0 14px;font-size:20px}.project-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,320px),1fr));gap:12px}.project-card{min-width:0;padding:14px;border:1px solid #174b6b;border-radius:10px;background:#082d4c}.card-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;font-size:15px}.section-name{margin:8px 0 12px;color:#b9d5e6;line-height:1.55}.project-card dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 14px;margin:0}.project-card dl div{min-width:0}.project-card dt{font-size:12px;color:#83adc5}.project-card dd{margin:2px 0 0;overflow-wrap:anywhere;font-size:13px;color:#e6f5fc}
.urgency{display:inline-block;padding:3px 9px;border-radius:12px;font-weight:700}.urgent{background:#8f2630;color:#fff}.high{background:#8a6719;color:#fff}.normal{background:#236b5b;color:#fff}
@media(max-width:520px){.maintenance-panel{padding:14px}.project-card dl{grid-template-columns:1fr}.card-heading{font-size:14px}}
</style>
