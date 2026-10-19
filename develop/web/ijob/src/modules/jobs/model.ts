// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { parse, stringify, isLosslessNumber } from 'lossless-json'
import { exactId, record } from '../../shared/api/values'
import { ApiError } from '../../shared/api/errors'
export { pageData } from '../../shared/api/values'
export type Operand = { var?: string; literal?: unknown }
export interface Condition {
  op: string
  left?: Operand
  right?: Operand
  operands?: Condition[]
  operand?: Condition
}
export interface JobRow {
  nodeCount?: number
  id: string
  name: string
  enabled: boolean
  currentDefinitionId: string
  currentRevision: string
  groups: string[]
  timeout: number | null
  description: string
  updateTime: string
}
export interface JobNode {
  nodeId: string
  name: string
  kind: 'EXECUTION' | 'GATEWAY'
  nodeType?: 'TASK' | 'JOB'
  refId?: string
  gateway?: { mode: 'EXCLUSIVE' }
  policy?: {
    failStrategy: 'STOP' | 'SKIP'
    executeCondition: 'ALL_SUCCESS' | 'ANY_SUCCESS' | 'ALWAYS'
  }
  position: { x: number; y: number }
  refName?: string
  refDefinitionId?: string
  refRevision?: string
  currentRefName?: string
  currentRefRevision?: string
  referenceAvailable?: boolean
}
export interface JobEdge {
  id: string
  fromNodeId: string
  toNodeId: string
  routeKey?: string
  priority?: number
  defaultRoute?: boolean
  conditionText: string
  sourceHandle?: string
  targetHandle?: string
}
export interface JobDraft {
  name: string
  enabled: boolean
  timeout: number | null
  description: string
  groups: string
  nodes: JobNode[]
  edges: JobEdge[]
}
export interface JobDetail {
  row: JobRow
  draft: JobDraft
  publishedAt: string
  current: boolean
  hasLayout: boolean
}
export interface Version {
  jobId: string
  definitionId: string
  revision: string
  name: string
  enabled: boolean
  publishedAt: string
  current: boolean
}
export const operators = [
  'AND',
  'OR',
  'NOT',
  'EQ',
  'NE',
  'GT',
  'GE',
  'LT',
  'LE',
  'IN',
  'NOT_IN',
  'EXISTS',
  'IS_NULL',
  'IS_NOT_NULL',
]
export const json = (value: unknown) => stringify(value, null, 2) ?? ''
export function jsonValue(text: string): unknown {
  return parse(text)
}
export function jobRow(value: unknown): JobRow {
  const r = record(value)
  if (
    typeof r.name !== 'string' ||
    typeof r.enabled !== 'boolean' ||
    !Array.isArray(r.groups) ||
    !r.groups.every((g) => typeof g === 'string')
  )
    throw new ApiError('protocol')
  return {
    nodeCount:
      Number.isSafeInteger(r.nodeCount) && Number(r.nodeCount) >= 0
        ? Number(r.nodeCount)
        : undefined,
    id: exactId(r.id),
    name: r.name,
    enabled: r.enabled,
    groups: r.groups as string[],
    currentDefinitionId: exactId(r.currentDefinitionId),
    currentRevision: exactId(r.currentRevision),
    timeout: r.timeout == null ? null : Number(r.timeout),
    description: typeof r.description === 'string' ? r.description : '',
    updateTime: String(r.updateTime ?? ''),
  }
}
export function version(value: unknown): Version {
  const r = record(value)
  if (
    typeof r.name !== 'string' ||
    typeof r.enabled !== 'boolean' ||
    typeof r.current !== 'boolean' ||
    typeof r.publishedAt !== 'string'
  )
    throw new ApiError('protocol')
  return {
    jobId: exactId(r.jobId),
    definitionId: exactId(r.definitionId),
    revision: exactId(r.revision),
    name: r.name,
    enabled: r.enabled,
    publishedAt: r.publishedAt,
    current: r.current,
  }
}
export function freshDraft(): JobDraft {
  return {
    name: '',
    enabled: true,
    timeout: null,
    description: '',
    groups: '',
    nodes: [],
    edges: [],
  }
}
export function jobDetail(value: unknown): JobDetail {
  const r = record(value),
    row = jobRow(value)
  if (!Array.isArray(r.nodes) || !Array.isArray(r.edges)) throw new ApiError('protocol')
  const layout = r.layout == null ? {} : record(r.layout)
  const positions = layout.positions ? record(layout.positions) : {}
  const keys = new Map<string, string>()
  const nodes = r.nodes.map((v): JobNode => {
    const n = record(v),
      key = String(n.nodeKey),
      kind = n.kind
    if (!key || typeof n.name !== 'string' || !['EXECUTION', 'GATEWAY'].includes(String(kind)))
      throw new ApiError('protocol')
    keys.set(exactId(n.id), key)
    const p = positions[key] ? record(positions[key]) : { x: 0, y: 0 }
    const node: JobNode = {
      nodeId: key,
      name: n.name,
      kind: kind as JobNode['kind'],
      position: { x: Number(p.x), y: Number(p.y) },
    }
    if (kind === 'GATEWAY') {
      if (record(n.gateway).mode !== 'EXCLUSIVE') throw new ApiError('protocol')
      node.gateway = { mode: 'EXCLUSIVE' }
    } else {
      if (!['TASK', 'JOB'].includes(String(n.nodeType))) throw new ApiError('protocol')
      const policy = record(n.policy)
      if (
        !['STOP', 'SKIP'].includes(String(policy.failStrategy)) ||
        !['ALL_SUCCESS', 'ANY_SUCCESS', 'ALWAYS'].includes(String(policy.executeCondition))
      )
        throw new ApiError('protocol')
      Object.assign(node, {
        nodeType: n.nodeType,
        refId: exactId(n.refId),
        policy: { failStrategy: policy.failStrategy, executeCondition: policy.executeCondition },
        refName: String(n.refName ?? ''),
        refDefinitionId: n.refDefinitionId ? exactId(n.refDefinitionId) : undefined,
        refRevision: n.refRevision ? exactId(n.refRevision) : undefined,
        currentRefName: String(n.currentRefName ?? ''),
        currentRefRevision: n.currentRefRevision ? exactId(n.currentRefRevision) : undefined,
        referenceAvailable: n.referenceAvailable === true,
      })
    }
    return node
  })
  const ports = new Map(
    (Array.isArray(layout.connections) ? layout.connections : []).map((v) => {
      const port = record(v)
      return [JSON.stringify([port.fromNodeId, port.toNodeId]), port]
    }),
  )
  const edges = r.edges.map((v): JobEdge => {
    const e = record(v),
      from = keys.get(exactId(e.fromNodeId)),
      to = keys.get(exactId(e.toNodeId))
    if (!from || !to) throw new ApiError('protocol')
    const port = ports.get(JSON.stringify([from, to]))
    return {
      sourceHandle: typeof port?.sourceHandle === 'string' ? port.sourceHandle : undefined,
      targetHandle: typeof port?.targetHandle === 'string' ? port.targetHandle : undefined,
      id: exactId(e.id),
      fromNodeId: from,
      toNodeId: to,
      routeKey: typeof e.routeKey === 'string' ? e.routeKey : undefined,
      priority: e.priority == null ? undefined : Number(e.priority),
      defaultRoute: e.defaultRoute === true,
      conditionText: e.condition == null ? '' : json(e.condition),
    }
  })
  return {
    row,
    draft: {
      name: row.name,
      enabled: row.enabled,
      description: row.description,
      timeout: row.timeout,
      groups: row.groups.join(', '),
      nodes,
      edges,
    },
    current: r.current === true,
    publishedAt: String(r.publishedAt ?? ''),
    hasLayout: layout.version === 1,
  }
}
export function jobBody(draft: JobDraft) {
  return {
    name: draft.name,
    enabled: draft.enabled,
    timeout: draft.timeout,
    description: draft.description,
    groups: [
      ...new Set(
        draft.groups
          .split(',')
          .map((v) => v.trim().toLowerCase())
          .filter(Boolean),
      ),
    ],
    nodes: draft.nodes.map((n) => ({
      nodeId: n.nodeId,
      name: n.name,
      kind: n.kind,
      ...(n.kind === 'GATEWAY'
        ? { gateway: n.gateway }
        : { nodeType: n.nodeType, refId: n.refId, policy: n.policy }),
    })),
    edges: draft.edges.map((e) => ({
      fromNodeId: e.fromNodeId,
      toNodeId: e.toNodeId,
      ...(draft.nodes.find((n) => n.nodeId === e.fromNodeId)?.kind === 'GATEWAY'
        ? {
            routeKey: e.routeKey,
            priority: e.priority,
            defaultRoute: !!e.defaultRoute,
            ...(!e.defaultRoute ? { condition: jsonValue(e.conditionText) } : {}),
          }
        : {}),
    })),
    layout: {
      version: 1,
      ...(draft.edges.some((e) => e.sourceHandle || e.targetHandle)
        ? {
            connections: draft.edges
              .filter((e) => e.sourceHandle || e.targetHandle)
              .map((e) => ({
                fromNodeId: e.fromNodeId,
                toNodeId: e.toNodeId,
                sourceHandle: e.sourceHandle ?? 'right',
                targetHandle: e.targetHandle ?? 'left',
              })),
          }
        : {}),
      positions: Object.fromEntries(draft.nodes.map((n) => [n.nodeId, n.position])),
    },
  }
}
export interface Issue {
  path: string
  key: string
}
export function validateCondition(value: unknown, parents: string[]): boolean {
  let count = 0
  const literal = (v: unknown): boolean =>
    v === null || ['string', 'number', 'boolean'].includes(typeof v) || isLosslessNumber(v)
  const operand = (v: unknown, variable = false): boolean => {
    if (!v || typeof v !== 'object') return false
    const o = v as Operand
    if (o.var !== undefined)
      return (
        typeof o.var === 'string' &&
        o.literal == null &&
        /^(input\.[A-Za-z0-9_.-]+|nodes\.[A-Za-z0-9_.-]+|trigger\.(id|type|time|date))$/.test(
          o.var,
        ) &&
        (!o.var.startsWith('nodes.') ||
          parents.filter((p) => o.var!.startsWith('nodes.' + p + '.')).length === 1)
      )
    return !variable && literal(o.literal ?? null)
  }
  const visit = (v: unknown, depth: number): boolean => {
    if (++count > 100 || depth > 16 || !v || typeof v !== 'object') return false
    const c = v as Condition
    if (!operators.includes(c.op)) return false
    if (c.op === 'AND' || c.op === 'OR')
      return (
        !c.left &&
        !c.right &&
        !c.operand &&
        Array.isArray(c.operands) &&
        c.operands.length >= 2 &&
        c.operands.every((n) => visit(n, depth + 1))
      )
    if (c.op === 'NOT') return !c.left && !c.right && !c.operands && visit(c.operand, depth + 1)
    if (
      c.operands ||
      c.operand ||
      !operand(c.left, ['EXISTS', 'IS_NULL', 'IS_NOT_NULL'].includes(c.op))
    )
      return false
    if (['EXISTS', 'IS_NULL', 'IS_NOT_NULL'].includes(c.op)) return !c.right
    if (c.op === 'IN' || c.op === 'NOT_IN')
      return (
        c.right?.var == null &&
        Array.isArray(c.right?.literal) &&
        c.right.literal.length > 0 &&
        c.right.literal.length <= 100 &&
        c.right.literal.every(literal)
      )
    return operand(c.right)
  }
  return new TextEncoder().encode(json(value)).length <= 16384 && visit(value, 1)
}
export function issues(d: JobDraft): Issue[] {
  const result: Issue[] = [],
    add = (path: string, key = 'invalid') => result.push({ path, key })
  const name = (s: string) => !!s.trim() && s === s.trim() && s.length <= 128
  if (!name(d.name)) add('name')
  if (d.description.length > 500) add('description')
  if (
    d.timeout !== null &&
    (!Number.isInteger(d.timeout) || d.timeout < 1 || d.timeout > 2147483647)
  )
    add('timeout')
  const groups = d.groups
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean)
  if (new Set(groups).size > 16 || groups.some((g) => !/^[a-z0-9][a-z0-9._-]{0,63}$/.test(g)))
    add('groups')
  if (!d.nodes.length || d.nodes.length > 500) add('nodes', 'nodeCount')
  if (d.edges.length > 5000) add('edges', 'edgeCount')
  const keys = new Set<string>()
  d.nodes.forEach((n, i) => {
    if (!name(n.name)) add(`nodes[${i}].name`)
    if (
      !n.nodeId.trim() ||
      n.nodeId !== n.nodeId.trim() ||
      n.nodeId.length > 64 ||
      keys.has(n.nodeId)
    )
      add(`nodes[${i}].nodeId`)
    keys.add(n.nodeId)
    if (n.kind === 'EXECUTION') {
      try {
        exactId(n.refId)
      } catch {
        add(`nodes[${i}].refId`, 'referenceRequired')
      }
    }
  })
  const pairs = new Set<string>(),
    degrees = new Map(d.nodes.map((n) => [n.nodeId, 0])),
    children = new Map(d.nodes.map((n) => [n.nodeId, [] as string[]])),
    connected = new Set<string>()
  d.edges.forEach((e, i) => {
    const key = JSON.stringify([e.fromNodeId, e.toNodeId])
    if (
      !keys.has(e.fromNodeId) ||
      !keys.has(e.toNodeId) ||
      e.fromNodeId === e.toNodeId ||
      pairs.has(key)
    )
      add(`edges[${i}]`, 'edgeInvalid')
    else {
      children.get(e.fromNodeId)!.push(e.toNodeId)
      degrees.set(e.toNodeId, degrees.get(e.toNodeId)! + 1)
    }
    pairs.add(key)
    connected.add(e.fromNodeId)
    connected.add(e.toNodeId)
  })
  const ready = [...degrees].filter(([, v]) => !v).map(([k]) => k)
  let visited = 0
  while (ready.length) {
    const key = ready.pop()!
    visited++
    for (const child of children.get(key) ?? []) {
      const degree = degrees.get(child)! - 1
      degrees.set(child, degree)
      if (!degree) ready.push(child)
    }
  }
  if (visited !== d.nodes.length) add('edges', 'cycle')
  d.nodes.forEach((n, i) => {
    if (d.nodes.length > 1 && !connected.has(n.nodeId)) add(`nodes[${i}]`, 'isolated')
    if (n.kind !== 'GATEWAY') return
    const edges = d.edges.filter((e) => e.fromNodeId === n.nodeId),
      priorities = new Set<number>(),
      routes = new Set<string>()
    if (edges.length < 2 || edges.filter((e) => e.defaultRoute).length !== 1)
      add(`nodes[${i}]`, 'gatewayRoutes')
    for (const e of edges) {
      const index = d.edges.indexOf(e)
      if (
        !e.routeKey?.trim() ||
        e.routeKey !== e.routeKey.trim() ||
        e.routeKey.length > 64 ||
        routes.has(e.routeKey) ||
        e.priority == null ||
        !Number.isInteger(e.priority) ||
        e.priority < 0 ||
        e.priority > 10000 ||
        priorities.has(e.priority)
      )
        add(`edges[${index}]`, 'routeInvalid')
      routes.add(e.routeKey ?? '')
      priorities.add(e.priority ?? -1)
      if (!e.defaultRoute) {
        try {
          if (
            !validateCondition(
              jsonValue(e.conditionText),
              d.edges.filter((p) => p.toNodeId === n.nodeId).map((p) => p.fromNodeId),
            )
          )
            add(`edges[${index}].condition`, 'conditionInvalid')
        } catch {
          add(`edges[${index}].condition`, 'conditionInvalid')
        }
      }
    }
  })
  return result
}
export function renameNode(d: JobDraft, old: string, next: string) {
  if (
    !next.trim() ||
    next !== next.trim() ||
    next.length > 64 ||
    d.nodes.some((n) => n.nodeId === next && n.nodeId !== old)
  )
    return false
  const pending: [JobEdge, string][] = []
  const rewrite = (c: Condition) => {
    for (const o of [c.left, c.right])
      if (o?.var?.startsWith('nodes.' + old + '.'))
        o.var = 'nodes.' + next + o.var.slice(('nodes.' + old).length)
    c.operands?.forEach(rewrite)
    if (c.operand) rewrite(c.operand)
  }
  try {
    for (const edge of d.edges)
      if (edge.conditionText) {
        const c = jsonValue(edge.conditionText) as Condition
        rewrite(c)
        pending.push([edge, json(c)])
      }
  } catch {
    return false
  }
  const node = d.nodes.find((n) => n.nodeId === old)
  if (!node) return false
  node.nodeId = next
  for (const edge of d.edges) {
    if (edge.fromNodeId === old) edge.fromNodeId = next
    if (edge.toNodeId === old) edge.toNodeId = next
  }
  pending.forEach(([edge, text]) => (edge.conditionText = text))
  return true
}
