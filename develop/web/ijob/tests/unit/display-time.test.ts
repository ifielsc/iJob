import { describe, expect, it } from 'vitest'
import { wallToUtc, utcToWall } from '../../src/shared/time/display'
describe('explicit display timezone, independent from browser timezone', () => {
  it('converts business input across UTC day boundary and preserves fractions', () => {
    expect(wallToUtc('2026-09-29T01:02:03.456', 'Asia/Shanghai')).toBe('2026-09-28T17:02:03.456')
    expect(utcToWall('2026-09-28T17:02:03.456', 'Asia/Shanghai')).toBe('2026-09-29T01:02:03.456')
  })
  it('supports fractional offsets and UTC', () => {
    expect(wallToUtc('2026-09-29T09:00:00', 'Asia/Kathmandu')).toBe('2026-09-29T03:15:00')
    expect(wallToUtc('2026-09-29T09:00:00', 'UTC')).toBe('2026-09-29T09:00:00')
  })
  it('rejects gaps and folds instead of silently moving the query', () => {
    expect(() => wallToUtc('2026-03-08T02:30:00', 'America/New_York')).toThrow()
    expect(() => wallToUtc('2026-11-01T01:30:00', 'America/New_York')).toThrow()
    expect(wallToUtc('2026-11-01T02:30:00', 'America/New_York')).toBe('2026-11-01T07:30:00')
  })
  it('rejects invalid calendar values and zone identifiers', () => {
    expect(() => wallToUtc('2026-02-30T12:00:00', 'UTC')).toThrow()
    expect(() => wallToUtc('2026-09-29T12:00:00', 'invalid/zone')).toThrow()
  })
})
