import dagre from '@dagrejs/dagre'
import { exactId, record } from '../../shared/api/values'
import type { JobNode, JobEdge } from '../jobs/public/contracts'
import { ApiError } from '../../shared/api/errors'

export interface GraphNode extends JobNode {
  instanceId: string
  status: string
  progress: number | null
  retryCount: number | null
}
export interface RunGraph {
  instanceId: string
  rootInstanceId: string
  name: string
  definitionId: string
  status: string
  nodes: GraphNode[]
  edges: JobEdge[]
  highlightedEdges: string[]
}
export function runGraph(value: unknown): RunGraph {
  const r = record(value)
  if (
    !Array.isArray(r.nodes) ||
    !Array.isArray(r.edges) ||
    typeof r.name !== 'string' ||
    typeof r.status !== 'string'
  )
    throw new ApiError('protocol')
  const layout = r.layout == null ? {} : record(r.layout),
    positions = layout.positions == null ? {} : record(layout.positions)
  const keys = new Map<string, string>()
  let missing = false
  const nodes = r.nodes.map((value): GraphNode => {
    const n = record(value),
      id = exactId(n.id)
    if (
      typeof n.nodeKey !== 'string' ||
      typeof n.name !== 'string' ||
      typeof n.status !== 'string' ||
      !['GATEWAY', 'EXECUTION'].includes(String(n.kind))
    )
      throw new ApiError('protocol')
    keys.set(id, n.nodeKey)
    const pos = positions[n.nodeKey] == null ? {} : record(positions[n.nodeKey])
    const valid =
      typeof pos.x === 'number' &&
      Number.isFinite(pos.x) &&
      typeof pos.y === 'number' &&
      Number.isFinite(pos.y)
    missing ||= !valid
    return {
      nodeId: n.nodeKey,
      name: n.name,
      kind: n.kind as GraphNode['kind'],
      nodeType: n.nodeType as GraphNode['nodeType'],
      refId: n.refId == null ? '' : exactId(n.refId),
      instanceId: n.instanceId == null ? '' : exactId(n.instanceId),
      status: n.status,
      progress: typeof n.progress === 'number' ? n.progress : null,
      retryCount: typeof n.retryCount === 'number' ? n.retryCount : null,
      position: valid ? { x: Number(pos.x), y: Number(pos.y) } : { x: 0, y: 0 },
    }
  })
  if (new Set(nodes.map((n) => n.nodeId)).size !== nodes.length) throw new ApiError('protocol')
  const connections = Array.isArray(layout.connections) ? layout.connections.map(record) : []
  const highlightedEdges: string[] = []
  const edges = r.edges.map((value): JobEdge => {
    const e = record(value),
      id = exactId(e.id),
      from = keys.get(exactId(e.fromNodeId)),
      to = keys.get(exactId(e.toNodeId))
    if (!from || !to) throw new ApiError('protocol')
    const ports = connections.find((c) => c.fromNodeId === from && c.toNodeId === to)
    if (e.selected === true) highlightedEdges.push(id)
    return {
      id,
      fromNodeId: from,
      toNodeId: to,
      conditionText: '',
      routeKey: typeof e.routeKey === 'string' ? e.routeKey : '',
      priority: Number(e.priority ?? 0),
      defaultRoute: e.defaultRoute === true,
      sourceHandle: String(ports?.sourceHandle ?? 'right'),
      targetHandle: String(ports?.targetHandle ?? 'left'),
    }
  })
  if (missing) {
    const graph = new dagre.graphlib.Graph()
    graph.setGraph({ rankdir: 'LR', ranksep: 90, nodesep: 55, marginx: 30, marginy: 30 })
    graph.setDefaultEdgeLabel(() => ({}))
    nodes.forEach((n) => graph.setNode(n.nodeId, { width: 180, height: 110 }))
    edges.forEach((e) => graph.setEdge(e.fromNodeId, e.toNodeId))
    dagre.layout(graph)
    nodes.forEach((n) => {
      const p = graph.node(n.nodeId)
      n.position = { x: p.x - 90, y: p.y - 55 }
    })
  }
  return {
    instanceId: exactId(r.instanceId),
    rootInstanceId: exactId(r.rootInstanceId),
    name: r.name,
    definitionId: exactId(r.definitionId),
    status: r.status,
    nodes,
    edges,
    highlightedEdges,
  }
}
