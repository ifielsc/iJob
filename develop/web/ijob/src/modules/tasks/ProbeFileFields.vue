<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { NInput, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import IntegerInput from '../../shared/ui/IntegerInput.vue'
import FileResourcePicker from './FileResourcePicker.vue'
import type { EventValues } from './event-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const value = defineModel<EventValues>('value', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n()
const error = (key: string) =>
  props.submitted && props.errors['event.' + key] ? t('files.invalid') : undefined
</script>
<template>
  <div class="file-probe-fields">
    <p class="task-note">{{ t('files.probeHint') }}</p>
    <FormItem
      id="task-event.fileResourceId"
      :label="t('files.fileResourceId')"
      required
      :invalid="!!error('fileResourceId')"
      :message="error('fileResourceId')"
      ><FileResourcePicker
        id="task-event.fileResourceId"
        v-model:value="value.fileResourceId"
        :label="t('files.fileResourceId')"
        :disabled="disabled"
        :invalid="!!error('fileResourceId')"
        @select="emit('select', $event)"
    /></FormItem>
    <FormItem
      v-for="key in ['filePath', 'filePattern'] as const"
      :id="'task-event.' + key"
      :key="key"
      :label="t('files.' + key)"
      required
      :invalid="!!error(key)"
      :message="error(key)"
      ><NInput
        v-model:value="value[key]"
        :disabled="disabled"
        :maxlength="key === 'filePath' ? 1024 : 128"
        :input-props="{ id: 'task-event.' + key, 'aria-invalid': !!error(key) }"
    /></FormItem>
    <p class="task-note">{{ t('files.probePathHint') }}</p>
    <div class="task-advanced-grid">
      <div
        v-for="key in ['recursive', 'fileCaseSensitive'] as const"
        :key="key"
        class="task-inline"
      >
        <label :for="'task-event.' + key">{{ t('files.' + key) }}</label
        ><NSwitch :id="'task-event.' + key" v-model:value="value[key]" :disabled="disabled" />
      </div>
    </div>
    <div class="task-advanced-grid">
      <FormItem
        v-for="key in [
          'minStableAgeSeconds',
          'maxMatches',
          'maxDepth',
          'maxScannedEntries',
          'scanTimeoutSeconds',
        ] as const"
        :id="'task-event.' + key"
        :key="key"
        :label="t('files.' + key)"
        required
        :invalid="!!error(key)"
        :message="error(key)"
        ><IntegerInput
          :id="'task-event.' + key"
          v-model:value="value[key]"
          :disabled="disabled"
          :invalid="!!error(key)"
      /></FormItem>
    </div>
    <p class="task-note">{{ t('files.ageHint') }}</p>
    <FormItem
      id="task-event.summary"
      :label="t('eventTask.summary')"
      :invalid="!!error('summary')"
      :message="error('summary')"
      ><NInput
        v-model:value="value.summary"
        :disabled="disabled"
        :maxlength="500"
        :input-props="{ id: 'task-event.summary' }"
    /></FormItem>
    <p class="task-note">{{ t('files.metadataHint') }}</p>
    <p class="task-note">{{ t('files.saveTaskHint') }}</p>
  </div>
</template>
<style scoped>
.file-probe-fields {
  display: grid;
  gap: 12px;
  min-width: 0;
}
</style>
