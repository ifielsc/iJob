<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NDropdown, NInput, useDialog, useMessage } from 'naive-ui'
import { Plus, ChevronDown } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError, describeError } from '../../shared/api/errors'
import AppliedFilters from '../../shared/ui/AppliedFilters.vue'
import { useListMemory } from '../../shared/ui/use-list-memory'
import RequestError from '../../shared/ui/RequestError.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import Select from '../../shared/ui/CompactSelect.vue'
import { pageData } from '../../shared/api/values'
import { metadata, schedule, types, id, type Schedule } from './model'
import ScheduleEditor from './ScheduleEditor.vue'
import ScheduleDetails from './ScheduleDetails.vue'
import AdvancedFilter from '../../shared/ui/AdvancedFilter.vue'
import './schedules.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const allowed = computed(() => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value),
  writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && allowed.value)
const advanced = ref(false)
const page = ref(1),
  size = ref(20),
  draft = reactive({ targetName: '', targetType: '', targetId: '', scheduleType: '', enabled: '' }),
  applied = ref({ ...draft }),
  filterError = ref(false),
  checked = ref<string[]>([]),
  busy = ref(false),
  selected = shallowRef<Schedule>(),
  editor = ref(false),
  details = ref(false),
  actionError = shallowRef<unknown>()
