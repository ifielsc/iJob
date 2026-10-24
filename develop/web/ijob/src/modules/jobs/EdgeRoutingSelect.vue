<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, useId } from 'vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import { usePreferences } from '../../shared/preferences/store'
const { t, locale } = useI18n()
const preferences = usePreferences()
const id = useId()
const props = defineProps<{ direction?: 'LR' | 'TB' }>()
const emit = defineEmits<{ 'update:direction': ['LR' | 'TB'] }>()
const options = computed(() => {
  const modes = [
    { value: 'avoid', label: t('jobs.edgeAvoid') },
    { value: 'simple', label: t('jobs.edgeSimple') },
  ]
  return props.direction
    ? ['LR', 'TB'].flatMap((direction) =>
        modes.map((mode) => ({
          value: `${direction}:${mode.value}`,
          label: `${t(direction === 'LR' ? 'jobs.horizontal' : 'jobs.vertical')} · ${mode.label}`,
        })),
      )
    : modes
})
function changeRouting(raw: string | number) {
  const [direction, mode] = String(raw).split(':')
  const value = props.direction ? mode : raw
  if (props.direction && (direction === 'LR' || direction === 'TB'))
    emit('update:direction', direction)
  if (value === 'avoid' || value === 'simple') preferences.setEdgeRouting(value)
}
</script>
<template>
  <CompactSelect
    :id="id"
    class="job-routing-select"
    size="small"
    :style="direction ? { width: locale === 'zh-CN' ? '176px' : '224px' } : undefined"
    :value="direction ? `${direction}:${preferences.edgeRouting}` : preferences.edgeRouting"
    :label="t(direction ? 'jobs.layoutAndRouting' : 'jobs.edgeRouting')"
    :title="t(direction ? 'jobs.layoutAndRoutingHelp' : 'jobs.edgeRoutingHelp')"
    :options="options"
    @update:value="changeRouting"
  />
</template>
<style scoped>
.job-routing-select {
  width: 144px;
  max-width: 100%;
  min-width: 0;
}
</style>
