<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NDrawer, NDrawerContent, NInput } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import AppliedFilters from '../../shared/ui/AppliedFilters.vue'
import AdvancedFilter from '../../shared/ui/AdvancedFilter.vue'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import RunProgress from '../../shared/ui/RunProgress.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import type { ReferenceName } from '../../shared/runtime/reference-names'
import { formatUtc } from '../../shared/time/format'
import { exactId } from '../../shared/api/values'
import {
  instance,
  pageData,
  range,
  results,
  sendRecord,
  sources,
  states,
  type Instance,
  type SendRecord,
} from './model'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import DateTime from '../../shared/ui/UtcDateTimePicker.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import './runs.css'
const props = defineProps<{ notifications?: boolean }>()
const route = useRoute(),
  router = useRouter(),
  runtime = useRuntime(),
  { user } = useIdentity(),
  { t, te } = useI18n()
const mode = computed(() =>
  props.notifications ? 'records' : route.query.tab === 'instances' ? 'instances' : 'triggers',
)
const allowed = computed(
  () =>
    !runtime.accessIssue.value &&
    can(user.value, props.notifications ? 'SENSITIVE_READ' : 'SUMMARY_READ'),
)
function defaults() {
  const now = new Date()
  return {
    name: '',
    targetType: '',
    targetId: '',
    status: '',
    triggerType: '',
    scheduleId: '',
    triggerId: '',
    rootInstanceId: '',
    rootOnly: 'true',
    sourceType: '',
    result: '',
    notifyConfigId: '',
    instanceId: '',
    from: new Date(+now - 86400000).toISOString().slice(0, 19),
    to: now.toISOString().slice(0, 19),
  }
}
type Filters = ReturnType<typeof defaults>
const draft = reactive(defaults()),
  applied = ref(defaults()),
  page = ref(1),
  size = ref(20),
  advanced = ref(false),
  errors = ref<Record<string, string>>({}),
  initialized = ref(false)
const tabQueries = new Map<string, typeof route.query>()
async function switchTab(tab: string) {
  if (tab === mode.value) return
  tabQueries.set(mode.value, { ...route.query })
  await router.replace({ query: tabQueries.get(tab) ?? { tab } })
}
const selected = ref<Instance | SendRecord>(),
  returnFocus = ref<HTMLElement>()
