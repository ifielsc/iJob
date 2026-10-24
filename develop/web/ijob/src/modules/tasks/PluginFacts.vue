<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, inject, ref } from 'vue'
import { referenceNamesKey, referenceKind } from '../../shared/runtime/reference-names'
import { isLosslessNumber, stringify } from 'lossless-json'
import { useI18n } from 'vue-i18n'
import { detailLabel, literalChildren } from '../../shared/ui/detail-labels'
import JsonViewer from '../../shared/ui/JsonViewer.vue'
defineOptions({ name: 'PluginFacts' })
const props = withDefaults(
  defineProps<{
    value: unknown
    scope?: string
    path?: string
    grouped?: boolean
    literal?: boolean
    withinTable?: boolean
  }>(),
  { scope: 'tasks', path: '', grouped: true, literal: false },
)
const { t, te } = useI18n()
const references = inject(referenceNamesKey, undefined)
const reference = computed(() =>
  props.literal
    ? undefined
    : references?.value.get(referenceKind(props.path) + ':' + String(props.value)),
)
const structured = computed(
  () => props.value !== null && typeof props.value === 'object' && !isLosslessNumber(props.value),
)
const entries = computed(() => (structured.value ? Object.entries(props.value as object) : []))
const flatArray = computed(
  () => props.grouped && Array.isArray(props.value) && props.value.every((value) => !nested(value)),
)
function label(key: string) {
  if (Array.isArray(props.value)) return String(Number(key) + 1)
  return props.literal ? key : detailLabel(props.scope, props.path, key, t, te)
}
const nested = (value: unknown) =>
  value !== null && typeof value === 'object' && !isLosslessNumber(value)
const display = computed(() => {
  const value = props.value
  if (value === undefined) return t('details.notSaved')
  if (value === null) return 'null'
  if (value === '') return t('details.emptyString')
  if (!props.literal && typeof value === 'boolean') {
    if (props.path.endsWith('Configured')) return t(value ? 'config.configured' : 'config.unset')
    return t(value ? 'details.yes' : 'details.no')
  }
  if (props.scope === 'monitor') {
    if (typeof value === 'boolean') return t('monitor.' + (value ? 'yes' : 'no'))
    if (
      (/^collect\.items\.\d+$/.test(props.path) || props.path.endsWith('.matchType')) &&
      te('monitor.' + String(value))
    )
      return t('monitor.' + String(value))
  }
  return String(value)
})
const query = ref('')
const openRows = ref<number[]>([])
const mountedRows = ref(new Set<number>())
function toggleRow(index: number) {
  mountedRows.value.add(index)
  openRows.value = openRows.value.includes(index)
    ? openRows.value.filter((value) => value !== index)
    : [...openRows.value, index]
}
const record = (value: unknown): value is Record<string, unknown> =>
  nested(value) && !Array.isArray(value)
const records = computed(() =>
  Array.isArray(props.value) && props.value.length > 0 && props.value.every(record)
    ? (props.value as Record<string, unknown>[])
    : [],
)
const columns = computed(() => {
  const keys = [...new Set(records.value.flatMap(Object.keys))]
  if (props.literal) return keys
  const preferred: Record<string, string[]> = {
    checks: ['name', 'type', 'compareType'],
    extractors: ['variable', 'type', 'path'],
    validations: ['type', 'ruleId', 'onFailure'],
    transforms: ['type', 'properties'],
    applications: ['name', 'matchType', 'pattern'],
  }
  const leading = (preferred[props.path.split('.').at(-1) ?? ''] ?? []).filter((key) =>
    keys.includes(key),
  )
  return [...leading, ...keys.filter((key) => !leading.includes(key))]
})
const primaryColumns = computed(() => columns.value.slice(0, 3))
const extraColumns = computed(() => columns.value.slice(3))
const matches = (value: unknown) =>
  !query.value.trim() ||
  (stringify(value) ?? '').toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())
