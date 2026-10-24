// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { computed, onBeforeUnmount, provide, shallowReactive, shallowRef, watch } from 'vue'
import type { ComputedRef, InjectionKey, Ref } from 'vue'
import type { GraphEdge, GraphNode } from '@vue-flow/core'
import { getBezierPath } from '@vue-flow/core'
import { portSide, placeEdgeLabels } from './edge-routing'
import type { Point, Route } from './edge-routing'
import type { RoutingReply, RoutingRequest } from './edge-routing.worker'
import type { EdgeRoutingMode } from '../../shared/preferences/preferences'

export const routingKey: InjectionKey<{
  routes: Map<string, Route>
  mode: Ref<EdgeRoutingMode>
  dragging: Ref<boolean>
  available: Ref<boolean>
  labels: ComputedRef<Map<string, Point>>
  select: (id: string) => void
}> = Symbol('job-edge-routing')

export function useEdgeRouting(
  nodes: Ref<GraphNode[]>,
  edges: Ref<GraphEdge[]>,
  mode: Ref<EdgeRoutingMode>,
  dragging: Ref<boolean>,
  select: (id: string) => void = () => {},
) {
  const routes = shallowReactive(new Map<string, Route>())
  const pending = shallowRef(false)
  const available = shallowRef(true)
  let worker: Worker | undefined
  let timer: ReturnType<typeof setTimeout> | undefined,
    revision = 0,
    stopped = false
  const snapshot = computed(() => ({
    nodes: nodes.value.map((n) => ({
      id: n.id,
      x: n.computedPosition.x,
      y: n.computedPosition.y,
      width: n.dimensions.width || (n.data.kind === 'GATEWAY' ? 110 : 180),
      height: n.dimensions.height || (n.data.kind === 'GATEWAY' ? 110 : 76),
    })),
    edges: edges.value
      .filter((e) => [e.sourceX, e.sourceY, e.targetX, e.targetY].every(Number.isFinite))
      .map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        start: { x: e.sourceX, y: e.sourceY },
        end: { x: e.targetX, y: e.targetY },
        sourceSide: portSide(e.sourceHandle, 'right'),
        targetSide: portSide(e.targetHandle, 'left'),
      })),
  }))
  // Keep collision-driven label placement stable while nodes are being dragged.
  const labelNodes = shallowRef(snapshot.value.nodes)
  function failWorker() {
    ++revision
    worker?.terminate()
    worker = undefined
    available.value = false
    pending.value = false
    routes.clear()
  }
  function submit() {
    timer = undefined
    if (stopped || !available.value || dragging.value || mode.value !== 'avoid') return
    const data = snapshot.value
    const ids = new Set(data.edges.map((e) => e.id))
    for (const id of routes.keys()) if (!ids.has(id)) routes.delete(id)
    try {
      if (!worker) {
        worker = new Worker(new URL('./edge-routing.worker.ts', import.meta.url), {
          type: 'module',
        })
        worker.onmessage = ({ data: reply }: MessageEvent<RoutingReply>) => {
          if (reply.revision !== revision || stopped || !available.value) return
          for (const [id, route] of reply.routes) {
            const current = routes.get(id)
            if (current?.path !== route.path || current.kind !== route.kind) routes.set(id, route)
          }
          if (reply.done) pending.value = false
        }
        worker.onerror = failWorker
      }
      pending.value = true
      worker.postMessage({ ...data, revision } satisfies RoutingRequest)
    } catch {
      failWorker()
    }
  }
  // Only geometry and ports are tracked: selection, labels, theme and zoom do not reroute.
  watch(
    [snapshot, mode, dragging],
    () => {
      // Invalidate immediately, including the debounce window and suspended gestures.
      ++revision
      clearTimeout(timer)
      timer = undefined
      pending.value = false
      if (!dragging.value) labelNodes.value = snapshot.value.nodes
      if (mode.value === 'simple') routes.clear()
      if (stopped || !available.value || dragging.value || mode.value !== 'avoid') return
      pending.value = true
      timer = setTimeout(submit, 60)
    },
    { immediate: true, flush: 'post' },
  )
  const labels = computed(() =>
    placeEdgeLabels(
      labelNodes.value,
      edges.value
        .filter((e) => e.label && Number.isFinite(e.sourceX) && Number.isFinite(e.targetX))
        .map((e) => {
          const saved = routes.get(e.id)
          const current =
            mode.value === 'avoid' &&
            saved &&
            Math.abs(saved.start.x - e.sourceX) < 0.1 &&
            Math.abs(saved.start.y - e.sourceY) < 0.1 &&
            Math.abs(saved.end.x - e.targetX) < 0.1 &&
            Math.abs(saved.end.y - e.targetY) < 0.1
          const [, x, y] = getBezierPath(e)
          return {
            id: e.id,
            text: String(e.label),
            anchor: current ? saved.label : { x, y },
            points: current ? saved.points : undefined,
          }
        }),
    ),
  )
  provide(routingKey, { routes, mode, dragging, available, labels, select })
  onBeforeUnmount(() => {
    stopped = true
    clearTimeout(timer)
    worker?.terminate()
  })
  return { pending }
}
