<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { pageData } from '../../shared/api/values'
import { pluginLabel } from '../../shared/plugins'
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { NButton, NCheckbox, NInput } from 'naive-ui'
import { Plus } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import LineTabs from '../../shared/ui/LineTabs.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import PolicyDrawer from './PolicyDrawer.vue'
import ResourceDetails from './ResourceDetails.vue'
import ResourceDeleteDialog from './ResourceDeleteDialog.vue'
import {
  list,
  metadata,
  plugin,
  resource,
  type Metadata,
  type Plugin,
  type Resource,
  type Target,
} from './model'
import './concurrency.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const reader = computed(() => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value)
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && reader.value)
const tab = ref('resources'),
  resources = ref<Resource[]>([]),
  plugins = ref<Plugin[]>([]),
  meta = shallowRef<Metadata>()
const loading = ref(false),
  error = shallowRef<unknown>(),
  loaded = ref(false),
  target = shallowRef<Target>(),
  removing = ref(false)
const detailId = ref('')
function openDetails(id: string, event: MouseEvent) {
  returnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  detailId.value = id
}
async function closeDetails() {
  detailId.value = ''
  await nextTick()
  returnFocus?.focus({ preventScroll: true })
}
const checked = ref<string[]>([])
const deletion = ref<Resource[]>([])
const deletable = (r: Resource) => r.referenceCount === 0 && r.activeInstanceCount === 0
const selectable = computed(() => resourceResult.value.rows.filter(deletable))
function select(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((key) => key !== id)
}
function removeResources(rows: Resource[], event: MouseEvent) {
  if (!writer.value || loading.value || !rows.length || rows.some((r) => !deletable(r))) return
  returnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  deletion.value = rows.map((row) => ({ ...row }))
}
async function closeDeletion() {
  deletion.value = []
  await nextTick()
  returnFocus?.focus({ preventScroll: true })
}
const filters = reactive({ name: '', status: '', type: '', policy: '' }),
  applied = ref({ ...filters })
const page = reactive({ resources: 1, plugins: 1 }),
  size = ref(20)
let controller: AbortController | undefined,
  returnFocus: HTMLElement | null = null