const visibleRecords = computed(() =>
  records.value.map((row, index) => ({ row, index })).filter(({ row }) => matches(row)),
)
const visibleEntries = computed(() => entries.value.filter((entry) => matches(entry)))
const visibleValues = computed(() =>
  (Array.isArray(props.value) ? props.value : [])
    .map((value, index) => ({ value, index }))
    .filter(({ value }) => matches(value)),
)
const mapTable = computed(
  () =>
    record(props.value) &&
    /(?:^|\.)(env|parameters|columns|columnNullPolicies|replace|headers)$/.test(props.path),
)
const totalRows = computed(
  () => records.value.length || (mapTable.value || flatArray.value ? entries.value.length : 0),
)
const matchedRows = computed(() =>
  records.value.length
    ? visibleRecords.value.length
    : mapTable.value
      ? visibleEntries.value.length
      : visibleValues.value.length,
)
const bounded = computed(() => !props.withinTable && totalRows.value > 6)
const jsonRoot = computed(
  () =>
    props.literal &&
    props.value !== undefined &&
    /^(userConfig|payload|body|remoteStop\.body|webService\.parameters)$/.test(props.path),
)
const code = computed(
  () =>
    typeof props.value === 'string' &&
    /(?:^|\.)(script|sql|beforeSql|afterSql|body)$/.test(props.path),
)
const childPath = (key: string | number) => (props.path ? props.path + '.' + key : String(key))
const cellLabel = (key: string) =>
  props.literal ? key : detailLabel(props.scope, props.path + '.0', key, t, te)
const remaining = (row: Record<string, unknown>) =>
  Object.fromEntries(
    extraColumns.value.filter((key) => Object.hasOwn(row, key)).map((key) => [key, row[key]]),
  )
