<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NInput } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import Select from '../../shared/ui/CompactSelect.vue'
import EtlTable from './EtlTable.vue'
import PushResourcePicker from './PushResourcePicker.vue'
import {
  formValue,
  emptyNode,
  type FormNode,
  type FormSchema,
  type NodeKind,
} from './etl-form-model'
const props = defineProps<{
  schema?: FormSchema
  id: string
  disabled: boolean
  submitted?: boolean
  inputLabel?: string
  tableTitle?: string
}>()
const node = defineModel<FormNode>('node', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t, te } = useI18n()
const schema = computed(() => props.schema ?? {})
const label = (key: string) =>
  te('etl.form_' + key)
    ? t('etl.form_' + key)
    : te('etl.field_' + key)
      ? t('etl.field_' + key)
      : key
const options = (values: string[]) =>
  values.map((value) => ({
    value,
    label: te('etl.option_' + value)
      ? t('etl.option_' + value)
      : te('etl.form_' + value)
        ? t('etl.form_' + value)
        : value,
  }))
const known = computed(() => Object.entries(schema.value.fields ?? {}))
const extras = computed(() =>
  node.value.entries.filter((entry) => !schema.value.fields?.[entry.key]),
)
const rowKeys = new WeakMap<object, number>()
let nextKey = 0
function rowKey(entry: object) {
  if (!rowKeys.has(entry)) rowKeys.set(entry, ++nextKey)
  return rowKeys.get(entry)!
}
const tableColumns = computed(() => [
  ...(node.value.kind === 'object'
    ? [{ key: 'key', label: t('etl.' + (schema.value.keyLabel ?? 'form_key')), required: true }]
    : []),
  { key: 'value', label: t('etl.' + (schema.value.valueLabel ?? 'form_value')) },
  { key: 'actions', label: t('common.actions'), width: '60px' },
])
function fieldNode(key: string, field: FormSchema) {
  return node.value.entries.find((e) => e.key === key)?.node ?? emptyNode(field)
}
function invalid(child: FormNode, field: FormSchema = {}) {
  if (!props.submitted) return false
  try {
    formValue(child, field, !field.required)
    return false
  } catch {
    return true
  }
}
function changeKind(kind: string | number) {
  node.value.omitted = false
  node.value.kind = kind as NodeKind
  node.value.value = kind === 'boolean' ? 'false' : ''
  node.value.entries =
    kind === 'object'
      ? Object.entries(schema.value.fields ?? {}).map(([key, field]) => ({
          key,
          node: emptyNode(field),
        }))
      : []
}
</script>
<template>
  <div class="etl-form-node">
    <FormItem v-if="schema.nullable" :id="id + '-enabled'" :label="t('etl.form_enabled')">
      <Select
        :id="id + '-enabled'"
        :label="t('etl.form_enabled')"
        :value="node.kind === 'null' ? 'off' : 'on'"
        :options="options(['off', 'on'])"
        :disabled="disabled"
        @show="emit('select', $event)"
        @update:value="changeKind($event === 'off' ? 'null' : (schema.kind ?? 'object'))"
      />
    </FormItem>
    <FormItem v-if="!schema.kind" :id="id + '-kind'" :label="t('etl.form_valueType')">
      <Select
        :id="id + '-kind'"
        :label="t('etl.form_valueType')"
        :value="node.kind"
        :options="options(['string', 'number', 'boolean', 'null', 'object', 'array'])"
        :disabled="disabled"
        @show="emit('select', $event)"
        @update:value="changeKind"
      />
    </FormItem>
    <template v-if="node.kind !== 'null'">
      <template v-if="node.kind === 'object' || node.kind === 'array'">
        <div v-if="known.length" class="etl-form-grid">
          <FormItem
            v-for="[key, field] in known"
            :id="id + '-' + key"
            :key="key"
            :label="field.label ? t('etl.' + field.label) : label(key)"
            :hide-label="
              ['array', 'object'].includes(field.kind ?? '') && !field.fields && !field.nullable
            "
            :required="field.required"
            :invalid="invalid(fieldNode(key, field), field)"
            :message="invalid(fieldNode(key, field), field) ? t('etl.invalid') : undefined"
            :class="{ 'etl-form-wide': ['array', 'object', 'sql'].includes(field.kind ?? '') }"
          >
            <EtlFormNode
              :id="id + '-' + key"
              :node="fieldNode(key, field)"
              :schema="field"
              :input-label="field.label ? t('etl.' + field.label) : label(key)"
              :table-title="
                ['array', 'object'].includes(field.kind ?? '') && !field.fields && !field.nullable
                  ? field.label
                    ? t('etl.' + field.label)
                    : label(key)
                  : undefined
              "
              :submitted="submitted"
              :disabled="disabled"
              @select="emit('select', $event)"
            />
          </FormItem>
        </div>
        <EtlTable
          v-if="!schema.fields || extras.length"
          compact
          :show-title="!!tableTitle"
          :title="tableTitle ?? inputLabel ?? t('etl.ruleProperties')"
          :columns="tableColumns"
          :count="extras.length"
          :disabled="disabled"
          :add-label="!schema.fields ? t('etl.form_addItem') : undefined"
          @add="node.entries.push({ key: '', node: emptyNode(schema.item) })"
        >
          <tr v-for="(entry, index) in extras" :key="rowKey(entry)">
            <td v-if="node.kind === 'object'">
              <NInput
                v-model:value="entry.key"
                :disabled="disabled || !!schema.fields"
                :input-props="{
                  id: id + '-key-' + index,
                  'aria-label': t('etl.' + (schema.keyLabel ?? 'form_key')) + ' ' + (index + 1),
                  'aria-required': true,
                }"
              />
            </td>
            <td>
              <EtlFormNode
                :id="id + '-value-' + index"
                :node="entry.node"
                :schema="schema.item"
                :submitted="submitted"
                :disabled="disabled"
                :input-label="t('etl.' + (schema.valueLabel ?? 'form_value')) + ' ' + (index + 1)"
                @select="emit('select', $event)"
              />
            </td>
            <td class="etl-table-actions">
              <NButton
                size="small"
                text
                type="error"
                :disabled="disabled"
                :aria-label="t('etl.form_removeItem', { n: index + 1 })"
                @click="node.entries.splice(node.entries.indexOf(entry), 1)"
                >{{ t('tasks.delete') }}</NButton
              >
            </td>
          </tr>
        </EtlTable>
      </template>
      <PushResourcePicker
        v-else-if="schema.kind === 'resource'"
        :id="id"
        v-model:value="node.value"
        type="DB"
        :label="t('etl.field_resourceId')"
        :disabled="disabled"
        @select="emit('select', $event)"
      />
      <Select
        v-else-if="schema.options || node.kind === 'boolean'"
        :id="id"
        v-model:value="node.value"
        :label="t('etl.form_value')"
        :options="options(schema.options ?? ['true', 'false'])"
        :disabled="disabled"
        @show="emit('select', $event)"
      />
      <NInput
        v-else
        v-model:value="node.value"
        :type="schema.kind === 'sql' ? 'textarea' : 'text'"
        :autosize="schema.kind === 'sql' ? { minRows: 3, maxRows: 12 } : undefined"
        :disabled="disabled"
        :input-props="{ id, spellcheck: false, 'aria-label': inputLabel ?? t('etl.form_value') }"
      />
    </template>
  </div>
</template>
<style scoped>
.etl-form-node {
  min-width: 0;
  width: 100%;
}
.etl-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}
.etl-form-grid > * {
  min-width: 0;
}
.etl-form-wide {
  grid-column: 1/-1;
}
@media (max-width: 480px) {
  .etl-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
