<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NCheckbox, NInput } from 'naive-ui'
import { Play, Square, GripHorizontal, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from '../api/request'
import { randomUuid } from '../runtime/uuid'
import RequestError from './RequestError.vue'
import { record } from '../api/values'
import {
  previewParameters,
  parameterValues,
  previewResult,
  applyPreviewFields,
  previewFieldsKey,
  type QueryContext,
  type QuerySelection,
  type PreviewParameter,
  type PreviewResult,
} from './sql-preview'
const props = defineProps<{
  context: QueryContext
  sql: string
  selection: QuerySelection
  expanded: boolean
}>()
const emit = defineEmits<{ locate: [number] }>()
const request = useRequest(),
  { t, te } = useI18n()
const loadFields = inject(previewFieldsKey, () => Promise.resolve(null))
const busy = ref(false),
  stopping = ref(false),
  paramsOpen = ref(false),
  parameterError = ref(false)
const rows = ref<PreviewParameter[]>([]),
  result = shallowRef<PreviewResult>(),
  error = shallowRef<unknown>()
const parameterDialog = ref<HTMLDialogElement>(),
  area = ref<HTMLElement>(),
  ratio = ref(60),
  collapsed = ref(false)
const maxRows = ref(200),
  resourceName = ref(''),
  snapshot = ref(''),
  pending = ref(''),
  from = ref(0)
let requestId = '',
  controller: AbortController | undefined,
  disposed = false
const identity = computed(() => JSON.stringify([props.context, props.sql, maxRows.value]))
const stale = computed(() => !!snapshot.value && snapshot.value !== identity.value)
const status = computed(() => {
  const states: Record<string, string> = {
    '42S02': 'MISSING_TABLE',
    '42P01': 'MISSING_TABLE',
    '42S22': 'MISSING_COLUMN',
    '42703': 'MISSING_COLUMN',
    '42501': 'PERMISSION',
    '42601': 'SYNTAX',
    '42702': 'AMBIGUOUS_COLUMN',
    '42S21': 'DUPLICATE_COLUMN',
    '42701': 'DUPLICATE_COLUMN',
    '42883': 'MISSING_FUNCTION',
    '22012': 'DIVISION_ZERO',
    '22003': 'NUMBER_RANGE',
    '22018': 'VALUE_TYPE',
    '22P02': 'VALUE_TYPE',
  }
  const reason =
    result.value?.status === 'SQL_SYNTAX' || result.value?.status === 'QUERY_FAILED'
      ? states[result.value.sqlState ?? '']
      : undefined
  const key = 'preview.status_' + (reason ?? result.value?.status)
  return te(key) ? t(key) : t('preview.status_QUERY_FAILED')
})
watch(
  () => props.context.resourceId,
  async (id) => {
    resourceName.value = '#' + id
    if (!id) return
    try {
      const r = record(await request('/api/resources/' + id, { localForbidden: true }))
      if (!disposed && id === props.context.resourceId)
        resourceName.value = String(r.name ?? '#' + id)
    } catch {
      /* Keep the resource identifier visible. */
    }
  },
  { immediate: true },
)
watch(identity, () => {
  if (busy.value) void stop()
  closeParameters()
})
watch(
  () => props.expanded,
  (value) => {
    if (!value) {
      void stop()
      closeParameters()
      rows.value = []
    }
  },
)
async function prepare(all: boolean) {
  if (busy.value || !props.context.resourceId) return
  if (!all && props.selection.count !== 1) return
  pending.value = all ? props.sql : props.selection.text
  from.value = all ? 0 : props.selection.from
  parameterError.value = false
  try {
    rows.value = previewParameters(pending.value, props.context)
  } catch {
    parameterError.value = true
    rows.value = []
    return
  }
  if (rows.value.length) {
    const captured = identity.value
    busy.value = true
    try {
      applyPreviewFields(rows.value, await loadFields())
    } catch (e) {
      error.value = e
      return
    } finally {
      busy.value = false
    }
    if (disposed || captured !== identity.value || !props.expanded) return
    paramsOpen.value = true
    await nextTick()
    parameterDialog.value?.showModal()
  } else await execute()
}
function closeParameters() {
  parameterDialog.value?.close()
  paramsOpen.value = false
}
async function execute() {
  let values: ReturnType<typeof parameterValues>
  try {
    values = parameterValues(rows.value)
    parameterError.value = false
  } catch {
    parameterError.value = true
    return
  }
  closeParameters()
  busy.value = true
  stopping.value = false
  error.value = undefined
  result.value = undefined
  collapsed.value = false
  requestId = randomUuid()
  const currentId = requestId
  const captured = identity.value
  controller = new AbortController()
  try {
    const response = await request('/api/sql-preview', {
      method: 'POST',
      lossless: true,
      body: {
        requestId,
        resourceId: props.context.resourceId,
        mode: props.context.mode,
        sql: pending.value,
        maxRows: maxRows.value,
        ...values,
      },
      signal: controller.signal,
      timeoutMs: 40_000,
    })
    if (!disposed && captured === identity.value && props.expanded) {
      result.value = previewResult(response)
      snapshot.value = captured
    }
  } catch (e) {
    if (!disposed && captured === identity.value) error.value = e
    void request('/api/sql-preview/' + currentId + '/cancel', { method: 'POST' }).catch(() => {})
  } finally {
    busy.value = false
    stopping.value = false
    requestId = ''
    rows.value = []
    pending.value = ''
  }
}
async function stop() {
  if (!requestId || stopping.value) return
  stopping.value = true
  try {
    await request('/api/sql-preview/' + requestId + '/cancel', { method: 'POST' })
  } catch (e) {
    if (!disposed) error.value = e
    stopping.value = false
  }
}
function resize(event: PointerEvent) {
  const element = event.currentTarget as HTMLElement
  element.setPointerCapture(event.pointerId)
  const move = (e: PointerEvent) => {
    const rect = area.value?.getBoundingClientRect()
    if (rect) ratio.value = Math.max(25, Math.min(80, ((e.clientY - rect.top) / rect.height) * 100))
  }
  const end = () => {
    element.removeEventListener('pointermove', move)
    element.removeEventListener('pointerup', end)
    element.removeEventListener('pointercancel', end)
  }
  element.addEventListener('pointermove', move)
  element.addEventListener('pointerup', end)
  element.addEventListener('pointercancel', end)
}
onBeforeUnmount(() => {
  disposed = true
  void stop()
  controller?.abort()
  closeParameters()
  rows.value = []
})
defineExpose({ prepare })
</script>
<template>
  <section class="query-panel">
    <div class="query-toolbar">
      <div class="query-actions">
        <span class="query-resource" :title="resourceName"
          >{{ t('preview.database') }}：{{ resourceName }}</span
        >
        <NButton
          type="primary"
          size="small"
          :disabled="busy || !context.resourceId || selection.count !== 1"
          @mousedown.prevent
          @click="prepare(false)"
          ><template #icon><Play :size="14" /></template>{{ t('preview.selection') }}</NButton
        >
        <NButton
          size="small"
          :disabled="busy || !context.resourceId || !sql.trim()"
          @mousedown.prevent
          @click="prepare(true)"
          >{{ t('preview.full') }}</NButton
        >
        <NButton size="small" :disabled="!busy || stopping" @click="stop"
          ><template #icon><Square :size="13" /></template
          >{{ t(stopping ? 'preview.stopping' : 'preview.stop') }}</NButton
        >
        <select v-model="maxRows" :disabled="busy" :aria-label="t('preview.limit')">
          <option v-for="limit in [50, 200, 1000]" :key="limit" :value="limit">
            {{ t('preview.rowsLimit', { count: limit }) }}
          </option>
        </select>
      </div>
      <slot name="tools" />
    </div>
    <p class="query-hint">
      {{ t('preview.readOnly') }} ·
      {{
        t(
          selection.count > 1
            ? 'preview.multi'
            : selection.count === 1
              ? 'preview.selected'
              : 'preview.selectHint',
        )
      }}
    </p>
    <p v-if="parameterError && !paramsOpen" role="alert">{{ t('preview.parameterError') }}</p>
    <div
      ref="area"
      class="query-area"
      :style="{ '--query-ratio': ratio + '%' }"
      :class="{ collapsed }"
    >
      <div class="query-code"><slot /></div>
      <div
        class="query-splitter"
        role="separator"
        tabindex="0"
        aria-orientation="horizontal"
        :aria-valuenow="ratio"
        :aria-valuemin="25"
        :aria-valuemax="80"
        :aria-label="t('preview.resize')"
        @pointerdown="resize"
        @keydown.up.prevent="ratio = Math.max(25, ratio - 5)"
        @keydown.down.prevent="ratio = Math.min(80, ratio + 5)"
      >
        <GripHorizontal :size="18" />
      </div>
      <div class="query-results">
        <div class="query-result-heading">
          <strong>{{ t('preview.results') }}</strong
          ><span v-if="stale" class="query-stale">{{ t('preview.stale') }}</span
          ><NButton size="tiny" quaternary @click="collapsed = !collapsed">{{
            t(collapsed ? 'preview.expand' : 'preview.collapse')
          }}</NButton>
        </div>
        <template v-if="!collapsed">
          <p v-if="busy" role="status">{{ t('preview.running') }}</p>
          <RequestError v-else-if="error" :error="error" />
          <template v-else-if="result">
            <p :role="result.status === 'SUCCESS' ? 'status' : 'alert'">
              {{ status }} · {{ result.elapsedMillis }} ms
              <span v-if="result.status === 'SUCCESS'">
                · {{ result.rows.length }} {{ t('preview.rows') }}</span
              ><span v-if="result.truncated">
                · {{ t('preview.truncated_' + result.truncated) }}</span
              >
            </p>
            <div v-if="result.status === 'SUCCESS'" class="query-table-scroll">
              <table class="query-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th v-for="(column, i) in result.columns" :key="i" :title="column.type">
                      {{ column.name }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in result.rows" :key="index">
                    <td>{{ index + 1 }}</td>
                    <td
                      v-for="(cell, col) in row"
                      :key="col"
                      :class="{ 'query-null': cell === null }"
                    >
                      <span>{{ cell === null ? 'NULL' : cell }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!result.rows.length">{{ t('preview.empty') }}</p>
            </div>
            <pre
              v-if="result.status !== 'SUCCESS' && result.databaseMessage"
              class="query-database-error"
              :aria-label="t('preview.databaseError')"
              >{{ result.databaseMessage }}</pre>
            <p v-if="result.status !== 'SUCCESS'" class="query-error-ref">
              {{ result.sqlState ? 'SQLSTATE ' + result.sqlState + ' · ' : ''
              }}<span v-if="result.vendorCode != null"
                >{{ t('preview.vendorCode') }} {{ result.vendorCode }} · </span
              >{{ t('preview.trace') }}: {{ result.traceId }}
              <NButton
                v-if="result.position"
                size="tiny"
                @click="emit('locate', from + result.position - 1)"
                >{{ t('preview.locate') }}</NButton
              >
            </p>
          </template>
          <p v-else class="query-hint">{{ t('preview.ready') }}</p>
        </template>
      </div>
    </div>
    <dialog
      ref="parameterDialog"
      class="query-parameters"
      :aria-label="t('preview.parameters')"
      @cancel.prevent="closeParameters"
      @keydown.esc.stop.prevent="closeParameters"
    >
      <header>
        <h3>{{ t('preview.parameters') }}</h3>
        <NButton quaternary circle :aria-label="t('common.close')" @click="closeParameters"
          ><X :size="18"
        /></NButton>
      </header>
      <p>{{ t('preview.parametersHelp') }}</p>
      <div class="query-parameter-scroll">
        <div v-for="row in rows" :key="String(row.named) + row.name" class="query-parameter-row">
          <label :for="'preview-param-' + row.name"
            >{{ row.named ? ':' : '' }}{{ row.name
            }}<small>{{ t('preview.source_' + row.source) }}</small></label
          >
          <select v-model="row.kind" :disabled="!!row.definition" :aria-label="t('preview.type')">
            <option value="text">{{ t('preview.text') }}</option>
            <option value="number">{{ t('preview.number') }}</option>
            <option value="boolean">Boolean</option>
            <option v-if="row.named" value="null">NULL</option>
          </select>
          <select
            v-if="row.definition?.type === 'ENUM'"
            :id="'preview-param-' + row.name"
            v-model="row.value"
            :aria-label="row.name"
          >
            <option
              v-for="option in row.definition.options as string[]"
              :key="option"
              :value="option"
            >
              {{ option }}
            </option>
          </select>
          <NInput
            v-else
            v-model:value="row.value"
            :input-props="{ id: 'preview-param-' + row.name, 'aria-label': row.name }"
            :type="row.sensitive ? 'password' : 'text'"
            :disabled="row.kind === 'null'"
            :maxlength="16384"
          />
          <NCheckbox v-model:checked="row.sensitive">{{ t('preview.sensitive') }}</NCheckbox>
        </div>
      </div>
      <p v-if="parameterError" role="alert">{{ t('preview.parameterError') }}</p>
      <p class="query-hint">{{ t('preview.parametersScope') }}</p>
      <footer>
        <NButton @click="closeParameters">{{ t('common.cancel') }}</NButton
        ><NButton type="primary" @click="execute">{{ t('preview.start') }}</NButton>
      </footer>
    </dialog>
  </section>
</template>
<style scoped>
.query-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}
.query-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
}
.query-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}
.query-resource {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.query-hint {
  color: var(--muted);
  font-size: 12px;
  margin: 4px 20px 8px;
}
.query-area {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(90px, var(--query-ratio)) 16px minmax(70px, 1fr);
}
.query-area.collapsed {
  grid-template-rows: minmax(100px, 1fr) 16px 42px;
}
.query-code {
  display: flex;
  min-height: 0;
  overflow: hidden;
}
.query-code :deep(.sql-large-host) {
  width: 100%;
}
.query-splitter {
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: row-resize;
  touch-action: none;
  border-top: 1px solid var(--border);
  color: var(--muted);
}
.query-splitter:focus-visible {
  outline: 2px solid var(--primary);
}
.query-results {
  padding: 0 20px 8px;
  overflow: auto;
  min-width: 0;
}
.query-result-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  position: sticky;
  top: 0;
  background: var(--panel);
  z-index: 1;
  padding: 6px 0;
}
.query-result-heading button {
  margin-left: auto;
}
.query-stale {
  color: var(--warning);
}
.query-table-scroll {
  overflow: auto;
  max-width: 100%;
}
.query-table {
  border-collapse: collapse;
  min-width: 100%;
  font-size: 12px;
}
.query-table th,
.query-table td {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
  white-space: pre-wrap;
  max-width: 400px;
  overflow-wrap: anywhere;
  vertical-align: top;
}
.query-table th {
  background: var(--bg);
  white-space: nowrap;
}
.query-table td span {
  display: block;
  max-height: 100px;
  overflow: auto;
  min-width: 60px;
}
.query-null {
  color: var(--muted);
  font-style: italic;
}
.query-database-error {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: Consolas, monospace;
  font-size: 12px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  user-select: text;
}
.query-error-ref {
  overflow-wrap: anywhere;
  font-size: 12px;
}
.query-parameters {
  width: min(760px, calc(100vw - 24px));
  box-sizing: border-box;
  max-height: min(85dvh, calc(100dvh - 24px));
  overflow: hidden;
  padding: 12px 20px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--panel);
  color: var(--text);
  font: inherit;
}
.query-parameters[open] {
  display: flex;
  flex-direction: column;
}
.query-parameters > header,
.query-parameters > footer,
.query-parameters > p {
  flex: none;
}
.query-parameters > p {
  margin: 8px 0;
}
.query-parameters::backdrop {
  background: rgba(12, 24, 43, 0.35);
}
.query-parameters header,
.query-parameters footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.query-parameters h3 {
  margin: 0;
  font-size: 18px;
}
.query-parameters footer {
  justify-content: flex-end;
  margin-top: 12px;
  padding: 0;
}
.query-parameters footer > .n-button {
  margin-left: 0;
}
.query-parameters header {
  padding: 0 0 8px;
}
.query-parameter-scroll {
  flex: 0 1 auto;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
}
.query-parameter-row {
  display: grid;
  grid-template-columns: minmax(100px, 1fr) 90px minmax(120px, 1.5fr) 70px;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}
.query-parameter-row label {
  overflow-wrap: anywhere;
}
.query-parameter-row small {
  display: block;
  color: var(--muted);
  font-size: 12px;
}
.query-toolbar select,
.query-parameter-row select {
  background: var(--panel);
  color: var(--text);
  border: 1px solid var(--controlBorder);
  border-radius: 6px;
  padding: 6px;
  min-width: 0;
}
@media (max-width: 600px) {
  .query-toolbar {
    padding: 8px;
  }
  .query-resource {
    max-width: 100%;
    width: 100%;
  }
  .query-results {
    padding: 0 8px 8px;
  }
  .query-parameter-row {
    grid-template-columns: minmax(0, 1fr) 90px;
  }
  .query-parameters {
    padding: 12px;
  }
  .query-hint {
    margin-left: 8px;
    margin-right: 8px;
  }
}
</style>
