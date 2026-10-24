<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, nextTick, ref, watch } from 'vue'
import { NButton, NCheckbox, NCollapse, NCollapseItem, NInput, NSelect, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import EtlTable from './EtlTable.vue'
import PushResourcePicker from './PushResourcePicker.vue'
import BatchEtlValue from './BatchEtlValue.vue'
import BatchTableDialog from './BatchTableDialog.vue'
import { newTableNames } from './batch-table-selection'
import { batchLimits, type BatchEtlDraft, type BatchLimit } from './batch-etl-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
  retries: number
}>()
const draft = defineModel<BatchEtlDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n(),
  tab = ref('tables'),
  expanded = ref<string[]>([]),
  root = ref<HTMLElement>()
const query = ref('')
const metadataMode = ref<'picker' | 'check'>()
watch(metadataMode, (value) => emit('select', !!value))
function appendTables(names: string[]) {
  const added = newTableNames(draft.value.tables, names)
  if (draft.value.tables.length + added.length > 500) return
  draft.value.tables.push(...added.map((name) => ({ name })))
}
const editingRow = ref<BatchEtlDraft['tables'][number]>()
watch(query, () => {
  editingRow.value = undefined
})
const visibleTables = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  return draft.value.tables
    .map((row, i) => ({ row, i }))
    .filter(
      ({ row }) =>
        row === editingRow.value ||
        !q ||
        (row.name + ' ' + draft.value.prefix + row.name + draft.value.suffix)
          .toLocaleLowerCase()
          .includes(q),
    )
})
async function addTable() {
  query.value = ''
  draft.value.tables.push({ name: '' })
  await nextTick()
  root.value
    ?.querySelector<HTMLElement>(
      '[id="task-batchEtl.tables.' + (draft.value.tables.length - 1) + '"]',
    )
    ?.focus()
}
const incremental = computed(() => draft.value.extractMode === 'INCREMENTAL')
const tabs = computed(() =>
  ['tables', 'write', 'incremental'].map((value) => ({ value, label: t('batchEtl.tab_' + value) })),
)
const options = (values: string[]) =>
  values.map((value) => ({ value, label: t('batchEtl.' + value) }))
const error = (key: string) =>
  props.submitted && props.errors['batchEtl.' + key]
    ? t('batchEtl.' + props.errors['batchEtl.' + key])
    : undefined
const label = (row: number, field: string) =>
  t('batchEtl.rowLabel', { row: row + 1, field: t('batchEtl.' + field) })