const pageNumber = computed(() => (tab.value === 'resources' ? page.resources : page.plugins))
const resourceTotal = ref(0)
const resourceResult = computed(() => ({ rows: resources.value, total: resourceTotal.value }))
const pluginRows = computed(() =>
  (meta.value?.plugins ?? [])
    .map((item) => ({ ...item, policy: plugins.value.find((p) => p.pluginType === item.type) }))
    .filter(
      (item) =>
        (!applied.value.type || item.type === applied.value.type) &&
        (!applied.value.policy ||
          (item.policy ? 'configured' : 'unconfigured') === applied.value.policy),
    ),
)
const visiblePlugins = computed(() =>
  pluginRows.value.slice((page.plugins - 1) * size.value, page.plugins * size.value),
)
const total = computed(() =>
  tab.value === 'resources' ? resourceResult.value.total : pluginRows.value.length,
)
const tabs = computed(() => [
  { value: 'resources', label: t('concurrency.resources') },
  { value: 'plugins', label: t('concurrency.plugins') },
])
const statuses = computed(() => [
  { value: '', label: t('concurrency.all') },
  { value: 'true', label: t('concurrency.on') },
  { value: 'false', label: t('concurrency.off') },
])
const pluginOptions = computed(() => [
  { value: '', label: t('concurrency.all') },
  ...(meta.value?.plugins ?? []).map((p) => ({ value: p.type, label: pluginLabel(p.type, t) })),
])
async function load() {
  if (!reader.value) return
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  checked.value = []
  error.value = undefined
  try {
    const [r, p, m] = await Promise.all([
      runtime.request(
        '/api/concurrency/resources/page?' +
          new URLSearchParams({
            keyword: applied.value.name,
            enabled: applied.value.status,
            pageNum: String(page.resources),
            pageSize: String(size.value),
          }),
        { signal: current.signal },
      ),
      runtime.request('/api/concurrency/plugin-policies', { signal: current.signal }),
      runtime.request('/api/concurrency/metadata', { signal: current.signal }),
    ])
    if (current.signal.aborted) return
    const result = pageData(r, resource)
    resources.value = result.list
    resourceTotal.value = result.total
    plugins.value = list(p, plugin)
    meta.value = metadata(m)
    loaded.value = true
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.resources > lastPage) {
      page.resources = lastPage
      void load()
      return
    }
    page.plugins = Math.min(
      page.plugins,
      Math.max(1, Math.ceil(pluginRows.value.length / size.value)),
    )
  } catch (e) {
    if (!current.signal.aborted) error.value = e
  } finally {
    if (!current.signal.aborted) loading.value = false
  }
}
function search(reset = false) {
  if (reset) Object.assign(filters, { name: '', status: '', type: '', policy: '' })
  if (JSON.stringify(applied.value) !== JSON.stringify(filters)) {
    page.resources = 1
    page.plugins = 1
  }
  applied.value = { ...filters }
  void load()
}
function setPage(value: number) {
  if (tab.value === 'resources') page.resources = value
  else page.plugins = value
  if (tab.value === 'resources') void load()
}
function setSize(value: number) {
  size.value = value
  page.resources = 1
  page.plugins = 1
  void load()
}
function open(value: Target, event: MouseEvent, remove = false) {
  returnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  removing.value = remove
  target.value = value
}
async function close() {
  target.value = undefined
  await nextTick()
  returnFocus?.focus({ preventScroll: true })
}
watch([tab, pageNumber, size], () => {
  checked.value = []
})
watch(
  [() => runtime.session.revision, reader],
  () => {
    controller?.abort()
    resources.value = []
    plugins.value = []
    meta.value = undefined
    target.value = undefined
    detailId.value = ''
    deletion.value = []
    checked.value = []
    loaded.value = false
    error.value = undefined
    page.resources = 1
    page.plugins = 1
    if (reader.value) void load()
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <section class="concurrency-page">
    <header class="concurrency-heading list-heading">
      <div>
        <h1>{{ t('concurrency.title') }}</h1>
        <p>{{ t('concurrency.subtitle') }}</p>
      </div>
      <NButton
        v-if="writer && tab === 'resources'"
        class="list-create-action"
        type="primary"
        :disabled="loading"
        @click="open({ kind: 'resource' }, $event)"
        ><template #icon><Plus :size="16" /></template>{{ t('concurrency.add') }}</NButton
      >
    </header>
    <LineTabs
      id="concurrency-tabs"
      :label="t('concurrency.title')"
      :value="tab"
      :tabs="tabs"
      panel-id="concurrency-panel"
      @change="tab = $event"
    />
    <div
      id="concurrency-panel"
      class="concurrency-panel list-panel"
      role="tabpanel"
      :aria-labelledby="'concurrency-tabs-' + tab"
    >
      <form class="concurrency-query list-filters" @submit.prevent="search()">
        <template v-if="tab === 'resources'"
          ><div>
            <label for="concurrency-filter-name">{{ t('concurrency.name') }}</label
            ><NInput
              v-model:value="filters.name"
              :input-props="{ id: 'concurrency-filter-name' }"
            />
          </div>
          <div>
            <label for="concurrency-filter-status">{{ t('concurrency.status') }}</label
            ><CompactSelect
              id="concurrency-filter-status"
              v-model:value="filters.status"
              :label="t('concurrency.status')"
              :options="statuses"
            /></div
        ></template>
        <template v-else
          ><div>
            <label for="concurrency-filter-type">{{ t('concurrency.plugin') }}</label
            ><CompactSelect
              id="concurrency-filter-type"
              v-model:value="filters.type"
              :label="t('concurrency.plugin')"
              :options="pluginOptions"
            />
          </div>
          <div>
            <label for="concurrency-filter-policy">{{ t('concurrency.policy') }}</label
            ><CompactSelect
              id="concurrency-filter-policy"
              v-model:value="filters.policy"
              :label="t('concurrency.policy')"
              :options="[
                { value: '', label: t('concurrency.all') },
                { value: 'configured', label: t('concurrency.configured') },
                { value: 'unconfigured', label: t('concurrency.unconfigured') },
              ]"
            /></div
        ></template>
        <div class="concurrency-query-actions">
          <NButton class="list-query-action" attr-type="submit" type="primary" :loading="loading">{{
            t('concurrency.search')
          }}</NButton
          ><NButton class="list-query-action" :disabled="loading" @click="search(true)">{{
            t('concurrency.reset')
          }}</NButton>
        </div>
      </form>
      <div v-if="writer && tab === 'resources'" class="concurrency-toolbar list-toolbar">
        <NButton
          class="list-batch-action"
          type="error"
          size="small"
          :disabled="loading || !!error || !checked.length"
          @click="
            removeResources(
              resourceResult.rows.filter((r) => checked.includes(r.id)),
              $event,
            )
          "
          >{{ t('config.batchDelete') }}</NButton
        >
        <span>{{ t('config.selectedCount', { count: checked.length }) }}</span>
      </div>
      <RequestError :error="error" />
      <p v-if="loading && !loaded" role="status">{{ t('common.loading') }}</p>
      <div v-if="loaded && !error" class="concurrency-table-scroll">
        <table v-if="tab === 'resources'" class="concurrency-table concurrency-list">
          <thead>
            <tr>
              <th v-if="writer" class="concurrency-check">
                <NCheckbox
                  :checked="
                    selectable.length > 0 && selectable.every((r) => checked.includes(r.id))
                  "
                  :indeterminate="checked.length > 0 && checked.length < selectable.length"
                  :disabled="loading || !selectable.length"
                  :aria-disabled="loading || !selectable.length"
                  :aria-label="t('concurrency.selectPage')"
                  @update:checked="checked = $event ? selectable.map((r) => r.id) : []"
                />
              </th>
              <th>{{ t('concurrency.name') }}</th>
              <th>{{ t('concurrency.code') }}</th>
              <th>{{ t('concurrency.maximum') }}</th>
              <th>{{ t('concurrency.status') }}</th>
              <th class="concurrency-actions">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in resourceResult.rows"
              :key="r.id"
              :class="{ 'is-selected': checked.includes(r.id) }"
            >
              <td v-if="writer" class="concurrency-check">
                <NCheckbox
                  :checked="checked.includes(r.id)"
                  :disabled="loading || !deletable(r)"
                  :aria-disabled="loading || !deletable(r)"
                  :aria-label="t('concurrency.selectRow', { name: r.name })"
                  @update:checked="select(r.id, $event)"
                />
              </td>
              <td>
                <NButton
                  text
                  type="primary"
                  :aria-label="t('common.details') + ' ' + r.name"
                  @click="openDetails(r.id, $event)"
                  >{{ r.name }}</NButton
                >
              </td>
              <td>{{ r.code }}</td>
              <td>{{ r.maxConcurrency }}</td>
              <td>
                <span :class="r.enabled ? 'status-success' : 'status-disabled'">{{
                  t(r.enabled ? 'concurrency.on' : 'concurrency.off')
                }}</span>
              </td>
              <td class="concurrency-actions">
                <NButton
                  text
                  type="primary"
                  :disabled="loading"
                  :aria-label="t(writer ? 'common.edit' : 'concurrency.view') + ' ' + r.name"
                  @click="
                    writer
                      ? open({ kind: 'resource', id: r.id }, $event)
                      : openDetails(r.id, $event)
                  "
                  >{{ t(writer ? 'common.edit' : 'concurrency.view') }}</NButton
                ><NButton
                  v-if="writer"
                  text
                  type="error"
                  :disabled="loading || r.referenceCount > 0 || r.activeInstanceCount > 0"
                  :title="r.referenceCount > 0 ? t('concurrency.referenced') : undefined"
                  @click="removeResources([r], $event)"
                  >{{ t('concurrency.remove') }}</NButton
                >
              </td>
            </tr>
          </tbody>
        </table>
        <table v-else class="concurrency-table concurrency-list">
          <thead>
            <tr>
              <th>{{ t('concurrency.plugin') }}</th>
              <th>{{ t('concurrency.maximum') }}</th>
              <th>{{ t('concurrency.policy') }}</th>
              <th class="concurrency-actions">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in visiblePlugins" :key="r.type">
              <td>{{ pluginLabel(r.type, t) }}</td>
              <td>{{ r.policy?.maxConcurrency ?? '—' }}</td>
              <td>{{ t(r.policy ? 'concurrency.configured' : 'concurrency.unconfigured') }}</td>
              <td class="concurrency-actions">
                <NButton
                  text
                  type="primary"
                  :disabled="loading"
                  :aria-label="
                    t(
                      !writer
                        ? 'concurrency.view'
                        : r.policy
                          ? 'common.edit'
                          : 'concurrency.configure',
                    ) +
                    ' ' +
                    r.type
                  "
                  @click="open({ kind: 'plugin', type: r.type }, $event)"
                  >{{
                    t(
                      !writer
                        ? 'concurrency.view'
                        : r.policy
                          ? 'common.edit'
                          : 'concurrency.configure',
                    )
                  }}</NButton
                ><NButton
                  v-if="writer && r.policy"
                  text
                  type="error"
                  :disabled="loading || r.activeInstanceCount > 0"
                  @click="open({ kind: 'plugin', type: r.type }, $event, true)"
                  >{{ t('concurrency.removeLimit') }}</NButton
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-if="loaded && !error && !total" :message="t('concurrency.empty')" />
      <Pagination
        v-if="loaded && !error"
        :page="pageNumber"
        :size="size"
        :total="total"
        :busy="loading"
        @page="setPage"
        @size="setSize"
      />
    </div>
    <ResourceDetails v-if="detailId" :id="detailId" @close="closeDetails" />
    <ResourceDeleteDialog
      v-if="deletion.length"
      :rows="deletion"
      @close="closeDeletion"
      @changed="load"
    />
    <PolicyDrawer
      v-if="target"
      :target="target"
      :remove-on-open="removing"
      @close="close"
      @saved="load"
    />
  </section>
</template>
