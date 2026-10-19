<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import Preferences from './CompactPreferences.vue'
defineProps<{ title: string }>()
</script>
<template>
  <div class="editor-title">
    <h2>{{ title }}</h2>
    <Preferences />
  </div>
</template>
<style scoped>
.editor-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}
.editor-title h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
</style>
