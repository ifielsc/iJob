<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NCheckbox, NInput, NModal, NPagination, NSelect, NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { record } from '../../shared/api/values'
import RequestError from '../../shared/ui/RequestError.vue'
import { batchEtlConfig, type BatchEtlDraft } from './batch-etl-model'
import { newTableNames, tableIdentity } from './batch-table-selection'
const props = defineProps<{ draft: BatchEtlDraft; mode: 'picker' | 'check' }>()
const emit = defineEmits<{ close: []; add: [names: string[]] }>()
const runtime = useRuntime(),
  { t, te } = useI18n()
const query = ref(''),
  includeViews = ref(false),
  page = ref(1),
  pageSize = ref(10),
  sourceName = ref(''),
  selected = ref<string[]>([])
const tables = shallowRef<{ name: string; type: string }[]>([])
const checks = shallowRef<
  { sourceTable: string; targetTable: string; status: string; message: string }[]
>([])
const schema = ref(''),
  truncated = ref(false),
  busy = ref(false),
  error = shallowRef<unknown>()
const invalid = ref(false),
  checkedDraft = ref('')
let controller: AbortController | undefined
const identity = computed(() =>
  JSON.stringify([props.draft.sourceResourceId, props.draft.sourceSchema, includeViews.value]),
)
const existing = computed(() => new Set(props.draft.tables.map((r) => tableIdentity(r.name))))
const matching = computed(() =>
  tables.value.filter((r) => r.name.toLowerCase().includes(query.value.trim().toLowerCase())),
)
const visible = computed(() =>
  matching.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const selectable = computed(() =>
  visible.value.filter((r) => !existing.value.has(tableIdentity(r.name))),
)
const pageSelected = computed(
  () => selectable.value.filter((r) => selected.value.includes(r.name)).length,
)
function selectPage(checked: boolean) {
  if (checked) choose(visible.value)
  else {
    const names = new Set(selectable.value.map((r) => r.name))
    selected.value = selected.value.filter((name) => !names.has(name))
  }
}
const added = computed(() => newTableNames(props.draft.tables, selected.value))
const overLimit = computed(() => props.draft.tables.length + added.value.length > 500)
const checkStale = computed(
  () => !!checkedDraft.value && checkedDraft.value !== JSON.stringify(props.draft),
)
function toggle(name: string, checked: boolean) {
  if (existing.value.has(tableIdentity(name))) return
  selected.value = checked
    ? [...new Set([...selected.value, name])]
    : selected.value.filter((n) => n !== name)
}
function choose(rows: { name: string }[]) {
  selected.value = [
    ...new Set([
      ...selected.value,
      ...rows.filter((r) => !existing.value.has(tableIdentity(r.name))).map((r) => r.name),
    ]),
  ]
}
async function load() {
  controller?.abort()
  const current = new AbortController()
  controller = current
  error.value = undefined
  invalid.value = false
  busy.value = true
  checks.value = []
  try {
    if (props.mode === 'picker') {
      tables.value = []
      sourceName.value = '#' + props.draft.sourceResourceId
      void runtime
        .request('/api/resources/' + props.draft.sourceResourceId, {
          signal: current.signal,
          localForbidden: true,
        })
        .then((value) => {
          if (!current.signal.aborted)
            sourceName.value = String(record(value).name ?? sourceName.value)
        })
        .catch(() => {
          /* Keep the exact resource ID visible if its name cannot be resolved. */
        })
      const params = new URLSearchParams({
        resourceId: props.draft.sourceResourceId,
        schema: props.draft.sourceSchema,
        includeViews: String(includeViews.value),
      })
      const result = record(
        await runtime.request('/api/tasks/batch-etl/source-tables?' + params, {
          signal: current.signal,
        }),
      )
      if (current.signal.aborted) return
      if (!Array.isArray(result.tables)) throw Error('Invalid tables response')
      tables.value = result.tables.map((v) => {
        const r = record(v)
        return { name: String(r.name), type: String(r.type) }
      })
      schema.value = String(result.schema ?? '')
      truncated.value = result.truncated === true
    } else {
      let config: string
      try {
        config = batchEtlConfig(props.draft)
      } catch {
        invalid.value = true
        return
      }
      const snapshot = JSON.stringify(props.draft)
      const result = record(
        await runtime.request('/api/tasks/batch-etl/check-tables', {
          method: 'POST',
          body: { pluginConfig: config },
          signal: current.signal,
        }),
      )
      if (current.signal.aborted) return
      if (!Array.isArray(result.tables)) throw Error('Invalid checks response')
      checks.value = result.tables.map((v) => {
        const r = record(v)
        return {
          sourceTable: String(r.sourceTable),
          targetTable: String(r.targetTable),
          status: String(r.status),
          message: String(r.message),
        }
      })
      checkedDraft.value = snapshot
    }
  } catch (e) {
    if (!current.signal.aborted) error.value = e
  } finally {
    if (!current.signal.aborted) busy.value = false
  }
}
function add() {
  if (busy.value || overLimit.value || !added.value.length) return
  emit('add', added.value)
  emit('close')
}
watch(identity, () => {
  selected.value = []
  page.value = 1
  if (props.mode === 'picker') void load()
})
watch([query, pageSize], () => {
  page.value = 1
})
watch(
  () => runtime.session.revision,
  () => emit('close'),
)
onBeforeUnmount(() => controller?.abort())
void load()
</script>
<template>
  <NModal
    :show="true"
    preset="card"
    class="batch-metadata-modal"
    :title="t('insights.' + (mode === 'picker' ? 'picker' : 'check'))"
    :style="{ width: 'min(900px, calc(100vw - 24px))', maxHeight: 'calc(100dvh - 32px)' }"
    :mask-closable="false"
    :content-style="{ overflow: 'auto', minHeight: '0' }"
    @close="emit('close')"
    @update:show="
      (value) => {
        if (!value) emit('close')
      }
    "
  >
    <p class="task-note">{{ t('insights.metadataOnly') }}</p>
    <RequestError :error="error" />
    <p v-if="invalid" role="alert">{{ t('insights.invalid') }}</p>
    <template v-if="mode === 'picker'">
      <div class="metadata-tools metadata-filters">
        <span class="metadata-context" :title="sourceName"
          >{{ t('insights.sourceResource') }}: <strong>{{ sourceName }}</strong></span
        >
        <span class="metadata-context" :title="schema || draft.sourceSchema"
          >{{ t('insights.schema') }}:
          <strong>{{ schema || draft.sourceSchema || '—' }}</strong></span
        >
        <NInput
          v-model:value="query"
          clearable
          :placeholder="t('insights.search')"
          :input-props="{ 'aria-label': t('insights.search') }"
        />
        <NCheckbox v-model:checked="includeViews">{{ t('insights.views') }}</NCheckbox>
        <NButton :loading="busy" @click="load">{{ t('insights.refresh') }}</NButton>
      </div>
      <div class="metadata-tools metadata-selection">
        <NCheckbox
          :checked="!!selectable.length && pageSelected === selectable.length"
          :indeterminate="pageSelected > 0 && pageSelected < selectable.length"
          :disabled="busy || !selectable.length"
          @update:checked="selectPage"
          >{{ t('insights.pageSelect') }}</NCheckbox
        >
        <NButton text type="primary" size="small" :disabled="busy" @click="choose(matching)"
          >{{ t('insights.allSelect') }} ({{ matching.length }})</NButton
        >
        <NButton text type="primary" size="small" @click="selected = []">{{
          t('insights.clear')
        }}</NButton>
        <span class="metadata-selection-count" aria-live="polite">{{
          t('insights.selected', {
            count: existing.size + added.length,
            existing: existing.size,
            added: added.length,
          })
        }}</span>
      </div>
      <p v-if="truncated" role="alert">{{ t('insights.truncated') }}</p>
      <div class="metadata-scroll">
        <table>
          <thead>
            <tr>
              <th>{{ t('insights.source') }}</th>
              <th>{{ t('insights.target') }}</th>
              <th>{{ t('insights.addStatus') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.name">
              <td>
                <NCheckbox
                  :checked="existing.has(tableIdentity(row.name)) || selected.includes(row.name)"
                  :disabled="existing.has(tableIdentity(row.name)) || busy"
                  @update:checked="toggle(row.name, $event)"
                  >{{ row.name }}</NCheckbox
                >
              </td>
              <td>{{ draft.prefix + row.name + draft.suffix }}</td>
              <td>
                <NTag
                  size="small"
                  :type="
                    existing.has(tableIdentity(row.name))
                      ? 'success'
                      : selected.includes(row.name)
                        ? 'info'
                        : 'default'
                  "
                  >{{
                    t(
                      'insights.' +
                        (existing.has(tableIdentity(row.name))
                          ? 'existing'
                          : selected.includes(row.name)
                            ? 'pendingAdd'
                            : 'notSelected'),
                    )
                  }}</NTag
                >
              </td>
            </tr>
            <tr v-if="!visible.length && !busy">
              <td colspan="3">{{ t('insights.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="metadata-pagination">
        <div class="metadata-page-summary">
          <span>{{
            t('insights.pageSummary', {
              page,
              pages: Math.max(1, Math.ceil(matching.length / pageSize)),
              total: matching.length,
            })
          }}</span>
          <NSelect
            v-model:value="pageSize"
            size="small"
            :aria-label="t('insights.perPage')"
            :options="
              [10, 20, 50].map((value) => ({
                value,
                label: t('insights.pageSize', { count: value }),
              }))
            "
          />
        </div>
        <NPagination
          v-model:page="page"
          :page-size="pageSize"
          :item-count="matching.length"
          :page-slot="5"
        />
      </div>
      <p v-if="overLimit" role="alert">{{ t('insights.limit') }}</p>
      <p class="task-note">{{ t('insights.appendHint') }}</p>
    </template>
    <template v-else>
      <p class="task-note">{{ t('insights.checkHint') }}</p>
      <p v-if="checkStale" role="alert">{{ t('insights.checkStale') }}</p>
      <div class="metadata-tools">
        <NTag
          v-for="status in ['PASS', 'WARNING', 'FAIL']"
          :key="status"
          :type="status === 'PASS' ? 'success' : status === 'FAIL' ? 'error' : 'warning'"
        >
          {{ t('insights.' + status) }} {{ checks.filter((r) => r.status === status).length }}
        </NTag>
      </div>
      <div class="metadata-scroll">
        <table>
          <thead>
            <tr>
              <th>{{ t('insights.source') }}</th>
              <th>{{ t('insights.target') }}</th>
              <th>{{ t('insights.problem') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in checks" :key="row.sourceTable">
              <td>{{ row.sourceTable }}</td>
              <td>{{ row.targetTable }}</td>
              <td>
                <NTag
                  size="small"
                  :type="
                    row.status === 'PASS' ? 'success' : row.status === 'FAIL' ? 'error' : 'warning'
                  "
                  >{{ t('insights.' + row.status) }}</NTag
                >
                <p>
                  {{ te('insights.' + row.message) ? t('insights.' + row.message) : row.message }}
                </p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <template #footer>
      <div class="metadata-footer">
        <NButton @click="emit('close')">{{ t('insights.close') }}</NButton>
        <NButton
          v-if="mode === 'picker'"
          type="primary"
          :disabled="busy || overLimit || !added.length"
          @click="add"
          >{{ t('insights.append', { count: added.length }) }}</NButton
        >
        <NButton v-else type="primary" :loading="busy" @click="load">{{
          t('insights.check')
        }}</NButton>
      </div>
    </template>
  </NModal>
</template>
<style scoped>
.metadata-tools {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin: 12px 0;
}
.metadata-filters .n-input {
  width: 180px;
  min-width: 120px;
}
.metadata-context {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.metadata-selection {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.metadata-selection-count {
  margin-left: auto;
  padding: 5px 8px;
  background: var(--selected);
  border-radius: 6px;
  font-size: 12px;
}
.metadata-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--muted);
}
.metadata-pagination :deep(.n-pagination) {
  flex-wrap: wrap;
  gap: 6px;
}
.metadata-page-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.metadata-page-summary .n-select {
  width: 100px;
}
.metadata-scroll {
  max-height: min(420px, 45dvh);
  overflow: auto;
  margin: 12px 0;
}
.metadata-scroll table {
  border-collapse: collapse;
  width: 100%;
  min-width: 500px;
  font-size: 12px;
}
.metadata-scroll th,
.metadata-scroll td {
  border-bottom: 1px solid var(--border);
  padding: 10px;
  text-align: left;
  overflow-wrap: anywhere;
  max-width: 320px;
}
.metadata-scroll th {
  position: sticky;
  top: 0;
  background: var(--surface);
  z-index: 1;
}
.metadata-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
