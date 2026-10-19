<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { useTableOverflow } from '../../shared/ui/use-table-overflow'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NInput, useDialog, useMessage } from 'naive-ui'
import { Plus } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { describeError } from '../../shared/api/errors'
import AppliedFilters from '../../shared/ui/AppliedFilters.vue'
import { useListMemory } from '../../shared/ui/use-list-memory'
import RequestError from '../../shared/ui/RequestError.vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import { parsePage } from '../security-contracts'
import { parseConfig } from './contracts'
import type { ConfigRow } from './contracts'
import ConfigEditor from './ConfigEditor.vue'
import TestSend from './TestSend.vue'
import { notificationTypes, notificationLabel } from './notification-channels'
import ConfigDetails from './ConfigDetails.vue'
import './configuration.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value)
const page = ref(1),
  size = ref(20),
  filters = reactive({ name: '', type: '', enabled: '' }),
  applied = ref({ ...filters })
const inspecting = ref<string>()
const editing = ref(false),
  selected = shallowRef<ConfigRow>(),
  sending = shallowRef<ConfigRow>(),
  busy = ref(false),
  checked = ref<string[]>([])
const abort = new AbortController()
let returnFocus: HTMLElement | null = null
const tableScroll = ref<HTMLElement | null>(null)
const tableOverflow = useTableOverflow(tableScroll)
onBeforeUnmount(() => {
  abort.abort()
  message.destroyAll()
})
const statuses = computed(() => [
  { label: t('config.all'), value: '' },
  { label: t('config.enabled'), value: 'true' },
  { label: t('config.disabled'), value: 'false' },
])
const types = computed(() => [
  { label: t('config.all'), value: '' },
  ...notificationTypes.map((value) => ({ value, label: t(notificationLabel(value)) })),
])
const query = useQuery(
  computed(() => ({
    staleTime: 0,
    queryKey: [
      'configuration',
      'notifications',
      runtime.session.revision,
      runtime.preferences.locale,
      page.value,
      size.value,
      applied.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const params = new URLSearchParams({
        pageNum: String(page.value),
        pageSize: String(size.value),
      })
      if (applied.value.type) params.set('type', applied.value.type)
      if (applied.value.name) params.set('name', applied.value.name)
      if (applied.value.enabled) params.set('enabled', applied.value.enabled)
      return parsePage(await runtime.request('/api/notify-configs?' + params, { signal }), (v) =>
        parseConfig(v),
      )
    },
    enabled: can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value,
  })),
)
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) {
      sending.value = undefined
      inspecting.value = undefined
      editing.value = false
      selected.value = undefined
      checked.value = []
      message.destroyAll()
    }
  },
)
watch([page, size, applied], () => {
  checked.value = []
})
watch(
  () => query.data.value,
  (data) => {
    checked.value = checked.value.filter((id) => data?.list.some((row) => row.id === id))
  },
)
const allChecked = computed(
  () =>
    !!query.data.value?.list.length &&
    query.data.value.list.every((row) => checked.value.includes(row.id)),
)
function selectRow(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((item) => item !== id)
}
function failed(error: unknown) {
  message.error(() => h('span', { role: 'alert' }, describeError(error, t)), { duration: 5000 })
}
async function open(row?: ConfigRow, event?: MouseEvent) {
  if (busy.value || !writer.value) return
  returnFocus = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  busy.value = true
  try {
    const detail = row
      ? parseConfig(
          await runtime.request('/api/notify-configs/' + row.id, { signal: abort.signal }),
          true,
        )
      : undefined
    if (abort.signal.aborted || runtime.accessIssue.value) return
    selected.value = detail
    editing.value = true
  } catch (error) {
    if (!abort.signal.aborted && !runtime.accessIssue.value) failed(error)
  } finally {
    busy.value = false
  }
}
async function closeEditor() {
  sending.value = undefined
  editing.value = false
  selected.value = undefined
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
async function search(reset = false) {
  if (busy.value || runtime.accessIssue.value || query.isFetching.value) return
  const previous = JSON.stringify([applied.value, page.value])
  if (reset) Object.assign(filters, { name: '', type: '', enabled: '' })
  const next = { ...filters }
  if (reset || JSON.stringify(next) !== JSON.stringify(applied.value)) page.value = 1
  applied.value = next
  checked.value = []
  if (JSON.stringify([applied.value, page.value]) === previous) await query.refetch()
}
async function refresh() {
  if (busy.value || runtime.accessIssue.value) return
  checked.value = []
  await query.refetch()
}
function setSize(value: number) {
  size.value = value
  page.value = 1
}
async function saved() {
  await closeEditor()
  message.success(() => h('span', { role: 'status' }, t('config.saved')), { duration: 3000 })
  await query.refetch()
}
function remove(rows: ConfigRow[]) {
  if (busy.value || !writer.value || !rows.length) return
  const targets = [...rows]
  dialog.warning({
    title: t(targets.length > 1 ? 'config.batchDelete' : 'config.delete'),
    content: () =>
      h(
        'div',
        { class: 'notification-delete-confirm' },
        targets.length === 1
          ? t('config.deleteHint', { name: targets[0]!.name })
          : t('config.notifyBatchDeleteHint', {
              count: targets.length,
              names: targets.map((row) => row.name).join('、'),
            }),
      ),
    positiveText: t('config.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (busy.value || !writer.value) return
      busy.value = true
      let removed = 0
      const failures: { id: string; text: string }[] = []
      try {
        for (const row of targets) {
          if (runtime.accessIssue.value || abort.signal.aborted) break
          try {
            await runtime.request('/api/notify-configs/' + row.id, {
              method: 'DELETE',
              signal: abort.signal,
            })
            removed++
          } catch (error) {
            failures.push({ id: row.id, text: row.name + '：' + describeError(error, t) })
          }
        }
        if (runtime.accessIssue.value || abort.signal.aborted) return
        if (removed)
          message.success(
            () => h('span', { role: 'status' }, t('config.deleteCount', { count: removed })),
            { duration: 3000 },
          )
        if (failures.length)
          message.error(
            () =>
              h('div', { role: 'alert', class: 'notification-delete-failures' }, [
                h('strong', t('config.deleteFailed', { count: failures.length })),
                h(
                  'ul',
                  failures.map((item) => h('li', item.text)),
                ),
              ]),
            { duration: 0, closable: true },
          )
        if (query.data.value?.list.length === removed && page.value > 1) page.value--
        await nextTick()
        await query.refetch()
        checked.value = failures
          .map((item) => item.id)
          .filter((id) => query.data.value?.list.some((row) => row.id === id))
      } finally {
        busy.value = false
      }
    },
  })
}
async function openSend(row: ConfigRow, event: MouseEvent) {
  if (busy.value || !writer.value || !row.enabled) return
  returnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  busy.value = true
  try {
    const detail = parseConfig(
      await runtime.request('/api/notify-configs/' + row.id, { signal: abort.signal }),
      true,
    )
    if (abort.signal.aborted || runtime.accessIssue.value) return
    if (detail.type === 'EMAIL' && !detail.config?.mailChannelId) {
      message.error(t('mailChannels.requiredHint'))
      return
    }
    sending.value = detail
  } catch (error) {
    if (!abort.signal.aborted) failed(error)
  } finally {
    busy.value = false
  }
}
async function sent() {
  await closeEditor()
  message.success(() => h('span', { role: 'status' }, t('config.delivered')), { duration: 3000 })
}
const { removeFilter } = useListMemory(filters, applied, page, size, query.isFetching)
</script>
<template>
  <ConfigDetails
    v-if="inspecting"
    :id="inspecting"
    kind="notifications"
    @close="inspecting = undefined"
  />
  <div class="page-heading list-heading">
    <div>
      <h1>{{ t('config.titleNotifications') }}</h1>
      <p class="subtitle">{{ t('config.notifySubtitle') }}</p>
    </div>
    <NButton
      v-if="writer"
      class="notification-create list-create-action"
      type="primary"
      :disabled="busy"
      @click="open(undefined, $event)"
      ><template #icon><Plus :size="16" /></template>{{ t('config.createNotification') }}</NButton
    >
  </div>
  <slot name="tabs" />
  <section
    v-if="!runtime.accessIssue.value"
    id="notification-list-panel"
    role="tabpanel"
    aria-labelledby="notification-tabs-configurations"
    class="panel glass notification-list-panel list-panel"
  >
    <form
      class="configuration-filter notification-filter list-filters"
      novalidate
      @submit.prevent="search()"
    >
      <div class="filter-item">
        <label for="filter-name">{{ t('config.name') }}</label
        ><NInput
          v-model:value="filters.name"
          :disabled="busy"
          :input-props="{ id: 'filter-name' }"
        />
      </div>
      <div class="filter-item">
        <label for="filter-type">{{ t('config.type') }}</label
        ><CompactSelect
          id="filter-type"
          v-model:value="filters.type"
          :disabled="busy"
          :label="t('config.type')"
          :options="types"
        />
      </div>
      <div class="filter-item">
        <label for="filter-enabled">{{ t('config.status') }}</label
        ><CompactSelect
          id="filter-enabled"
          v-model:value="filters.enabled"
          :disabled="busy"
          :label="t('config.status')"
          :options="statuses"
        />
      </div>
      <div class="list-query-actions">
        <NButton
          class="list-query-action"
          type="primary"
          attr-type="submit"
          :loading="query.isFetching.value"
          :disabled="busy"
          >{{ t('config.search') }}</NButton
        ><NButton class="list-query-action" :disabled="busy" @click="search(true)">{{
          t('config.resetFilters')
        }}</NButton>
      </div>
    </form>
    <div v-if="writer" class="notification-toolbar list-toolbar">
      <div>
        <template v-if="writer"
          ><NButton
            size="small"
            class="list-batch-action"
            type="error"
            :disabled="busy || query.isFetching.value || query.isError.value || !checked.length"
            @click="
              remove((query.data.value?.list ?? []).filter((row) => checked.includes(row.id)))
            "
            >{{ t('config.batchDelete') }}</NButton
          ><span>{{ t('config.selectedCount', { count: checked.length }) }}</span></template
        >
      </div>
    </div>
    <AppliedFilters
      :values="applied"
      :busy="query.isFetching.value"
      @remove="removeFilter"
      @clear="search(true)"
    /><RequestError :error="query.error.value" /><NButton
      v-if="query.isError.value"
      @click="refresh"
      >{{ t('common.retry') }}</NButton
    >
    <p v-if="query.isPending.value" role="status">{{ t('common.loading') }}</p>
    <template v-if="query.data.value && !query.isError.value">
      <div
        ref="tableScroll"
        class="table-scroll notification-table-scroll"
        :class="{ 'has-overflow': tableOverflow }"
      >
        <table class="configuration-table notification-table">
          <thead>
            <tr>
              <th v-if="writer" class="notification-check">
                <NCheckbox
                  :checked="allChecked"
                  :indeterminate="!allChecked && !!checked.length"
                  :disabled="busy || query.isFetching.value || !query.data.value.list.length"
                  :aria-label="t('config.selectPage')"
                  @update:checked="
                    checked = $event ? query.data.value.list.map((row) => row.id) : []
                  "
                />
              </th>
              <th>{{ t('config.name') }}</th>
              <th>{{ t('config.type') }}</th>
              <th>{{ t('config.rateProtection') }}</th>
              <th class="notification-status">{{ t('config.status') }}</th>
              <th v-if="writer" class="notification-actions-cell">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in query.data.value.list"
              :key="row.id"
              :class="{ 'is-selected': checked.includes(row.id) }"
            >
              <td v-if="writer" class="notification-check">
                <NCheckbox
                  :checked="checked.includes(row.id)"
                  :disabled="busy || query.isFetching.value"
                  :aria-label="t('config.selectNotification', { name: row.name })"
                  @update:checked="selectRow(row.id, $event)"
                />
              </td>
              <td>
                <NButton
                  v-if="can(user, 'SENSITIVE_READ')"
                  text
                  type="primary"
                  @click="inspecting = row.id"
                  >{{ row.name }}</NButton
                ><span v-else>{{ row.name }}</span>
              </td>
              <td>{{ t(notificationLabel(row.type)) }}</td>
              <td>{{ row.type === 'EMAIL' ? '—' : t('config.automaticProtection') }}</td>
              <td>
                <span :class="row.enabled ? 'status-success' : 'status-disabled'">
                  {{ t(row.enabled ? 'config.enabled' : 'config.disabled') }}</span
                >
              </td>
              <td v-if="writer" class="notification-actions-cell">
                <div class="configuration-actions">
                  <NButton
                    text
                    type="primary"
                    :disabled="busy"
                    :aria-label="t('common.edit') + ' ' + row.name"
                    @click="open(row, $event)"
                    >{{ t('common.edit') }}</NButton
                  ><NButton text :disabled="busy || !row.enabled" @click="openSend(row, $event)">{{
                    t('config.testSend')
                  }}</NButton
                  ><NButton
                    text
                    type="error"
                    :disabled="busy"
                    :aria-label="t('config.delete') + ' ' + row.name"
                    @click="remove([row])"
                    >{{ t('config.delete') }}</NButton
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-if="!query.data.value.list.length"
        :message="t(Object.values(applied).some(Boolean) ? 'experience.noMatch' : 'common.empty')"
      />
      <PageControls
        :page="page"
        :size="size"
        :total="query.data.value.total"
        :busy="busy || query.isFetching.value"
        @page="page = $event"
        @size="setSize"
      />
    </template>
  </section>
  <ConfigEditor
    v-if="editing && !runtime.accessIssue.value"
    kind="notifications"
    :row="selected"
    @close="closeEditor"
    @saved="saved"
  />
  <TestSend
    v-if="sending && !runtime.accessIssue.value"
    :row="sending"
    @close="closeEditor"
    @sent="sent"
  />
</template>
