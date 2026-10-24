// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { stringify } from 'lossless-json'
import { record } from '../../shared/api/values'
import { ApiError } from '../../shared/api/errors'
import { parseExact } from '../../shared/ui/json-form'

export type Kind =
  | 'TASK'
  | 'JOB'
  | 'RESOURCE'
  | 'CONCURRENCY_RESOURCE'
  | 'PARAMETER'
  | 'CALENDAR'
  | 'SCHEDULE'
  | 'NOTIFY'
  | 'PARAMETER_BINDING'
  | 'NOTIFY_BINDING'
export type Action = 'CREATE' | 'REUSE' | 'UPDATE'
export interface Selection {
  id: string
  name: string
}
export interface Entry {
  key: string
  kind: Kind
  name: string
  body: Record<string, unknown>
  references: Record<string, string>
  requiredSecrets: string[]
  referenceOnly: boolean
}
export interface Bundle {
  roots: string[]
  objects: Entry[]
  businessTimezone: string
}
export interface Choice {
  action: Action
  targetId?: string
  body?: Record<string, unknown>
  secrets?: Record<string, string>
}
export interface Step {
  key: string
  kind: Kind
  name: string
  action: Action
  targetId?: string
  status: string
  issues: string[]
  before?: Record<string, unknown>
  after?: Record<string, unknown>
}
export interface Plan {
  id: string
  status: string
  steps: Step[]
  createdAt: string
  sourceTimezone: string
  targetTimezone: string
  actor?: string | null
  startedAt?: string | null
  completedAt?: string | null
}
export function actionTotals(steps: Step[], completedOnly = true) {
  return (['CREATE', 'UPDATE', 'REUSE'] as const).map((action) => ({
    action,
    count: steps.filter((s) => s.action === action && (!completedOnly || s.status === 'DONE'))
      .length,
  }))
}
export const maxBytes = 16 * 1024 * 1024
export function bundle(value: unknown): Bundle {
  const data = record(value)
  if (!Array.isArray(data.objects) || !Array.isArray(data.roots) || data.objects.length > 500)
    throw new ApiError('protocol')
  for (const item of data.objects) {
    const e = record(item)
    if (
      typeof e.key !== 'string' ||
      typeof e.name !== 'string' ||
      !Array.isArray(e.requiredSecrets)
    )
      throw new ApiError('protocol')
    record(e.body)
    record(e.references)
  }
  return data as unknown as Bundle
}
export function plan(value: unknown): Plan {
  const data = record(value)
  if (typeof data.id !== 'string' || typeof data.status !== 'string' || !Array.isArray(data.steps))
    throw new ApiError('protocol')
  return data as unknown as Plan
}
export function json(value: unknown) {
  return stringify(value, null, 2) ?? 'null'
}
export function clone(value: Record<string, unknown>) {
  return record(parseExact(json(value)))
}
export function filename(value: string) {
  return (
    value
      .trim()
      .replace(/[<>:"/\\|?*]/g, '-')
      .split('')
      .map((c) => (c.charCodeAt(0) < 32 ? '-' : c))
      .join('')
      .slice(0, 110)
      .replace(/(?:\.ijob)?\.zip$/i, '') + '.ijob.zip'
  )
}
export function download(name: string, data: Blob) {
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export function zip(content: string) {
  const raw = atob(content)
  if (raw.length > maxBytes) throw new Error('size')
  return new Blob([Uint8Array.from(raw, (c) => c.charCodeAt(0))], { type: 'application/zip' })
}
export async function upload(file: File) {
  if (file.size > maxBytes || !file.size) throw new Error('size')
  const bytes = new Uint8Array(await file.arrayBuffer())
  let text = ''
  for (let i = 0; i < bytes.length; i += 8192)
    text += String.fromCharCode(...bytes.subarray(i, i + 8192))
  return btoa(text)
}
/** Presentation order only; never changes dependency/application order. */
export function displayOrder<T extends { kind: Kind }>(entries: T[]): T[] {
  const priority: Partial<Record<Kind, number>> = {
    JOB: 0,
    TASK: 1,
    RESOURCE: 2,
    CONCURRENCY_RESOURCE: 2,
  }
  return [...entries].sort((a, b) => (priority[a.kind] ?? 3) - (priority[b.kind] ?? 3))
}
export function totals(entries: { kind: Kind }[]) {
  const counts = new Map<Kind, number>()
  for (const e of entries) counts.set(e.kind, (counts.get(e.kind) ?? 0) + 1)
  return [...counts].map(([kind, count]) => ({ kind, count }))
}
export function differences(
  before: Record<string, unknown> | undefined,
  after: Record<string, unknown> | undefined,
) {
  const a = before ?? {},
    b = after ?? {}
  return [...new Set([...Object.keys(a), ...Object.keys(b)])]
    .filter((key) => json(a[key]) !== json(b[key]))
    .map((key) => ({ key, before: json(a[key]), after: json(b[key]) }))
}
export function pointerLabel(path: string) {
  return path
    .split('/')
    .filter(Boolean)
    .map((s) => s.replace(/~1/g, '/').replace(/~0/g, '~'))
    .join(' / ')
}
