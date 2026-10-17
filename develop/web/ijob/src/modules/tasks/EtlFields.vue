<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { NButton, NCollapse, NCollapseItem, NInput, NSelect, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import LineTabs from '../../shared/ui/LineTabs.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import EtlField from './EtlField.vue'
import EtlMessageFields from './EtlMessageFields.vue'
import TieBreakerEditor from './TieBreakerEditor.vue'
import EtlTable from './EtlTable.vue'
import { ArrowUp, ArrowDown } from '@lucide/vue'
import EtlFormNode from './EtlFormNode.vue'
import { prepareForm, ruleSchemas, targetSchemas } from './etl-form-model'
import { stringify } from 'lossless-json'
import {
  sourceFields,
  isMessageSource,
  targetFields,
  incrementalFields,
  fileFields,
  executionFields,
  sourceKeys,
  targetKeys,
  incrementalKeys,
  fileKeys,
  transformExamples,
  validationExamples,
  type Rule,
  type Field,
  type EtlDraft,
} from './etl-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<EtlDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean]; expanded: [boolean] }>()
const { t } = useI18n(),
  tab = ref('source'),
  root = ref<HTMLElement>(),
  expanded = ref<string[]>([])
const tabs = computed(() =>
  ['source', 'rules', 'target', 'incremental'].map((value) => ({
    value,
    label: t(
      value === 'incremental' && isMessageSource(draft.value.source.type)
        ? 'etl.messageTab'
        : 'etl.tab_' + value,
    ),
  })),
)
const error = (key: string) =>
  props.submitted && props.errors['etl.' + key] ? t('etl.' + props.errors['etl.' + key]) : undefined
const sourceAdvanced = [
  'prefetch',
  'escape',
  'headerRow',
  'startRow',
  'sheetName',
  'sheetIndex',
  'regexMode',
  'blockStart',
  'blockEnd',
  'maxBlockLines',
  'maxBlockChars',
  'regexFields',
  'stagingTable',
  'stagingColumns',
  'sheets',
  'fetchSize',
  'parameters',
  'beforeSql',
]
const targetAdvanced = [
  'transactionScope',
  'nullPolicy',
  'columnNullPolicies',
  'protection',
  'scd2',
  'beforeSql',
  'afterSql',
]
const fileAdvanced = [
  'recursive',
  'maxDepth',
  'maxScanEntries',
  'maxFiles',
  'maxTotalBytes',
  'stableAgeSeconds',
  'readySuffix',
]
const fields = (schema: Field[], keys: string[], advanced: string[] = [], details = false) =>
  schema.filter(
    (f) => keys.includes(f.key) && (details ? advanced.includes(f.key) : !advanced.includes(f.key)),
  )
const sourcePrimary = computed(() =>
  fields(sourceFields, sourceKeys(draft.value), sourceAdvanced).sort(
    (a, b) => sourceKeys(draft.value).indexOf(a.key) - sourceKeys(draft.value).indexOf(b.key),
  ),
)
const sourceDetails = computed(() =>
  fields(sourceFields, sourceKeys(draft.value), sourceAdvanced, true),
)
const targetPrimary = computed(() =>
  fields(targetFields, targetKeys(draft.value), targetAdvanced).map((field) => {
    if (!isMessageSource(draft.value.source.type)) return field
    if (field.key === 'type') return { ...field, options: ['DATABASE'] }
    if (field.key === 'mode') return { ...field, options: ['UPSERT', 'INSERT_ONLY'] }
    return field
  }),
)
const targetDetails = computed(() =>
  fields(targetFields, targetKeys(draft.value), targetAdvanced, true),
)
const incrementalPrimary = computed(() => {
  const d = draft.value
  const common = fields(incrementalFields, incrementalKeys(d))
    .filter((f) => !['enabled', 'tieBreakerFields', 'initialTieBreakerValues'].includes(f.key))
    .map((field) => ({ field, section: 'incremental' as const }))
  if (d.source.type !== 'FILE' || !d.incremental.enabled) return common
  const file = fields(fileFields, fileKeys(d), fileAdvanced).map((field) => ({
    field:
      field.key === 'selectionMode' && d.incremental.watermarkType === 'STRING'
        ? { ...field, options: field.options?.filter((v) => v !== 'LOOKBACK') }
        : field,
    section: 'file' as const,
  }))
  const order = [
    'checkpointKey',
    'watermarkType',
    'initialWatermark',
    'markerLocation',
    'matchMode',
    'changedFilePolicy',
    'expression',
    'markerFormat',
    'zoneId',
    'selectionMode',
    'lookback',
    'lowerBound',
    'upperBound',
  ]
  return [...common, ...file].sort(
    (a, b) => order.indexOf(a.field.key) - order.indexOf(b.field.key),
  )
})
function update(
  section: 'source' | 'target' | 'file' | 'incremental' | 'execution',
  field: Field,
  value: string | boolean,
) {
  const d = draft.value
  if (
    (section === 'source' || section === 'target') &&
    field.key === 'type' &&
    d[section].type !== value
  )
    if (section === 'source') {
      d.sourceDrafts[String(d.source.type)] = { ...d.source }
      d.source = {
        ...(d.sourceDrafts[String(value)] ?? d.source),
        type: value,
        resourceId: d.sourceDrafts[String(value)]?.resourceId ?? '',
      }
      if (isMessageSource(value)) {
        if (d.target.type !== 'DATABASE') d.target.resourceId = ''
        d.target.type = 'DATABASE'
        d.target.transactionScope = 'BATCH'
        if (!['UPSERT', 'INSERT_ONLY'].includes(String(d.target.mode))) d.target.mode = 'UPSERT'
        d.incremental.enabled = false
        d.allowNonIdempotentRetry = false
        d.validations.forEach((r) => {
          r.onFailure = 'FAIL_TASK'
        })
      }
    } else d[section].resourceId = ''
  d[section][field.key] = value
  if (
    section === 'incremental' &&
    field.key === 'watermarkType' &&
    value === 'STRING' &&
    d.file.selectionMode === 'LOOKBACK'
  )
    d.file.selectionMode = 'STRICT'
  if (section === 'source' && field.key === 'format') {
    if (['SQLITE', 'MDB'].includes(String(value))) d.source.readMode = 'SQL'
    if (value === 'REGEX') d.source.readMode = 'DIRECT'
  }
}
const rowKeys = new WeakMap<object, number>()
let nextRowKey = 0
function rowKey(row: object) {
  if (!rowKeys.has(row)) rowKeys.set(row, ++nextRowKey)
  return rowKeys.get(row)!
}
type RuleKind = 'transforms' | 'validations'
const selection = ref<Record<RuleKind, number>>({ transforms: 0, validations: 0 })
function selectedRule(kind: RuleKind) {
  return (
    draft.value[kind].find((rule) => rowKey(rule) === selection.value[kind]) ?? draft.value[kind][0]
  )
}
function removeRule(kind: RuleKind, index: number) {
  const rows = draft.value[kind],
    selected = selectedRule(kind)
  const removed = rows.splice(index, 1)[0]
  if (removed === selected && rows.length)
    selection.value[kind] = rowKey(rows[Math.min(index, rows.length - 1)]!)
}
function addRule(validation: boolean) {
  const rows = validation ? draft.value.validations : draft.value.transforms,
    type = validation ? 'REQUIRED' : 'TRIM'
  let i = rows.length + 1
  while (rows.some((r) => r.ruleId === 'rule-' + i)) i++
  rows.push({
    type,
    form: prepareForm(
      stringify((validation ? validationExamples : transformExamples)[type])!,
      ruleSchemas[type]!,
    ),
    ruleId: validation ? 'rule-' + i : '',
    onFailure: 'FAIL_TASK',
    properties: stringify((validation ? validationExamples : transformExamples)[type], null, 2)!,
    original: '{}',
  })
  selection.value[validation ? 'validations' : 'transforms'] = rowKey(rows.at(-1)!)
}
function changeRule(rule: Rule, type: string, validation: boolean) {
  rule.type = type
  rule.properties = stringify((validation ? validationExamples : transformExamples)[type], null, 2)!
  rule.form = prepareForm(rule.properties, ruleSchemas[type]!)
}
function move(rows: Rule[], index: number, offset: number) {
  const other = rows[index + offset]
  if (!other) return
  const kind = rows === draft.value.transforms ? 'transforms' : 'validations'
  const selected = selectedRule(kind)
  if (selected) selection.value[kind] = rowKey(selected)
  rows[index + offset] = rows[index]!
  rows[index] = other
}
function ruleSummary(rule: Rule) {
  const entries = rule.form?.entries ?? []
  const field = entries.find((entry) => entry.key === 'field')
  if (field?.node.value) return field.node.value
  const fields = entries.find((entry) => entry.key === 'fields')
  if (fields)
    return (
      fields.node.entries
        .map((entry) => entry.node.value)
        .filter(Boolean)
        .join(', ') || '—'
    )
  return entries.map((entry) => entry.key).join(', ') || '—'
}
function ruleOptions(validation: boolean) {
  return Object.keys(validation ? validationExamples : transformExamples)
    .filter((value) => !isMessageSource(draft.value.source.type) || value !== 'DEDUPLICATE')
    .map((value) => ({
      value,
      label: t('etl.rule_' + value),
    }))
}
async function focusError(key: string) {
  const path = key.replace(/^etl\./, '')
  const rulePath = /^(transforms|validations)\.(\d+)/.exec(path)
  if (rulePath) {
    const kind = rulePath[1] as RuleKind,
      rule = draft.value[kind][Number(rulePath[2])]
    if (rule) selection.value[kind] = rowKey(rule)
  }
  tab.value =
    path.startsWith('transforms.') || path.startsWith('validations.')
      ? 'rules'
      : path.startsWith('target.')
        ? 'target'
        : path.startsWith('source.') || path === 'message.metadataColumns'
          ? 'source'
          : 'incremental'
  expanded.value = [
    'source',
    'target',
    'file',
    'execution',
    ...draft.value.transforms.map((_, i) => 't' + i),
    ...draft.value.validations.map((_, i) => 'v' + i),
  ]
  await nextTick()
  const el = root.value?.querySelector<HTMLElement>('[data-etl-key="' + path + '"]')
  el?.scrollIntoView({ block: 'center' })
  el?.querySelector<HTMLElement>('input,textarea,.cm-content,button')?.focus()
}
defineExpose({ focusError })
</script>
<template>
  <section ref="root" class="etl-fields">
    <p v-if="draft.blocked" role="alert">{{ t('etl.unsupported') }}</p>
    <template v-else>
      <LineTabs
        id="etl-tab"
        :label="t('etl.sections')"
        :tabs="tabs"
        :value="tab"
        panel-id="etl-panel"
        :disabled="disabled"
        @change="tab = $event"
      />
      <div id="etl-panel" role="tabpanel" :aria-labelledby="'etl-tab-' + tab">
        <template v-if="tab === 'source'">
          <h3>{{ t('etl.sourceTitle') }}</h3>
          <div class="task-advanced-grid">
            <EtlField
              v-for="field in sourcePrimary"
              :key="field.key + String(draft.source.type)"
              :field="field"
              section="source"
              :value="draft.source[field.key]"
              :resource-type="String(draft.source.type)"
              :label="
                field.key === 'path'
                  ? t(draft.incremental.enabled ? 'etl.scanRoot' : 'etl.filePath')
                  : undefined
              "
              :disabled="disabled"
              :error="error('source.' + field.key)"
              @update:value="update('source', field, $event)"
              @select="emit('select', $event)"
              @expanded="emit('expanded', $event)"
            />
          </div>
          <p class="task-note">
            {{
              t(
                draft.source.type === 'FILE'
                  ? draft.incremental.enabled
                    ? 'etl.scanHelp'
                    : 'etl.fileHelp'
                  : isMessageSource(draft.source.type)
                    ? draft.source.type === 'KAFKA'
                      ? 'etl.kafkaHelp'
                      : 'etl.rabbitHelp'
                    : 'etl.databaseHelp',
              )
            }}
          </p>
          <EtlMessageFields
            v-if="isMessageSource(draft.source.type)"
            v-model:draft="draft"
            mode="source"
            :disabled="disabled"
            :errors="errors"
            :submitted="submitted"
            @select="emit('select', $event)"
          />
          <NCollapse v-if="sourceDetails.length" v-model:expanded-names="expanded"
            ><NCollapseItem name="source" :title="t('etl.sourceAdvanced')"
              ><div class="task-advanced-grid">
                <EtlField
                  v-for="field in sourceDetails"
                  :key="field.key"
                  :field="field"
                  section="source"
                  :value="draft.source[field.key]"
                  :disabled="disabled"
                  :error="error('source.' + field.key)"
                  @update:value="update('source', field, $event)"
                  @expanded="emit('expanded', $event)"
                  @select="emit('select', $event)"
                />
              </div>
              <p class="task-note">{{ t('etl.sourceAdvancedHelp') }}</p></NCollapseItem
            ></NCollapse
          >
        </template>
        <template v-else-if="tab === 'rules'">
          <p class="task-note">{{ t('etl.rulesHelp') }}</p>
          <section
            v-for="kind in ['transforms', 'validations'] as const"
            :key="kind"
            class="etl-rule-section"
          >
            <EtlTable
              :title="t('etl.' + kind)"
              :count="draft[kind].length"
              :add-label="t('etl.addRule')"
              :disabled="disabled"
              :columns="[
                { key: 'type', label: t('etl.ruleType') },
                {
                  key: 'summary',
                  label: t(kind === 'validations' ? 'etl.onFailure' : 'etl.ruleProperties'),
                },
                { key: 'actions', label: t('common.actions'), width: '124px' },
              ]"
              @add="addRule(kind === 'validations')"
            >
              <tr
                v-for="(rule, index) in draft[kind]"
                :key="rowKey(rule)"
                :class="{ 'etl-rule-selected': rule === selectedRule(kind) }"
                @click="selection[kind] = rowKey(rule)"
              >
                <td>
                  <NButton
                    text
                    type="primary"
                    :aria-pressed="rule === selectedRule(kind)"
                    @click.stop="selection[kind] = rowKey(rule)"
                  >
                    {{ index + 1 }}. {{ t('etl.rule_' + rule.type) }}
                  </NButton>
                  <small v-if="kind === 'validations'" class="etl-rule-id">{{
                    rule.ruleId || '—'
                  }}</small>
                  <small v-if="error(kind + '.' + index)" class="etl-error">{{
                    error(kind + '.' + index)
                  }}</small>
                </td>
                <td class="etl-rule-summary">
                  {{
                    kind === 'validations' ? t('etl.option_' + rule.onFailure) : ruleSummary(rule)
                  }}
                </td>
                <td class="etl-table-actions">
                  <div class="etl-order-actions">
                    <NButton
                      size="small"
                      quaternary
                      :aria-label="t('etl.up')"
                      :title="t('etl.up')"
                      :disabled="disabled || index === 0"
                      @click.stop="move(draft[kind], index, -1)"
                      ><ArrowUp :size="14"
                    /></NButton>
                    <NButton
                      size="small"
                      quaternary
                      :aria-label="t('etl.down')"
                      :title="t('etl.down')"
                      :disabled="disabled || index === draft[kind].length - 1"
                      @click.stop="move(draft[kind], index, 1)"
                      ><ArrowDown :size="14"
                    /></NButton>
                    <NButton
                      size="small"
                      text
                      type="error"
                      :disabled="disabled"
                      :aria-label="t('etl.removeRule', { n: index + 1 })"
                      @click.stop="removeRule(kind, index)"
                      >{{ t('tasks.delete') }}</NButton
                    >
                  </div>
                </td>
              </tr>
            </EtlTable>
            <template v-for="(rule, index) in draft[kind]" :key="rowKey(rule)">
              <section
                v-if="rule === selectedRule(kind)"
                class="etl-rule-editor"
                :data-etl-key="kind + '.' + index"
              >
                <h3>{{ t('etl.ruleEditor', { n: index + 1 }) }}</h3>
                <div class="task-advanced-grid">
                  <FormItem :id="'etl-' + kind + '-' + index + '-type'" :label="t('etl.ruleType')">
                    <NSelect
                      :value="rule.type"
                      :options="ruleOptions(kind === 'validations')"
                      :disabled="disabled"
                      :input-props="{
                        id: 'etl-' + kind + '-' + index + '-type',
                        'aria-label': t('etl.ruleType') + ' ' + (index + 1),
                      }"
                      @update:value="changeRule(rule, $event, kind === 'validations')"
                      @update:show="emit('select', $event)"
                    />
                  </FormItem>
                  <template v-if="kind === 'validations'">
                    <FormItem
                      :id="'etl-rule-id-' + index"
                      :label="t('etl.ruleId')"
                      class="etl-rule-meta"
                      required
                    >
                      <NInput
                        v-model:value="rule.ruleId"
                        :disabled="disabled"
                        :input-props="{
                          id: 'etl-rule-id-' + index,
                          'aria-label': t('etl.ruleId') + ' ' + (index + 1),
                        }"
                      />
                    </FormItem>
                    <FormItem
                      :id="'etl-failure-' + index"
                      :label="t('etl.onFailure')"
                      class="etl-rule-meta"
                    >
                      <NSelect
                        v-model:value="rule.onFailure"
                        :disabled="disabled"
                        :input-props="{
                          id: 'etl-failure-' + index,
                          'aria-label': t('etl.onFailure') + ' ' + (index + 1),
                        }"
                        :options="
                          (isMessageSource(draft.source.type)
                            ? ['FAIL_TASK']
                            : ['FILTER', 'FAIL_TASK']
                          ).map((value) => ({
                            value,
                            label: t('etl.option_' + value),
                          }))
                        "
                        @update:show="emit('select', $event)"
                      />
                    </FormItem>
                  </template>
                </div>
                <p v-if="error(kind + '.' + index)" class="etl-error" role="alert">
                  {{ error(kind + '.' + index) }}
                </p>
                <EtlFormNode
                  :id="'etl-rule-' + kind + '-' + index"
                  :node="rule.form!"
                  :submitted="submitted"
                  :schema="ruleSchemas[rule.type]"
                  :disabled="disabled"
                  @select="emit('select', $event)"
                />
              </section>
            </template>
          </section>
        </template>
        <template v-else-if="tab === 'target'">
          <h3>{{ t('etl.targetTitle') }}</h3>
          <div class="task-advanced-grid">
            <EtlField
              v-for="field in targetPrimary"
              :key="field.key + String(draft.target.type)"
              :field="field"
              section="target"
              :value="draft.target[field.key]"
              :resource-type="String(draft.target.type)"
              :disabled="disabled"
              :error="error('target.' + field.key)"
              @update:value="update('target', field, $event)"
              @expanded="emit('expanded', $event)"
              @select="emit('select', $event)"
            />
          </div>
          <div data-etl-key="target.columns">
            <p class="task-note">
              {{
                t(draft.target.mappingMode === 'AUTO' ? 'etl.autoMappingHelp' : 'etl.mappingHelp')
              }}
            </p>
            <p
              v-if="draft.target.type === 'FILE' && draft.target.mappingMode === 'AUTO'"
              class="task-note"
            >
              {{ t('etl.autoFileHelp') }}
            </p>
            <EtlTable
              :title="
                t(draft.target.mappingMode === 'AUTO' ? 'etl.mappingOverrides' : 'etl.mapping')
              "
              :count="draft.columns.length"
              :add-label="t('etl.addColumn')"
              :disabled="disabled"
              :columns="[
                { key: 'source', label: t('etl.sourceColumn'), required: true },
                { key: 'target', label: t('etl.targetColumn'), required: true },
                { key: 'actions', label: t('common.actions'), width: '78px' },
              ]"
              @add="draft.columns.push({ target: '', source: '' })"
            >
              <tr v-for="(column, index) in draft.columns" :key="rowKey(column)">
                <td>
                  <NInput
                    v-model:value="column.source"
                    :disabled="disabled"
                    :input-props="{
                      id: 'etl-column-source-' + index,
                      'aria-label': t('etl.sourceColumn') + ' ' + (index + 1),
                      'aria-required': true,
                    }"
                  />
                </td>
                <td>
                  <NInput
                    v-model:value="column.target"
                    :disabled="disabled"
                    :input-props="{
                      id: 'etl-column-target-' + index,
                      'aria-label': t('etl.targetColumn') + ' ' + (index + 1),
                      'aria-required': true,
                    }"
                  />
                </td>
                <td class="etl-table-actions">
                  <NButton
                    size="small"
                    text
                    type="error"
                    :aria-label="t('etl.removeColumn', { n: index + 1 })"
                    :disabled="disabled"
                    @click="draft.columns.splice(index, 1)"
                    >{{ t('tasks.delete') }}</NButton
                  >
                </td>
              </tr>
              <template #feedback
                ><p v-if="error('target.columns')" class="etl-error" role="alert">
                  {{ error('target.columns') }}
                </p></template
              >
            </EtlTable>
          </div>
          <p class="task-note">
            {{
              t(
                draft.incremental.enabled && draft.source.type === 'FILE'
                  ? 'etl.targetIncrementalHelp'
                  : 'etl.targetHelp',
              )
            }}
          </p>
          <NCollapse v-model:expanded-names="expanded"
            ><NCollapseItem name="target" :title="t('etl.targetAdvanced')"
              ><div class="task-advanced-grid">
                <EtlField
                  v-for="field in targetDetails.filter((f) => !targetSchemas[f.key])"
                  :key="field.key"
                  :field="field"
                  section="target"
                  :value="draft.target[field.key]"
                  :disabled="disabled"
                  :error="error('target.' + field.key)"
                  @update:value="update('target', field, $event)"
                  @expanded="emit('expanded', $event)"
                  @select="emit('select', $event)"
                />
              </div>
              <div
                v-for="field in targetDetails.filter((f) => targetSchemas[f.key])"
                :key="field.key"
                :data-etl-key="'target.' + field.key"
                class="etl-target-form"
              >
                <h4 v-if="targetSchemas[field.key]?.fields">
                  {{ t('etl.form_section_' + field.key) }}
                </h4>
                <p v-if="error('target.' + field.key)" role="alert" class="etl-error">
                  {{ error('target.' + field.key) }}
                </p>
                <EtlFormNode
                  :id="'etl-target-' + field.key"
                  :node="draft.targetForms[field.key]!"
                  :submitted="submitted"
                  :schema="targetSchemas[field.key]"
                  :table-title="
                    !targetSchemas[field.key]?.fields
                      ? t('etl.form_section_' + field.key)
                      : undefined
                  "
                  :disabled="disabled"
                  @select="emit('select', $event)"
                />
              </div>
              <FormItem
                v-if="!isMessageSource(draft.source.type)"
                id="etl-non-idempotent"
                :label="t('etl.allowRetry')"
                ><NSwitch
                  id="etl-non-idempotent"
                  v-model:value="draft.allowNonIdempotentRetry"
                  :disabled="disabled"
                  :aria-label="t('etl.allowRetry')"
              /></FormItem>
              <p v-if="!isMessageSource(draft.source.type)" class="task-note">
                {{ t('etl.retryHelp') }}
              </p>
              <p v-else class="task-note">{{ t('etl.messageTargetHelp') }}</p>
            </NCollapseItem></NCollapse
          >
        </template>
        <template v-else-if="isMessageSource(draft.source.type)">
          <EtlMessageFields
            v-model:draft="draft"
            mode="limits"
            :disabled="disabled"
            :errors="errors"
            :submitted="submitted"
            @select="emit('select', $event)"
          />
        </template>
        <template v-else>
          <div class="etl-heading">
            <h3>{{ t('etl.incrementalTitle') }}</h3>
            <NSwitch
              id="task-etl.incremental.enabled"
              :value="draft.incremental.enabled === true"
              :disabled="disabled"
              :aria-label="t('etl.field_enabled')"
              @update:value="draft.incremental.enabled = $event"
            />
          </div>
          <div class="task-advanced-grid">
            <EtlField
              v-for="item in incrementalPrimary"
              :key="item.section + item.field.key"
              :field="item.field"
              :section="item.section"
              :value="draft[item.section][item.field.key]"
              :disabled="disabled"
              :error="error(item.section + '.' + item.field.key)"
              @update:value="update(item.section, item.field, $event)"
              @expanded="emit('expanded', $event)"
              @select="emit('select', $event)"
            />
          </div>
          <TieBreakerEditor
            v-if="draft.incremental.enabled && draft.source.type !== 'FILE'"
            :fields="String(draft.incremental.tieBreakerFields)"
            :values="String(draft.incremental.initialTieBreakerValues)"
            :disabled="disabled"
            :error="
              error('incremental.initialTieBreakerValues') || error('incremental.tieBreakerFields')
            "
            @change="
              (fields, values) => {
                draft.incremental.tieBreakerFields = fields
                draft.incremental.initialTieBreakerValues = values
              }
            "
            @expanded="emit('expanded', $event)"
            @select="emit('select', $event)"
          />
          <template v-if="draft.incremental.enabled">
            <p class="etl-info">
              {{
                t(
                  draft.source.type === 'FILE'
                    ? 'etl.completedHelp'
                    : 'etl.databaseIncrementalHelp',
                )
              }}
            </p>
            <details class="etl-help">
              <summary>{{ t('etl.markerHelp') }}</summary>
              <p class="task-note">{{ t('etl.precisionHelp') }}</p>
              <template v-if="draft.source.type === 'FILE'"
                ><p class="task-note">{{ t('etl.selectionHelp') }} {{ t('etl.lookbackHelp') }}</p>
                <p class="task-note">{{ t('etl.fileIncrementalHelp') }}</p></template
              >
            </details>
          </template>
          <NCollapse v-model:expanded-names="expanded">
            <NCollapseItem v-if="fileKeys(draft).length" name="file" :title="t('etl.fileAdvanced')"
              ><div class="task-advanced-grid">
                <EtlField
                  v-for="field in fields(fileFields, fileKeys(draft), fileAdvanced, true)"
                  :key="field.key"
                  :field="field"
                  section="file"
                  :value="draft.file[field.key]"
                  :disabled="disabled"
                  :error="error('file.' + field.key)"
                  @update:value="update('file', field, $event)"
                  @select="emit('select', $event)"
                />
              </div>
              <p class="task-note">{{ t('etl.readyHelp') }}</p></NCollapseItem
            >
            <NCollapseItem name="execution" :title="t('etl.execution')"
              ><div class="task-advanced-grid">
                <EtlField
                  v-for="field in executionFields"
                  :key="field.key"
                  :field="field"
                  section="execution"
                  :value="draft.execution[field.key]"
                  :disabled="disabled"
                  :error="error('execution.' + field.key)"
                  @update:value="update('execution', field, $event)"
                /></div
            ></NCollapseItem>
          </NCollapse>
        </template>
      </div>
    </template>
  </section>
