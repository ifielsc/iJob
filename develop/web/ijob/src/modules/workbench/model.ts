// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../../shared/api/errors'
import { parseUtc } from '../../shared/time/format'
import { exactId, record } from '../../shared/api/values'

export const statusOrder = [
  'SUCCESS',
  'FAILED',
  'TIMED_OUT',
  'STOPPED',
  'SKIPPED',
  'WAITING',
  'RUNNING',
  'WAITING_EVENT',
  'PAUSED',
]
export const activeOrder = ['RUNNING', 'WAITING', 'PAUSED', 'WAITING_EVENT']
export interface WindowRange {
  from: string
  to: string
}
export function count(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !/^(0|[1-9]\d*)$/.test(value) ||
    value.length > 19 ||
    BigInt(value) > 9223372036854775807n
  )
    throw new ApiError('protocol')
  return value
}
function text(value: unknown) {
  if (typeof value !== 'string' || !value) throw new ApiError('protocol')
  return value
}
function bool(value: unknown) {
  if (typeof value !== 'boolean') throw new ApiError('protocol')
  return value
}
function timestamp(value: unknown) {
  const v = text(value)
  parseUtc(v)
  return v.replace(/Z$/, '')
}
function counts(value: unknown, required: string[]) {
  const source = record(value)
  for (const key of required) count(source[key])
  return Object.fromEntries(Object.entries(source).map(([key, value]) => [key, count(value)]))
}
export function parseWorkbench(value: unknown) {
  try {
    const r = record(value),
      node = record(r.node)
    const statuses = counts(r.statuses, statusOrder),
      total = count(r.total)
    if (sum(Object.values(statuses)) !== total || r.timeZone !== 'UTC')
      throw new ApiError('protocol')
    const denominator = count(r.successRateDenominator)
    if (denominator !== sum([statuses.SUCCESS!, statuses.FAILED!, statuses.TIMED_OUT!]))
      throw new ApiError('protocol')
    const rate = r.successRatePercent == null ? null : text(r.successRatePercent)
    if (
      (denominator === '0') !== (rate === null) ||
      (rate !== null && (!/^\d{1,3}\.\d{2}$/.test(rate) || Number(rate) > 100))
    )
      throw new ApiError('protocol')
    if (
      !Array.isArray(r.recentFailures) ||
      r.recentFailures.length > 5 ||
      !Array.isArray(node.roles)
    )
      throw new ApiError('protocol')
    const visible = bool(r.approvalsVisible)
    return {
      from: timestamp(r.from),
      to: timestamp(r.to),
      queriedAt: timestamp(r.queriedAt),
      businessZone: text(r.businessZone),
      total,
      statuses,
      successRateDenominator: denominator,
      successRatePercent: rate,
      currentActiveRoots: counts(r.currentActiveRoots, activeOrder),
      approvalsVisible: visible,
      pendingApprovals: visible ? count(r.pendingApprovals) : null,
      recentFailures: r.recentFailures.map((item) => {
        const f = record(item)
        return {
          id: exactId(f.id),
          targetId: exactId(f.targetId),
          targetType: text(f.targetType),
          name: f.name == null ? '' : text(f.name),
          status: text(f.status),
          createTime: timestamp(f.createTime),
        }
      }),
      node: {
        mode: text(node.mode),
        roles: node.roles.map(text),
        workerRoleConfigured: bool(node.workerRoleConfigured),
        schedulerRoleConfigured: bool(node.schedulerRoleConfigured),
        scheduleEnabled: bool(node.scheduleEnabled),
        clusterWorkerHealth: text(node.clusterWorkerHealth),
      },
    }
  } catch {
    throw new ApiError('protocol')
  }
}
export type Workbench = ReturnType<typeof parseWorkbench>
export function sum(values: string[]) {
  return values.reduce((sum, v) => sum + BigInt(v), 0n).toString()
}
export function formatCount(value: string, locale: string) {
  return new Intl.NumberFormat(locale).format(BigInt(value))
}
// Convert only a bounded visual ratio to Number; counts themselves stay exact.
export function barWidth(value: string, total: string) {
  return total === '0' ? 0 : Number((BigInt(value) * 10000n) / BigInt(total)) / 100
}
export function validateWindow(from: string, to: string): WindowRange {
  const start = parseUtc(from),
    end = parseUtc(to)
  if (+end <= +start || +end - +start > 31 * 86400000) throw new Error('Invalid window')
  return { from: from.replace(/Z$/, ''), to: to.replace(/Z$/, '') }
}
export function recentWindow(days: number, now = new Date()): WindowRange {
  return {
    from: new Date(+now - days * 86400000).toISOString().slice(0, 19),
    to: now.toISOString().slice(0, 19),
  }
}
export function runLink(status: string, window?: WindowRange) {
  return {
    name: 'runs',
    query: {
      tab: 'instances',
      rootOnly: 'true',
      status,
      from: window?.from ?? '',
      to: window?.to ?? '',
    },
  }
}
