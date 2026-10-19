// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../../shared/api/errors'
export function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError('protocol')
  return value as Record<string, unknown>
}
export function list<T>(value: unknown, parse: (item: unknown) => T): T[] {
  if (!Array.isArray(value)) throw new ApiError('protocol')
  return value.map(parse)
}
export function revision(value: unknown, zero = false): string {
  if (
    typeof value !== 'string' ||
    !(zero ? /^(0|[1-9]\d*)$/ : /^[1-9]\d*$/).test(value) ||
    BigInt(value) > 9223372036854775807n
  )
    throw new ApiError('protocol')
  return value
}
function integer(value: unknown, maximum = Number.MAX_SAFE_INTEGER): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0 || value > maximum)
    throw new ApiError('protocol')
  return value
}
function text(value: unknown): string {
  if (typeof value !== 'string') throw new ApiError('protocol')
  return value
}
function maximum(value: unknown) {
  const n = integer(value, 10000)
  if (!n) throw new ApiError('protocol')
  return n
}
export interface Resource {
  id: string
  version: string
  name: string
  code: string
  maxConcurrency: number
  enabled: boolean
  description: string
  referenceCount: number
  activeInstanceCount: number
}
export function resource(value: unknown): Resource {
  const r = object(value)
  if (typeof r.enabled !== 'boolean') throw new ApiError('protocol')
  return {
    id: revision(r.id),
    version: revision(r.version),
    name: text(r.name),
    code: text(r.code),
    maxConcurrency: maximum(r.maxConcurrency),
    enabled: r.enabled,
    description: r.description == null ? '' : text(r.description),
    referenceCount: integer(r.referenceCount),
    activeInstanceCount: integer(r.activeInstanceCount),
  }
}
export interface Plugin {
  pluginType: string
  version: string
  maxConcurrency: number
  activeInstanceCount: number
}
export function plugin(value: unknown): Plugin {
  const r = object(value)
  return {
    pluginType: text(r.pluginType),
    version: revision(r.version),
    maxConcurrency: maximum(r.maxConcurrency),
    activeInstanceCount: integer(r.activeInstanceCount),
  }
}
export interface TaskPolicy {
  taskId: string
  taskName: string
  pluginType: string
  version: string
  taskMaxConcurrency: number | null
  resources: Resource[]
  activeInstanceCount: number
}
export function taskPolicy(value: unknown): TaskPolicy {
  const r = object(value)
  return {
    taskId: revision(r.taskId),
    taskName: text(r.taskName),
    pluginType: text(r.pluginType),
    version: revision(r.version, true),
    taskMaxConcurrency: r.taskMaxConcurrency == null ? null : maximum(r.taskMaxConcurrency),
    resources: list(r.resources, resource),
    activeInstanceCount: integer(r.activeInstanceCount),
  }
}
export interface Metadata {
  plugins: { type: string; activeInstanceCount: number }[]
  permitWaitTimeoutMillis: number
  retryIntervalMillis: number
  maxRetryIntervalMillis: number
  retryJitterMillis: number
}
export function metadata(value: unknown): Metadata {
  const r = object(value)
  if (r.configurationSource !== 'API_NODE' || r.minConcurrency !== 1 || r.maxConcurrency !== 10000)
    throw new ApiError('protocol')
  return {
    plugins: list(r.plugins, (x) => {
      const p = object(x)
      return { type: text(p.type), activeInstanceCount: integer(p.activeInstanceCount) }
    }),
    permitWaitTimeoutMillis: integer(r.permitWaitTimeoutMillis),
    retryIntervalMillis: integer(r.retryIntervalMillis),
    maxRetryIntervalMillis: integer(r.maxRetryIntervalMillis),
    retryJitterMillis: integer(r.retryJitterMillis),
  }
}
export type Target =
  | { kind: 'resource'; id?: string }
  | { kind: 'plugin'; type: string }
  | { kind: 'task'; id: string }
export interface Draft {
  name: string
  code: string
  maximum: string
  limited: boolean
  enabled: boolean
  description: string
  resourceIds: string[]
}
export function limit(value: string): number | undefined {
  return /^\d+$/.test(value) && Number(value) >= 1 && Number(value) <= 10000
    ? Number(value)
    : undefined
}
export function issues(draft: Draft, kind: Target['kind'], resources: Resource[]) {
  const result: Record<string, string> = {}
  if (kind === 'resource') {
    if (!draft.name.trim() || draft.name.trim().length > 128) result.name = 'nameError'
    if (!/^[A-Za-z0-9._-]{1,128}$/.test(draft.code.trim())) result.code = 'codeError'
    if (draft.description.length > 500) result.description = 'descriptionError'
  }
  if ((kind !== 'task' || draft.limited) && limit(draft.maximum) === undefined)
    result.maximum = 'limitError'
  if (
    kind === 'task' &&
    draft.resourceIds.some((id) => !resources.find((r) => r.id === id)?.enabled)
  )
    result.resources = 'resourceError'
  return result
}
export function outcomeUnknown(e: unknown) {
  return (
    !(e instanceof ApiError) ||
    ['network', 'timeout', 'protocol'].includes(e.kind) ||
    e.status >= 500
  )
}
export function versionConflict(e: unknown) {
  return (
    e instanceof ApiError &&
    [
      'error.concurrency.version',
      'error.detail.plugin-concurrency-policy-version-changed',
      'error.detail.task-concurrency-policy-version-changed',
    ].includes(e.envelope?.messageKey ?? '')
  )
}
export function resourcePage(
  rows: Resource[],
  name: string,
  enabled: string,
  page: number,
  size: number,
) {
  const filtered = rows
    .filter(
      (r) =>
        r.name.toLocaleLowerCase().includes(name.toLocaleLowerCase()) &&
        (!enabled || String(r.enabled) === enabled),
    )
    .sort((a, b) =>
      a.code < b.code ? -1 : a.code > b.code ? 1 : BigInt(a.id) < BigInt(b.id) ? -1 : 1,
    )
  return { total: filtered.length, rows: filtered.slice((page - 1) * size, page * size) }
}