const basic = computed(() =>
  props.notifications
    ? ['sourceType', 'result']
    : ['name', 'targetType', 'status', 'triggerType'].filter(
        (k) => mode.value === 'triggers' || k !== 'triggerType',
      ),
)
const extra = computed(() =>
  props.notifications
    ? ['notifyConfigId', 'targetType', 'targetId', 'instanceId']
    : mode.value === 'triggers'
      ? ['targetId', 'scheduleId']
      : ['targetId', 'triggerId', 'rootInstanceId'],
)
const advancedCount = computed(
  () => extra.value.filter((k) => applied.value[k as keyof Filters]).length,
)
const relevant = computed(
  () =>
    [
      ...basic.value,
      ...extra.value,
      'from',
      'to',
      ...(mode.value === 'instances' ? ['rootOnly'] : []),
    ] as (keyof Filters)[],
)
watch(
  () => [route.query, mode.value],
  () => {
    const values = defaults()
    if ('from' in route.query || 'to' in route.query) {
      values.from = ''
      values.to = ''
    }
    for (const key of relevant.value)
      if (typeof route.query[key] === 'string') values[key] = route.query[key] as string
    Object.assign(draft, values)
    applied.value = { ...values }
    page.value = Math.max(1, Math.min(100000, Number(route.query.page) || 1))
    size.value = [20, 50, 100].includes(Number(route.query.size)) ? Number(route.query.size) : 20
    errors.value = {}
    if (!('from' in route.query) || !('to' in route.query)) {
      initialized.value = false
      void router.replace({ query: { ...route.query, from: values.from, to: values.to } })
    } else initialized.value = true
    selected.value = undefined
  },
  { immediate: true },
)
const queryString = computed(() => {
  const q = new URLSearchParams({ pageNum: String(page.value), pageSize: String(size.value) })
  for (const key of relevant.value)
    if (applied.value[key])
      q.set(
        props.notifications && key === 'from'
          ? 'fromTime'
          : props.notifications && key === 'to'
            ? 'toTime'
            : key,
        applied.value[key],
      )
  return q.toString()
})
const query = useQuery(
  computed(() => ({
    staleTime: 0,
    queryKey: [
      'run-list',
      runtime.session.revision,
      runtime.preferences.locale,
      mode.value,
      queryString.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const data = await runtime.request(
        '/api/' + (props.notifications ? 'notify-send-logs' : mode.value) + '?' + queryString.value,
        { signal },
      )
      return props.notifications
        ? pageData<Instance | SendRecord>(data, sendRecord)
        : pageData<Instance | SendRecord>(data, instance)
    },
    enabled: allowed.value && initialized.value,
    retry: false,
  })),
)
watch(
  () => query.data.value,
  (data) => {
    if (selected.value) selected.value = data?.list.find((r) => r.id === selected.value?.id)
  },
)
watch(allowed, (value) => {
  if (!value) selected.value = undefined
})
function enumLabel(value: string) {
  return te('runEnums.' + value)
    ? t('runEnums.' + value)
    : states.includes(value)
      ? t('tasks.status_' + value)
      : value || '—'
}
function options(key: string) {
  const values =
    key === 'targetType'
      ? ['TASK', 'JOB']
      : key === 'status'
        ? states.filter(
            (s) => mode.value === 'instances' || !['PAUSED', 'WAITING_EVENT'].includes(s),
          )
        : key === 'triggerType'
          ? ['SCHEDULE', 'MANUAL', 'EVENT']
          : key === 'sourceType'
            ? sources
            : key === 'result'
              ? results
              : null
  return values?.map((v) => ({ value: v, label: enumLabel(v) }))
}
async function update(p?: number, s = size.value) {
  if (!allowed.value || query.isFetching.value) return
  errors.value = {}
  let normalized = { from: draft.from, to: draft.to }
  try {
    normalized = range(draft.from, draft.to, props.notifications)
  } catch {
    errors.value.from = t(props.notifications ? 'runs.invalidNotifyTime' : 'runs.invalidTime')
    errors.value.to = t(props.notifications ? 'runs.invalidNotifyTime' : 'runs.invalidTime')
  }
  for (const key of extra.value.filter((k) => k.endsWith('Id'))) {
    try {
      if (draft[key as keyof Filters]) exactId(draft[key as keyof Filters])
    } catch {
      errors.value[key] = t('runs.invalidId')
    }
  }
  if (props.notifications && !!draft.targetType !== !!draft.targetId) {
    errors.value.targetType = t('runs.invalidPair')
    errors.value.targetId = t('runs.invalidPair')
  }
  const first = Object.keys(errors.value)[0]
  if (first) {
    if (extra.value.some((key) => errors.value[key])) advanced.value = true
    await nextTick()
    document.getElementById('run-' + first)?.focus()
    return
  }
  Object.assign(draft, normalized)
  const changed = relevant.value.some((key) => draft[key] !== applied.value[key])
  const q: Record<string, string> = {
    tab: mode.value,
    page: String(p ?? (changed ? 1 : page.value)),
    size: String(s),
  }
  for (const key of relevant.value)
    if (draft[key] || key === 'from' || key === 'to') q[key] = draft[key]
  const previous = queryString.value
  await router.replace({ query: q })
  await nextTick()
  if (queryString.value === previous) await query.refetch()
}
async function paginate(p: number, s = size.value) {
  Object.assign(draft, applied.value)
  await update(p, s)
}
const filterChips = computed(() =>
  Object.fromEntries(
    [...basic.value, ...extra.value].map((key) => [key, applied.value[key as keyof Filters]]),
  ),
)
function removeFilter(key: string) {
  Object.assign(draft, applied.value, { [key]: '' })
  if (props.notifications && ['targetId', 'targetType'].includes(key)) {
    draft.targetId = ''
    draft.targetType = ''
  }
  void update(1)
}
function reset() {
  Object.assign(draft, defaults())
  void update(1)
}

