<script setup lang="ts">
import { randomUuid } from '../../shared/runtime/uuid'
import { computed, markRaw, nextTick, ref, watch } from 'vue'
import { VueFlow, Handle, Position, MarkerType, ConnectionMode, useVueFlow } from '@vue-flow/core'
import type { Connection, NodeDragEvent } from '@vue-flow/core'
import dagre from '@dagrejs/dagre'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { GitBranch, FileCode2, Layers3 } from '@lucide/vue'
import type { JobNode, JobEdge } from './model'
import { connectionProblem, isTextTarget } from './graph-tools'
import SmartJobEdge from './JobEdge.vue'
import JobConnection from './JobConnection.vue'
import { useEdgeRouting } from './use-edge-routing'
import '@vue-flow/core/dist/style.css'
import './canvas.css'
const props = defineProps<{
  nodes: JobNode[]
  edges: JobEdge[]
  readonly: boolean
  selected?: string
  selectedEdge?: string
  runtimeStates?: Record<string, { status: string; label: string }>
  highlightedEdges?: string[]
  runtimeNavigation?: boolean
  initialViewport?: { x: number; y: number; zoom: number }
}>()
const emit = defineEmits<{
  select: [string]
  activate: [string]
  edge: [string]
  clear: []
  connect: [string, string, string, string]
  move: [string, { x: number; y: number }]
  ports: [string, string]
  remove: [string[]]
  invalid: [string]
}>()
const { t } = useI18n(),
  flowId = 'job-canvas-' + randomUuid()
const {
  fitView,
  zoomIn,
  zoomOut,
  viewport,
  dimensions,
  setCenter,
  setViewport,
  screenToFlowCoordinate,
  getSelectedNodes,
  getNodes,
  getEdges,
  removeSelectedElements,
  connectionStartHandle,
} = useVueFlow(flowId)
const { pending: routingPending } = useEdgeRouting(getNodes, getEdges)
const edgeTypes = { smart: markRaw(SmartJobEdge) }
const ports = [Position.Left, Position.Top, Position.Right, Position.Bottom]
const host = ref<HTMLElement>(),
  mini = ref(false)
