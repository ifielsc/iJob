<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { randomUuid } from '../../shared/runtime/uuid'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NInput, useDialog } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError } from '../../shared/api/errors'
import RunProgress from '../../shared/ui/RunProgress.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import { formatUtc } from '../../shared/time/format'
import { exactId } from '../../shared/api/values'
import { type Log } from '../../shared/runtime/contracts'
import { logSnapshot } from '../../shared/runtime/log-snapshot'
import {
  actions,
  command,
  commandTerminal,
  instance,
  terminal,
  tree,
  treeRows,
  type Action,
} from './model'
import RequestError from '../../shared/ui/RequestError.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import './runs.css'
import { commandMemory } from './command-memory'
import { ApprovalEntry } from '../approvals/public/components'
import RunGraph from './RunGraph.vue'
const runtime = useRuntime(),
  { user } = useIdentity(),
  route = useRoute(),
  router = useRouter(),
  { t, te } = useI18n(),
  dialog = useDialog()
const id = computed(() => String(route.params.id)),
  tab = ref('summary'),
  selectedId = ref(''),
  treeOpen = ref(false)
const visible = ref(!document.hidden),
  auto = ref(true)
const visibility = () => {
  visible.value = !document.hidden
}
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => document.removeEventListener('visibilitychange', visibility))
const allowed = computed(() => can(user.value, 'SUMMARY_READ') && !runtime.accessIssue.value)
const sensitive = computed(() => can(user.value, 'SENSITIVE_READ') && allowed.value)
const control = computed(() => can(user.value, 'EXECUTION_CONTROL') && allowed.value)
const enabled = computed(() => allowed.value && visible.value)
const selectedQuery = useQuery(
  computed(() => ({
    queryKey: ['run-instance', runtime.session.revision, runtime.preferences.locale, id.value],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      instance(await runtime.request('/api/instances/' + exactId(id.value), { signal })),
    enabled: enabled.value,
    retry: false,
    refetchInterval: (q: { state: { data?: { status: string }; fetchFailureCount: number } }) =>
      auto.value && (!q.state.data || !terminal.has(q.state.data.status))
        ? Math.min(30000, 2000 * 2 ** q.state.fetchFailureCount)
        : false,
  })),
)
const rootId = computed(
  () => selectedQuery.data.value?.rootInstanceId || selectedQuery.data.value?.id || '',
)
const rootQuery = useQuery(
  computed(() => ({
    queryKey: ['run-root', runtime.session.revision, runtime.preferences.locale, rootId.value],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      instance(await runtime.request('/api/instances/' + rootId.value, { signal })),
    enabled: enabled.value && !!rootId.value,
    retry: false,
    refetchInterval: (q: { state: { data?: { status: string }; fetchFailureCount: number } }) =>
      auto.value && (!q.state.data || !terminal.has(q.state.data.status))
        ? Math.min(30000, 2000 * 2 ** q.state.fetchFailureCount)
        : false,
  })),
)
const treeQuery = useQuery(
  computed(() => ({
    queryKey: ['run-tree', runtime.session.revision, runtime.preferences.locale, rootId.value],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      tree(await runtime.request('/api/instances/' + rootId.value + '/tree', { signal })),
    enabled: enabled.value && !!rootId.value,
    retry: false,
    refetchInterval: (q: { state: { fetchFailureCount: number } }) =>
      auto.value && rootQuery.data.value && !terminal.has(rootQuery.data.value.status)
        ? Math.min(30000, 2000 * 2 ** q.state.fetchFailureCount)
        : false,
  })),
)
watch(id, () => {
  selectedId.value = ''
  tab.value = 'summary'
})
watch(
  () => selectedQuery.data.value,
  (row) => {
    if (row && !selectedId.value) selectedId.value = row.id
  },
)
const nodes = computed(() => treeRows(treeQuery.data.value?.nodes ?? []))
const collapsed = ref(new Set<string>())
const parents = computed(
  () => new Set((treeQuery.data.value?.nodes ?? []).map((n) => n.parentInstanceId)),
)
const visibleNodes = computed(() => {
  let hiddenDepth = Infinity
  return nodes.value.filter((item) => {
    if (item.depth > hiddenDepth) return false
    hiddenDepth = collapsed.value.has(item.node.id) ? item.depth : Infinity
    return true
  })
})
function toggleNode(id: string) {
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
}
watch(
  () => rootQuery.data.value?.status,
  (status, previous) => {
    if (status && status !== previous && rootId.value && allowed.value) {
      void treeQuery.refetch()
      if (tab.value === 'logs') void loadLogs()
    }
  },
)
const selected = computed(
  () =>
    treeQuery.data.value?.nodes.find((n) => n.id === selectedId.value) ??
    (selectedQuery.data.value?.id === selectedId.value ? selectedQuery.data.value : undefined),
)
const graphScope = ref(''),
  graph = ref<InstanceType<typeof RunGraph>>()
