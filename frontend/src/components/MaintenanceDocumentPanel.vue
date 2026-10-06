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
      <div v-if="section.rows.length" class="table-wrap"><table>
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
.document-panel{min-width:0;margin-top:12px;padding:20px;border:1px solid #176286;border-radius:14px;background:#062746;color:#dceefa;overflow:hidden}.eyebrow{margin:0 0 4px;color:#44d8db;font-size:13px}.document-panel h2{margin:0 0 6px;font-size:21px;line-height:1.4}.document-panel small{color:#91b9d1}.document-section,.conclusion{margin-top:20px}.document-section h3,.conclusion h3{margin:0 0 8px;color:#65dce1;font-size:18px}.document-section p,.conclusion p{line-height:1.75;font-size:15px}.table-wrap{max-width:100%;overflow-x:auto;border-radius:8px}table{width:100%;min-width:760px;border-collapse:collapse;font-size:13px;table-layout:auto}th,td{padding:9px;border:1px solid #174b6b;text-align:left;vertical-align:top;overflow-wrap:anywhere}th{background:#0d4267;color:#d7f4ff;white-space:nowrap}.download{display:inline-block;margin-top:18px;padding:9px 16px;border-radius:8px;background:#1688b7;color:white;text-decoration:none;font-weight:700}.download:hover{background:#20a4d6}@media(max-width:520px){.document-panel{padding:14px}.document-panel h2{font-size:18px}.document-section p,.conclusion p{font-size:14px}.table-wrap table{font-size:12px}}
</style>
