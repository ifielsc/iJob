// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../../shared/api/errors'
import { parsePage } from '../security-contracts'
import { parseCalendar, record, validDate } from './contracts'
import type { CalendarRow, PreviewDay } from './contracts'

export function parseCalendarContext(value: unknown) {
  const data = record(value)
  if (
    typeof data.today !== 'string' ||
    !validDate(data.today) ||
    typeof data.timezone !== 'string' ||
    !data.timezone
  )
    throw new ApiError('protocol')
  return { today: data.today, timezone: data.timezone }
}

export async function allCalendars(request: (path: string) => Promise<unknown>, name: string) {
  const rows: CalendarRow[] = []
  const ids = new Set<string>()
  for (let page = 1; ; page++) {
    const result = parsePage(
      await request(
        '/api/calendars?' + new URLSearchParams({ pageNum: String(page), pageSize: '100', name }),
      ),
      (v) => parseCalendar(v),
    )
    for (const row of result.list) {
      if (ids.has(row.id)) throw new ApiError('protocol')
      ids.add(row.id)
      rows.push(row)
    }
    if (rows.length >= result.total) return rows
    if (!result.list.length) throw new ApiError('protocol')
  }
}

export function addDays(date: string, days: number) {
  if (!validDate(date) || !Number.isInteger(days) || Math.abs(days) > 366) return ''
  const result = new Date(date + 'T12:00:00Z')
  result.setUTCDate(result.getUTCDate() + days)
  return result.toISOString().slice(0, 10)
}

export function calendarCells(days: PreviewDay[]): (PreviewDay | null)[] {
  if (!days.length) return []
  const offset = (new Date(days[0]!.date + 'T12:00:00Z').getUTCDay() + 6) % 7
  const cells: (PreviewDay | null)[] = [...Array<null>(offset).fill(null), ...days]
  while (cells.length % 7) cells.push(null)
  return cells
}
