// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import {
  addDays,
  allCalendars,
  calendarCells,
  parseCalendarContext,
} from '../../src/modules/configuration/calendar-data'
describe('calendar display contracts', () => {
  it('collects every matching backend page without exposing list pagination', async () => {
    const calls: string[] = []
    const rows = await allCalendars(async (path) => {
      calls.push(path)
      const page = Number(new URL(path, 'https://test.local').searchParams.get('pageNum'))
      return {
        list: [{ id: String(page), name: 'Calendar', workdays: ['MONDAY'] }],
        total: 2,
        pages: 2,
        pageNum: page,
        pageSize: 1,
      }
    }, 'Business')
    expect(rows.map((row) => row.id)).toEqual(['1', '2'])
    expect(calls).toHaveLength(2)
    expect(calls.every((path) => path.includes('name=Business'))).toBe(true)
  })
  it('aligns a Thursday under a single Monday-first header and preserves cross-month dates', () => {
    const cells = calendarCells([{ date: '2026-09-24', allowed: true, exceptionType: null }])
    expect(cells.slice(0, 3)).toEqual([null, null, null])
    expect(cells[3]?.date).toBe('2026-09-24')
    expect(cells).toHaveLength(7)
    expect(addDays('2026-09-24', 13)).toBe('2026-10-07')
    expect(parseCalendarContext({ today: '2026-09-24', timezone: 'Asia/Shanghai' }).today).toBe(
      '2026-09-24',
    )
    expect(() => parseCalendarContext({ today: '2026-02-30', timezone: 'UTC' })).toThrow()
  })
})
