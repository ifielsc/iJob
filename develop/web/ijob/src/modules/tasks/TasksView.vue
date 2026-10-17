<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { randomUuid } from '../../shared/runtime/uuid'
import { ParameterFormEditor } from '../parameters/public/components'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NDropdown, NInput, useDialog, useMessage } from 'naive-ui'
import { Plus, ChevronDown } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { describeError, ApiError } from '../../shared/api/errors'
import { formatUtc } from '../../shared/time/format'
import AppliedFilters from '../../shared/ui/AppliedFilters.vue'
import { useListMemory } from '../../shared/ui/use-list-memory'
import RequestError from '../../shared/ui/RequestError.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import { taskRow, pageData, exactId, record } from './model'
import type { TaskRow } from './model'
import TaskEditor from './TaskEditor.vue'
import RunDrawer from './RunDrawer.vue'
import EventBindingsDrawer from './EventBindingsDrawer.vue'
import NotifyBindingsDrawer from './NotifyBindingsDrawer.vue'
import { PolicyDrawer } from '../concurrency/public/components'
import GroupFilter from './GroupFilter.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import AdvancedFilter from '../../shared/ui/AdvancedFilter.vue'
import TaskDetails from './TaskDetails.vue'
import TaskVersions from './TaskVersions.vue'
import { pluginLabel, pluginTypes, isSupportedPlugin } from '../../shared/plugins'
import './tasks.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value)
const policyReader = computed(
  () => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value,
)
const concurrencyTask = shallowRef<TaskRow>()
const sensitive = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const executor = computed(() => can(user.value, 'EXECUTION_CONTROL') && !runtime.accessIssue.value)
const summary = computed(() => can(user.value, 'SUMMARY_READ') && !runtime.accessIssue.value)
const page = ref(1),
  size = ref(20),
  filters = reactive({ id: '', name: '', pluginType: '', enabled: '', group: '' }),
  applied = ref({ ...filters }),
  advanced = ref(false)
const busy = ref(false),
  checked = ref<string[]>([]),
  editing = ref(false),
  selected = shallowRef<TaskRow>(),
  bindingTask = shallowRef<TaskRow>(),
  eventBindingTask = shallowRef<TaskRow>(),
  running = shallowRef<{ row: TaskRow; triggerId?: string }>()
const detailTask = ref<string>(),
  versionsTask = ref<string>(),
  idError = ref(false)
