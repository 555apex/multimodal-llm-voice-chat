<script setup lang="ts">
import type { MaintenanceDocument } from '../types/maintenance'
import MaintenanceCharts from './MaintenanceCharts.vue'
defineProps<{ document: MaintenanceDocument }>()
</script>

<template>
  <article class="document-panel">
    <header><p class="eyebrow">养护管理文档</p><h2>{{ document.title }}</h2><small>{{ document.reportYear }}年度 · 版本 {{ document.version }}</small></header>
    <section v-for="section in document.sections" :key="section.heading" class="document-section">
      <h3>{{ section.heading }}</h3><p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
      <div v-if="section.rows.length" class="table-wrap" :class="{ 'wide-data': Object.keys(section.rows[0]).length > 6 && section.heading !== '项目实施安排', 'schedule-table': section.heading === '项目实施安排' }"><table>
        <thead><tr><th v-for="key in Object.keys(section.rows[0])" :key="key">{{ key }}</th></tr></thead>
        <tbody><tr v-for="(row,index) in section.rows" :key="index"><td v-for="key in Object.keys(section.rows[0])" :key="key" :data-label="key">{{ row[key] }}</td></tr></tbody>
      </table></div>
    </section>
    <MaintenanceCharts v-if="document.charts.length" :charts="document.charts" />
    <section class="conclusion"><h3>结论与建议</h3><p>{{ document.conclusion }}</p></section>
    <a class="download" :href="document.downloadUrl">下载Word文档</a>
  </article>
</template>

<style scoped>
.document-panel{min-width:0;margin-top:12px;padding:20px;border:1px solid #176286;border-radius:14px;background:#062746;color:#dceefa;overflow:hidden}.eyebrow{margin:0 0 4px;color:#44d8db;font-size:13px}.document-panel h2{margin:0 0 6px;font-size:21px;line-height:1.4}.document-panel small{color:#91b9d1}.document-section,.conclusion{margin-top:20px}.document-section h3,.conclusion h3{margin:0 0 8px;color:#65dce1;font-size:18px}.document-section p,.conclusion p{line-height:1.75;font-size:15px}.table-wrap{max-width:100%;overflow:auto;border-radius:8px}table{width:100%;border-collapse:collapse;font-size:13px;table-layout:auto}th,td{padding:9px;border:1px solid #174b6b;text-align:left;vertical-align:top;overflow-wrap:anywhere}th{background:#0d4267;color:#d7f4ff;white-space:nowrap}.schedule-table table{min-width:1060px}.schedule-table td{white-space:normal}.wide-data table,.wide-data thead,.wide-data tbody{display:block}.wide-data thead{display:none}.wide-data tbody{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,330px),1fr));gap:12px}.wide-data tr{display:block;min-width:0;padding:12px;border:1px solid #174b6b;border-radius:9px;background:#082d4c}.wide-data td{display:grid;grid-template-columns:96px minmax(0,1fr);gap:8px;border:0;border-bottom:1px solid rgba(23,75,107,.55);padding:6px 2px}.wide-data td:last-child{border-bottom:0}.wide-data td::before{content:attr(data-label);color:#83adc5;font-size:12px}.download{display:inline-block;margin-top:18px;padding:9px 16px;border-radius:8px;background:#1688b7;color:white;text-decoration:none;font-weight:700}.download:hover{background:#20a4d6}@media(max-width:520px){.document-panel{padding:14px}.document-panel h2{font-size:18px}.document-section p,.conclusion p{font-size:14px}.table-wrap:not(.wide-data) table{font-size:12px}.wide-data td{grid-template-columns:88px minmax(0,1fr)}}
</style>
