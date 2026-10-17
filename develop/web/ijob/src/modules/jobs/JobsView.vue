<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { randomUuid } from '../../shared/runtime/uuid'
import { ParameterFormEditor } from '../parameters/public/components'
import { computed, h, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCheckbox, NDropdown, NInput, useDialog, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError, describeError } from '../../shared/api/errors'
import { formatUtc } from '../../shared/time/format'
import AppliedFilters from '../../shared/ui/AppliedFilters.vue'
import { useListMemory } from '../../shared/ui/use-list-memory'
import RequestError from '../../shared/ui/RequestError.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import { GroupFilter } from '../tasks/public/components'
import { EventBindingsDrawer } from '../tasks/public/components'
import { NotifyBindingsDrawer } from '../tasks/public/components'
import { RunDrawer } from '../tasks/public/components'
import { exactId, record } from '../../shared/api/values'
import { jobRow, pageData, type JobRow } from './model'
import { ChevronDown } from '@lucide/vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import AdvancedFilter from '../../shared/ui/AdvancedFilter.vue'
import './jobs.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  router = useRouter(),
  route = useRoute(),
  dialog = useDialog(),
  message = useMessage()
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value),
  sensitive = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value),
  executor = computed(() => can(user.value, 'EXECUTION_CONTROL') && !runtime.accessIssue.value)
const param = (key: string) =>
  typeof route.query[key] === 'string' ? (route.query[key] as string) : ''
const savedPage = Number(param('pageNum')),
  savedSize = Number(param('pageSize'))
const page = ref(Number.isSafeInteger(savedPage) && savedPage > 0 ? savedPage : 1),
  size = ref([10, 20, 50, 100].includes(savedSize) ? savedSize : 20),
  filters = reactive({
    id: param('id'),
    name: param('name'),
    enabled: ['true', 'false'].includes(param('enabled')) ? param('enabled') : '',
    group: param('group'),
  }),
  applied = ref({ ...filters }),
  checked = ref<string[]>([]),
  busy = ref(false),
  binding = shallowRef<JobRow>(),
  eventBinding = shallowRef<JobRow>(),
  running = shallowRef<{ row: JobRow; triggerId?: string }>()
let controller = new AbortController()
onBeforeUnmount(() => {
  controller.abort()
  dialog.destroyAll()
  message.destroyAll()
})
const statuses = computed(() => [
  { value: '', label: t('tasks.all') },
  { value: 'true', label: t('tasks.enabled') },
  { value: 'false', label: t('tasks.disabled') },
])
const query = useQuery(
  computed(() => ({
    queryKey: [
      'jobs',
      runtime.session.revision,
      runtime.preferences.locale,
      page.value,
      size.value,
      applied.value,
    ],
    enabled: can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value,
    staleTime: 0,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const params = new URLSearchParams({
        pageNum: String(page.value),
        pageSize: String(size.value),
      })
      for (const [key, value] of Object.entries(applied.value)) if (value) params.set(key, value)
      return pageData(await runtime.request('/api/jobs?' + params, { signal }), jobRow)
    },
  })),
)
const rows = computed(() => (runtime.accessIssue.value ? [] : (query.data.value?.list ?? []))),
  allChecked = computed(
    () => rows.value.length > 0 && rows.value.every((r) => checked.value.includes(r.id)),
  )
watch([page, size, applied], () => (checked.value = []))
watch([() => runtime.session.revision, () => runtime.accessIssue.value], () => {
  controller.abort()
  controller = new AbortController()
  busy.value = false
  binding.value = undefined
  eventBinding.value = undefined
  running.value = undefined
  checked.value = []
  dialog.destroyAll()
})
const advanced = ref(Boolean(filters.id || filters.group)),
  idError = ref(false)
