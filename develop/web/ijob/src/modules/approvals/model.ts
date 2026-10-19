// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { exactId, record } from '../../shared/api/values'
import { ApiError } from '../../shared/api/errors'
import { formatUtc } from '../../shared/time/format'
import type { AppLocale } from '../../shared/preferences/preferences'

export const statuses = [
  'WAITING',
  'APPROVED',
  'REJECTED',
  'WITHDRAWN',
  'TIMED_OUT',
  'STOPPED',
] as const
export type Decision = 'approve' | 'reject' | 'withdraw'
export interface Approval {
  id: string
  instanceId: string
  rootInstanceId: string
  taskId: string
  taskDefinitionId: string
  matchedEventId: string
  taskName: string
  rootTargetName: string
  attemptNo: number
  waitType: 'APPROVAL' | 'EVENT'
  status: string
  createTime: string
  timeoutAt: string
  decisionBy: string
  decisionComment: string
  resolvedAt: string
}
export function approval(value: unknown): Approval {
  const row = record(value)
  if (
    !['APPROVAL', 'EVENT'].includes(String(row.waitType)) ||
    ![...statuses, 'MATCHED'].includes(String(row.status)) ||
    (row.attemptNo != null && (!Number.isSafeInteger(row.attemptNo) || Number(row.attemptNo) < 0))
  )
    throw new ApiError('protocol')
  const text = (key: string) => {
    if (row[key] == null) return ''
    if (typeof row[key] !== 'string') throw new ApiError('protocol')
    return row[key] as string
  }
  return {
    id: exactId(row.id),
    instanceId: exactId(row.instanceId),
    rootInstanceId: exactId(row.rootInstanceId),
    taskId: row.taskId == null ? '' : exactId(row.taskId),
    taskDefinitionId: row.taskDefinitionId == null ? '' : exactId(row.taskDefinitionId),
    matchedEventId: row.matchedEventId == null ? '' : exactId(row.matchedEventId),
    taskName: text('taskName'),
    rootTargetName: text('rootTargetName'),
    attemptNo: Number(row.attemptNo ?? 0),
    waitType: row.waitType as Approval['waitType'],
    status: String(row.status),
    createTime: text('createTime'),
    timeoutAt: text('timeoutAt'),
    decisionBy: text('decisionBy'),
    decisionComment: text('decisionComment'),
    resolvedAt: text('resolvedAt'),
  }
}
export interface Filters {
  taskName: string
  status: string
  instanceId: string
  rootInstanceId: string
}
export const emptyFilters = (): Filters => ({
  taskName: '',
  status: 'WAITING',
  instanceId: '',
  rootInstanceId: '',
})
export function filterIssues(value: Filters) {
  const errors: Record<string, string> = {}
  if (value.taskName.length > 128) errors.taskName = 'nameLength'
  if (!(statuses as readonly string[]).includes(value.status)) errors.status = 'invalidStatus'
  for (const key of ['instanceId', 'rootInstanceId'] as const) {
    if (value[key]) {
      try {
        exactId(value[key])
      } catch {
        errors[key] = 'invalidId'
      }
    }
  }
  return errors
}
export function time(value: string, locale: AppLocale, zone = 'UTC') {
  if (!value) return '—'
  try {
    return formatUtc(value, locale, zone)
  } catch {
    return '—'
  }
}
export function outcomeUnknown(error: unknown) {
  return (
    !(error instanceof ApiError) ||
    error.status < 400 ||
    error.status >= 500 ||
    error.status === 408
  )
}