const adjacent = computed(() => {
  const ids = new Set([props.selected])
  for (const e of props.edges)
    if (e.fromNodeId === props.selected || e.toNodeId === props.selected) {
      ids.add(e.fromNodeId)
      ids.add(e.toNodeId)
    }
  return ids
})
const nodes = computed(() =>
  props.nodes.map((n) => ({
    id: n.nodeId,
    type: 'job',
    position: { ...n.position },
    data: n,
    selected: props.selected === n.nodeId,
    ariaLabel: n.name,
    class:
      !props.runtimeStates && props.selected && !adjacent.value.has(n.nodeId)
        ? 'job-unrelated'
        : '',
  })),
)
const edges = computed(() =>
  props.edges.map((e) => ({
    id: e.id,
    source: e.fromNodeId,
    target: e.toNodeId,
    sourceHandle: e.sourceHandle ?? 'right',
    targetHandle: e.targetHandle ?? 'left',
    type: 'smart',
    label:
      e.routeKey && (viewport.value.zoom >= 0.45 || props.selectedEdge === e.id)
        ? `${e.priority} · ${e.defaultRoute ? t('jobs.defaultRoute') : e.routeKey}`
        : '',
    markerEnd: MarkerType.ArrowClosed,
    selectable: true,
    selected: props.selectedEdge === e.id || props.highlightedEdges?.includes(e.id),
    interactionWidth: 20,
    class:
      !props.runtimeStates &&
      props.selected &&
      e.fromNodeId !== props.selected &&
      e.toNodeId !== props.selected
        ? 'job-unrelated'
        : '',
  })),
)
let viewportBusy = false
let initiallyFitted = false
function initializeView() {
  if (initiallyFitted || !props.nodes.length) return
  initiallyFitted = true
  if (props.initialViewport) void setViewport(props.initialViewport)
  else void fit(0)
}
function ensureVisible() {
  if (viewportBusy) return
  const n = props.nodes.find((n) => n.nodeId === props.selected)
  if (!n || !dimensions.value.width) return
  const x = n.position.x + 90,
    y = n.position.y + 38,
    view = viewport.value
  const px = x * view.zoom + view.x,
    py = y * view.zoom + view.y
  if (px < 70 || px > dimensions.value.width - 70 || py < 55 || py > dimensions.value.height - 85)
    void setCenter(x, y, { zoom: view.zoom, duration: 120 })
}
watch(
  [() => dimensions.value.width, () => dimensions.value.height],
  () => void nextTick(ensureVisible),
)
async function fit(duration = 180) {
  viewportBusy = true
  try {
    await nextTick()
    await fitView({ padding: 0.18, maxZoom: 1.15, duration })
  } finally {
    viewportBusy = false
  }
}
function clearSelection() {
  removeSelectedElements()
  emit('clear')
}
async function overview() {
  clearSelection()
  await fit()
}
async function locate(keys: string[]) {
  viewportBusy = true
  try {
    await nextTick()
    await new Promise<void>((resolve) =>
      requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
    )
    await fitView({ nodes: keys, padding: 0.55, minZoom: 0.45, maxZoom: 1, duration: 180 })
  } finally {
    viewportBusy = false
  }
}
function centerPosition() {
  const rect = host.value?.getBoundingClientRect()
  return rect
    ? screenToFlowCoordinate({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 })
    : { x: 40, y: 80 }
}
async function autoLayout(direction: 'LR' | 'TB' = 'LR') {
  const graph = new dagre.graphlib.Graph()
  graph.setGraph({ rankdir: direction, ranksep: 100, nodesep: 60, marginx: 30, marginy: 30 })
  graph.setDefaultEdgeLabel(() => ({}))
  props.nodes.forEach((n) =>
    graph.setNode(n.nodeId, {
      width: n.kind === 'GATEWAY' ? 110 : 180,
      height: n.kind === 'GATEWAY' ? 110 : 76,
    }),
  )
  props.edges.forEach((e) => {
    if (graph.hasNode(e.fromNodeId) && graph.hasNode(e.toNodeId))
      graph.setEdge(e.fromNodeId, e.toNodeId)
  })
  dagre.layout(graph)
  props.nodes.forEach((n) => {
    const p = graph.node(n.nodeId)
    emit('move', n.nodeId, { x: p.x - p.width / 2, y: p.y - p.height / 2 })
  })
  emit('ports', direction === 'TB' ? 'bottom' : 'right', direction === 'TB' ? 'top' : 'left')
  await fit()
}
const edgeIds = computed(() => new Set(props.edges.map((e) => e.id)))
function valid(c: Connection) {
  // Vue Flow also validates hydrated edges; only new connections need duplicate checks.
  if ('id' in c && edgeIds.value.has(c.id as string)) return true
  return (
    !props.readonly &&
    ports.some((port) => port === c.sourceHandle) &&
    ports.some((port) => port === c.targetHandle) &&
    !connectionProblem(props.edges, c.source, c.target)
  )
}
let connectionMade = false
function completeConnection(c: Connection) {
  connectionMade = true
  emit('connect', c.source, c.target, c.sourceHandle ?? 'right', c.targetHandle ?? 'left')
}
function endConnection(event: MouseEvent | TouchEvent | undefined) {
  if (connectionMade || props.readonly || !(event?.target instanceof Element)) return
  const target = event.target.closest('.vue-flow__handle'),
    start = connectionStartHandle.value
  if (!target || !start) return
  const from = start.nodeId,
    to = target.getAttribute('data-nodeid') ?? ''
  emit('invalid', connectionProblem(props.edges, from, to) ?? 'edgeInvalid')
}
function dragStop(event: NodeDragEvent) {
  for (const n of event.nodes.length ? event.nodes : [event.node]) emit('move', n.id, n.position)
}
function keydown(event: KeyboardEvent) {
  if (event.key === 'Delete' && !event.repeat && !props.readonly && !isTextTarget(event.target)) {
    event.preventDefault()
    emit(
      'remove',
      getSelectedNodes.value.map((n) => n.id),
    )
  }
}
const bounds = computed(() => {
  const xs = props.nodes.map((n) => n.position.x),
    ys = props.nodes.map((n) => n.position.y)
  const x = Math.min(0, ...xs) - 30,
    y = Math.min(0, ...ys) - 30
  return {
    x,
    y,
    w: Math.max(300, ...xs.map((v) => v - x + 210)),
    h: Math.max(200, ...ys.map((v) => v - y + 140)),
  }
})
function miniMove(event: MouseEvent) {
  const svg = event.currentTarget as SVGSVGElement,
    matrix = svg.getScreenCTM()
  if (!matrix) return
  const p = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse())
  void setCenter(p.x, p.y, { zoom: viewport.value.zoom, duration: 120 })
}
defineExpose({
  fit,
  autoLayout,
  locate,
  centerPosition,
  getViewport: () => ({ ...viewport.value }),
})
</script>
<template>
  <div
    ref="host"
    class="job-canvas"
    :class="{ 'job-low-zoom': viewport.zoom < 0.45 }"
    :aria-label="t('jobs.graph')"
    :data-routing-pending="routingPending"
    tabindex="0"
    @keydown="keydown"
  >
    <VueFlow
      v-if="nodes.length"
      :id="flowId"
      :nodes="nodes"
      :edges="edges"
      :edge-types="edgeTypes"
      :nodes-draggable="!readonly"
      :nodes-connectable="!readonly"
      :edges-updatable="false"
      :delete-key-code="null"
      :min-zoom="0.015"
      :max-zoom="2"
      :connect-on-click="!readonly"
      :connection-mode="ConnectionMode.Loose"
      :is-valid-connection="valid"
      :auto-pan-on-connect="true"
      :auto-pan-on-node-drag="true"
      :multi-selection-key-code="'Shift'"
      :snap-to-grid="true"
      :snap-grid="[8, 8]"
      :zoom-on-double-click="!runtimeNavigation"
      @node-double-click="runtimeNavigation && emit('activate', $event.node.id)"
      @node-click="!$event.event.shiftKey && emit('select', $event.node.id)"
      @nodes-initialized="initializeView"
      @edge-click="emit('edge', $event.edge.id)"
      @pane-click="clearSelection"
      @connect="completeConnection"
      @connect-start="connectionMade = false"
      @connect-end="endConnection"
      @node-drag-stop="dragStop"
      @selection-drag-stop="dragStop"
    >
      <template #connection-line="connectionProps"
        ><JobConnection v-bind="connectionProps"
      /></template>
      <template #node-job="{ data, selected: active }">
        <div
          class="job-node"
          :data-runtime-status="runtimeStates?.[data.nodeId]?.status"
          :class="{ 'job-node-gateway': data.kind === 'GATEWAY', 'is-selected': active }"
        >
          <!-- All handles start outgoing connections; loose mode also accepts them as targets. -->
          <Handle
            v-for="port in ports"
            :id="port"
            :key="port"
            type="source"
            :position="port"
            :title="t('jobs.bidirectionalPort')"
          />
          <component
            :is="
              data.kind === 'GATEWAY' ? GitBranch : data.nodeType === 'JOB' ? Layers3 : FileCode2
            "
            :size="19"
          />
          <div>
            <strong :title="data.name">{{ data.name }}</strong
            ><small v-if="data.kind !== 'GATEWAY'"
              >{{ t('runEnums.' + data.nodeType) }} · #{{ data.refId || '—' }}</small
            >
            <span
              v-if="runtimeStates?.[data.nodeId]"
              class="run-graph-badge"
              :data-status="runtimeStates[data.nodeId]?.status"
              >{{ runtimeStates[data.nodeId]?.label }}</span
            >
          </div>
        </div>
      </template>
    </VueFlow>
    <div v-if="!nodes.length" class="job-canvas-empty">{{ t('jobs.noNodes') }}</div>
    <div class="job-canvas-controls">
      <NButton size="small" :aria-label="t('jobs.graph') + ' −'" @click="zoomOut()">−</NButton>
      <span class="job-zoom">{{ Math.round(viewport.zoom * 100) }}%</span>
      <NButton size="small" :aria-label="t('jobs.graph') + ' +'" @click="zoomIn()">＋</NButton>
      <NButton size="small" @click="overview">{{ t('jobs.fit') }}</NButton>
      <NButton size="small" :aria-pressed="mini" @click="mini = !mini">{{
        t('jobs.minimap')
      }}</NButton>
    </div>
    <svg
      v-if="mini && nodes.length"
      class="job-minimap"
      :viewBox="`${bounds.x} ${bounds.y} ${bounds.w} ${bounds.h}`"
      :aria-label="t('jobs.minimap')"
      role="img"
      @click="miniMove"
    >
      <rect
        v-for="n in props.nodes"
        :key="n.nodeId"
        :x="n.position.x"
        :y="n.position.y"
        width="180"
        height="76"
        :class="{ active: n.nodeId === selected }"
      />
      <rect
        class="job-minimap-window"
        :x="-viewport.x / viewport.zoom"
        :y="-viewport.y / viewport.zoom"
        :width="dimensions.width / viewport.zoom"
        :height="dimensions.height / viewport.zoom"
      />
    </svg>
    <span class="job-canvas-count">{{
      t('jobs.count', { nodes: nodes.length, edges: edges.length })
    }}</span>
  </div>
</template>
