<script setup lang="ts">
import type { RagResult } from '../types/agent'

defineProps<{ result: RagResult }>()
</script>

<template>
  <section class="rag-sources" aria-label="知识库参考来源">
    <header>
      <span>参考来源</span>
      <small>检索到 {{ result.hitCount }} 条相关资料</small>
    </header>
    <p v-if="!result.sources.length" class="empty">知识库没有返回可引用的来源</p>
    <ol v-else>
      <li v-for="source in result.sources" :key="source">{{ source }}</li>
    </ol>
  </section>
</template>

<style scoped>
.rag-sources {
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid rgba(31, 128, 93, 0.28);
  border-radius: 10px;
  background: rgba(237, 250, 245, 0.82);
  color: #1f4035;
}
.rag-sources header { display: flex; justify-content: space-between; gap: 16px; align-items: baseline; }
.rag-sources header span { font-weight: 650; }
.rag-sources small, .rag-sources .empty { color: #5c746c; }
.rag-sources ol { margin: 8px 0 0; padding-left: 20px; }
.rag-sources li { margin-top: 4px; overflow-wrap: anywhere; }
.rag-sources .empty { margin: 8px 0 0; }
</style>
