<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
defineProps<{ message?: string; compact?: boolean }>()
const { t } = useI18n()
</script>

<template>
  <div class="list-empty" :class="{ 'list-empty-compact': compact }" role="status">
    <p>{{ message ?? t('common.empty') }}</p>
  </div>
</template>

<style scoped>
.list-empty {
  display: grid;
  place-items: center;
  min-height: 180px;
  width: 100%;
  padding: 24px;
  color: var(--muted);
  text-align: center;
  box-sizing: border-box;
}
.list-empty p {
  margin: 0;
  max-width: 36rem;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.list-empty-cell > .list-empty {
  position: sticky;
  left: 0;
  width: 100cqi;
  max-width: 100%;
}
.list-empty-compact {
  min-height: 96px;
  padding: 20px 16px;
}
</style>
