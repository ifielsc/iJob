<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { JobCanvas } from '../jobs/public/components'
import RequestError from '../../shared/ui/RequestError.vue'
import { runGraph } from './graph'
import { terminal } from './model'
const props = defineProps<{
  instanceId: string
  rootId: string
  selectedId: string
  active: boolean
  auto: boolean
}>()
const maximized = ref(false)
let previousOverflow = ''
let maximizeTrigger: HTMLElement | undefined
function setMaximized(value: boolean) {
  if (value === maximized.value) return
  if (value) {
    maximizeTrigger =
      document.activeElement instanceof HTMLElement ? document.activeElement : undefined
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', escapeMaximized)
  } else {
    document.body.style.overflow = previousOverflow
    window.removeEventListener('keydown', escapeMaximized)
    void nextTick(() => maximizeTrigger?.isConnected && maximizeTrigger.focus())
  }
  maximized.value = value
}
function escapeMaximized(event: KeyboardEvent) {
  if (event.key === 'Escape' && !event.defaultPrevented) setMaximized(false)
}
watch(
  () => props.active,
  (active) => {
    if (!active) setMaximized(false)
  },
)
onBeforeUnmount(() => setMaximized(false))
const emit = defineEmits<{ select: [string]; navigate: [string, string] }>()
const runtime = useRuntime(),
  { t, te } = useI18n(),
  picked = ref(''),
  selectedEdge = ref(''),
  searchRevision = ref(0),
  canvas = ref<InstanceType<typeof JobCanvas>>()
const query = useQuery(
  computed(() => ({
    queryKey: ['run-graph', runtime.session.revision, runtime.preferences.locale, props.instanceId],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      runGraph(await runtime.request('/api/instances/' + props.instanceId + '/graph', { signal })),
    enabled: props.active && !!props.instanceId,
    retry: false,
    refetchInterval: (q: { state: { data?: { status: string }; fetchFailureCount: number } }) =>
      props.auto && (!q.state.data || !terminal.has(q.state.data.status))
        ? Math.min(30000, 2000 * 2 ** q.state.fetchFailureCount)
        : false,
  })),
)
const label = (status: string) =>
  te('tasks.status_' + status)
    ? t('tasks.status_' + status)
    : te('runGraph.state_' + status)
      ? t('runGraph.state_' + status)
      : status
