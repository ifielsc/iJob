// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { addExceptionRange } from '../../src/modules/configuration/calendar-exceptions'
import type { CalendarException } from '../../src/modules/configuration/contracts'

describe('calendar range editing', () => {
  it('includes leap day and both boundaries without replacing an existing workday', () => {
    const existing: CalendarException[] = [{ date: '2028-02-29', type: 'WORKDAY' }]
    expect(addExceptionRange(existing, '2028-02-28', '2028-03-01', 'HOLIDAY')).toEqual({
      added: [
        { date: '2028-02-28', type: 'HOLIDAY' },
        { date: '2028-03-01', type: 'HOLIDAY' },
      ],
      skipped: 1,
    })
    expect(existing).toEqual([{ date: '2028-02-29', type: 'WORKDAY' }])
  })
  it('rejects invalid, reversed and oversized ranges before changing the draft', () => {
    for (const [from, to] of [
      ['2026-02-29', '2026-03-01'],
      ['2026-10-02', '2026-10-01'],
      ['2026-01-01', '2027-01-02'],
    ]) {
      expect(() => addExceptionRange([], from!, to!, 'HOLIDAY')).toThrow('range')
    }
    expect(addExceptionRange([], '2028-01-01', '2028-12-31', 'HOLIDAY').added).toHaveLength(366)
  })
  it('enforces the 2000-row total atomically and counts only new dates toward it', () => {
    const existing: CalendarException[] = Array.from({ length: 1999 }, (_, index) => ({
      date: new Date(Date.UTC(2020, 0, 1 + index)).toISOString().slice(0, 10),
      type: 'HOLIDAY',
    }))
    expect(() => addExceptionRange(existing, '2030-01-01', '2030-01-02', 'WORKDAY')).toThrow(
      'limit',
    )
    expect(existing).toHaveLength(1999)
    expect(addExceptionRange(existing, '2030-01-01', '2030-01-01', 'WORKDAY').added).toHaveLength(1)
    expect(addExceptionRange(existing, '2020-01-01', '2020-01-02', 'WORKDAY')).toEqual({
      added: [],
      skipped: 2,
    })
  })
})
