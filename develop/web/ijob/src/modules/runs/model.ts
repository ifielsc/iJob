// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { exactId, record } from '../../shared/api/values'
import { runRow, type Run, type Log } from '../../shared/runtime/contracts'
import { parseUtc } from '../../shared/time/format'
import { ApiError } from '../../shared/api/errors'
export { pageData } from '../../shared/api/values'
export { terminal, logRows } from '../../shared/runtime/contracts'
export const states = [
  'WAITING',
  'RUNNING',
  'PAUSED',
  'WAITING_EVENT',
  'SUCCESS',
  'FAILED',
  'STOPPED',
  'TIMED_OUT',
  'SKIPPED',
]
export const sources = ['TEST_SEND', 'INSTANCE', 'MONITOR', 'DATA_CHECK', 'SYSTEM']
export const results = [
  'STARTED',
  'DELIVERED',
  'CONFIG_NOT_FOUND',
  'CONFIG_DISABLED',
  'CHANNEL_UNAVAILABLE',
  'DELIVERY_FAILED',
]
export function text(v: unknown) {
  return typeof v === 'string' ? v : ''
}
const id = (v: unknown) => (v == null ? '' : exactId(v))
export interface Instance extends Run {
  targetType: string
  targetId: string
  targetName: string
  triggerId: string
  triggerType: string
  parentInstanceId: string
  jobDefinitionId: string
  definitionNodeId: string
  scheduleId: string
  businessDate?: string
  businessTime?: string
  businessZoneId?: string
  triggerTime: string
  updateTime: string
  timeoutAt: string
  waitReason: string
  permitWaitDeadline: string
  progress: number | null
  retryCount: number | null
  errorCode: string
  inputVariables: string
  outputVariables: string
  variablesTruncated: boolean
  variablesRedacted: boolean
}
export function instance(v: unknown): Instance {
  const r = record(v)
  return {
    ...runRow(v),
    targetType: text(r.targetType),
    targetId: id(r.targetId),
    targetName: text(r.targetName),
    triggerId: id(r.triggerId),
    triggerType: text(r.triggerType),
    parentInstanceId: id(r.parentInstanceId),
    jobDefinitionId: id(r.jobDefinitionId),
    definitionNodeId: id(r.definitionNodeId),
    scheduleId: id(r.scheduleId),
    businessDate: text(r.businessDate),
    businessTime: text(r.businessTime),
    businessZoneId: text(r.businessZoneId),
    triggerTime: text(r.triggerTime),
    updateTime: text(r.updateTime),
    timeoutAt: text(r.timeoutAt),
    waitReason: text(r.waitReason),
    permitWaitDeadline: text(r.permitWaitDeadline),
    errorCode: text(r.errorCode),
    progress: typeof r.progress === 'number' ? r.progress : null,
    retryCount: typeof r.retryCount === 'number' ? r.retryCount : null,
    inputVariables: text(r.inputVariables),
    outputVariables: text(r.outputVariables),
    variablesTruncated: r.variablesTruncated === true,
    variablesRedacted: r.variablesRedacted === true,
  }
}
export interface Decision {
  id: string
  jobInstanceId: string
  definitionNodeId: string
  evaluationNo: number
  selectedEdgeIds: string
  status: string
  errorCode: string
  contextDigest: string
  evaluatorVersion: number
  createTime: string
}
export function tree(v: unknown) {
  const r = record(v)
  if (!Array.isArray(r.nodes) || !Array.isArray(r.gatewayDecisions)) throw new ApiError('protocol')
  return {
    rootInstanceId: exactId(r.rootInstanceId),
    nodes: r.nodes.map(instance),
    truncated: r.truncated === true,
    gatewayDecisionsTruncated: r.gatewayDecisionsTruncated === true,
    maxNodes: typeof r.maxNodes === 'number' ? r.maxNodes : 0,
    decisions: r.gatewayDecisions.map((v): Decision => {
      const d = record(v)
      return {
        id: exactId(d.id),
        jobInstanceId: id(d.jobInstanceId),
        definitionNodeId: id(d.definitionNodeId),
        evaluationNo: Number(d.evaluationNo),
        selectedEdgeIds: text(d.selectedEdgeIds),
        status: text(d.status),
        errorCode: text(d.errorCode),
        contextDigest: text(d.contextDigest),
        evaluatorVersion: Number(d.evaluatorVersion),
        createTime: text(d.createTime),
      }
    }),
  }
}
export function treeRows(nodes: Instance[]) {
  const ids = new Set(nodes.map((n) => n.id)),
    children = new Map<string, Instance[]>()
  for (const n of nodes) {
    const key = ids.has(n.parentInstanceId) ? n.parentInstanceId : ''
    children.set(key, [...(children.get(key) ?? []), n])
  }
  const out: { node: Instance; depth: number }[] = [],
    seen = new Set<string>()
  const visit = (start: Instance, depth: number) => {
    const stack = [{ node: start, depth }]
    while (stack.length) {
      const item = stack.pop()!
      if (seen.has(item.node.id)) continue
      seen.add(item.node.id)
      out.push(item)
      for (const child of [...(children.get(item.node.id) ?? [])].reverse())
        stack.push({ node: child, depth: item.depth + 1 })
    }
  }
  for (const n of children.get('') ?? []) visit(n, 0)
  for (const n of nodes) if (!seen.has(n.id)) visit(n, 0)
  return out
}
export const commandTerminal = new Set(['SUCCEEDED', 'REJECTED', 'FAILED', 'EXPIRED'])
export type Action = 'pause' | 'resume' | 'stop' | 'retry'
export interface Command {
  requestId: string
  instanceId: string
  rootInstanceId: string
  triggerId: string
  commandType: string
  status: string
  resultMessage: string
  requestedBy: string
  createTime: string
  processedAt: string
  expiresAt: string
  replayed: boolean
}
export function command(v: unknown): Command {
  const r = record(v),
    status = text(r.status)
  if (!['PENDING', 'PROCESSING', ...commandTerminal].includes(status) || !text(r.requestId))
    throw new ApiError('protocol')
  return {
    requestId: text(r.requestId),
    instanceId: exactId(r.instanceId),
    rootInstanceId: id(r.rootInstanceId),
    triggerId: id(r.triggerId),
    commandType: text(r.commandType),
    status,
    resultMessage: text(r.resultMessage),
    requestedBy: text(r.requestedBy),
    createTime: text(r.createTime),
    processedAt: text(r.processedAt),
    expiresAt: text(r.expiresAt),
    replayed: r.replayed === true,
  }
}
export function actions(row?: Instance): Action[] {
  if (!row || row.parentInstanceId || row.rootInstanceId !== row.id) return []
  if (['FAILED', 'TIMED_OUT'].includes(row.status)) return ['retry']
  if (['SUCCESS', 'STOPPED', 'SKIPPED'].includes(row.status)) return []
  return row.status === 'RUNNING'
    ? ['pause', 'stop']
    : row.status === 'PAUSED'
      ? ['resume', 'stop']
      : ['stop']
}
export function mergeLogs(current: Log[], batch: Log[]) {
  const all = [...new Map([...current, ...batch].map((l) => [l.id, l])).values()].sort((a, b) =>
    BigInt(a.id) < BigInt(b.id) ? -1 : 1,
  )
  return { rows: all.slice(-5000), cursor: all.at(-1)?.id ?? '0', truncated: all.length > 5000 }
}
export interface SendRecord {
  id: string
  sourceType: string
  result: string
  notifyConfigId: string
  notifyType: string
  targetType: string
  targetId: string
  instanceId: string
  notifyBindingId: string
  correlationId: string
  operatorId: string
  durationMs: number | null
  failureCode: string
  failureSummary: string
  createTime: string
  updateTime: string
}
export function sendRecord(v: unknown): SendRecord {
  const r = record(v)
  return {
    id: exactId(r.id),
    sourceType: text(r.sourceType),
    result: text(r.result),
    notifyConfigId: id(r.notifyConfigId),
    notifyType: text(r.notifyType),
    targetType: text(r.targetType),
    targetId: id(r.targetId),
    instanceId: id(r.instanceId),
    notifyBindingId: id(r.notifyBindingId),
    correlationId: text(r.correlationId),
    operatorId: text(r.operatorId),
    durationMs: typeof r.durationMs === 'number' ? r.durationMs : null,
    failureCode: text(r.failureCode),
    failureSummary: text(r.failureSummary),
    createTime: text(r.createTime),
    updateTime: text(r.updateTime),
  }
}
export function range(from: string, to: string, notify = false) {
  const start = from.length === 16 ? from + ':00' : from,
    end = to.length === 16 ? to + ':00' : to
  if (notify && (!start || !end)) throw new Error('range')
  if (start) parseUtc(start)
  if (end) parseUtc(end)
  if (start && end) {
    const span = parseUtc(end).getTime() - parseUtc(start).getTime()
    if (span < 0 || (notify && (span === 0 || span > 31 * 86400000))) throw new Error('range')
  }
  return { from: start, to: end }
}
