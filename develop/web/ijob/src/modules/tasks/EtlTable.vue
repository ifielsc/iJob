<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { NButton } from 'naive-ui'
import { Plus } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
withDefaults(
  defineProps<{
    title: string
    columns: { key: string; label: string; width?: string; required?: boolean }[]
    count: number
    addLabel?: string
    disabled: boolean
    compact?: boolean
    showTitle?: boolean
    bounded?: boolean
  }>(),
  { bounded: true, addLabel: undefined },
)
defineEmits<{ add: [] }>()
const { t } = useI18n()
</script>
<template>
  <section
    class="etl-table-section"
    :class="{ 'etl-table-compact': compact, 'etl-table-bounded': bounded !== false && count > 6 }"
    :aria-label="title"
  >
    <div class="etl-table-toolbar">
      <h3 v-if="!compact || showTitle">
        {{ title }} <span>({{ count }})</span>
      </h3>
      <span v-else class="etl-table-count">{{ count }} {{ t('etl.tableRows') }}</span>
      <NButton v-if="addLabel" size="small" :disabled="disabled" @click="$emit('add')">
        <template #icon><Plus :size="14" /></template>{{ addLabel }}
      </NButton>
    </div>
    <slot name="tools" />
    <div class="etl-table-scroll" role="region" :aria-label="title" tabindex="0">
      <table class="etl-table" :aria-label="title">
        <colgroup>
          <col v-for="column in columns" :key="column.key" :style="{ width: column.width }" />
        </colgroup>
        <thead>
          <tr>
            <th
              v-for="column in columns"
              :key="column.key"
              scope="col"
              :class="{ 'etl-table-actions': column.key === 'actions' }"
            >
              {{ column.label }}
              <span v-if="column.required" class="etl-table-required" aria-hidden="true">*</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <slot />
          <tr v-if="!count">
            <td :colspan="columns.length" class="list-empty-cell">
              <EmptyState compact :message="t('common.empty')" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <slot name="feedback" />
  </section>
</template>
<style scoped>
/* Match CalendarEditor exception rows, including the sticky action column. */
.etl-table-section {
  min-width: 0;
  width: 100%;
}
.etl-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.etl-table-toolbar h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.etl-table-toolbar h3 span,
.etl-table-count {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.etl-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
  overscroll-behavior-x: contain;
}
.etl-table {
  min-width: 520px;
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
}
.etl-table :deep(th),
.etl-table :deep(td) {
  padding: 7px 10px;
  font-size: 13px;
  vertical-align: top;
  box-sizing: border-box;
}
.etl-table :deep(th) {
  background: var(--selected);
  text-align: left;
  font-weight: 500;
}
.etl-table :deep(td) {
  border-top: 1px solid var(--border);
  background: var(--panel);
}
.etl-table :deep(.etl-table-actions) {
  position: sticky;
  right: 0;
  background: var(--panel);
  z-index: 1;
  text-align: center;
}
.etl-table :deep(th.etl-table-actions) {
  background: var(--selected);
}
.etl-table :deep(tbody > tr:hover > td) {
  background: var(--selected);
}
.etl-table :deep(.etl-table-empty) {
  color: var(--muted);
  text-align: center;
  padding: 16px 10px;
}
.etl-table-required {
  color: var(--error);
}
.etl-table-compact > .etl-table-scroll > .etl-table {
  min-width: 280px;
}
.etl-table-compact .etl-table :deep(th),
.etl-table-compact .etl-table :deep(td) {
  padding: 7px 8px;
}
/* Keep short lists natural; cap only lists that can dominate the drawer. */
.etl-table-bounded > .etl-table-scroll {
  max-height: min(320px, 42dvh);
  overflow-y: auto;
  scroll-padding-top: 40px;
  scrollbar-gutter: stable;
}
.etl-table-bounded > .etl-table-scroll > table > thead > tr > th {
  position: sticky;
  top: 0;
  z-index: 2;
}
.etl-table-bounded > .etl-table-scroll > table > thead > tr > th.etl-table-actions {
  z-index: 3;
}
</style>
