// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../../shared/api/errors'
import { parseUtc } from '../../shared/time/format'
import { exactId, record } from '../../shared/api/values'

export const categories = [
  'LEASE_EXPIRED',
  'QUEUE_DELAYED',
  'ROOT_DEADLINE_EXCEEDED',
  'COMMAND_EXPIRED',
] as const
export type Category = (typeof categories)[number]
export function selection(category: unknown, limit: unknown) {
  const c = category === undefined ? 'LEASE_EXPIRED' : category
  const l = limit === undefined ? '20' : limit
  if (
    typeof c !== 'string' ||
    !categories.includes(c as Category) ||
    typeof l !== 'string' ||
    !/^[1-9]\d{0,2}$/.test(l) ||
    Number(l) > 100
  )
    throw new ApiError('protocol')
  return { category: c as Category, limit: Number(l) }
}
function text(value: unknown) {
  if (typeof value !== 'string' || !value) throw new ApiError('protocol')
  return value
}
function flag(value: unknown) {
  if (typeof value !== 'boolean') throw new ApiError('protocol')
  return value
}
function time(value: unknown) {
  const result = text(value)
  parseUtc(result)
  return result
}
const optionalId = (value: unknown) => (value == null ? '' : exactId(value))
const optionalText = (value: unknown) => (value == null ? '' : text(value))
export function diagnostics(value: unknown, expected: ReturnType<typeof selection>) {
  try {
    const r = record(value),
      node = record(r.node)
    if (
      r.category !== expected.category ||
      r.limit !== expected.limit ||
      r.timeZone !== 'UTC' ||
      !Number.isInteger(r.queueDelaySeconds) ||
      Number(r.queueDelaySeconds) < 1 ||
      !Array.isArray(r.items) ||
      r.items.length > expected.limit ||
      !Array.isArray(node.roles)
    )
      throw new ApiError('protocol')
    const hasMore = flag(r.hasMore)
    if (hasMore && r.items.length !== expected.limit) throw new ApiError('protocol')
    const items = r.items.map((value) => {
      const row = record(value)
      const id = expected.category === 'COMMAND_EXPIRED' ? text(row.id) : exactId(row.id)
      if (id.length > 191) throw new ApiError('protocol')
      const item = {
        id,
        triggerId: optionalId(row.triggerId),
        rootInstanceId: optionalId(row.rootInstanceId),
        instanceId: optionalId(row.instanceId),
        targetType: optionalText(row.targetType),
        targetId: optionalId(row.targetId),
        name: optionalText(row.name),
        status: text(row.status),
        commandType: optionalText(row.commandType),
        observedDeadline: time(row.observedDeadline),
        createTime: time(row.createTime),
      }
      if (expected.category === 'COMMAND_EXPIRED' && (!item.instanceId || !item.commandType))
        throw new ApiError('protocol')
      if (expected.category === 'ROOT_DEADLINE_EXCEEDED' && item.rootInstanceId !== id)
        throw new ApiError('protocol')
      if (
        (expected.category === 'LEASE_EXPIRED' || expected.category === 'QUEUE_DELAYED') &&
        item.triggerId !== id
      )
        throw new ApiError('protocol')
      return item
    })
    if (new Set(items.map((row) => row.id)).size !== items.length) throw new ApiError('protocol')
    return {
      ...expected,
      observedAt: time(r.observedAt),
      businessZone: text(r.businessZone),
      queueDelaySeconds: Number(r.queueDelaySeconds),
      hasMore,
      items,
      node: {
        mode: text(node.mode),
        roles: node.roles.map(text),
        workerRoleConfigured: flag(node.workerRoleConfigured),
        schedulerRoleConfigured: flag(node.schedulerRoleConfigured),
        scheduleEnabled: flag(node.scheduleEnabled),
        clusterWorkerHealth: text(node.clusterWorkerHealth),
      },
    }
  } catch {
    throw new ApiError('protocol')
  }
}
export type Diagnostics = ReturnType<typeof diagnostics>
export type DiagnosticItem = Diagnostics['items'][number]
export function drilldown(row: DiagnosticItem, category: Category, back: string) {
  const id = category === 'COMMAND_EXPIRED' ? row.instanceId : row.rootInstanceId
  if (id)
    return {
      name: 'run-detail',
      params: { id },
      query: {
        back,
        ...(category === 'COMMAND_EXPIRED' && /^[a-zA-Z0-9-]{1,128}$/.test(row.id)
          ? { requestId: row.id }
          : {}),
      },
    }
  if (row.triggerId)
    return {
      name: 'runs',
      query: { tab: 'triggers', inspectTrigger: row.triggerId, from: '', to: '', back },
    }
  return undefined
}
