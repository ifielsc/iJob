<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { NButton, NCheckbox } from 'naive-ui'
import { useI18n } from 'vue-i18n'
defineProps<{
  hasContent: boolean
  hasSelection: boolean
  copying: boolean
  readOnly?: boolean
  disabled?: boolean
}>()
const wrap = defineModel<boolean>('wrap', { required: true })
const emit = defineEmits<{ selectAll: []; copy: []; cut: []; find: [] }>()
const { t } = useI18n()
</script>
<template>
  <div class="sql-large-toolbar">
    <NButton size="small" :disabled="!hasContent" @mousedown.prevent @click="emit('selectAll')">{{
      t('tasks.selectAllText')
    }}</NButton>
    <NButton
      size="small"
      :disabled="(!hasSelection && !(readOnly && hasContent)) || copying"
      @mousedown.prevent
      @click="emit('copy')"
      >{{ t('unified.copy') }}</NButton
    >
    <NButton
      v-if="!readOnly"
      size="small"
      :disabled="disabled || !hasSelection || copying"
      @mousedown.prevent
      @click="emit('cut')"
      >{{ t('tasks.cutText') }}</NButton
    >
    <NButton size="small" @click="emit('find')">{{ t('tasks.find') }}</NButton>
    <NCheckbox v-model:checked="wrap">{{ t('tasks.wrap') }}</NCheckbox>
  </div>
</template>