const controller = new AbortController()
let returnFocus: HTMLElement | null = null
const meta = useQuery(
  computed(() => ({
    queryKey: ['schedule-metadata', runtime.session.revision],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      metadata(await runtime.request('/api/schedules/metadata', { signal })),
    enabled: allowed.value,
    retry: false,
  })),
)
const query = useQuery(
  computed(() => ({
    queryKey: [
      'schedules',
      runtime.session.revision,
      runtime.preferences.locale,
      page.value,
      size.value,
      applied.value,
    ],
    staleTime: 0,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const params = new URLSearchParams({
        pageNum: String(page.value),
        pageSize: String(size.value),
      })
      for (const [k, v] of Object.entries(applied.value)) if (v) params.set(k, v)
      return pageData(
        await runtime.request('/api/schedules?' + params, { signal, lossless: true }),
        schedule,
      )
    },
    enabled: allowed.value,
    retry: false,
  })),
)
const allChecked = computed(
  () =>
    !!query.data.value?.list.length &&
    query.data.value.list.every((v) => checked.value.includes(v.id)),
)
watch([page, size, applied], () => (checked.value = []))
watch(
  () => query.data.value,
  (data) => {
    checked.value = checked.value.filter((v) => data?.list.some((row) => row.id === v))
  },
)
watch([allowed, writer, () => runtime.session.revision], () => {
  editor.value = false
  details.value = false
  selected.value = undefined
  checked.value = []
  actionError.value = undefined
  dialog.destroyAll()
})
onBeforeUnmount(() => {
  controller.abort()
  dialog.destroyAll()
})
function report(e: unknown) {
  actionError.value = e
  message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
}
async function search(reset = false) {
  if (query.isFetching.value || busy.value) return
  if (reset)
    Object.assign(draft, {
      targetName: '',
      targetType: '',
      targetId: '',
      scheduleType: '',
      enabled: '',
    })
  filterError.value = false
  if (draft.targetId) {
    try {
      id(draft.targetId)
    } catch {
      advanced.value = true
      filterError.value = true
      await nextTick()
      document.getElementById('schedule-filter-targetId')?.focus()
      return
    }
  }
  const old = JSON.stringify([applied.value, page.value])
  if (reset || JSON.stringify(draft) !== JSON.stringify(applied.value)) page.value = 1
  applied.value = { ...draft }
  checked.value = []
  if (old === JSON.stringify([applied.value, page.value])) await query.refetch()
}
async function open(row: Schedule | undefined, event: MouseEvent, edit = false) {
  if (busy.value || !allowed.value || (edit && (!writer.value || !meta.data.value))) return
  returnFocus = event.currentTarget as HTMLElement
  busy.value = true
  actionError.value = undefined
  try {
    const data = row
      ? schedule(
          await runtime.request('/api/schedules/' + row.id, {
            signal: controller.signal,
            lossless: true,
          }),
        )
      : undefined
    if (controller.signal.aborted || !allowed.value) return
    if (edit && data && data.variablesText === undefined) throw new ApiError('protocol')
    selected.value = data
    editor.value = edit
    details.value = !edit
  } catch (e) {
    if (!controller.signal.aborted) report(e)
  } finally {
    busy.value = false
  }
}
async function close() {
  editor.value = false
  details.value = false
  selected.value = undefined
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus()
}
async function saved() {
  await close()
  message.success(t('schedules.saved'))
  await query.refetch()
}
function select(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((v) => v !== id)
}
function mutation(kind: 'delete' | 'enabled', rows: Schedule[]) {
  if (!writer.value || busy.value || !rows.length) return
  const targets = rows.map((v) => ({ ...v })),
    first = targets[0]!,
    enable = !first.enabled
  dialog.warning({
    title: t(
      kind === 'delete'
        ? 'schedules.batchDelete'
        : enable
          ? 'schedules.enable'
          : 'schedules.disable',
    ),
    content:
      kind === 'delete'
        ? t('schedules.confirmDelete', { count: targets.length })
        : t('schedules.confirmEnabled', {
            action: t(enable ? 'schedules.enable' : 'schedules.disable'),
            id: first.id,
          }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (!writer.value || busy.value) return false
      busy.value = true
      actionError.value = undefined
      try {
        await runtime.request(
          '/api/schedules/' +
            (kind === 'enabled'
              ? first.id + '/enabled'
              : targets.length === 1
                ? first.id
                : 'batch'),
          {
            method: kind === 'enabled' ? 'PATCH' : 'DELETE',
            body:
              kind === 'enabled'
                ? { enabled: enable }
                : targets.length > 1
                  ? { ids: targets.map((v) => v.id) }
                  : undefined,
            signal: controller.signal,
          },
        )
        if (!controller.signal.aborted && writer.value) {
          message.success(t(kind === 'delete' ? 'schedules.deleted' : 'schedules.updated'))
          checked.value = []
          await query.refetch()
          if (page.value > 1 && query.data.value && !query.data.value.list.length) page.value--
        }
      } catch (e) {
        if (!controller.signal.aborted) report(e)
      } finally {
        busy.value = false
      }
    },
  })
}
function changeSize(value: number) {
  size.value = value
  page.value = 1
}
function rule(row: Schedule) {
  return row.scheduleType === 'CRON'
    ? row.cronExpr
    : row.scheduleType === 'INTERVAL'
      ? t('schedules.intervalSec') + ': ' + row.intervalSec
      : t('schedules.ONCE') + ' · ' + row.startTime.replace('T', ' ')
}
const { removeFilter } = useListMemory(draft, applied, page, size, query.isFetching)
</script>
<template>
  <div class="schedules-page">
    <div class="page-heading list-heading">
      <div>
        <h1>{{ t('schedules.title') }}</h1>
        <p class="subtitle">{{ t('schedules.subtitle') }}</p>
      </div>
      <NButton
        v-if="writer"
        class="list-create-action"
        type="primary"
        :disabled="busy || !meta.data.value"
        @click="open(undefined, $event, true)"
        ><template #icon><Plus :size="16" /></template>{{ t('schedules.new') }}</NButton
      >
    </div>
    <div class="schedule-zone">
      <span>{{ t('schedules.zone') }}: {{ meta.data.value?.zoneId ?? '—' }}</span
      ><NButton v-if="meta.isError.value" text @click="meta.refetch()">{{
        t('common.refresh')
      }}</NButton>
    </div>
    <RequestError :error="meta.error.value" />
    <section class="schedule-panel list-panel">
      <form class="schedule-filters list-filters" novalidate @submit.prevent="search()">
        <div
          v-for="key in ['targetName', 'targetType', 'enabled'] as const"
          :key="key"
          class="schedule-filter"
        >
          <label :for="'schedule-filter-' + key">{{
            t('schedules.' + (key === 'enabled' ? 'state' : key))
          }}</label>
          <NInput
            v-if="key === 'targetName'"
            v-model:value="draft.targetName"
            :placeholder="t('schedules.nameSearch')"
            :input-props="{ id: 'schedule-filter-targetName' }"
          />
          <Select
            v-else
            :id="'schedule-filter-' + key"
            :label="t('schedules.' + key)"
            :value="draft[key]"
            :options="[
              { value: '', label: t('common.all') },
              ...(key === 'targetType' ? ['TASK', 'JOB'] : ['true', 'false']).map((value) => ({
                value,
                label: t(
                  'schedules.' + (value === 'true' ? 'on' : value === 'false' ? 'off' : value),
                ),
              })),
            ]"
            @update:value="draft[key] = String($event)"
          />
        </div>
        <div class="schedule-actions">
          <NButton
            class="list-query-action"
            type="primary"
            attr-type="submit"
            :loading="query.isFetching.value"
            >{{ t('runs.search') }}</NButton
          ><NButton
            class="list-query-action"
            :disabled="query.isFetching.value"
            @click="search(true)"
            >{{ t('runs.reset') }}</NButton
          ><AdvancedFilter
            :expanded="advanced"
            :count="Number(!!applied.targetId) + Number(!!applied.scheduleType)"
            controls="schedules-advanced"
            @toggle="advanced = !advanced"
          />
        </div>
        <div v-show="advanced" id="schedules-advanced" class="unified-advanced-fields">
          <div
            v-for="key in ['targetId', 'scheduleType'] as const"
            :key="key"
            class="schedule-filter"
          >
            <label :for="'schedule-filter-' + key">{{ t('schedules.' + key) }}</label>
            <div v-if="key === 'targetId'">
              <NInput
                v-model:value="draft.targetId"
                :input-props="{
                  id: 'schedule-filter-targetId',
                  'aria-invalid': filterError,
                  'aria-describedby': filterError ? 'schedule-filter-error' : undefined,
                }"
              />
              <p v-if="filterError" id="schedule-filter-error" role="alert" class="schedule-error">
                {{ t('schedules.invalidId') }}
              </p>
            </div>
            <Select
              v-else
              :id="'schedule-filter-' + key"
              :label="t('schedules.' + key)"
              :value="draft[key]"
              :options="[
                { value: '', label: t('common.all') },
                ...types.map((value) => ({
                  value,
                  label: t('schedules.' + value),
                })),
              ]"
              @update:value="draft[key] = String($event)"
            />
          </div>
        </div>
      </form>
      <div v-if="writer" class="schedule-batch list-toolbar">
        <NButton
          size="small"
          class="list-batch-action"
          type="error"
          :disabled="busy || !checked.length"
          @click="
            mutation('delete', query.data.value?.list.filter((v) => checked.includes(v.id)) ?? [])
          "
          >{{ t('schedules.batchDelete') }}</NButton
        ><span>{{ t('schedules.selected', { count: checked.length }) }}</span>
      </div>
      <AppliedFilters
        :values="applied"
        :busy="query.isFetching.value"
        @remove="removeFilter"
        @clear="search(true)"
      /><RequestError :error="query.error.value" /><RequestError :error="actionError" />
      <p v-if="query.isPending.value" role="status">{{ t('common.loading') }}</p>
      <template v-if="query.data.value && !query.isError.value && allowed">
        <div class="schedule-table-scroll">
          <table class="schedule-table">
            <thead>
              <tr>
                <th v-if="writer" class="schedule-check">
                  <NCheckbox
                    :aria-label="t('schedules.allRows')"
                    :checked="allChecked"
                    :indeterminate="!!checked.length && !allChecked"
                    @update:checked="checked = $event ? query.data.value.list.map((v) => v.id) : []"
                  />
                </th>
                <th
                  v-for="key in ['id', 'target', 'rule', 'state', 'nextFireTime']"
                  :key="key"
                  :class="'schedule-column-' + key"
                >
                  {{ t('schedules.' + key)
                  }}<span v-if="key === 'nextFireTime'">
                    ({{ meta.data.value?.zoneId ?? '—' }})</span
                  >
                </th>
                <th class="schedule-sticky">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in query.data.value.list" :key="row.id">
                <td v-if="writer" class="schedule-check">
                  <NCheckbox
                    :aria-label="t('schedules.select', { id: row.id })"
                    :checked="checked.includes(row.id)"
                    @update:checked="select(row.id, $event)"
                  />
                </td>
                <td class="schedule-column-id">{{ row.id }}</td>
                <td class="schedule-column-target">
                  <div class="schedule-target-name">
                    {{ row.targetName || t('schedules.nameUnavailable') }}
                  </div>
                  <div class="schedule-target-id">
                    {{ t('schedules.' + row.targetType) }} #{{ row.targetId }}
                  </div>
                  <span
                    class="schedule-mobile-state"
                    :class="row.enabled ? 'status-success' : 'status-disabled'"
                    >{{ t(row.enabled ? 'schedules.on' : 'schedules.off') }}</span
                  >
                </td>
                <td class="schedule-column-rule">{{ rule(row) }}</td>
                <td class="schedule-column-state">
                  <span
                    class="schedule-state"
                    :class="row.enabled ? 'status-success' : 'status-disabled'"
                    >{{ t(row.enabled ? 'schedules.on' : 'schedules.off') }}</span
                  >
                </td>
                <td class="schedule-column-nextFireTime">
                  {{ row.nextFireTime.replace('T', ' ') || '—' }}
                </td>
                <td class="schedule-sticky">
                  <div class="schedule-actions">
                    <NButton
                      text
                      type="primary"
                      :disabled="busy"
                      :aria-label="t('common.details') + ' ' + row.id"
                      @click="open(row, $event)"
                      >{{ t('common.details') }}</NButton
                    ><NButton
                      v-if="writer"
                      text
                      type="primary"
                      :disabled="busy || !meta.data.value"
                      :aria-label="t('common.edit') + ' ' + row.id"
                      @click="open(row, $event, true)"
                      >{{ t('common.edit') }}</NButton
                    ><NDropdown
                      v-if="writer"
                      trigger="click"
                      :options="[
                        {
                          key: 'enabled',
                          label: t(row.enabled ? 'schedules.disable' : 'schedules.enable'),
                          disabled: busy || !meta.data.value,
                        },
                        {
                          key: 'delete',
                          label: () => h('span', { class: 'danger-action' }, t('tasks.delete')),
                          disabled: busy,
                        },
                      ]"
                      @select="mutation($event, [row])"
                      ><NButton
                        text
                        type="primary"
                        :disabled="busy"
                        :aria-label="t('schedules.more') + ' ' + row.id"
                        >{{ t('schedules.more') }}<ChevronDown :size="14" /></NButton
                    ></NDropdown>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="!query.data.value.list.length"
          :message="
            t(Object.values(applied).some(Boolean) ? 'experience.noMatch' : 'schedules.empty')
          "
        />
        <Pagination
          :page="page"
          :size="size"
          :total="query.data.value.total"
          :busy="busy || query.isFetching.value"
          @page="page = $event"
          @size="changeSize"
        />
      </template>
    </section>
    <ScheduleEditor
      v-if="editor && meta.data.value && writer"
      :row="selected"
      :metadata="meta.data.value"
      @close="close"
      @saved="saved"
    />
    <ScheduleDetails
      v-if="details && selected && allowed"
      :row="selected"
      :metadata="meta.data.value"
      @close="close"
    />
  </div>
</template>