watch(rootId, () => {
  graphScope.value = ''
})
function navigateGraph(id: string, selectionId: string) {
  graphScope.value = id
  selectedId.value = selectionId
}
const variables = useQuery(
  computed(() => ({
    queryKey: ['run-variables', runtime.session.revision, rootId.value, selectedId.value],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      tree(
        await runtime.request('/api/instances/' + rootId.value + '/tree?includeVariables=true', {
          signal,
        }),
      ),
    enabled: enabled.value && !!rootId.value && tab.value === 'variables',
    retry: false,
    gcTime: 0,
  })),
)
const variableNode = computed(() =>
  variables.data.value?.nodes.find((n) => n.id === selectedId.value),
)
const logScope = ref('self'),
  logSearch = ref(''),
  logLevel = ref(''),
  logWrap = ref(true),
  logFollow = ref(false),
  logTechnical = ref(false),
  logBox = ref<HTMLElement>()
const filteredLogs = computed(() =>
  logs.value.filter(
    (log) =>
      (!logLevel.value || log.level === logLevel.value) &&
      (!logSearch.value ||
        log.content.toLocaleLowerCase().includes(logSearch.value.toLocaleLowerCase())),
  ),
)
function scrollLogs() {
  const element = logBox.value
  if (element && element.scrollTop + element.clientHeight < element.scrollHeight - 40)
    logFollow.value = false
}
const logs = ref<Log[]>([]),
  logError = shallowRef<unknown>(),
  logBusy = ref(false),
  logLimit = ref(200),
  logTruncated = ref(false)
let logController: AbortController | undefined,
  logTimer: ReturnType<typeof setTimeout> | undefined,
  logGeneration = 0
function stopLogs() {
  logGeneration++
  logController?.abort()
  clearTimeout(logTimer)
  logBusy.value = false
}
async function loadLogs(expand = false) {
  if (!sensitive.value || !selectedId.value || !visible.value || logBusy.value) return
  clearTimeout(logTimer)
  const generation = logGeneration,
    target = selectedId.value
  const controller = new AbortController()
  logController = controller
  logBusy.value = true
  logError.value = undefined
  if (expand) logLimit.value = Math.min(5000, logLimit.value + 200)
  try {
    const snapshot = logSnapshot(
      await runtime.request(
        '/api/instances/' +
          target +
          '/logs/snapshot?scope=' +
          logScope.value +
          '&limit=' +
          logLimit.value,
        { signal: controller.signal },
      ),
    )
    if (controller.signal.aborted || generation !== logGeneration) return
    logs.value = snapshot.rows
    logTruncated.value = snapshot.truncated
    if (logFollow.value) {
      await nextTick()
      logBox.value?.scrollTo({ top: logBox.value.scrollHeight })
    }
  } catch (e) {
    if (!controller.signal.aborted) logError.value = e
  } finally {
    if (generation === logGeneration) {
      logBusy.value = false
      if (auto.value && tab.value === 'logs' && sensitive.value && visible.value && !logError.value)
        logTimer = setTimeout(
          () => void loadLogs(),
          terminal.has(
            (logScope.value === 'run' ? rootQuery.data.value?.status : selected.value?.status) ??
              '',
          )
            ? 10000
            : 2000,
        )
    }
  }
}
watch([selectedId, logScope, () => runtime.session.revision], () => {
  stopLogs()
  logs.value = []
  logLimit.value = 200
  logError.value = undefined
  logTruncated.value = false
  if (tab.value === 'logs') void loadLogs()
})
watch([tab, visible, auto, sensitive], () => {
  stopLogs()
  if (!sensitive.value) {
    logs.value = []
    logLimit.value = 200
  }
  if (tab.value === 'logs' && visible.value) void loadLogs()
})
onBeforeUnmount(stopLogs)
const requestId = computed(() =>
  typeof route.query.requestId === 'string' && /^[a-zA-Z0-9-]{1,128}$/.test(route.query.requestId)
    ? route.query.requestId
    : (commandMemory(runtime).get(rootId.value) ?? ''),
)
const submitting = ref(false),
  postError = shallowRef<unknown>(),
  uncertain = ref(false)