function time(value: unknown) {
  try {
    return value
      ? formatUtc(String(value), runtime.preferences.locale, runtime.preferences.displayTimezone)
      : '—'
  } catch {
    return '—'
  }
}
const columns = computed(() =>
  props.notifications
    ? ['id', 'sourceType', 'notifyConfigId', 'notifyType', 'result', 'createTime']
    : mode.value === 'triggers'
      ? ['id', 'target', 'status', 'triggerType', 'triggerTime']
      : ['id', 'target', 'status', 'progress', 'startTime', 'endTime', 'createTime'],
)
function value(row: Instance | SendRecord, key: string): string {
  const val = (row as unknown as Record<string, unknown>)[key]
  if (key === 'notifyType' && typeof val === 'string')
    return `${t('config.' + ({ EMAIL: 'email', DINGTALK: 'ding', WECOM: 'wecom', FEISHU: 'feishu', SLACK: 'slack', DISCORD: 'discord' } as Record<string, string>)[val])} · ${val}`
  if (key === 'businessTime') return typeof val === 'string' && val ? val : '—'
  if (key.endsWith('Time') || key === 'timeoutAt' || key === 'permitWaitDeadline') return time(val)
  if (['sourceType', 'result', 'status', 'triggerType'].includes(key))
    return enumLabel(String(val ?? ''))
  if (key === 'target') return (row as Instance).targetName || t('experience.nameUnavailable')
  return val == null || val === '' ? '—' : String(val)
}
const notificationIds = computed(() =>
  props.notifications && allowed.value && !query.isError.value
    ? [
        ...new Set(
          (query.data.value?.list ?? [])
            .map((row) => (row as SendRecord).notifyConfigId)
            .filter((id): id is string => !!id),
        ),
      ]
    : [],
)
const notificationNamesQuery = useQuery(
  computed(() => ({
    queryKey: [
      'send-record-config-names',
      runtime.session.revision,
      runtime.preferences.locale,
      notificationIds.value,
    ],
    enabled: allowed.value && notificationIds.value.length > 0,
    retry: false,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      runtime.request<ReferenceName[]>('/api/reference-summaries', {
        method: 'POST',
        signal,
        body: { resourceIds: [], notificationIds: notificationIds.value },
      }),
  })),
)
function notificationName(row: Instance | SendRecord) {
  return (
    (allowed.value &&
    !notificationNamesQuery.isError.value &&
    Array.isArray(notificationNamesQuery.data.value)
      ? notificationNamesQuery.data.value?.find(
          (item) => item.kind === 'NOTIFICATION' && item.id === (row as SendRecord).notifyConfigId,
        )?.name
      : '') || t('experience.nameUnavailable')
  )
}
const detailFields = computed(() =>
  props.notifications
    ? [
        'id',
        'sourceType',
        'result',
        'notifyConfigId',
        'notifyType',
        'target',
        'instanceId',
        'notifyBindingId',
        'operatorId',
        'correlationId',
        'durationMs',
        'createTime',
        'updateTime',
        'failureCode',
        'failureSummary',
      ]
    : [
        'id',
        'target',
        'status',
        'triggerType',
        'scheduleId',
        'taskDefinitionId',
        'jobDefinitionId',
        'triggerTime',
        'businessDate',
        'businessTime',
        'businessZoneId',
        'createTime',
        'updateTime',
        'message',
        'errorCode',
      ],
)
const inspectedTrigger = computed(() => {
  if (!allowed.value || mode.value !== 'triggers' || typeof route.query.inspectTrigger !== 'string')
    return ''
  try {
    return exactId(route.query.inspectTrigger)
  } catch {
    return ''
  }
})
const diagnosticBack = computed(() =>
  typeof route.query.back === 'string' && /^\/diagnostics(?:\?|$)/.test(route.query.back)
    ? route.query.back
    : '',
)
const triggerQuery = useQuery(
  computed(() => ({
    queryKey: [
      'run-trigger',
      runtime.session.revision,
      runtime.preferences.locale,
      selected.value?.id || inspectedTrigger.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      instance(
        await runtime.request('/api/triggers/' + (selected.value?.id || inspectedTrigger.value), {
          signal,
        }),
      ),
    enabled:
      allowed.value && (!!selected.value || !!inspectedTrigger.value) && !props.notifications,
    retry: false,
  })),
)
const detail = computed(() =>
  props.notifications
    ? selected.value
    : triggerQuery.data.value
      ? {
          ...triggerQuery.data.value,
          targetName:
            triggerQuery.data.value.targetName ||
            (selected.value as Instance | undefined)?.targetName ||
            '',
        }
      : undefined,
)
async function close() {
  selected.value = undefined
  if (inspectedTrigger.value) {
    const query = { ...route.query }
    delete query.inspectTrigger
    await router.replace({ query })
  }
  await nextTick()
  returnFocus.value?.focus()
}
function open(row: Instance | SendRecord, event: MouseEvent) {
  if (mode.value === 'instances') {
    void router.push({
      name: 'run-detail',
      params: { id: row.id },
      query: { back: route.fullPath },
    })
    return
  }
  returnFocus.value = event.currentTarget as HTMLElement
  selected.value = row
}
</script>
<template>
  <div class="runs-page">
    <RouterLink v-if="diagnosticBack" :to="diagnosticBack"
      >← {{ t('diagnostics.back') }}</RouterLink
    >
    <div class="page-heading list-heading">
      <div>
        <h1>{{ t(notifications ? 'config.titleNotifications' : 'runs.title') }}</h1>
        <p class="subtitle">{{ t(notifications ? 'runs.recordsSubtitle' : 'runs.subtitle') }}</p>
      </div>
    </div>
    <slot name="tabs">
      <LineTabs
        id="run-tabs"
        panel-id="run-list-panel"
        :label="t('runs.title')"
        :value="mode"
        :tabs="['triggers', 'instances'].map((value) => ({ value, label: t('runs.' + value) }))"
        @change="switchTab"
      />
    </slot>
    <section
      id="run-list-panel"
      class="run-panel list-panel"
      role="tabpanel"
      :aria-labelledby="notifications ? 'notification-tabs-records' : 'run-tabs-' + mode"
    >
      <form novalidate @submit.prevent="update()">
        <div class="run-filters">
          <div v-for="key in basic" :key="key" class="run-field" :class="'run-filter-' + key">
            <label :for="'run-' + key">{{ t('runs.' + key) }}</label>
            <CompactSelect
              v-if="options(key)"
              :id="'run-' + key"
              :label="t('runs.' + key)"
              :value="draft[key as keyof Filters]"
              :options="[{ value: '', label: t('common.all') }, ...options(key)!]"
              @update:value="draft[key as keyof Filters] = String($event)"
            />
            <NInput
              v-else
              v-model:value="draft[key as keyof Filters]"
              :input-props="{ id: 'run-' + key, 'aria-label': t('runs.' + key) }"
            />
          </div>
          <NCheckbox
            v-if="mode === 'instances'"
            :checked="draft.rootOnly === 'true'"
            @update:checked="draft.rootOnly = $event ? 'true' : 'false'"
            >{{ t('runs.rootOnly') }}</NCheckbox
          >
        </div>
        <div
          class="run-time-group"
          role="group"
          :aria-label="t(mode === 'triggers' ? 'runs.triggerTime' : 'runs.createTime')"
        >
          <span class="run-time-label">{{
            t(mode === 'triggers' ? 'runs.triggerTime' : 'runs.createTime')
          }}</span>
          <div class="run-date-pair">
            <div v-for="key in ['from', 'to'] as const" :key="key" class="run-date">
              <label class="run-date-label" :for="'run-' + key">{{ t('runs.' + key) }}</label>
              <DateTime
                :id="'run-' + key"
                v-model="draft[key]"
                :label="t('runs.' + key)"
                :invalid="!!errors[key]"
                :describedby="errors[key] ? 'run-' + key + '-error' : undefined"
                :required="notifications"
              />
              <p
                v-if="errors[key]"
                :id="'run-' + key + '-error'"
                class="run-field-error"
                role="alert"
              >
                {{ errors[key] }}
              </p>
            </div>
          </div>
          <div class="run-actions">
            <NButton
              class="list-query-action"
              type="primary"
              attr-type="submit"
              :loading="query.isFetching.value"
              >{{ t('runs.search') }}</NButton
            >
            <NButton class="list-query-action" :disabled="query.isFetching.value" @click="reset">{{
              t('runs.reset')
            }}</NButton>
            <AdvancedFilter
              :expanded="advanced"
              :count="advancedCount"
              :label="t('runs.advanced')"
              controls="run-advanced"
              @toggle="advanced = !advanced"
            />
          </div>
        </div>
        <p v-if="notifications" class="run-note">{{ t('runs.notifyRange') }}</p>
        <div v-show="advanced" id="run-advanced" class="run-filters run-advanced">
          <div v-for="key in extra" :key="key" class="run-field run-id-field">
            <label :for="'run-' + key">{{ t('runs.' + key) }}</label>
            <div class="run-field-control">
              <CompactSelect
                v-if="options(key)"
                :id="'run-' + key"
                :invalid="!!errors[key]"
                :label="t('runs.' + key)"
                :value="draft[key as keyof Filters]"
                :options="[{ value: '', label: t('common.all') }, ...options(key)!]"
                @update:value="draft[key as keyof Filters] = String($event)"
              />
              <NInput
                v-else
                v-model:value="draft[key as keyof Filters]"
                :input-props="{
                  id: 'run-' + key,
                  'aria-label': t('runs.' + key),
                  'aria-invalid': !!errors[key],
                  'aria-describedby': errors[key] ? 'run-' + key + '-error' : undefined,
                }"
              />
              <p
                v-if="errors[key]"
                :id="'run-' + key + '-error'"
                class="run-field-error"
                role="alert"
              >
                {{ errors[key] }}
              </p>
            </div>
          </div>
        </div>
      </form>
      <AppliedFilters
        :values="filterChips"
        :busy="query.isFetching.value"
        @remove="removeFilter"
        @clear="reset"
      /><RequestError :error="query.error.value" />
      <p v-if="query.isPending.value && allowed" role="status">{{ t('common.loading') }}</p>
      <template v-if="query.data.value && !query.isError.value && allowed">
        <div class="run-table-scroll">
          <table class="run-table">
            <thead>
              <tr>
                <th v-for="key in columns" :key="key">
                  {{ t('runs.' + (key === 'notifyConfigId' ? 'notifyConfiguration' : key)) }}
                </th>
                <th class="run-sticky">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in query.data.value.list" :key="row.id">
                <td v-for="key in columns" :key="key">
                  <template v-if="key === 'progress' && 'progress' in row"
                    ><RunProgress
                      :progress="row.progress"
                      :target-type="row.targetType"
                      :status="row.status" /></template
                  ><template v-else-if="key === 'target'"
                    ><div>
                      {{
                        ('targetName' in row && row.targetName) || t('experience.nameUnavailable')
                      }}
                    </div>
                    <IdentityText
                      :value="row.targetId"
                      :label="
                        row.targetType === 'JOB' ? t('unified.jobId') : t('unified.taskId')
                      " /></template
                  ><template v-else-if="key === 'notifyConfigId'"
                    ><div>{{ notificationName(row) }}</div>
                    <IdentityText :value="(row as SendRecord).notifyConfigId" /></template
                  ><IdentityText
                    v-else-if="key === 'id'"
                    :value="row.id"
                    :label="t('unified.recordId')"
                  />
                  <span
                    v-else
                    :class="['status', 'result'].includes(key) ? 'run-status' : ''"
                    :data-status="
                      ['status', 'result'].includes(key)
                        ? (row as unknown as Record<string, unknown>)[key]
                        : undefined
                    "
                    >{{ value(row, key) }}</span
                  >
                </td>
                <td class="run-sticky">
                  <NButton
                    text
                    type="primary"
                    :aria-label="t('common.details') + ' ' + row.id"
                    @click="open(row, $event)"
                    >{{ t('common.details') }}</NButton
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState v-if="!query.data.value.list.length" :message="t('runs.empty')" />
        <Pagination
          :page="page"
          :size="size"
          :total="query.data.value.total"
          :busy="query.isFetching.value"
          @page="paginate($event)"
          @size="paginate(1, $event)"
        />
      </template>
    </section>
    <NDrawer
      :show="allowed && (!!selected || !!inspectedTrigger)"
      width="min(520px, 100vw)"
      class="run-drawer"
      @update:show="close"
      ><NDrawerContent
        closable
        :title="t(notifications ? 'runs.sendDetail' : 'runs.triggerDetail')"
        :native-scrollbar="true"
      >
        <p v-if="notifications" class="run-note">{{ t('runs.snapshot') }}</p>
        <template v-else
          ><RequestError :error="triggerQuery.error.value" /><NButton
            :loading="triggerQuery.isFetching.value"
            @click="triggerQuery.refetch()"
            >{{ t('common.refresh') }}</NButton
          ></template
        >
        <dl v-if="detail" class="run-meta">
          <template v-for="key in detailFields" :key="key"
            ><dt>{{ t('runs.' + (key === 'notifyConfigId' ? 'notifyConfiguration' : key)) }}</dt>
            <dd>
              <RouterLink
                v-if="
                  key === 'instanceId' &&
                  (detail as SendRecord).instanceId &&
                  can(user, 'SUMMARY_READ')
                "
                :to="'/runs/instances/' + (detail as SendRecord).instanceId"
                >{{ value(detail, key) }}</RouterLink
              ><template v-else-if="key === 'notifyConfigId'"
                ><div>{{ notificationName(detail) }}</div>
                <IdentityText :value="(detail as SendRecord).notifyConfigId" /></template
              ><template v-else>{{ value(detail, key) }}</template>
            </dd></template
          >
        </dl>
        <template v-if="!notifications && detail"
          ><RouterLink
            v-if="(detail as Instance).rootInstanceId"
            :to="{
              name: 'run-detail',
              params: { id: (detail as Instance).rootInstanceId! },
              query: { back: route.fullPath },
            }"
            >{{ t('runs.open') }}</RouterLink
          >
          <p v-else>{{ t('runs.noInstance') }}</p></template
        >
        <template #footer
          ><NButton @click="close">{{ t('common.close') }}</NButton></template
        >
      </NDrawerContent></NDrawer
    >
  </div>
</template>
