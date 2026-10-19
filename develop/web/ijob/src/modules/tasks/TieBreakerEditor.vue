<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { ref, watch } from 'vue'
import { NInput } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import JsonFormEditor from '../../shared/ui/JsonFormEditor.vue'
import { parseExact, readDocument, valueText } from '../../shared/ui/json-form'
import { stringify } from 'lossless-json'
import { list } from './etl-model'
const props = defineProps<{ fields: string; values: string; disabled?: boolean; error?: string }>()
const emit = defineEmits<{
  change: [fields: string, values: string]
  expanded: [boolean]
  select: [boolean]
}>()
const { t } = useI18n(),
  text = ref('{}'),
  fallback = ref(false)
let ownFields: string | undefined, ownValues: string | undefined
watch(
  () => [props.fields, props.values],
  () => {
    if (props.fields === ownFields && props.values === ownValues) return
    try {
      const fields = list(props.fields),
        values = parseExact(props.values.trim() || '[]')
      if (
        !Array.isArray(values) ||
        values.length !== fields.length ||
        new Set(fields).size !== fields.length
      )
        throw Error()
      text.value =
        '{' +
        fields.map((name, i) => JSON.stringify(name) + ':' + stringify(values[i])).join(',') +
        '}'
      fallback.value = false
    } catch {
      fallback.value = true
    }
  },
  { immediate: true },
)
function update(value: string) {
  text.value = value
  try {
    const draft = readDocument(value)
    if (draft.kind !== 'object' || draft.absent) throw Error()
    const rows = draft.rows.filter((r) => r.included)
    if (rows.some((r) => !r.name.trim() || r.name.includes(',') || r.kind === 'null')) throw Error()
    ownFields = rows.map((r) => r.name).join(', ')
    ownValues = '[' + rows.map(valueText).join(',') + ']'
  } catch {
    ownFields = props.fields
    ownValues = '['
  }
  emit('change', ownFields, ownValues)
}
</script>
<template>
  <section class="etl-wide" data-etl-key="incremental.initialTieBreakerValues">
    <h4>{{ t('jsonForm.ties') }}</h4>
    <template v-if="fallback"
      ><p role="alert">{{ t('jsonForm.advancedOnly') }}</p>
      <NInput
        :value="fields"
        :disabled="disabled"
        :input-props="{ 'aria-label': t('etl.field_tieBreakerFields') }"
        @update:value="emit('change', $event, values)" /><JsonFormEditor
        :model-value="values"
        :disabled="disabled"
        context-key="ETL_TIE_VALUES"
        @update:model-value="emit('change', fields, $event)"
        @expanded="emit('expanded', $event)"
    /></template>
    <JsonFormEditor
      v-else
      :model-value="text"
      shape="object"
      :disabled="disabled"
      context-key="ETL_TIES"
      @update:model-value="update"
      @expanded="emit('expanded', $event)"
      @select="emit('select', $event)"
    />
    <p class="task-note">{{ t('jsonForm.tieHint') }}</p>
    <p v-if="error" class="etl-error" role="alert">{{ error }}</p>
  </section>
</template>
