// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from './errors'
export function record(v: unknown): Record<string, unknown> {
  if (!v || typeof v !== 'object' || Array.isArray(v)) throw new ApiError('protocol')
  return v as Record<string, unknown>
}
export function exactId(v: unknown): string {
  const s = typeof v === 'number' && Number.isSafeInteger(v) ? String(v) : v
  if (typeof s !== 'string' || !/^[1-9]\d*$/.test(s) || BigInt(s) > 9223372036854775807n)
    throw new ApiError('protocol')
  return s
}
export function integer(v: unknown) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < 0) throw new ApiError('protocol')
  return v
}
export function pageData<T>(v: unknown, parser: (item: unknown) => T) {
  const r = record(v)
  if (!Array.isArray(r.list)) throw new ApiError('protocol')
  return { list: r.list.map((item) => parser(item)), total: integer(r.total) }
}
