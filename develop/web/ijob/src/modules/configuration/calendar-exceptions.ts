// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { addDays } from './calendar-data'
import { validDate } from './contracts'
import type { CalendarException } from './contracts'

// Build an atomic addition: existing dates retain their explicit type.
export function addExceptionRange(
  existing: CalendarException[],
  from: string,
  to: string,
  type: CalendarException['type'],
) {
  if (!validDate(from) || !validDate(to)) throw new Error('range')
  const span = (Date.parse(to + 'T12:00:00Z') - Date.parse(from + 'T12:00:00Z')) / 86400000
  if (span < 0 || span > 365) throw new Error('range')
  const dates = new Set(existing.map((entry) => entry.date))
  const added: CalendarException[] = []
  let skipped = 0
  for (let offset = 0; offset <= span; offset++) {
    const date = addDays(from, offset)
    if (dates.has(date)) skipped++
    else added.push({ date, type })
  }
  if (existing.length + added.length > 2000) throw new Error('limit')
  return { added, skipped }
}
