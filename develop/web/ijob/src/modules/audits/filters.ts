// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { parseUtc } from '../../shared/time/format'
export const resultCodes = [
  'SUCCESS',
  'REJECTED',
  'FAILED',
  'ATTEMPTED',
  'ACCEPTED',
  'REPLAYED',
] as const
export function recentRange(now = new Date()) {
  return {
    from: new Date(now.getTime() - 7 * 86400000).toISOString().slice(0, 19),
    to: now.toISOString().slice(0, 19),
  }
}
export function normalizeRange(from: string, to: string) {
  const start = from.length === 16 ? `${from}:00` : from
  const end = to.length === 16 ? `${to}:00` : to
  const span = parseUtc(end).getTime() - parseUtc(start).getTime()
  if (span < 0 || span > 90 * 86400000) throw new Error('Invalid range')
  return { from: start, to: end }
}
