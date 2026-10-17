import { computed, onBeforeUnmount, provide, shallowReactive, shallowRef, watch } from 'vue'
import type { InjectionKey, Ref } from 'vue'
import type { GraphEdge, GraphNode } from '@vue-flow/core'
import { ObstacleIndex, portSide, routeEdge } from './edge-routing'
import type { Route, RouteInput } from './edge-routing'
import type { RoutingReply, RoutingRequest } from './edge-routing.worker'

export const routingKey: InjectionKey<{
  routes: Map<string, Route>
  preview: (input: RouteInput) => Route
}> = Symbol('job-edge-routing')

export function useEdgeRouting(nodes: Ref<GraphNode[]>, edges: Ref<GraphEdge[]>) {
  const routes = shallowReactive(new Map<string, Route>())
  const pending = shallowRef(false)
  let index = new ObstacleIndex([]),
    worker: Worker | undefined
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
  function submit() {
    timer = undefined
    if (stopped) return
    const data = snapshot.value
    index = new ObstacleIndex(data.nodes)
    const ids = new Set(data.edges.map((e) => e.id))
    for (const id of routes.keys()) if (!ids.has(id)) routes.delete(id)
    try {
      if (!worker) {
        worker = new Worker(new URL('./edge-routing.worker.ts', import.meta.url), {
          type: 'module',
        })
        worker.onmessage = ({ data: reply }: MessageEvent<RoutingReply>) => {
          if (reply.revision !== revision || stopped) return
          for (const [id, route] of reply.routes) {
            const current = routes.get(id)
            if (current?.path !== route.path || current.kind !== route.kind) routes.set(id, route)
          }
          if (reply.done) pending.value = false
        }
        worker.onerror = () => {
          // Keep the editor usable if the browser cannot start the routing worker.
          worker?.terminate()
          worker = undefined
          stopped = true
          pending.value = false
          routes.clear()
        }
      }
      pending.value = true
      worker.postMessage({ ...data, revision: ++revision } satisfies RoutingRequest)
    } catch {
      worker?.terminate()
      worker = undefined
      stopped = true
      pending.value = false
    }
  }
  // Only geometry and ports are tracked: selection, labels, theme and zoom do not reroute.
  watch(
    snapshot,
    () => {
      if (!timer) timer = setTimeout(submit, 60)
    },
    { immediate: true, flush: 'post' },
  )
  provide(routingKey, { routes, preview: (input) => routeEdge(input, index, 400) })
  onBeforeUnmount(() => {
    stopped = true
    clearTimeout(timer)
    worker?.terminate()
  })
  return { pending }
}
