<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { NButton, NInput, NModal, NPagination, NSelect } from 'naive-ui'
import { ArrowUp, RefreshCw, Folder, File, Home } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from '../api/request'
import { record } from '../api/values'
import { ApiError } from '../api/errors'
import RequestError from './RequestError.vue'
import { pathParts, type FileChoice } from './file-browser'
import { usePreferences } from '../preferences/store'
import { formatUtc } from '../time/format'
const props = defineProps<{
  resourceId?: string
  mode: 'file' | 'directory' | 'both'
  initialPath?: string
  draft?: () => unknown
  local?: boolean
  transfer?: boolean
}>()
const emit = defineEmits<{ close: []; choose: [FileChoice] }>()
const prefs = usePreferences()
function modifiedTime(value: string) {
  try {
    return formatUtc(value, prefs.locale, prefs.displayTimezone)
  } catch {
    return '—'
  }
}
const request = useRequest(),
  { t } = useI18n()
const session = shallowRef<{
  id: string
  protocol: string
  name: string
  root: string
  node: string
}>()
const entries = shallowRef<{ name: string; directory: boolean; size: number; modified: string }[]>(
  [],
)
const path = ref(''),
  query = ref(''),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  selected = ref('')
const roots = ref<{ value: string; label: string }[]>([]),
  rootId = ref<string>(),
  error = shallowRef<unknown>()
const kind = ref(props.mode === 'both' ? 'file' : props.mode)
let controller: AbortController | undefined,
  generation = 0,
  disposed = false