</script>
<template>
  <div class="plugin-facts-content">
    <JsonViewer v-if="jsonRoot" :value="value" :context-key="scope + '.' + path" />
    <template v-else>
      <div v-if="totalRows > 8" class="plugin-list-tools">
        <input
          v-model="query"
          type="search"
          :placeholder="t('details.searchRows')"
          :aria-label="t('details.searchRows')"
        />
        <span aria-live="polite">{{
          t('details.rowCounts', { total: totalRows, count: matchedRows })
        }}</span>
      </div>
      <div v-if="records.length" class="plugin-records">
        <div
          class="plugin-table-scroll"
          :class="{ 'plugin-table-bounded': bounded }"
          tabindex="0"
          :aria-label="t('details.configurationTable')"
          role="region"
        >
          <table class="plugin-facts-table">
            <thead>
              <tr>
                <th scope="col">#</th>
                <th v-for="key in primaryColumns" :key="key" scope="col">{{ cellLabel(key) }}</th>
                <th v-if="extraColumns.length" scope="col">{{ t('details.additionalFields') }}</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="{ row, index } in visibleRecords" :key="index">
                <tr>
                  <th scope="row">{{ index + 1 }}</th>
                  <td v-for="key in primaryColumns" :key="key">
                    <PluginFacts
                      :value="row[key]"
                      :scope="scope"
                      :path="childPath(index) + '.' + key"
                      :literal="literal || literalChildren(scope, childPath(index), key)"
                      within-table
                    />
                  </td>
                  <td v-if="extraColumns.length">
                    <button
                      v-if="Object.keys(remaining(row)).length"
                      type="button"
                      class="plugin-expand plugin-row-toggle"
                      :aria-expanded="openRows.includes(index)"
                      @click="toggleRow(index)"
                    >
                      {{
                        t(
                          openRows.includes(index)
                            ? 'details.showLess'
                            : 'details.additionalFields',
                        )
                      }}
                      ({{ Object.keys(remaining(row)).length }})
                    </button>
                    <span v-else>—</span>
                  </td>
                </tr>
                <tr
                  v-if="extraColumns.length && mountedRows.has(index)"
                  v-show="openRows.includes(index)"
                  class="plugin-detail-row"
                >
                  <td :colspan="primaryColumns.length + 2">
                    <PluginFacts
                      :value="remaining(row)"
                      within-table
                      :scope="scope"
                      :path="childPath(index)"
                      :literal="literal"
                    />
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
      <div
        v-else-if="mapTable && entries.length"
        class="plugin-table-scroll"
        :class="{ 'plugin-table-bounded': bounded }"
      >
        <table class="plugin-facts-table">
          <thead>
            <tr>
              <th scope="col">{{ t('details.key') }}</th>
              <th scope="col">{{ t('details.value') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="[key, item] in visibleEntries" :key="key">
              <th scope="row">{{ key }}</th>
              <td>
                <PluginFacts
                  :value="item"
                  :scope="scope"
                  :path="childPath(key)"
                  :literal="true"
                  within-table
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-else-if="flatArray && entries.length > 8"
        class="plugin-table-scroll"
        :class="{ 'plugin-table-bounded': bounded }"
        role="region"
        :aria-label="t('details.configurationTable')"
        tabindex="0"
      >
        <table class="plugin-facts-table">
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">{{ t('details.value') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="{ value: item, index } in visibleValues" :key="index">
              <th scope="row">{{ index + 1 }}</th>
              <td>
                <PluginFacts
                  :value="item"
                  :scope="scope"
                  :path="childPath(index)"
                  :literal="literal"
                  within-table
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="flatArray" class="fact-values">
        <PluginFacts
          v-for="(item, index) in value as unknown[]"
          :key="index"
          :value="item"
          :scope="scope"
          :path="childPath(index)"
          :literal="literal"
          :within-table="withinTable"
        />
        <span v-if="!(value as unknown[]).length">[]</span>
      </div>
      <dl v-else-if="structured" class="config-facts">
        <template v-for="[key, item] in entries" :key="key"
          ><dt :class="{ 'fact-group': grouped && nested(item) }">{{ label(key) }}</dt>
          <dd :class="{ 'fact-group-value': grouped && nested(item) }">
            <PluginFacts
              :value="item"
              :scope="scope"
              :path="path ? path + '.' + key : key"
              :grouped="grouped"
              :within-table="withinTable"
              :literal="literal || literalChildren(scope, path, key)"
            /></dd
        ></template>
        <dd v-if="!entries.length">{{ Array.isArray(value) ? '[]' : '{}' }}</dd>
      </dl>
      <div v-else-if="reference" class="fact-value">
        <strong>{{ reference.name }}</strong
        ><br />{{ display }}
      </div>
      <pre v-else class="fact-value" :class="{ 'plugin-code': code }">{{ display }}</pre>
      <EmptyState v-if="totalRows > 8 && !matchedRows" compact :message="t('experience.noMatch')" />
    </template>
  </div>
</template>
<style scoped>
.plugin-facts-content {
  min-width: 0;
}
.plugin-list-tools {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.plugin-list-tools input {
  flex: 1;
  min-width: 100px;
  height: 30px;
  box-sizing: border-box;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
  color: var(--text);
  background: var(--panel);
  font: inherit;
  font-size: 12px;
}
.plugin-list-tools span,
.plugin-no-matches {
  font-size: 12px;
  color: var(--muted);
}
.plugin-table-bounded {
  max-height: min(320px, 42dvh);
  scrollbar-gutter: stable;
  scroll-padding-top: 36px;
}
.plugin-table-bounded > table > thead > tr > th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--panel);
}

.plugin-facts-table .plugin-detail-row > td {
  padding: 14px;
  background: var(--panel);
}
.plugin-table-scroll {
  max-width: 100%;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
}
.plugin-facts-table {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  font-size: 12px;
  text-align: left;
}
.plugin-facts-table th,
.plugin-facts-table td {
  padding: 9px 10px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
  min-width: 80px;
  overflow-wrap: anywhere;
  max-width: 280px;
}
.plugin-facts-table th {
  color: var(--muted);
  font-weight: 500;
  background: var(--panel);
}
.plugin-facts-table tbody > tr:last-child > td {
  border-bottom: 0;
}
.plugin-facts-table th:first-child {
  min-width: 24px;
}
.plugin-facts-table details {
  min-width: 100px;
}
summary {
  cursor: pointer;
  color: var(--primary);
  margin-bottom: 6px;
}
.plugin-json {
  margin-top: 12px;
}
.plugin-code {
  padding: 12px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 6px;
  max-height: 320px;
  overflow: auto;
  font: 12px/1.6 monospace;
  white-space: pre;
}
.plugin-expand {
  border: 0;
  color: var(--primary);
  background: transparent;
  cursor: pointer;
  padding: 4px 0;
}

.config-facts {
  display: grid;
  grid-template-columns: minmax(90px, 30%) minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}
dt {
  color: var(--muted);
  overflow-wrap: anywhere;
}
dd {
  margin: 0;
  min-width: 0;
}
.fact-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  margin: 0;
}
.fact-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.fact-group,
.fact-group-value {
  grid-column: 1 / -1;
}
.fact-group {
  font-weight: 600;
  color: var(--text);
}
.fact-group-value {
  padding-left: 12px;
  border-left: 1px solid var(--border);
}
@media (max-width: 480px) {
  .config-facts {
    grid-template-columns: minmax(0, 1fr);
  }
  dd {
    padding-left: 8px;
  }
}
</style>