</template>
<style scoped>
.etl-fields {
  min-width: 0;
}
.etl-fields h3 {
  font-size: 13px;
  margin: 12px 0;
  border-left: 2px solid var(--primary);
  padding-left: 8px;
}
.etl-wide {
  grid-column: 1/-1;
}
.etl-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.etl-help {
  color: var(--muted);
  font-size: 12px;
  margin: 12px 0;
}
.etl-help summary {
  cursor: pointer;
}
.etl-help p {
  margin: 8px 0;
}
.etl-order-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 4px;
}
.etl-rule-id,
.etl-rule-summary {
  overflow-wrap: anywhere;
}
.etl-rule-id {
  display: block;
  color: var(--muted);
  margin-top: 4px;
}
.etl-rule-selected :deep(td) {
  background: var(--selected);
}
.etl-rule-editor {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.etl-rule-editor > h3 {
  margin-top: 0;
}
.etl-rule-section :deep(.etl-table-scroll) {
  max-height: 240px;
  overflow-y: auto;
}
.etl-rule-section :deep(thead th) {
  position: sticky;
  top: 0;
  z-index: 2;
}
.etl-rule-section :deep(tbody tr) {
  cursor: pointer;
}
.etl-target-form {
  margin: 0;
}
.etl-target-form > h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
}
.etl-rule-section {
  margin-bottom: 20px;
}
.etl-error {
  color: var(--error);
  font-size: 12px;
}
.etl-info {
  padding: 10px 12px;
  background: var(--selected);
  border-left: 2px solid var(--primary);
  font-size: 12px;
}
.etl-fields :deep(.task-advanced-grid) > * {
  min-width: 0;
}
.etl-fields :deep(.n-collapse) {
  margin-top: 12px;
}
.etl-fields :deep(.n-collapse-item__content-inner) {
  padding-top: 8px;
}
</style>
