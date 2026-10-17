import { describe, expect, it } from 'vitest'
import {
  calendarCoordinate,
  calendarDay,
  clockCoordinate,
  clockText,
} from '../../src/shared/time/utc-picker'
describe('UTC picker display coordinates', () => {
  it('roundtrips calendar days including leap days without an API timestamp conversion', () => {
    for (const day of ['2026-09-22', '2024-02-29', '2026-03-08', '2026-11-01'])
      expect(calendarDay(calendarCoordinate(day))).toBe(day)
    expect(() => calendarCoordinate('2026-02-29')).toThrow()
  })
  it('keeps clock components separate from the chosen calendar date', () => {
    for (const time of ['00:00:00', '02:30:45', '13:00:09', '23:59:59'])
      expect(clockText(clockCoordinate(time))).toBe(time)
    expect(() => clockCoordinate('24:00:00')).toThrow()
  })
})