const rowKeys = new WeakMap<object, number>()
let nextKey = 0
function rowKey(row: object) {
  if (!rowKeys.has(row)) rowKeys.set(row, ++nextKey)
  return rowKeys.get(row)!
}
function move<T>(rows: T[], i: number, n: number) {
  const a = rows[i + n]
  if (a === undefined) return
  rows[i + n] = rows[i]!
  rows[i] = a
}
async function focusError(key: string) {
  const path = key.replace(/^batchEtl\./, '')
  tab.value = path.startsWith('keys')
    ? incremental.value
      ? 'incremental'
      : 'write'
    : [
          'loadMode',
          'nullPolicy',
          'clearMode',
          'acknowledgePartialCommit',
          'acknowledgeNonTransactionalTruncate',
          'allowNonIdempotentRetry',
        ].includes(path)
      ? 'write'
      : path.startsWith('limits.') || ['initial', 'watermarkType', 'watermarkColumn'].includes(path)
        ? 'incremental'
        : 'tables'
  if (path.startsWith('tables')) query.value = ''
  if (path.startsWith('limits.')) expanded.value = ['limits']
  await nextTick()
  root.value?.querySelector<HTMLElement>('[id="task-batchEtl.' + path + '"]')?.focus()
}
defineExpose({ focusError })
</script>
<template>
  <section ref="root" class="batch-etl-fields">
    <BatchTableDialog
      v-if="metadataMode"
      :draft="draft"
      :mode="metadataMode"
      @close="metadataMode = undefined"
      @add="appendTables"
    />
    <p v-if="draft.blocked" role="alert">{{ t('batchEtl.unsupported') }}</p>
    <template v-else>
      <LineTabs
        id="batch-etl-tabs"
        :label="t('batchEtl.title')"
        :value="tab"
        :tabs="tabs"
        panel-id="batch-etl-panel"
        :disabled="disabled"
        @change="tab = $event"
      />
      <section
        id="batch-etl-panel"
        role="tabpanel"
        :aria-labelledby="'batch-etl-tabs-' + tab"
        class="batch-panel"
      >
        <div v-show="tab === 'tables'">
          <p class="task-note">{{ t('batchEtl.intro') }}</p>
          <div class="task-advanced-grid">
            <FormItem
              v-for="key in ['sourceResourceId', 'targetResourceId'] as const"
              :id="'task-batchEtl.' + key"
              :key="key"
              :label="t('batchEtl.' + key)"
              required
              :invalid="!!error(key)"
              :message="error(key)"
            >
              <PushResourcePicker
                :id="'task-batchEtl.' + key"
                v-model:value="draft[key]"
                type="DB"
                :label="t('batchEtl.' + key)"
                :disabled="disabled"
                :invalid="!!error(key)"
                @select="emit('select', $event)"
              />
            </FormItem>
            <FormItem
              v-for="key in ['sourceSchema', 'targetSchema', 'prefix', 'suffix'] as const"
              :id="'task-batchEtl.' + key"
              :key="key"
              :label="t('batchEtl.' + key)"
              :invalid="!!error(key)"
              :message="error(key)"
            >
              <NInput
                v-model:value="draft[key]"
                :disabled="disabled"
                :input-props="{ id: 'task-batchEtl.' + key, 'aria-invalid': !!error(key) }"
              />
            </FormItem>
          </div>
          <div id="task-batchEtl.tables" class="batch-tables-section" tabindex="-1">
            <EtlTable
              :title="t('batchEtl.tables')"
              :count="draft.tables.length"
              :disabled="disabled || draft.tables.length >= 500"
              :columns="[
                { key: 'index', label: '#', width: '44px' },
                { key: 'source', label: t('batchEtl.sourceTable'), required: true },
                { key: 'target', label: t('batchEtl.targetTable') },
                { key: 'actions', label: t('batchEtl.actions'), width: '128px' },
              ]"
              @add="addTable"
            >
              <template #tools>
                <div class="batch-search batch-table-actions">
                  <NButton
                    size="small"
                    :disabled="disabled || draft.tables.length >= 500"
                    @click="addTable"
                    >{{ t('batchEtl.addTable') }}</NButton
                  >
                  <NButton
                    size="small"
                    :disabled="disabled || !draft.sourceResourceId"
                    @click="metadataMode = 'picker'"
                    >{{ t('insights.picker') }}</NButton
                  >
                  <NButton
                    size="small"
                    :disabled="
                      disabled ||
                      !draft.sourceResourceId ||
                      !draft.targetResourceId ||
                      !draft.tables.length
                    "
                    @click="metadataMode = 'check'"
                    >{{ t('insights.check') }}</NButton
                  >
                </div>
                <div v-if="draft.tables.length > 6 || query" class="batch-search">
                  <NInput
                    v-model:value="query"
                    size="small"
                    clearable
                    :placeholder="t('details.searchRows')"
                    :input-props="{ 'aria-label': t('details.searchRows') }"
                  />
                  <span aria-live="polite">{{
                    t('details.rowCounts', {
                      total: draft.tables.length,
                      count: visibleTables.length,
                    })
                  }}</span>
                </div>
              </template>
              <tr v-if="query && !visibleTables.length">
                <td colspan="4" class="list-empty-cell">
                  <EmptyState compact :message="t('common.empty')" />
                </td>
              </tr>
              <tr v-for="{ row, i } in visibleTables" :key="rowKey(row)">
                <td class="batch-index">{{ i + 1 }}</td>
                <td>
                  <NInput
                    v-model:value="row.name"
                    size="small"
                    :disabled="disabled"
                    :input-props="{
                      id: 'task-batchEtl.tables.' + i,
                      'aria-label': label(i, 'sourceTable'),
                      'aria-invalid': !!error('tables.' + i),
                    }"
                    @focus="editingRow = row"
                  />
                  <p v-if="error('tables.' + i)" class="batch-error">{{ error('tables.' + i) }}</p>
                </td>
                <td
                  class="batch-target"
                  :title="row.name ? draft.prefix + row.name + draft.suffix : undefined"
                >
                  {{ row.name ? draft.prefix + row.name + draft.suffix : '—' }}
                </td>
                <td class="etl-table-actions">
                  <div class="batch-actions">
                    <NButton
                      text
                      :disabled="disabled || !!query.trim() || i === 0"
                      :aria-label="label(i, 'up')"
                      @click="move(draft.tables, i, -1)"
                      >↑</NButton
                    ><NButton
                      text
                      :disabled="disabled || !!query.trim() || i === draft.tables.length - 1"
                      :aria-label="label(i, 'down')"
                      @click="move(draft.tables, i, 1)"
                      >↓</NButton
                    ><NButton
                      text
                      type="error"
                      :disabled="disabled"
                      :aria-label="label(i, 'remove')"
                      @click="draft.tables.splice(i, 1)"
                      >{{ t('batchEtl.remove') }}</NButton
                    >
                  </div>
                </td>
              </tr>
            </EtlTable>
            <p v-if="error('tables')" class="batch-error">{{ error('tables') }}</p>
          </div>
          <p class="task-note">{{ t('batchEtl.tableHelp') }}</p>
        </div>
        <template v-if="tab === 'write'">
          <div class="task-advanced-grid">
            <FormItem
              v-for="key in ['loadMode', 'nullPolicy'] as const"
              :id="'task-batchEtl.' + key"
              :key="key"
              :label="t('batchEtl.' + key)"
              :invalid="!!error(key)"
              :message="error(key)"
              ><NSelect
                v-model:value="draft[key]"
                filterable
                :node-props="() => ({ role: 'option' })"
                :menu-props="{ role: 'listbox', 'aria-label': t('batchEtl.' + key) }"
                :disabled="disabled"
                :options="
                  options(
                    key === 'loadMode'
                      ? ['UPSERT', 'INSERT_ONLY', 'UPDATE_ONLY', 'APPEND']
                      : ['OVERWRITE', 'KEEP_TARGET'],
                  )
                "
                :input-props="{ id: 'task-batchEtl.' + key, 'aria-label': t('batchEtl.' + key) }"
                @update:show="emit('select', $event)"
            /></FormItem>
          </div>
          <p class="task-note">{{ t('batchEtl.keyHelp') }}</p>
        </template>
        <template v-if="tab === 'incremental'">
          <div class="task-advanced-grid">
            <FormItem id="task-batchEtl.extractMode" :label="t('batchEtl.extractMode')"
              ><NSelect
                v-model:value="draft.extractMode"
                filterable
                :node-props="() => ({ role: 'option' })"
                :menu-props="{ role: 'listbox', 'aria-label': t('batchEtl.extractMode') }"
                :disabled="disabled"
                :options="options(['FULL', 'INCREMENTAL'])"
                :input-props="{
                  id: 'task-batchEtl.extractMode',
                  'aria-label': t('batchEtl.extractMode'),
                }"
                @update:show="emit('select', $event)"
            /></FormItem>
            <FormItem
              v-if="incremental"
              id="task-batchEtl.watermarkType"
              :label="t('batchEtl.watermarkType')"
              ><NSelect
                v-model:value="draft.watermarkType"
                filterable
                :node-props="() => ({ role: 'option' })"
                :menu-props="{ role: 'listbox', 'aria-label': t('batchEtl.watermarkType') }"
                :disabled="disabled"
                :options="options(['LONG', 'DECIMAL', 'DATE', 'TIMESTAMP', 'STRING'])"
                :input-props="{
                  id: 'task-batchEtl.watermarkType',
                  'aria-label': t('batchEtl.watermarkType'),
                }"
                @update:show="emit('select', $event)"
            /></FormItem>
            <FormItem
              v-if="incremental"
              id="task-batchEtl.watermarkColumn"
              :label="t('batchEtl.watermarkColumn')"
              required
              :invalid="!!error('watermarkColumn')"
              :message="error('watermarkColumn')"
              ><NInput
                v-model:value="draft.watermarkColumn"
                :disabled="disabled"
                :input-props="{ id: 'task-batchEtl.watermarkColumn' }"
            /></FormItem>
            <FormItem
              v-if="incremental"
              id="task-batchEtl.initial"
              :label="t('batchEtl.initial')"
              required
              :invalid="!!error('initial')"
              :message="error('initial')"
              ><BatchEtlValue
                id="task-batchEtl.initial"
                v-model="draft.initial"
                :disabled="disabled"
                :invalid="!!error('initial')"
                :label="t('batchEtl.initial')"
                @select="emit('select', $event)"
            /></FormItem>
          </div>
          <p
            v-if="incremental && ['DATE', 'TIMESTAMP'].includes(draft.watermarkType)"
            class="task-note"
          >
            {{ t('batchEtl.temporalHelp') }}
          </p>
        </template>
        <template
          v-if="(tab === 'write' && !incremental) || (tab === 'incremental' && incremental)"
        >
          <div id="task-batchEtl.keys" tabindex="-1">
            <EtlTable
              :title="t('batchEtl.' + (incremental ? 'keysInitial' : 'keys'))"
              :count="draft.keys.length"
              :disabled="disabled"
              :add-label="t('batchEtl.addKey')"
              :columns="[
                { key: 'key', label: t('batchEtl.key'), required: true },
                ...(incremental
                  ? [
                      {
                        key: 'initial',
                        label: t('batchEtl.initialKey'),
                        required: true,
                        width: '240px',
                      },
                    ]
                  : []),
                { key: 'actions', label: t('batchEtl.actions'), width: '128px' },
              ]"
              @add="draft.keys.push({ name: '', initial: { kind: 'string', text: '' } })"
            >
              <tr v-for="(row, i) in draft.keys" :key="rowKey(row)">
                <td>
                  <NInput
                    v-model:value="row.name"
                    size="small"
                    :disabled="disabled"
                    :input-props="{
                      id: 'task-batchEtl.keys.' + i + '.name',
                      'aria-label': label(i, 'key'),
                      'aria-invalid': !!error('keys.' + i + '.name'),
                    }"
                  />
                  <p v-if="error('keys.' + i + '.name')" class="batch-error">
                    {{ error('keys.' + i + '.name') }}
                  </p>
                </td>
                <td v-if="incremental">
                  <BatchEtlValue
                    :id="'task-batchEtl.keys.' + i + '.initial'"
                    v-model="row.initial"
                    :label="label(i, 'initialKey')"
                    :disabled="disabled"
                    :invalid="!!error('keys.' + i + '.initial')"
                    boolean
                    @select="emit('select', $event)"
                  />
                  <p v-if="error('keys.' + i + '.initial')" class="batch-error">
                    {{ error('keys.' + i + '.initial') }}
                  </p>
                </td>
                <td class="etl-table-actions">
                  <div class="batch-actions">
                    <NButton
                      text
                      :disabled="disabled || !!query.trim() || i === 0"
                      :aria-label="label(i, 'up')"
                      @click="move(draft.keys, i, -1)"
                      >↑</NButton
                    ><NButton
                      text
                      :disabled="disabled || i === draft.keys.length - 1"
                      :aria-label="label(i, 'down')"
                      @click="move(draft.keys, i, 1)"
                      >↓</NButton
                    ><NButton
                      text
                      type="error"
                      :disabled="disabled"
                      :aria-label="label(i, 'remove')"
                      @click="draft.keys.splice(i, 1)"
                      >{{ t('batchEtl.remove') }}</NButton
                    >
                  </div>
                </td>
              </tr>
            </EtlTable>
            <p v-if="error('keys')" class="batch-error">{{ error('keys') }}</p>
          </div>
          <p v-if="incremental" class="task-note">{{ t('batchEtl.initialHelp') }}</p>
        </template>
        <template v-if="tab === 'write'">
          <div v-if="!incremental" class="task-inline">
            <label for="task-batchEtl.clearBeforeImport">{{
              t('batchEtl.clearBeforeImport')
            }}</label
            ><NSwitch
              id="task-batchEtl.clearBeforeImport"
              v-model:value="draft.clearBeforeImport"
              :disabled="disabled"
              :aria-label="t('batchEtl.clearBeforeImport')"
            />
          </div>
          <FormItem
            v-if="!incremental && draft.clearBeforeImport"
            id="task-batchEtl.clearMode"
            :label="t('batchEtl.clearMode')"
            ><NSelect
              v-model:value="draft.clearMode"
              filterable
              :node-props="() => ({ role: 'option' })"
              :menu-props="{ role: 'listbox', 'aria-label': t('batchEtl.clearMode') }"
              :disabled="disabled"
              :options="options(['DELETE', 'TRUNCATE'])"
              :input-props="{
                id: 'task-batchEtl.clearMode',
                'aria-label': t('batchEtl.clearMode'),
              }"
              @update:show="emit('select', $event)"
          /></FormItem>
          <template
            v-for="key in [
              'acknowledgePartialCommit',
              'acknowledgeNonTransactionalTruncate',
              'allowNonIdempotentRetry',
            ] as const"
            :key="key"
            ><div
              v-if="
                key === 'allowNonIdempotentRetry'
                  ? draft.loadMode === 'APPEND' && retries > 0
                  : !incremental &&
                    draft.clearBeforeImport &&
                    (key === 'acknowledgePartialCommit'
                      ? draft.clearMode === 'DELETE'
                      : draft.clearMode === 'TRUNCATE')
              "
            >
              <NCheckbox
                :id="'task-batchEtl.' + key"
                v-model:checked="draft[key]"
                :disabled="disabled"
                >{{ t('batchEtl.' + key) }}</NCheckbox
              >
              <p v-if="error(key)" class="batch-error">{{ error(key) }}</p>
            </div></template
          >
        </template>
        <p v-if="['write', 'incremental'].includes(tab)" class="task-note">
          {{ t('batchEtl.commitHelp') }}
        </p>
        <template v-if="tab === 'incremental'">
          <NCollapse v-model:expanded-names="expanded"
            ><NCollapseItem name="limits" :title="t('batchEtl.limits')"
              ><div class="task-advanced-grid">
                <FormItem
                  v-for="key in Object.keys(batchLimits) as BatchLimit[]"
                  :id="'task-batchEtl.limits.' + key"
                  :key="key"
                  :label="t('batchEtl.' + key)"
                  required
                  :invalid="!!error('limits.' + key)"
                  :message="error('limits.' + key)"
                  ><NInput
                    v-model:value="draft.limits[key]"
                    :disabled="disabled"
                    :allow-input="(s: string) => /^\d*$/.test(s)"
                    :input-props="{
                      id: 'task-batchEtl.limits.' + key,
                      inputmode: 'numeric',
                      'aria-invalid': !!error('limits.' + key),
                    }"
                /></FormItem></div></NCollapseItem
          ></NCollapse>
          <p v-if="!expanded.length" class="task-note">
            {{
              t('batchEtl.limitsSummary', {
                fetch: draft.limits.fetchSize,
                write: draft.limits.writeBatchSize,
                timeout: draft.limits.queryTimeoutSeconds,
              })
            }}
          </p>
          <p v-if="incremental" class="task-note">{{ t('batchEtl.checkpointHelp') }}</p>
        </template>
      </section></template
    >
  </section>
</template>
<style scoped>
.batch-tables-section {
  margin-top: 12px;
}
.batch-etl-fields {
  min-width: 0;
  width: 100%;
}
.batch-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  min-width: 0;
}
.batch-error {
  color: var(--error);
  font-size: 12px;
  margin: 6px 0 0;
}
.batch-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 32px;
}
.batch-target {
  overflow-wrap: anywhere;
}
.batch-panel .task-note {
  margin: 0;
}
.batch-panel :deep(.n-collapse-item__content-inner) {
  padding-top: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}
.batch-search {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.batch-search > .n-input {
  flex: 1;
  min-width: 0;
}
.batch-table-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}
.batch-search > span {
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
}
.batch-index {
  color: var(--muted);
  padding-top: 12px !important;
}
@media (max-width: 480px) {
  .batch-search {
    flex-wrap: wrap;
  }
}
</style>
