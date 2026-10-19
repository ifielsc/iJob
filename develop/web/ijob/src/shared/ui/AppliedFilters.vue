<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
const props = defineProps<{ values: Record<string, string>; busy?: boolean }>()
defineEmits<{ remove: [string]; clear: [] }>()
const { t, te } = useI18n()
const items = computed(() => Object.entries(props.values).filter(([, v]) => v !== ''))
const fields: Record<string, string> = {
  name: 'tasks.name',
  id: 'runs.targetId',
  pluginType: 'tasks.type',
  enabled: 'users.status',
  group: 'tasks.group',
  targetName: 'schedules.targetName',
  targetType: 'runs.targetType',
  targetId: 'runs.targetId',
  scheduleType: 'schedules.scheduleType',
  type: 'config.type',
  roleCode: 'users.role',
  status: 'runs.status',
  triggerType: 'runs.triggerType',
  scheduleId: 'runs.scheduleId',
  triggerId: 'runs.triggerId',
  rootInstanceId: 'runs.rootInstanceId',
  sourceType: 'runs.sourceType',
  result: 'runs.result',
  notifyConfigId: 'runs.notifyConfigId',
  instanceId: 'runs.instanceId',
}
function label(key: string, value: string) {
  const field = fields[key]
  let display = value
  if (key === 'enabled') display = t(value === 'true' ? 'users.enabled' : 'users.disabled')
  else if (te('runEnums.' + value)) display = t('runEnums.' + value)
  else if (te('users.' + value)) display = t('users.' + value)
  else if (te('plugins.' + value)) display = t('plugins.' + value)
  return (field && te(field) ? t(field) : key) + ': ' + display
}
</script>
<template>
  <div v-if="items.length" class="applied-filters" :aria-label="t('experience.activeFilters')">
    <span>{{ t('experience.activeFilters') }}</span
    ><NButton
      v-for="[key, value] in items"
      :key="key"
      size="tiny"
      :disabled="busy"
      :aria-label="t('experience.clearFilters') + ' ' + label(key, value)"
      @click="$emit('remove', key)"
      >{{ label(key, value) }} ×</NButton
    ><NButton text size="tiny" :disabled="busy" @click="$emit('clear')">{{
      t('experience.clearFilters')
    }}</NButton>
  </div>
</template>
