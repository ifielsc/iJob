<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { NInput, NSelect, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import PushResourcePicker from './PushResourcePicker.vue'
import FileResourcePicker from './FileResourcePicker.vue'
import JsonFormEditor from '../../shared/ui/JsonFormEditor.vue'
import FilePathInput from '../../shared/ui/FilePathInput.vue'
import type { Field } from './etl-model'
import type { QueryContext } from '../../shared/ui/sql-preview'
const SqlEditor = defineAsyncComponent(() => import('../../shared/ui/SqlEditor.vue'))
const props = defineProps<{
  field: Field
  section: string
  value?: string | boolean
  disabled: boolean
  error?: string
  resourceType?: string
  label?: string
  queryContext?: QueryContext
  fileResourceId?: string
  fileMode?: 'file' | 'directory'
}>()
const emit = defineEmits<{
  'update:value': [string | boolean]
  select: [boolean]
  expanded: [boolean]
}>()
const { t, te } = useI18n()
const id = computed(() => 'task-etl.' + props.section + '.' + props.field.key)
const text = computed({
  get: () => String(props.value ?? ''),
  set: (v: string) => emit('update:value', v),
})
const label = computed(
  () =>
    props.label ??
    t(
      props.section === 'message' && props.field.key === 'maxTotalBytes'
        ? 'etl.messageTotalBytes'
        : props.field.key === 'type'
          ? 'etl.' + props.section + 'Type'
          : 'etl.field_' + props.field.key,
    ),
)
const options = computed(() =>
  props.field.options?.map((value) => ({
    value,
    label: te('etl.option_' + value) ? t('etl.option_' + value) : value,
  })),
)
</script>
<template>
  <div :data-etl-key="section + '.' + field.key" :class="{ 'etl-wide': field.wide }">
    <FormItem
      :id="id"
      :label="label"
      :required="field.required"
      :invalid="!!error"
      :message="error"
    >
      <FileResourcePicker
        v-if="field.kind === 'resource' && resourceType === 'FILE'"
        :id="id"
        v-model:value="text"
        :label="label"
        :disabled="disabled"
        :invalid="!!error"
        @select="emit('select', $event)"
      />
      <PushResourcePicker
        v-else-if="field.kind === 'resource'"
        :id="id"
        v-model:value="text"
        :type="resourceType === 'KAFKA' || resourceType === 'RABBITMQ' ? resourceType : 'DB'"
        :label="label"
        :disabled="disabled"
        :invalid="!!error"
        @select="emit('select', $event)"
      />
      <NSelect
        v-else-if="field.kind === 'select'"
        v-model:value="text"
        filterable
        :options="options"
        :node-props="() => ({ role: 'option' })"
        :menu-props="{ role: 'listbox', 'aria-label': label }"
        :disabled="disabled"
        :input-props="{ id, 'aria-label': label }"
        @update:show="emit('select', $event)"
      />
      <NSwitch
        v-else-if="field.kind === 'bool'"
        :id="id"
        :value="value === true"
        :disabled="disabled"
        :aria-label="label"
        @update:value="emit('update:value', $event)"
      />
      <JsonFormEditor
        v-else-if="
          field.kind === 'json' && ['parameters', 'beforeSql', 'afterSql'].includes(field.key)
        "
        v-model="text"
        :shape="field.key === 'parameters' ? 'object' : 'sqlArray'"
        :query-context="queryContext"
        :title="label"
        :context-key="id"
        :disabled="disabled"
        :invalid="!!error"
        @expanded="emit('expanded', $event)"
        @select="emit('select', $event)"
      />
      <SqlEditor
        v-else-if="field.kind === 'sql' || field.kind === 'json'"
        v-model="text"
        :language="field.kind === 'sql' ? 'SQL' : 'JSON'"
        :query-context="field.kind === 'sql' ? queryContext : undefined"
        :context-key="id"
        :disabled="disabled"
        :invalid="!!error"
        @expanded="emit('expanded', $event)"
      />
      <FilePathInput
        v-else-if="field.key === 'path' && fileResourceId"
        :id="id"
        v-model:value="text"
        :resource-id="fileResourceId"
        :mode="fileMode"
        :disabled="disabled"
        :invalid="!!error"
        @select="emit('select', $event)"
      />
      <NInput
        v-else
        v-model:value="text"
        :disabled="disabled"
        :input-props="{
          id,
          'aria-label': label,
          'aria-invalid': !!error,
          inputmode: field.kind === 'number' ? 'numeric' : undefined,
        }"
      />
      <p v-if="field.kind === 'list'" class="task-note">{{ t('etl.listHelp') }}</p>
      <p v-if="field.kind === 'number'" class="task-note">{{ field.min }} – {{ field.max }}</p>
    </FormItem>
  </div>
</template>