const commandQuery = useQuery(
  computed(() => ({
    queryKey: ['run-command', runtime.session.revision, requestId.value],
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const row = command(
        await runtime.request('/api/runtime-commands/' + requestId.value, { signal }),
      )
      if (row.instanceId !== rootId.value) throw new ApiError('protocol')
      return row
    },
    enabled: enabled.value && !!requestId.value && !!rootId.value && !submitting.value,
    retry: false,
    refetchInterval: (q: { state: { data?: { status: string }; fetchFailureCount: number } }) =>
      !q.state.data || !commandTerminal.has(q.state.data.status)
        ? Math.min(30000, 2000 * 2 ** q.state.fetchFailureCount)
        : false,
  })),
)
const waiting = computed(
  () =>
    submitting.value ||
    (!!requestId.value &&
      (!commandQuery.data.value || !commandTerminal.has(commandQuery.data.value.status))),
)
watch(
  () => commandQuery.data.value?.status,
  (status) => {
    if (status) {
      uncertain.value = false
      if (commandTerminal.has(status)) void refresh()
    }
  },
)
let postController: AbortController | undefined
onBeforeUnmount(() => postController?.abort())
watch([allowed, () => runtime.session.revision], () => {
  postController?.abort()
  postError.value = undefined
  dialog.destroyAll()
})
async function submit(action: Action) {
  if (!control.value || waiting.value || !actions(rootQuery.data.value).includes(action)) return
  const root = rootId.value,
    revision = runtime.session.revision,
    key = randomUuid()
  submitting.value = true
  postError.value = undefined
  uncertain.value = false
  postController = new AbortController()
  const signal = postController.signal
  try {
    commandMemory(runtime).set(root, key)
    await router.replace({ query: { ...route.query, requestId: key } })
    const result = command(
      await runtime.request('/api/instances/' + root + '/' + action, {
        method: 'POST',
        idempotencyKey: key,
        signal,
      }),
    )
    if (signal.aborted || revision !== runtime.session.revision) return
    if (result.requestId !== key || result.instanceId !== root) throw new ApiError('protocol')
  } catch (e) {
    if (!signal.aborted && revision === runtime.session.revision) {
      postError.value = e
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) {
        commandMemory(runtime).delete(root)
        await router.replace({ query: { ...route.query, requestId: undefined } })
      } else uncertain.value = true
    }
  } finally {
    if (!signal.aborted && revision === runtime.session.revision) {
      submitting.value = false
      void refresh()
    }
  }
}
function confirm(action: Action) {
  if (!control.value || waiting.value) return
  dialog.warning({
    title: t('runs.confirm', { action: t('runs.' + action) }),
    content: `${t(action === 'retry' ? 'runs.retryHint' : 'runs.controlHint')} ${label(rootQuery.data.value?.targetType ?? '')} #${rootQuery.data.value?.targetId} · ${t('runs.rootInstanceId')} #${rootId.value}`,
    positiveText: t('runs.confirm', { action: t('runs.' + action) }),
    negativeText: t('common.cancel'),
    onPositiveClick: () => submit(action),
  })
}
async function refresh() {
  if (!allowed.value) return
  await Promise.allSettled([
    ...(graph.value ? [graph.value.refresh()] : []),
    selectedQuery.refetch(),
    ...(rootId.value ? [rootQuery.refetch(), treeQuery.refetch()] : []),
  ])
  if (tab.value === 'variables') void variables.refetch()
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
function label(value: string) {
  if (value === 'SUCCESS') return t('tasks.status_SUCCESS')
  return te('runEnums.' + value) ? t('runEnums.' + value) : value || '—'
}
const summaryFields = [
  'targetId',
  'triggerId',
  'parentInstanceId',
  'rootInstanceId',
  'taskDefinitionId',
  'jobDefinitionId',
  'definitionNodeId',
  'progress',
  'retryCount',
  'createTime',
  'startTime',
  'endTime',
  'timeoutAt',
  'waitReason',
  'permitWaitDeadline',
  'errorCode',
  'message',
] as const
const decisionFields = [
  'definitionNodeId',
  'evaluationNo',
  'status',
  'selectedEdgeIds',
  'errorCode',
  'contextDigest',
  'evaluatorVersion',
  'createTime',
] as const
const decisions = computed(
  () => treeQuery.data.value?.decisions.filter((d) => d.jobInstanceId === selectedId.value) ?? [],
)
const back = computed(() =>
  typeof route.query.back === 'string' &&
  /^(?:\/runs|\/notifications|\/workbench|\/diagnostics|\/events(?:\/approvals)?)(?:\?|$)/.test(
    route.query.back,
  )
    ? route.query.back
    : '/runs',
)
</script>
<template>
  <div class="runs-page">
    <RouterLink :to="back">← {{ t('runs.back') }}</RouterLink>
    <header class="run-panel run-detail-header">
      <div class="run-detail-heading">
        <h1>{{ t('runs.detail') }} #{{ id }}</h1>
        <template v-if="allowed && rootQuery.data.value">
          <div class="run-detail-identity">
            <strong
              >{{ rootQuery.data.value.targetName || t('experience.nameUnavailable') }} ·
              {{ label(rootQuery.data.value.targetType) }} #{{
                rootQuery.data.value.targetId
              }}</strong
            >
            <span class="run-status" :data-status="rootQuery.data.value.status">{{
              t('tasks.status_' + rootQuery.data.value.status)
            }}</span>
          </div>
          <p class="run-note"><IdentityText :value="rootId" :label="t('runs.rootInstanceId')" /></p>
          <RunProgress
            class="run-detail-progress"
            :progress="rootQuery.data.value.progress"
            :target-type="rootQuery.data.value.targetType"
            :status="rootQuery.data.value.status"
          />
          <p class="experience-note" role="status">
            {{ t('experience.updated') }}:
            {{
              rootQuery.dataUpdatedAt.value
                ? new Date(rootQuery.dataUpdatedAt.value).toLocaleTimeString()
                : '—'
            }}<span v-if="rootQuery.isError.value"> · {{ t('experience.stale') }}</span>
          </p>
          <NButton
            v-if="
              can(user, 'SENSITIVE_READ') &&
              rootQuery.data.value.targetType === 'JOB' &&
              rootQuery.data.value.jobDefinitionId
            "
            text
            type="primary"
            @click="
              router.push(
                '/jobs/' +
                  rootQuery.data.value.targetId +
                  '/versions/' +
                  rootQuery.data.value.jobDefinitionId,
              )
            "
            >{{ t('jobs.snapshot') }}</NButton
          >
        </template>
      </div>
      <div class="run-detail-actions">
        <div class="run-actions">
          <NCheckbox v-if="allowed && rootQuery.data.value" v-model:checked="auto">{{
            t('runs.auto')
          }}</NCheckbox>
          <NButton
            :loading="rootQuery.isFetching.value || selectedQuery.isFetching.value"
            @click="refresh"
            >{{ t('common.refresh') }}</NButton
          >
        </div>
        <div
          v-if="control && actions(rootQuery.data.value).length"
          class="run-actions run-root-controls"
          role="group"
          :aria-label="t('runs.rootControls')"
        >
          <span class="run-note">{{ t('runs.rootControls') }}</span>
          <NButton
            v-for="action in actions(rootQuery.data.value)"
            :key="action"
            :disabled="waiting"
            :type="action === 'stop' ? 'error' : 'default'"
            @click="confirm(action)"
            >{{ t('runs.' + action) }}</NButton
          >
        </div>
      </div>
      <p v-if="control && actions(rootQuery.data.value).length" class="run-note run-control-hint">
        {{ t('runs.controlHint') }}
      </p>
    </header>
    <RequestError :error="selectedQuery.error.value || rootQuery.error.value" />
    <p v-if="selectedQuery.isPending.value" role="status">{{ t('common.loading') }}</p>
    <template v-if="allowed && rootQuery.data.value">
      <RequestError :error="postError" />
      <section v-if="requestId" class="run-panel run-command" aria-live="polite">
        <div class="run-toolbar">
          <h2>{{ t('runs.latest') }}</h2>
          <NButton
            :disabled="submitting"
            :loading="commandQuery.isFetching.value"
            @click="commandQuery.refetch()"
            >{{ t('common.refresh') }}</NButton
          >
        </div>
        <p>{{ t('runs.requestId') }}: {{ requestId }}</p>
        <RequestError :error="commandQuery.error.value" />
        <p v-if="uncertain || !commandQuery.data.value" class="run-note">{{ t('runs.unknown') }}</p>
        <template v-if="commandQuery.data.value"
          ><strong
            >{{ label(commandQuery.data.value.commandType) }} ·
            {{ label(commandQuery.data.value.status) }}</strong
          >
          <p v-if="waiting">{{ t('runs.pending') }}</p>
          <p>{{ commandQuery.data.value.resultMessage }}</p>
          <dl class="run-meta">
            <dt>{{ t('runs.requestedBy') }}</dt>
            <dd>{{ commandQuery.data.value.requestedBy || '—' }}</dd>
            <template v-for="key in ['createTime', 'processedAt', 'expiresAt'] as const" :key="key"
              ><dt>{{ t('runs.' + key) }}</dt>
              <dd>{{ time(commandQuery.data.value[key]) }}</dd></template
            >
            <dt>{{ t('runs.replayed') }}</dt>
            <dd>{{ t(commandQuery.data.value.replayed ? 'runs.on' : 'runs.off') }}</dd>
          </dl></template
        >
      </section>
      <RequestError :error="treeQuery.error.value" />
      <RunGraph
        v-if="rootQuery.data.value.targetType === 'JOB'"
        ref="graph"
        :instance-id="graphScope || rootId"
        :root-id="rootId"
        :selected-id="selectedId"
        :active="enabled"
        :auto="auto"
        @select="selectedId = $event"
        @navigate="navigateGraph"
      />
      <p v-if="treeQuery.data.value?.truncated" class="run-note" role="status">
        {{ t('runs.treeLimit', { count: treeQuery.data.value.maxNodes }) }}
      </p>
      <div class="run-layout">
        <aside class="run-panel run-tree-panel" :class="{ 'is-open': treeOpen }">
          <div class="run-toolbar run-section-heading">
            <h2>{{ t('runs.tree') }}</h2>
            <NButton class="run-tree-toggle" :aria-expanded="treeOpen" @click="treeOpen = !treeOpen"
              >{{ t('runs.tree') }} {{ nodes.length }}</NButton
            >
          </div>
          <div class="run-tree">
            <div
              v-for="item in visibleNodes"
              :key="item.node.id"
              class="run-tree-row"
              :style="{ paddingLeft: Math.min(item.depth, 8) * 10 + 'px' }"
            >
              <button
                v-if="parents.has(item.node.id)"
                class="run-disclosure"
                type="button"
                :aria-label="t('runs.tree') + ' ' + item.node.id"
                :aria-expanded="!collapsed.has(item.node.id)"
                @click="toggleNode(item.node.id)"
              >
                {{ collapsed.has(item.node.id) ? '▸' : '▾' }}
              </button>
              <button
                type="button"
                class="run-node"
                :aria-current="selectedId === item.node.id"
                @click="selectedId = item.node.id"
              >
                {{ item.depth ? '↳ ' : ''
                }}{{ item.node.targetName || t('experience.nameUnavailable') }} ·
                {{ label(item.node.targetType) }} #{{ item.node.targetId
                }}<small>#{{ item.node.id }} · {{ t('tasks.status_' + item.node.status) }}</small>
                <small
                  v-if="
                    item.node.progress != null &&
                    (!terminal.has(item.node.status) ||
                      (item.node.targetType === 'JOB' && item.node.status === 'SUCCESS')) &&
                    (item.node.targetType === 'JOB' || item.node.progress > 0)
                  "
                  >{{
                    t(
                      item.node.targetType === 'JOB'
                        ? 'experience.jobProgress'
                        : 'experience.taskProgress',
                    )
                  }}
                  · {{ item.node.progress }}%</small
                >
              </button>
            </div>
          </div>
        </aside>
        <section class="run-panel">
          <div class="run-toolbar run-section-heading">
            <h2>
              {{ selected?.targetName || t('experience.nameUnavailable') }} ·
              {{ label(selected?.targetType ?? '') }} #{{ selected?.targetId }}
              <IdentityText :value="selectedId" :label="t('runs.instanceId')" />
            </h2>
            <NButton
              v-if="tab === 'variables'"
              :loading="variables.isFetching.value"
              @click="variables.refetch()"
              >{{ t('common.refresh') }}</NButton
            >
          </div>
          <RunProgress
            v-if="selected"
            class="run-detail-progress"
            :progress="selected.progress"
            :target-type="selected.targetType"
            :status="selected.status"
          />
          <ApprovalEntry
            v-if="selected?.targetType === 'TASK' && allowed && can(user, 'APPROVAL_DECIDE')"
            :key="runtime.session.revision + ':' + selectedId"
            :instance-id="selectedId"
            :waiting="selected?.status === 'WAITING_EVENT'"
            @resolved="refresh"
          />
          <LineTabs
            id="detail-tabs"
            panel-id="run-detail-panel"
            :label="t('runs.detail')"
            :value="tab"
            :tabs="
              ['summary', 'logs', 'variables', 'decisions'].map((value) => ({
                value,
                label: t('runs.' + value),
              }))
            "
            @change="tab = $event"
          />
          <div id="run-detail-panel" role="tabpanel" :aria-labelledby="'detail-tabs-' + tab">
            <template v-if="tab === 'summary'"
              ><dl v-if="selected" class="run-meta">
                <template v-for="key in summaryFields" :key="key"
                  ><dt>{{ t('runs.' + key) }}</dt>
                  <dd>
                    {{
                      key.endsWith('Time') || key === 'timeoutAt' || key === 'permitWaitDeadline'
                        ? time(selected[key])
                        : selected[key] === '' || selected[key] == null
                          ? '—'
                          : selected[key]
                    }}
                  </dd></template
                >
              </dl>
              <EmptyState v-else compact :message="t('runs.empty')"
            /></template>
            <template v-if="tab === 'logs'"
              ><p v-if="!sensitive">{{ t('runs.noPermission') }}</p>
              <template v-else
                ><RequestError :error="logError" />
                <div class="experience-filters">
                  <CompactSelect
                    id="log-scope"
                    v-model:value="logScope"
                    :label="t('runs.logs')"
                    :options="[
                      { value: 'self', label: t('experience.selfLogs') },
                      { value: 'run', label: t('experience.runLogs') },
                    ]"
                  />
                  <CompactSelect
                    id="log-level"
                    v-model:value="logLevel"
                    :label="t('experience.allLevels')"
                    :options="[
                      { value: '', label: t('experience.allLevels') },
                      ...['INFO', 'WARN', 'ERROR', 'DEBUG'].map((value) => ({
                        value,
                        label: value,
                      })),
                    ]"
                  />
                  <NInput
                    v-model:value="logSearch"
                    :placeholder="t('experience.searchLogs')"
                    :input-props="{ 'aria-label': t('experience.searchLogs') }"
                  />
                  <NCheckbox v-model:checked="logWrap">{{ t('experience.wrapLogs') }}</NCheckbox>
                  <NCheckbox v-model:checked="logFollow">{{
                    t('experience.followLogs')
                  }}</NCheckbox>
                  <NCheckbox v-model:checked="logTechnical">{{
                    t('experience.technical')
                  }}</NCheckbox>
                </div>
                <p class="experience-note">{{ t('experience.loadedOnly') }}</p>
                <div
                  ref="logBox"
                  class="run-code"
                  :class="{ 'experience-log-wrap': logWrap }"
                  tabindex="0"
                  :aria-label="t('runs.logs')"
                  @scroll="scrollLogs"
                >
                  <div v-for="log in filteredLogs" :key="log.id" class="run-log-line">
                    <IdentityText
                      v-if="logTechnical"
                      :value="log.id"
                      :label="t('unified.logId')"
                    /><span
                      ><TimeEvidence :value="log.logTime" milliseconds /> [{{ log.level }}]
                      <small v-if="logScope === 'run'"
                        >[{{
                          treeQuery.data.value?.nodes.find((n) => n.id === log.instanceId)
                            ?.targetName || t('experience.nameUnavailable')
                        }}
                        #{{ log.instanceId }}] </small
                      >{{ log.content }}</span
                    >
                  </div>
                  <span v-if="!filteredLogs.length && !logBusy && !logError">{{
                    t('tasks.noLogs')
                  }}</span>
                </div>
                <p v-if="logBusy" role="status">{{ t('common.loading') }}</p>
                <div class="run-toolbar">
                  <span class="run-note">{{ t('runs.loaded', { count: logs.length }) }}</span
                  ><NButton :loading="logBusy" @click="loadLogs(true)">{{
                    t(logLimit < 5000 ? 'runs.loadMore' : 'runs.refreshLogs')
                  }}</NButton>
                </div>
                <p v-if="logTruncated" class="run-note">
                  {{ t('runs.logLimit', { count: logs.length }) }}
                </p></template
              ></template
            >
            <template v-if="tab === 'variables'"
              ><RequestError :error="variables.error.value" /><template
                v-if="variables.data.value && !variables.isError.value"
                ><p v-if="!variableNode">{{ t('runs.unavailable') }}</p>
                <template v-else
                  ><p v-if="variableNode.variablesRedacted" class="run-note">
                    {{ t('runs.redacted') }}
                  </p>
                  <p v-if="variableNode.variablesTruncated" class="run-note">
                    {{ t('runs.truncated') }}
                  </p>
                  <template v-for="key in ['inputVariables', 'outputVariables'] as const" :key="key"
                    ><h3>{{ t('runs.' + key) }}</h3>
                    <pre class="run-code" tabindex="0">{{ variableNode[key] || '—' }}</pre>
                  </template></template
                ></template
              ></template
            >
            <template v-if="tab === 'decisions'"
              ><p v-if="treeQuery.data.value?.gatewayDecisionsTruncated" class="run-note">
                {{ t('runs.decisionsLimit') }}
              </p>
              <div class="run-table-scroll">
                <table class="run-table">
                  <thead>
                    <tr>
                      <th v-for="key in decisionFields" :key="key">{{ t('runs.' + key) }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="d in decisions" :key="d.id">
                      <td v-for="key in decisionFields" :key="key">
                        {{
                          key === 'createTime'
                            ? time(d[key])
                            : key === 'status'
                              ? label(d[key])
                              : d[key] || '—'
                        }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <EmptyState v-if="!decisions.length" compact :message="t('runs.empty')"
            /></template>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>