const crumbs = computed(() => pathParts(path.value))
const choice = computed<FileChoice>(() => ({
  path: kind.value === 'directory' ? path.value : [...crumbs.value, selected.value].join('/'),
  directory: kind.value === 'directory',
  name: kind.value === 'directory' ? (crumbs.value.at(-1) ?? '') : selected.value,
  parent: path.value,
  root: session.value?.root ?? '',
}))
async function release() {
  const current = session.value
  session.value = undefined
  if (current)
    await request('/api/file-browser/sessions/' + current.id, { method: 'DELETE' }).catch(() => {})
}
async function open() {
  const token = ++generation
  controller?.abort()
  controller = new AbortController()
  await release()
  busy.value = true
  error.value = undefined
  entries.value = []
  selected.value = ''
  path.value = ''
  page.value = 1
  try {
    const value = record(
      await request('/api/file-browser/sessions', {
        method: 'POST',
        body: {
          resourceId: props.resourceId || undefined,
          draft: props.draft?.(),
          localRootId: rootId.value,
        },
        signal: controller.signal,
      }),
    )
    if (
      typeof value.id !== 'string' ||
      !/^[a-f0-9-]{36}$/.test(value.id) ||
      ['protocol', 'name', 'root', 'node'].some((k) => typeof value[k] !== 'string')
    )
      throw new ApiError('protocol')
    if (disposed || token !== generation) {
      void request('/api/file-browser/sessions/' + value.id, { method: 'DELETE' }).catch(() => {})
      return
    }
    session.value = {
      id: value.id,
      protocol: String(value.protocol),
      name: String(value.name),
      root: String(value.root),
      node: String(value.node),
    }
    // Start at the scoped root; unresolved templates and file paths are never treated as directories.
    await load('')
  } catch (e) {
    if (!disposed && token === generation) error.value = e
  } finally {
    if (token === generation) busy.value = false
  }
}
async function load(target = path.value, refresh = false) {
  if (!session.value) return
  const id = session.value.id,
    token = ++generation
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  error.value = undefined
  selected.value = ''
  entries.value = []
  if (path.value !== target) {
    page.value = 1
    query.value = ''
  }
  path.value = target
  try {
    const params = new URLSearchParams({
      path: target,
      search: query.value,
      page: String(page.value),
      pageSize: '50',
      refresh: String(refresh),
    })
    const value = record(
      await request('/api/file-browser/sessions/' + id + '?' + params, {
        signal: controller.signal,
        timeoutMs: 20_000,
      }),
    )
    if (disposed || token !== generation) return
    if (!Array.isArray(value.entries) || typeof value.total !== 'number')
      throw new ApiError('protocol')
    entries.value = value.entries.map((v) => {
      const e = record(v)
      if (
        typeof e.name !== 'string' ||
        typeof e.directory !== 'boolean' ||
        typeof e.size !== 'number' ||
        typeof e.modified !== 'string'
      )
        throw new ApiError('protocol')
      return { name: e.name, directory: e.directory, size: e.size, modified: e.modified }
    })
    total.value = value.total
  } catch (e) {
    if (!disposed && token === generation) error.value = e
  } finally {
    if (token === generation) busy.value = false
  }
}
function enter(name: string) {
  void load([...crumbs.value, name].join('/'))
}
function search() {
  page.value = 1
  void load()
}
function choose() {
  if (
    !busy.value &&
    !error.value &&
    session.value &&
    (kind.value === 'directory' || selected.value)
  )
    emit('choose', choice.value)
}
watch(rootId, () => {
  if (rootId.value !== undefined) void open()
})
onMounted(async () => {
  if (props.local && props.draft) {
    try {
      const value = await request<unknown>('/api/file-browser/roots')
      if (!Array.isArray(value)) throw new ApiError('protocol')
      if (disposed) return
      roots.value = value.map((v) => {
        const r = record(v)
        return { value: String(r.id), label: String(r.path) }
      })
      rootId.value = roots.value[0]?.value
    } catch (e) {
      error.value = e
    }
  } else void open()
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  controller?.abort()
  void release()
})
</script>
<template>
  <NModal
    :show="true"
    preset="card"
    class="file-browser"
    :title="t('browse.title')"
    :mask-closable="false"
    :auto-focus="true"
    @close="emit('close')"
    @esc="emit('close')"
  >
    <div class="file-browser-summary">
      <strong :title="[session?.protocol, session?.name, session?.node].filter(Boolean).join(' · ')"
        >{{ session?.protocol }} · {{ session?.name || t('browse.draft') }}</strong
      >
      <span :title="t('browse.boundary') + ' · ' + (session?.root || '/')"
        >{{ t('browse.root') }}：{{ session?.root || '/' }}</span
      >
      <NSelect
        v-if="mode === 'both'"
        v-model:value="kind"
        size="small"
        :aria-label="t('browse.type')"
        :options="[
          { value: 'file', label: t('browse.file') },
          { value: 'directory', label: t('browse.directory') },
        ]"
      />
    </div>
    <div v-if="local && draft" class="file-browser-root">
      <label>{{ t('browse.allowedRoot') }}</label>
      <NSelect
        v-model:value="rootId"
        size="small"
        :options="roots"
        :disabled="busy"
        :aria-label="t('browse.allowedRoot')"
      />
      <p v-if="!roots.length">{{ t('browse.noRoots') }}</p>
    </div>
    <div class="file-browser-tools">
      <nav :aria-label="t('browse.path')">
        <NButton size="small" quaternary :disabled="busy" @click="load('')"
          ><Home :size="15" />{{ t('browse.root') }}</NButton
        ><template v-for="(part, index) in crumbs" :key="index"
          ><span>/</span
          ><NButton
            size="small"
            quaternary
            :disabled="busy"
            @click="load(crumbs.slice(0, index + 1).join('/'))"
            >{{ part }}</NButton
          ></template
        >
      </nav>
      <NButton
        size="small"
        :disabled="busy || !path"
        :aria-label="t('browse.up')"
        @click="load(crumbs.slice(0, -1).join('/'))"
        ><ArrowUp :size="16"
      /></NButton>
      <NButton
        size="small"
        :disabled="busy || !session"
        :aria-label="t('common.refresh')"
        @click="load(path, true)"
        ><RefreshCw :size="16"
      /></NButton>
      <NInput
        v-model:value="query"
        size="small"
        :placeholder="t('browse.search')"
        :aria-label="t('browse.search')"
        :maxlength="128"
        :disabled="busy"
        @keydown.enter="search"
      />
      <NButton size="small" :disabled="busy" @click="search">{{
        t('browse.searchAction')
      }}</NButton>
    </div>
    <RequestError v-if="error" :error="error" />
    <p v-if="error">
      <NButton :disabled="busy" @click="open">{{ t('browse.reopen') }}</NButton>
    </p>
    <div class="file-browser-table" :aria-busy="busy">
      <table>
        <thead>
          <tr>
            <th></th>
            <th>{{ t('browse.name') }}</th>
            <th>{{ t('browse.type') }}</th>
            <th>{{ t('browse.size') }}</th>
            <th>{{ t('browse.modified') }} · {{ prefs.displayTimezone }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in entries"
            :key="entry.name"
            :class="{ selected: selected === entry.name }"
          >
            <td>
              <input
                v-if="!entry.directory && kind === 'file'"
                v-model="selected"
                type="radio"
                name="file-browser-selection"
                :value="entry.name"
                :aria-label="entry.name"
              />
            </td>
            <td>
              <NButton v-if="entry.directory" text @click="enter(entry.name)"
                ><Folder :size="16" />{{ entry.name }}</NButton
              ><button
                v-else
                type="button"
                class="file-browser-file"
                :disabled="kind !== 'file'"
                @click="selected = entry.name"
              >
                <File :size="16" />{{ entry.name }}
              </button>
            </td>
            <td>{{ t(entry.directory ? 'browse.folder' : 'browse.fileType') }}</td>
            <td>{{ entry.directory ? '—' : entry.size + ' B' }}</td>
            <td :title="entry.modified">{{ modifiedTime(entry.modified) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="busy" role="status">{{ t('common.loading') }}</p>
      <p v-else-if="!entries.length && !error">{{ t('browse.empty') }}</p>
    </div>
    <div class="file-browser-status">
      <div
        class="file-browser-selection"
        :title="t('browse.selection') + '：' + (choice.path || '/')"
      >
        <span>{{ t('browse.selection') }}：</span><code>{{ choice.path || '/' }}</code>
      </div>
      <NPagination
        v-model:page="page"
        size="small"
        :page-size="50"
        :item-count="total"
        :disabled="busy"
        :page-slot="3"
        @update:page="load()"
      />
    </div>
    <p
      v-if="transfer && kind === 'file' && selected"
      class="file-browser-note"
      :title="t('browse.transferFill', { path: path || '/', name: selected })"
    >
      {{ t('browse.transferFill', { path: path || '/', name: selected }) }}
    </p>
    <template #footer
      ><div class="file-browser-footer">
        <NButton @click="emit('close')">{{ t('common.cancel') }}</NButton
        ><NButton
          type="primary"
          :disabled="busy || !!error || !session || (kind === 'file' && !selected)"
          @click="choose"
          >{{ t(kind === 'directory' ? 'browse.chooseDirectory' : 'browse.chooseFile') }}</NButton
        >
      </div></template
    >
  </NModal>
</template>
<style>
.file-browser.n-card {
  width: min(1080px, calc(100vw - 24px));
  height: min(800px, calc(100dvh - 32px));
  max-height: calc(100dvh - 32px);
  overflow: hidden;
}
.file-browser .n-card-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0 20px;
}
.file-browser .n-card-header,
.file-browser .n-card__footer {
  flex: none;
}
.file-browser .n-card__footer {
  padding: 16px 20px 12px;
}
.file-browser-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: none;
}
.file-browser-summary strong,
.file-browser-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.file-browser-summary strong {
  max-width: 45%;
}
.file-browser-summary span {
  flex: 1;
  font-size: 12px;
  color: var(--muted);
}
.file-browser-summary .n-select {
  width: 128px;
  flex: none;
}
.file-browser-note {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: none;
}
.file-browser-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  flex: none;
}
.file-browser-tools nav {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 120px;
  overflow: hidden;
}
.file-browser-tools nav .n-button {
  min-width: 0;
  flex-shrink: 1;
}
.file-browser-tools nav .n-button__content {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-browser-tools .n-input {
  width: 190px;
}
.file-browser-table {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--border);
}
.file-browser-table table {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  font-size: 13px;
}
.file-browser-table th,
.file-browser-table td {
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  white-space: nowrap;
}
.file-browser-table th:first-child,
.file-browser-table td:first-child {
  width: 24px;
}
.file-browser-table th {
  background: var(--bg);
  position: sticky;
  top: 0;
  z-index: 1;
}
.file-browser-table .selected {
  background: var(--selectedBg, rgba(53, 100, 216, 0.1));
}
.file-browser-file {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: none;
  color: var(--text);
  font: inherit;
  cursor: pointer;
}
.file-browser-table .n-button__content {
  gap: 8px;
}
.file-browser-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 12px;
  flex: none;
}
.file-browser-selection {
  display: flex;
  flex: 1;
  min-width: 0;
  font-size: 12px;
  align-items: center;
  white-space: nowrap;
}
.file-browser-selection span {
  flex: none;
  color: var(--muted);
}
.file-browser-selection code {
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-browser-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.file-browser-root {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  flex: none;
}
.file-browser-root .n-select {
  flex: 1;
  min-width: 120px;
}
.file-browser-root p {
  margin: 0;
  width: 100%;
}
@media (max-width: 600px) {
  .file-browser.n-card {
    width: calc(100vw - 12px);
    height: calc(100dvh - 16px);
    max-height: calc(100dvh - 16px);
  }
  .file-browser .n-card-content {
    padding: 0 10px;
  }
  :root .file-browser.n-modal.n-card > .n-card-header {
    padding: 8px 10px;
  }
  .file-browser .n-card__footer {
    padding: 16px 10px 10px;
  }
  .file-browser-summary {
    flex-wrap: wrap;
    gap: 4px 8px;
  }
  .file-browser-summary strong {
    max-width: 100%;
  }
  .file-browser-summary span {
    min-width: 80px;
  }
  .file-browser-tools nav {
    flex-basis: calc(100% - 92px);
  }
  .file-browser-tools .n-input {
    flex: 1;
    min-width: 100px;
  }
  .file-browser-selection {
    flex-basis: 100%;
  }
}
</style>