function jobLink(path: string) {
  const params = new URLSearchParams({ pageNum: String(page.value), pageSize: String(size.value) })
  for (const [key, value] of Object.entries(applied.value)) if (value) params.set(key, value)
  return { path, query: { back: '/jobs?' + params.toString() } }
}
function check(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((v) => v !== id)
}
async function search(reset = false) {
  if (query.isFetching.value || busy.value) return
  const previous = JSON.stringify([applied.value, page.value])
  if (reset) Object.assign(filters, { id: '', name: '', enabled: '', group: '' })
  if (reset || JSON.stringify(filters) !== JSON.stringify(applied.value)) page.value = 1
  idError.value = false
  if (filters.id) {
    try {
      exactId(filters.id)
    } catch {
      idError.value = true
      advanced.value = true
      message.error(t('unified.invalidId'))
      return
    }
  }
  applied.value = { ...filters }
  checked.value = []
  if (JSON.stringify([applied.value, page.value]) === previous) await query.refetch()
}
function failure(e: unknown) {
  message.error(describeError(e, t), { duration: 5000 })
}
function options() {
  return [
    ...(can(user.value, 'SUMMARY_READ') ? [{ key: 'runs', label: t('tasks.records') }] : []),
    { key: 'versions', label: t('unified.versionHistory') },
    ...(writer.value && sensitive.value
      ? [
          { key: 'bindings', label: t('bindings.title') },
          { key: 'parameters', label: t('parameters.title') },
          { key: 'events', label: t('eventBindings.title') },
        ]
      : []),
    ...(writer.value
      ? [{ key: 'delete', label: () => h('span', { class: 'danger-action' }, t('tasks.delete')) }]
      : []),
  ]
}
function more(key: string, row: JobRow) {
  if (key === 'parameters' && writer.value && !busy.value) {
    const editor = ref<InstanceType<typeof ParameterFormEditor> | null>(null)
    const popup = dialog.info({
      title: t('parameters.title') + ' · ' + row.name,
      class: 'parameter-execution-dialog',
      showIcon: false,
      maskClosable: false,
      content: () =>
        h('div', { class: 'parameter-execution-content' }, [
          h(ParameterFormEditor, {
            ref: editor,
            targetType: 'JOB',
            targetId: row.id,
            autoDefault: true,
            showBindings: true,
            externalActions: true,
          }),
        ]),
      action: () =>
        h('div', { class: 'parameter-popup-actions' }, [
          h(
            NButton,
            {
              type: 'primary',
              disabled: !editor.value?.canSave,
              onClick: () => editor.value?.openSave(),
            },
            () => t('parameters.saveTemplate'),
          ),
          h(NButton, { disabled: editor.value?.pending, onClick: () => popup.destroy() }, () =>
            t('common.close'),
          ),
        ]),
    })
    return
  }

  if (busy.value) return
  if (key === 'versions') void router.push(jobLink('/jobs/' + row.id + '/versions'))
  if (key === 'bindings') binding.value = row
  if (key === 'runs') running.value = { row }
  if (key === 'events' && writer.value && sensitive.value) eventBinding.value = row
  if (key === 'delete') remove([row])
  if (key === 'execute') void execute(row)
}
function remove(targets: JobRow[]) {
  if (!writer.value || busy.value || !targets.length) return
  const ids = targets.map((r) => r.id)
  dialog.warning({
    title: t('tasks.delete'),
    content: t('jobs.deleteHint', { names: targets.map((r) => r.name + ' #' + r.id).join(', ') }),
    positiveText: t('tasks.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (busy.value || !writer.value) return
      busy.value = true
      try {
        await runtime.request('/api/jobs/' + (ids.length === 1 ? ids[0] : 'batch'), {
          method: 'DELETE',
          body: ids.length > 1 ? { ids } : undefined,
          signal: controller.signal,
        })
        if (controller.signal.aborted) return
        message.success(t('tasks.deleted', { count: ids.length }))
        checked.value = []
        if (targets.length === rows.value.length && page.value > 1) page.value--
        else await query.refetch()
      } catch (e) {
        if (!controller.signal.aborted) failure(e)
      } finally {
        busy.value = false
      }
    },
  })
}
async function execute(row: JobRow) {
  if (!executor.value || !row.enabled || busy.value) return
  busy.value = true
  let context: Record<string, unknown>
  try {
    context = record(
      await runtime.request('/api/jobs/' + row.id + '/execution-context', {
        signal: controller.signal,
      }),
    )
    exactId(context.revision)
    if (!context.enabled) throw new ApiError('conflict')
  } catch (e) {
    failure(e)
    busy.value = false
    return
  } finally {
    busy.value = false
  }
  if (controller.signal.aborted || runtime.accessIssue.value) return
  const parameterEditor = ref<InstanceType<typeof ParameterFormEditor> | null>(null)
  const requestId = randomUuid()
  const executionDialog = dialog.warning({
    title: t('parameters.executeJob'),
    class: 'parameter-execution-dialog',
    showIcon: false,
    positiveButtonProps: { type: 'primary' },
    maskClosable: false,
    onClose: () => !busy.value,
    onNegativeClick: () => !busy.value,
    onEsc: () => !busy.value,
    action: () =>
      h('div', { class: 'parameter-execution-footer' }, [
        h(
          'div',
          writer.value
            ? [
                h(
                  NButton,
                  {
                    type: 'primary',
                    disabled: !parameterEditor.value?.canSave,
                    onClick: () => parameterEditor.value?.openSave(),
                  },
                  () => t('parameters.saveTemplate'),
                ),
              ]
            : [],
        ),
        h('div', { class: 'parameter-actions' }, [
          h(NButton, { disabled: busy.value, onClick: () => executionDialog.destroy() }, () =>
            t('common.cancel'),
          ),
          h(
            NButton,
            {
              type: 'primary',
              loading: busy.value,
              disabled: busy.value,
              onClick: async (event: MouseEvent) => {
                const result = await executionDialog.onPositiveClick?.(event)
                if (result !== false) executionDialog.destroy()
              },
            },
            () => t('parameters.start'),
          ),
        ]),
      ]),
    positiveText: t('parameters.start'),
    negativeText: t('common.cancel'),
    content: () =>
      h('div', { class: 'parameter-execution-content' }, [
        h('p', { class: 'parameter-execution-name' }, row.name),
        h(ParameterFormEditor, {
          ref: parameterEditor,
          execution: true,
          disabled: busy.value,
          targetType: 'JOB',
          targetId: row.id,
          autoDefault: true,
        }),
      ]),
    onPositiveClick: async () => {
      if (busy.value || !executor.value) return false
      let parameters: ReturnType<InstanceType<typeof ParameterFormEditor>['read']>
      try {
        if (!parameterEditor.value) return false
        parameters = parameterEditor.value.read()
      } catch {
        message.error(t('tasks.variablesInvalid'))
        return false
      }
      busy.value = true
      executionDialog.closable = false
      executionDialog.closeOnEsc = false
      try {
        const id = exactId(
          await runtime.request('/api/jobs/' + row.id + '/manual-trigger', {
            method: 'POST',
            body: { requestId, ...parameters },
            lossless: true,
            signal: controller.signal,
          }),
        )
        if (!controller.signal.aborted && !runtime.accessIssue.value) {
          message.success(t('tasks.accepted'))
          running.value = { row, triggerId: id }
        }
      } catch (e) {
        if (!controller.signal.aborted) {
          if (e instanceof ApiError && ['network', 'timeout', 'protocol'].includes(e.kind)) {
            message.warning(t('tasks.uncertain'), { duration: 0, closable: true })
            running.value = { row }
          } else failure(e)
        }
      } finally {
        busy.value = false
        executionDialog.closable = true
        executionDialog.closeOnEsc = true
      }
    },
  })
}
function time(value: string) {
  try {
    return formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone)
  } catch {
    return '—'
  }
}
function resize(value: number) {
  size.value = value
  page.value = 1
}
const { removeFilter } = useListMemory(filters, applied, page, size, query.isFetching)
</script>
<template>
  <section class="jobs-page">
    <div class="job-heading list-heading">
      <div>
        <h1>{{ t('jobs.title') }}</h1>
        <p>{{ t('jobs.subtitle') }}</p>
      </div>
      <NButton
        v-if="writer && sensitive"
        class="list-create-action"
        :aria-label="t('jobs.create')"
        type="primary"
        :disabled="busy"
        @click="router.push(jobLink('/jobs/new'))"
        >＋ {{ t('jobs.create') }}</NButton
      >
    </div>
    <div class="job-panel list-panel">
      <form class="job-filters list-filters" @submit.prevent="search()">
        <div>
          <label for="jobs-name">{{ t('jobs.name') }}</label
          ><NInput
            v-model:value="filters.name"
            :input-props="{ id: 'jobs-name' }"
            :maxlength="128"
          />
        </div>
        <div>
          <label for="jobs-enabled">{{ t('jobs.status') }}</label
          ><CompactSelect
            id="jobs-enabled"
            v-model:value="filters.enabled"
            :label="t('jobs.status')"
            :options="statuses"
          />
        </div>

        <div class="job-actions">
          <NButton
            class="list-query-action"
            attr-type="submit"
            type="primary"
            :loading="query.isFetching.value"
            :disabled="busy"
            >{{ t('tasks.search') }}</NButton
          ><NButton
            class="list-query-action"
            :disabled="busy || query.isFetching.value"
            @click="search(true)"
            >{{ t('tasks.reset') }}</NButton
          ><AdvancedFilter
            :expanded="advanced"
            :count="Number(!!applied.id) + Number(!!applied.group)"
            controls="jobs-advanced"
            @toggle="advanced = !advanced"
          />
        </div>
      </form>
      <div v-if="advanced" id="jobs-advanced" class="unified-advanced-fields">
        <div class="job-group-filter">
          <label for="jobs-id">{{ t('unified.jobId') }}</label
          ><NInput
            v-model:value="filters.id"
            :input-props="{ id: 'jobs-id', 'aria-invalid': idError }"
          /><span v-if="idError" class="field-error">{{ t('unified.invalidId') }}</span>
        </div>
        <div class="job-group-filter">
          <label for="tasks-group">{{ t('jobs.groups') }}</label>
          <GroupFilter v-model:value="filters.group" endpoint="jobs" />
        </div>
      </div>
      <div v-if="writer" class="job-toolbar list-toolbar">
        <NButton
          size="small"
          class="list-batch-action"
          type="error"
          :disabled="busy || !checked.length"
          @click="remove(rows.filter((r) => checked.includes(r.id)))"
          >{{ t('tasks.batchDelete') }}</NButton
        ><span class="job-note">{{ t('tasks.selected', { count: checked.length }) }}</span>
      </div>
      <AppliedFilters
        :values="applied"
        :busy="query.isFetching.value"
        @remove="removeFilter"
        @clear="search(true)"
      /><RequestError :error="query.error.value" />
      <div class="job-table-scroll">
        <table class="job-table">
          <thead>
            <tr>
              <th v-if="writer">
                <NCheckbox
                  :checked="allChecked"
                  :aria-label="t('jobs.selectAll')"
                  :disabled="busy"
                  @update:checked="checked = $event ? rows.map((r) => r.id) : []"
                />
              </th>
              <th>{{ t('jobs.name') }}</th>
              <th>{{ t('jobs.status') }}</th>
              <th :title="t('unified.nodeHint')">{{ t('unified.nodeCount') }}</th>
              <th>{{ t('jobs.groups') }}</th>
              <th>{{ t('tasks.updated') }}</th>
              <th>{{ t('tasks.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td v-if="writer">
                <NCheckbox
                  :checked="checked.includes(row.id)"
                  :disabled="busy"
                  :aria-label="row.name"
                  @update:checked="check(row.id, $event)"
                />
              </td>
              <td>
                <strong>{{ row.name }}</strong
                ><IdentityText :value="row.id" :label="t('unified.jobId')" />
              </td>
              <td>
                <span :class="row.enabled ? 'job-enabled' : 'job-disabled'">{{
                  t(row.enabled ? 'tasks.enabled' : 'tasks.disabled')
                }}</span>
              </td>
              <td>{{ row.nodeCount ?? '—' }}</td>
              <td class="execution-groups">
                <span v-for="group in row.groups" :key="group" class="execution-group">{{
                  group
                }}</span
                ><span v-if="!row.groups.length">—</span>
              </td>
              <td>{{ time(row.updateTime) }}</td>
              <td>
                <div class="job-actions">
                  <NButton
                    v-if="sensitive"
                    text
                    type="primary"
                    @click="router.push(jobLink('/jobs/' + row.id))"
                    >{{ t('common.details') }}</NButton
                  ><NButton
                    v-if="writer && sensitive"
                    text
                    type="primary"
                    :disabled="busy"
                    @click="router.push(jobLink('/jobs/' + row.id + '/edit'))"
                    >{{ t('tasks.editAction') }}</NButton
                  ><NButton
                    v-if="executor"
                    text
                    :disabled="busy || !row.enabled"
                    @click="execute(row)"
                    >{{ t('tasks.execute') }}</NButton
                  ><NDropdown :options="options()" trigger="click" @select="more($event, row)"
                    ><NButton text :disabled="busy"
                      >{{ t('jobs.more') }}<ChevronDown :size="14" /></NButton
                  ></NDropdown>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-if="!rows.length && !query.isFetching.value && !query.isError.value"
        :message="t(Object.values(applied).some(Boolean) ? 'experience.noMatch' : 'jobs.empty')"
      />
      <Pagination
        :page="page"
        :size="size"
        :total="query.data.value?.total ?? 0"
        :busy="query.isFetching.value || busy"
        @page="page = $event"
        @size="resize"
      />
    </div>
    <EventBindingsDrawer
      v-if="eventBinding"
      :key="eventBinding.id"
      :row="eventBinding"
      target-type="JOB"
      @close="eventBinding = undefined"
    />
    <NotifyBindingsDrawer
      v-if="binding"
      :row="binding"
      target-type="JOB"
      @close="binding = undefined"
    /><RunDrawer
      v-if="running"
      :row="running.row"
      target-type="JOB"
      :trigger-id="running.triggerId"
      @close="running = undefined"
    />
  </section>
</template>