const states = computed(() =>
  Object.fromEntries(
    (query.data.value?.nodes ?? []).map((n) => [
      n.nodeId,
      { status: n.status, label: label(n.status) },
    ]),
  ),
)
const selected = computed(() =>
  selectedEdge.value
    ? undefined
    : picked.value ||
      query.data.value?.nodes.find((n) => n.instanceId === props.selectedId)?.nodeId,
)
const selectedNode = computed(() =>
  query.data.value?.nodes.find((n) => n.nodeId === selected.value),
)
const edge = computed(() => query.data.value?.edges.find((e) => e.id === selectedEdge.value))
const nodeNames = computed(
  () => new Map(query.data.value?.nodes.map((n) => [n.nodeId, n.name]) ?? []),
)
function edgeLabel(e: { fromNodeId: string; toNodeId: string; routeKey?: string; id: string }) {
  return (
    (nodeNames.value.get(e.fromNodeId) ?? e.fromNodeId) +
    ' → ' +
    (nodeNames.value.get(e.toNodeId) ?? e.toNodeId) +
    (e.routeKey ? ' · ' + e.routeKey : '') +
    ' · ' +
    e.id
  )
}
const searchOptions = computed(() => [
  {
    type: 'group' as const,
    key: 'nodes',
    label: t('jobs.node'),
    children: (query.data.value?.nodes ?? []).map((n) => ({
      value: 'n:' + n.nodeId,
      label: n.name + ' · ' + n.nodeId,
    })),
  },
  {
    type: 'group' as const,
    key: 'edges',
    label: t('jobs.connections'),
    children: (query.data.value?.edges ?? []).map((e) => ({
      value: 'e:' + e.id,
      label: edgeLabel(e),
    })),
  },
])
function selectEdge(id: string) {
  picked.value = ''
  selectedEdge.value = id
}
function locateSelected() {
  const keys = edge.value
    ? [edge.value.fromNodeId, edge.value.toNodeId]
    : selected.value
      ? [selected.value]
      : []
  if (keys.length) void nextTick(() => canvas.value?.locate(keys))
}
function findItem(value: string | null) {
  if (!value) return
  searchRevision.value++
  if (value.startsWith('n:')) select(value.slice(2))
  else selectEdge(value.slice(2))
  locateSelected()
}
const counts = computed(() => {
  const values = new Map<string, number>()
  query.data.value?.nodes.forEach((n) => values.set(n.status, (values.get(n.status) ?? 0) + 1))
  return [...values.entries()]
})
watch(
  () => props.instanceId,
  () => {
    picked.value = ''
    selectedEdge.value = ''
    searchRevision.value++
  },
)
watch(
  () => props.selectedId,
  (id) => {
    selectedEdge.value = ''
    picked.value = query.data.value?.nodes.find((n) => n.instanceId === id)?.nodeId ?? ''
  },
)
function select(key: string) {
  selectedEdge.value = ''
  picked.value = key
  const n = query.data.value?.nodes.find((n) => n.nodeId === key)
  if (n?.instanceId) emit('select', n.instanceId)
}
const viewports = new Map<string, { x: number; y: number; zoom: number }>()
const selections = new Map<string, string>()
const restoring = ref('')
watch(query.data, (graph) => {
  if (!restoring.value || graph?.instanceId !== props.instanceId) return
  const id = restoring.value
  restoring.value = ''
  if (id !== props.instanceId && !graph.nodes.some((node) => node.instanceId === id))
    emit('select', props.instanceId)
})
watch(
  () => props.rootId,
  () => {
    viewports.clear()
    selections.clear()
    restoring.value = ''
  },
)
function navigate(id: string) {
  if (id === props.instanceId) return
  const view = canvas.value?.getViewport()
  if (view) viewports.set(props.instanceId, view)
  selections.set(props.instanceId, selectedNode.value?.instanceId || props.instanceId)
  const selectedId = selections.get(id) || id
  restoring.value = selectedId
  emit('navigate', id, selectedId)
}
function enterJob(key = selected.value) {
  const node = query.data.value?.nodes.find((item) => item.nodeId === key)
  if (!query.isError.value && node?.nodeType === 'JOB' && node.instanceId) navigate(node.instanceId)
}
function clear() {
  selectedEdge.value = ''
  picked.value = ''
  emit('select', props.instanceId)
}
defineExpose({ refresh: () => query.refetch() })
</script>
<template>
  <section class="run-panel run-graph-panel" :class="{ 'run-graph-maximized': maximized }">
    <div class="run-toolbar">
      <div>
        <h2>
          {{ t('runGraph.title')
          }}<template v-if="query.data.value"> · {{ query.data.value.name }}</template>
        </h2>
        <p class="run-note">{{ t('runGraph.hint') }}</p>
      </div>
      <div class="run-graph-navigation">
        <NButton
          v-if="selectedNode?.nodeType === 'JOB' && selectedNode.instanceId"
          type="primary"
          :disabled="query.isError.value"
          @click="enterJob()"
          >{{ t('runGraph.enterJob') }}</NButton
        >
        <NButton v-if="instanceId !== rootId" type="primary" @click="navigate(rootId)">{{
          t('runGraph.backRoot')
        }}</NButton>
      </div>
    </div>
    <RequestError :error="query.error.value" />
    <p v-if="query.isError.value" class="run-note">
      {{ t('runGraph.stale') }}
      <NButton text type="primary" @click="query.refetch()">{{ t('common.retry') }}</NButton>
    </p>
    <p v-if="query.isPending.value" role="status">{{ t('common.loading') }}</p>
    <template v-if="query.data.value">
      <div class="run-graph-tools">
        <NButton size="small" :aria-pressed="maximized" @click="setMaximized(!maximized)">{{
          t(maximized ? 'jobs.restoreCanvas' : 'jobs.maximizeCanvas')
        }}</NButton>
        <NSelect
          :key="searchRevision"
          class="run-graph-search"
          filterable
          clearable
          :value="null"
          :node-props="() => ({ role: 'option' })"
          :options="searchOptions"
          :placeholder="t('jobs.searchGraph')"
          :aria-label="t('jobs.searchGraph')"
          @update:value="findItem"
        />
        <NButton size="small" :disabled="!selected && !edge" @click="locateSelected">{{
          t('jobs.locateSelected')
        }}</NButton>
      </div>
      <div class="run-graph-legend">
        <span
          v-for="[state, count] in counts"
          :key="state"
          class="run-graph-badge"
          :data-status="state"
          >{{ label(state) }} {{ count }}</span
        >
        <span class="run-note"
          >{{ t('runGraph.refreshHint') }} · {{ t('runGraph.updated') }}
          {{
            new Date(query.dataUpdatedAt.value).toLocaleTimeString(runtime.preferences.locale, {
              timeZone: runtime.preferences.displayTimezone,
            })
          }}
          {{ runtime.preferences.displayTimezone }}</span
        >
      </div>
      <div class="run-graph-canvas">
        <JobCanvas
          ref="canvas"
          :key="instanceId"
          :nodes="query.data.value.nodes"
          :edges="query.data.value.edges"
          readonly
          runtime-navigation
          :initial-viewport="viewports.get(instanceId)"
          :selected="selected"
          :selected-edge="selectedEdge"
          :runtime-states="states"
          :highlighted-edges="query.data.value.highlightedEdges"
          @activate="enterJob"
          @select="select"
          @edge="selectEdge"
          @clear="clear"
        />
      </div>
      <div v-if="edge" class="run-graph-selection">
        <strong>{{ t('jobs.connections') }}</strong
        ><span>{{ edgeLabel(edge) }}</span>
      </div>
      <div v-if="selectedNode" class="run-graph-selection">
        <strong>{{ selectedNode.name }}</strong
        ><span class="run-graph-badge" :data-status="selectedNode.status">{{
          label(selectedNode.status)
        }}</span>
        <span v-if="selectedNode.instanceId" class="run-note"
          >{{ t('runs.instanceId') }} #{{ selectedNode.instanceId }}</span
        ><span v-else class="run-note">{{
          t(selectedNode.kind === 'GATEWAY' ? 'runGraph.gatewayHint' : 'runGraph.noInstance')
        }}</span>
        <span v-if="selectedNode.nodeType === 'JOB' && selectedNode.instanceId" class="run-note">{{
          t('runGraph.enterHint')
        }}</span>
      </div>
    </template>
  </section>
</template>
<style scoped>
.run-graph-navigation {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.run-graph-maximized {
  position: fixed;
  inset: 0;
  z-index: 90;
  margin: 0;
  border-radius: 0;
  background: var(--panel);
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.run-graph-maximized .run-graph-canvas {
  flex: 1;
  min-height: 280px;
  height: auto;
}
.run-graph-maximized .run-graph-canvas :deep(.job-canvas) {
  min-height: 280px;
}

.run-graph-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.run-graph-search {
  width: 360px;
  max-width: 100%;
  min-width: 0;
}
.run-graph-selection {
  overflow-wrap: anywhere;
}
</style>