const abort = new AbortController()
let returnFocus: HTMLElement | null = null
onBeforeUnmount(() => {
  abort.abort()
  message.destroyAll()
  dialog.destroyAll()
})
const statuses = computed(() => [
  { value: '', label: t('tasks.all') },
  { value: 'true', label: t('tasks.enabled') },
  { value: 'false', label: t('tasks.disabled') },
])
const types = computed(() => [
  { value: '', label: t('tasks.all') },
  ...pluginTypes.map((value) => ({ value, label: pluginLabel(value, t) })),
])
const query = useQuery(
  computed(() => ({
    staleTime: 0,
    queryKey: [
      'tasks',
      runtime.session.revision,
      runtime.preferences.locale,
      page.value,
      size.value,
      applied.value,
    ],
    enabled: can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const params = new URLSearchParams({
        pageNum: String(page.value),
        pageSize: String(size.value),
      })
      for (const [key, value] of Object.entries(applied.value)) if (value) params.set(key, value)
      return pageData(await runtime.request('/api/tasks?' + params, { signal }), taskRow)
    },
  })),
)
watch([page, size, applied], () => {
  checked.value = []
})
watch(
  () => query.data.value,
  (data) => {
    checked.value = checked.value.filter((id) => data?.list.some((r) => r.id === id))
  },
)
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) {
      detailTask.value = undefined
      versionsTask.value = undefined
      editing.value = false
      selected.value = undefined
      running.value = undefined
      bindingTask.value = undefined
      eventBindingTask.value = undefined
      concurrencyTask.value = undefined
      checked.value = []
      dialog.destroyAll()
      message.destroyAll()
    }
  },
)
const rows = computed(() => (runtime.accessIssue.value ? [] : (query.data.value?.list ?? [])))
const allChecked = computed(
  () => !!rows.value.length && rows.value.every((r) => checked.value.includes(r.id)),
)
function check(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((x) => x !== id)
}
function remember(event?: MouseEvent) {
  returnFocus = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
}
async function closed() {
  detailTask.value = undefined
  versionsTask.value = undefined
  concurrencyTask.value = undefined
  editing.value = false
  selected.value = undefined
  running.value = undefined
  bindingTask.value = undefined
  eventBindingTask.value = undefined
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
function failure(e: unknown) {
  message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
}
const moreOptions = computed(() => [
  ...(summary.value ? [{ key: 'runs', label: t('tasks.records') }] : []),
  ...(policyReader.value ? [{ key: 'versions', label: t('unified.versionHistory') }] : []),
  ...(policyReader.value ? [{ key: 'concurrency', label: t('concurrency.task') }] : []),
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
])
function more(key: string, row: TaskRow) {
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
            targetType: 'TASK',
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

  if (!busy.value && key === 'runs' && summary.value) {
    running.value = { row }
    return
  }
  if (!busy.value && key === 'versions' && policyReader.value) {
    versionsTask.value = row.id
    return
  }
  if (!busy.value && key === 'concurrency' && policyReader.value) {
    concurrencyTask.value = row
    return
  }
  if (busy.value || !writer.value) return
  if (key === 'bindings' && sensitive.value) bindingTask.value = row
  if (key === 'events' && writer.value && sensitive.value) eventBindingTask.value = row
  if (key === 'delete') remove([row])
}
async function open(row?: TaskRow, event?: MouseEvent) {
  if (
    busy.value ||
    !writer.value ||
    !sensitive.value ||
    (row && !isSupportedPlugin(row.pluginType))
  )
    return
  remember(event)
  busy.value = true
  try {
    const detail = row
      ? taskRow(await runtime.request('/api/tasks/' + row.id, { signal: abort.signal }), true)
      : undefined
    if (abort.signal.aborted || runtime.accessIssue.value) return
    selected.value = detail
    editing.value = true
  } catch (e) {
    if (!abort.signal.aborted && !runtime.accessIssue.value) failure(e)
  } finally {
    busy.value = false
  }
}
async function search(reset = false) {
  if (busy.value || runtime.accessIssue.value || query.isFetching.value) return
  const previous = JSON.stringify([applied.value, page.value])
  if (reset) Object.assign(filters, { id: '', name: '', pluginType: '', enabled: '', group: '' })
  idError.value = false
  if (filters.id) {
    try {
      exactId(filters.id)
    } catch {
      idError.value = true
      advanced.value = true
      await nextTick()
      document.getElementById('tasks-id')?.focus()
      return
    }
  }
  const next = { ...filters }
  if (reset || JSON.stringify(next) !== JSON.stringify(applied.value)) page.value = 1
  applied.value = next
  checked.value = []
  if (JSON.stringify([applied.value, page.value]) === previous) await query.refetch()
}
async function saved(revision: string) {
  await closed()
  message.success(() => h('span', { role: 'status' }, t('tasks.published', { revision })), {
    duration: 3000,
  })
  await query.refetch()
}
function remove(targets: TaskRow[]) {
  if (!writer.value || busy.value || !targets.length) return
  const frozen = [...targets]
  dialog.warning({
    title: t('tasks.delete'),
    content: t('tasks.deleteHint', { names: frozen.map((r) => r.name).join(', ') }),
    positiveText: t('tasks.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (busy.value || !writer.value) return
      busy.value = true
      const failures: { id: string; text: string }[] = []
      let removed = 0
      try {
        for (const row of frozen) {
          if (abort.signal.aborted || runtime.accessIssue.value) break
          try {
            await runtime.request('/api/tasks/' + row.id, {
              method: 'DELETE',
              signal: abort.signal,
            })
            removed++
          } catch (e) {
            failures.push({ id: row.id, text: row.name + ': ' + describeError(e, t) })
          }
        }
        if (abort.signal.aborted || runtime.accessIssue.value) return
        if (removed) message.success(t('tasks.deleted', { count: removed }), { duration: 3000 })
        if (failures.length)
          message.error(
            () =>
              h('div', { class: 'task-failures', role: 'alert' }, [
                h('strong', t('tasks.failures', { count: failures.length })),
                h(
                  'ul',
                  failures.map((f) => h('li', f.text)),
                ),
              ]),
            { duration: 0, closable: true },
          )
        if (removed === rows.value.length && page.value > 1) page.value--
        await nextTick()
        await query.refetch()
        checked.value = failures
          .map((f) => f.id)
          .filter((id) => rows.value.some((r) => r.id === id))
      } finally {
        busy.value = false
      }
    },
  })
}
async function execute(row: TaskRow, event: MouseEvent) {
  if (busy.value || !executor.value || !row.enabled || !isSupportedPlugin(row.pluginType)) return
  remember(event)
  let context: Record<string, unknown> | undefined
  if (row.pluginType === 'SCRIPT' || row.pluginType === 'API_CALL') {
    busy.value = true
    try {
      context = record(
        await runtime.request('/api/tasks/' + row.id + '/execution-context', {
          signal: abort.signal,
        }),
      )
      if (
        context.pluginType !== row.pluginType ||
        (row.pluginType === 'SCRIPT' &&
          (!['LOCAL', 'SSH'].includes(String(context.executionMode)) ||
            !['SHELL', 'BAT', 'POWERSHELL', 'PYTHON'].includes(String(context.engine))))
      )
        throw new ApiError('protocol')
      if (row.pluginType === 'API_CALL') {
        exactId(context.serviceResourceId)
        if (
          !['HTTP', 'WEB_SERVICE'].includes(String(context.protocol)) ||
          !['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].includes(
            String(context.method),
          )
        )
          throw new ApiError('protocol')
      }
      exactId(context.revision)
      if (context.executionMode === 'SSH') exactId(context.sshResourceId)
    } catch (e) {
      if (!abort.signal.aborted && !runtime.accessIssue.value) failure(e)
      return
    } finally {
      busy.value = false
    }
    if (abort.signal.aborted || runtime.accessIssue.value || !executor.value) return
  }
  const parameterEditor = ref<InstanceType<typeof ParameterFormEditor> | null>(null)
  const requestId = randomUuid()
  const executionDialog = dialog.warning({
    title: t('parameters.executeTask'),
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
        h('p', { class: 'parameter-execution-name' }, row.name + ' · ' + row.pluginType),
        ...(context
          ? [
              h(
                'p',
                t(
                  row.pluginType === 'API_CALL'
                    ? 'apiCall.executionContext'
                    : 'tasks.executionContext',
                  {
                    resource: `${String(context.resourceName || 'SERVICE')} (#${String(context.serviceResourceId)})`,
                    protocol: context.protocol,
                    method: context.method,
                    engine: context.engine,
                    target:
                      context.executionMode === 'SSH'
                        ? `${String(context.resourceName || 'SSH')} (#${String(context.sshResourceId)})`
                        : t('tasks.localMode'),
                    revision: context.revision,
                  },
                ),
              ),
            ]
          : []),
        h(ParameterFormEditor, {
          ref: parameterEditor,
          execution: true,
          disabled: busy.value,
          targetType: 'TASK',
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
          await runtime.request('/api/tasks/' + row.id + '/manual-trigger', {
            method: 'POST',
            body: { requestId, ...parameters },
            lossless: true,
            signal: abort.signal,
          }),
        )
        if (!abort.signal.aborted && !runtime.accessIssue.value) {
          message.success(t('tasks.accepted'))
          running.value = { row, triggerId: id }
        }
      } catch (e) {
        if (!abort.signal.aborted && !runtime.accessIssue.value) {
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
function time(value?: string) {
  if (!value) return '—'
  try {
    return formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone)
  } catch {
    return '—'
  }
}
function setSize(value: number) {
  size.value = value
  page.value = 1
}
const { removeFilter } = useListMemory(filters, applied, page, size, query.isFetching)
</script>
<template>
  <section class="tasks-page">
    <div class="task-page-title list-heading">
      <div>
        <h1>{{ t('tasks.title') }}</h1>
        <p>{{ t('tasks.subtitle') }}</p>
      </div>
      <NButton
        v-if="writer && sensitive"
        class="list-create-action"
        type="primary"
        :disabled="busy"
        @click="open(undefined, $event)"
        ><template #icon><Plus :size="16" /></template>{{ t('tasks.create') }}</NButton
      >
    </div>
    <div class="task-panel list-panel">
      <form class="task-filters list-filters" @submit.prevent="search()">
        <div class="task-name-filter">
          <label for="tasks-name">{{ t('tasks.name') }}</label
          ><NInput v-model:value="filters.name" :input-props="{ id: 'tasks-name' }" />
        </div>
        <div class="task-type-filter">
          <label for="tasks-type">{{ t('tasks.type') }}</label
          ><CompactSelect
            id="tasks-type"
            v-model:value="filters.pluginType"
            :title="types.find((option) => option.value === filters.pluginType)?.label"
            :label="t('tasks.type')"
            :options="types"
          />
        </div>
        <div class="task-status-filter">
          <label for="tasks-enabled">{{ t('tasks.status') }}</label
          ><CompactSelect
            id="tasks-enabled"
            v-model:value="filters.enabled"
            :label="t('tasks.status')"
            :options="statuses"
          />
        </div>
        <div class="list-query-actions">
          <NButton
            class="list-query-action"
            type="primary"
            attr-type="submit"
            :disabled="busy"
            :loading="query.isFetching.value"
            >{{ t('tasks.search') }}</NButton
          ><NButton class="list-query-action" :disabled="busy" @click="search(true)">{{
            t('tasks.reset')
          }}</NButton>
          <AdvancedFilter
            :expanded="advanced"
            :count="Number(!!applied.group) + Number(!!applied.id)"
            controls="tasks-advanced"
            @toggle="advanced = !advanced"
          />
        </div>
      </form>
      <div v-if="advanced" id="tasks-advanced" class="unified-advanced-fields">
        <div class="task-group-filter">
          <label for="tasks-id">{{ t('unified.taskId') }}</label
          ><NInput
            v-model:value="filters.id"
            :input-props="{ id: 'tasks-id', 'aria-invalid': idError }"
          /><span v-if="idError" class="field-error">{{ t('unified.invalidId') }}</span>
        </div>
        <div class="task-group-filter">
          <label for="tasks-group">{{ t('tasks.group') }}</label
          ><GroupFilter v-model:value="filters.group" />
        </div>
      </div>
      <div v-if="writer" class="task-toolbar list-toolbar">
        <div>
          <template v-if="writer"
            ><NButton
              class="list-batch-action"
              type="error"
              size="small"
              :disabled="busy || !checked.length"
              @click="remove(rows.filter((r) => checked.includes(r.id)))"
              >{{ t('tasks.batchDelete') }}</NButton
            ><span>{{ t('tasks.selected', { count: checked.length }) }}</span></template
          >
        </div>
      </div>
      <p v-if="query.isFetching.value" role="status">{{ t('common.loading') }}</p>
      <AppliedFilters
        :values="applied"
        :busy="query.isFetching.value"
        @remove="removeFilter"
        @clear="search(true)"
      /><RequestError :error="query.error.value" />
      <div v-if="!runtime.accessIssue.value" class="task-table-scroll">
        <table class="task-table">
          <thead>
            <tr>
              <th v-if="writer" class="task-check">
                <NCheckbox
                  :checked="allChecked"
                  :indeterminate="!!checked.length && !allChecked"
                  :disabled="busy"
                  :aria-label="t('tasks.selected', { count: rows.length })"
                  @update:checked="checked = $event ? rows.map((r) => r.id) : []"
                />
              </th>
              <th>{{ t('tasks.name') }}</th>
              <th>{{ t('tasks.type') }}</th>
              <th>{{ t('tasks.status') }}</th>
              <th>{{ t('tasks.group') }}</th>
              <th>{{ t('tasks.updated') }}</th>
              <th class="task-actions-col">{{ t('tasks.actions') }}</th>
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
              <td class="task-identity">
                <span>{{ row.name }}</span
                ><IdentityText :value="row.id" :label="t('unified.taskId')" />
              </td>
              <td>{{ pluginLabel(row.pluginType, t) }}</td>
              <td>
                <span :class="row.enabled ? 'status-success' : 'status-disabled'">{{
                  t(row.enabled ? 'tasks.enabled' : 'tasks.disabled')
                }}</span>
              </td>
              <td class="execution-groups">
                <span v-for="group in row.groups" :key="group" class="execution-group">{{
                  group
                }}</span
                ><span v-if="!row.groups.length">—</span>
              </td>
              <td>{{ time(row.updateTime) }}</td>
              <td class="task-actions-col">
                <div class="task-row-actions">
                  <NButton
                    v-if="sensitive"
                    text
                    type="primary"
                    :aria-label="t('common.details') + ' ' + row.name"
                    @click="(remember($event), (detailTask = row.id))"
                    >{{ t('common.details') }}</NButton
                  >
                  <NButton
                    v-if="writer && sensitive"
                    text
                    type="primary"
                    :disabled="busy || !isSupportedPlugin(row.pluginType)"
                    :title="!isSupportedPlugin(row.pluginType) ? t('tasks.sqlOnly') : undefined"
                    :aria-label="t('tasks.editAction') + ' ' + row.name"
                    @click="open(row, $event)"
                    >{{ t('tasks.editAction') }}</NButton
                  >
                  <NButton
                    v-if="executor"
                    text
                    :disabled="busy || !row.enabled || !isSupportedPlugin(row.pluginType)"
                    :aria-label="t('tasks.execute') + ' ' + row.name"
                    @click="execute(row, $event)"
                    >{{ t('tasks.execute') }}</NButton
                  >
                  <NDropdown
                    v-if="moreOptions.length"
                    trigger="click"
                    :options="moreOptions"
                    @select="more($event, row)"
                  >
                    <NButton
                      text
                      :disabled="busy"
                      :aria-label="t('bindings.more') + ' ' + row.name"
                      @click="remember($event)"
                      >{{ t('bindings.more') }}<ChevronDown :size="14"
                    /></NButton>
                  </NDropdown>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-if="!rows.length && !query.isFetching.value && !query.error.value"
        :message="t(Object.values(applied).some(Boolean) ? 'experience.noMatch' : 'tasks.empty')"
      />
      <Pagination
        :page="page"
        :size="size"
        :total="query.data.value?.total ?? 0"
        :busy="busy || query.isFetching.value"
        @page="page = $event"
        @size="setSize"
      />
    </div>
    <TaskDetails v-if="detailTask && sensitive" :id="detailTask" @close="closed" /><TaskVersions
      v-if="versionsTask && policyReader"
      :id="versionsTask"
      @close="closed"
    />
    <TaskEditor v-if="editing" :row="selected" @close="closed" @saved="saved" />
    <PolicyDrawer
      v-if="concurrencyTask"
      :target="{ kind: 'task', id: concurrencyTask.id }"
      @close="closed"
    />
    <RunDrawer v-if="running" :row="running.row" :trigger-id="running.triggerId" @close="closed" />
    <EventBindingsDrawer
      v-if="eventBindingTask"
      :key="eventBindingTask.id"
      :row="eventBindingTask"
      target-type="TASK"
      @close="closed"
    />
    <NotifyBindingsDrawer
      v-if="bindingTask"
      :key="bindingTask.id"
      :row="bindingTask"
      @close="closed"
    />
  </section>
</template>
