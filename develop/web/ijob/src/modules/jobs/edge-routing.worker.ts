import { intersects, obstacleBox, ObstacleIndex, routeEdge } from './edge-routing'
import type { Obstacle, RouteInput, Route } from './edge-routing'

export type RoutingRequest = { revision: number; nodes: Obstacle[]; edges: RouteInput[] }
export type RoutingReply = { revision: number; routes: [string, Route][]; done: boolean }
let generation = 0
let previousNodes = new Map<string, Obstacle>()
let cache = new Map<string, { key: string; route: Route }>()

self.onmessage = async (event: MessageEvent<RoutingRequest>) => {
  const token = ++generation,
    { revision, nodes, edges } = event.data
  const currentNodes = new Map(nodes.map((n) => [n.id, n]))
  const changed = []
  for (const [id, old] of previousNodes) {
    const current = currentNodes.get(id)
    if (!current || JSON.stringify(old) !== JSON.stringify(current)) changed.push(obstacleBox(old))
  }
  for (const n of nodes)
    if (JSON.stringify(previousNodes.get(n.id)) !== JSON.stringify(n)) changed.push(obstacleBox(n))
  const index = new ObstacleIndex(nodes),
    nextCache = new Map<string, { key: string; route: Route }>()
  let batch: [string, Route][] = [],
    started = performance.now()
  for (const input of edges) {
    const key = JSON.stringify(input),
      old = cache.get(input.id)
    const route =
      old?.key === key && !changed.some((b) => intersects(b, old.route.bounds))
        ? old.route
        : routeEdge(input, index)
    nextCache.set(input.id, { key, route })
    // A previous revision's reply may have been discarded by the UI. Include reused
    // routes as well; the UI skips unchanged paths without invalidating components.
    batch.push([input.id, route])
    if (batch.length >= 128 || performance.now() - started > 12) {
      if (batch.length)
        self.postMessage({ revision, routes: batch, done: false } satisfies RoutingReply)
      batch = []
      // Yield between bounded searches so a new drag snapshot cancels stale work.
      await new Promise((resolve) => setTimeout(resolve, 0))
      if (generation !== token) return
      started = performance.now()
    }
  }
  previousNodes = currentNodes
  cache = nextCache
  self.postMessage({ revision, routes: batch, done: true } satisfies RoutingReply)
}
