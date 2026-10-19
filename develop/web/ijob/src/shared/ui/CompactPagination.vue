<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, ref, watch, useId } from 'vue'
import { NButton, NInputNumber } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import CompactSelect from './CompactSelect.vue'
const props = defineProps<{ page: number; total: number; size: number; busy: boolean }>()
const fieldId = useId(),
  jump = ref<number | null>(props.page)
const pages = computed(() => Math.max(1, Math.min(100000, Math.ceil(props.total / props.size))))
watch(
  () => props.page,
  (value) => (jump.value = value),
)
function go() {
  if (jump.value !== null && !props.busy)
    emit('page', Math.max(1, Math.min(pages.value, Math.trunc(jump.value))))
}
const emit = defineEmits<{ page: [number]; size: [number] }>()
const { t } = useI18n()
const sizes = [20, 50, 100].map((value) => ({ value, label: String(value) }))
</script>
<template>
  <div class="compact-pagination">
    <span>{{ t('experience.pages', { page, pages, total }) }}</span>
    <div class="compact-page-size">
      <label :for="fieldId">{{ t('common.pageSize') }}</label>
      <CompactSelect
        :id="fieldId"
        :label="t('common.pageSize')"
        :value="size"
        :options="sizes"
        size="small"
        :disabled="busy"
        @update:value="emit('size', Number($event))"
      />
    </div>
    <div class="compact-page-jump" @keydown.enter.prevent="go">
      <NInputNumber
        v-model:value="jump"
        :min="1"
        :max="pages"
        :precision="0"
        :show-button="false"
        :disabled="busy"
        :input-props="{ 'aria-label': t('experience.jump') }"
        size="small"
      /><NButton size="small" :disabled="busy || jump === null" @click="go">{{
        t('experience.jump')
      }}</NButton>
    </div>
    <NButton size="small" :disabled="busy || page <= 1" @click="emit('page', page - 1)">{{
      t('common.previous')
    }}</NButton>
    <NButton
      size="small"
      :disabled="busy || page * size >= total || page >= 100000"
      @click="emit('page', page + 1)"
      >{{ t('common.next') }}</NButton
    >
  </div>
</template>
<style scoped>
.compact-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  font-size: 12px;
  color: var(--muted);
}
.compact-pagination > span {
  margin-right: auto;
}
.compact-page-size {
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.compact-page-size > :last-child {
  width: 72px;
}
.compact-page-jump {
  display: flex;
  gap: 6px;
  align-items: center;
}
.compact-page-jump .n-input-number {
  width: 68px;
}
</style>
