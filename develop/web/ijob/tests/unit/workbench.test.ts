import { describe, expect, it } from 'vitest'
import { workbenchData } from '../workbench-data'
import {
  barWidth,
  count,
  formatCount,
  parseWorkbench,
  recentWindow,
  runLink,
  sum,
  validateWindow,
} from '../../src/modules/workbench/model'
describe('workbench contract', () => {
  it('keeps IDs and counts beyond JS safe integers exact', () => {
    const data = workbenchData()
    data.statuses.RUNNING = '9007199254740993'
    data.total = sum(Object.values(data.statuses))
    const parsed = parseWorkbench(data)
    expect(parsed.total).toBe('9007199254741120')
    expect(parsed.recentFailures[0]!.id).toBe('9007199254740993')
    expect(formatCount(parsed.total, 'en-US')).toBe('9,007,199,254,741,120')
    expect(barWidth('9007199254740993', parsed.total)).toBeLessThanOrEqual(100)
  })
  it.each([1, -1, '1.5', '01', '-2', '1e3', '9223372036854775808', null])(
    'rejects invalid counts %s',
    (value) => {
      expect(() => count(value)).toThrow()
    },
  )
  it('preserves unknown statuses without inventing a known status', () => {
    const data = workbenchData()
    data.statuses.FUTURE_STATE = '4'
    data.total = '132'
    expect(parseWorkbench(data).statuses.FUTURE_STATE).toBe('4')
  })
  it('rejects missing or inconsistent aggregate fields', () => {
    for (const change of [
      { total: '129' },
      { successRateDenominator: '128' },
      { successRatePercent: '100.01' },
      { statuses: {} },
      { timeZone: 'Asia/Shanghai' },
      { from: '2026-02-30T10:00:00' },
    ])
      expect(() => parseWorkbench({ ...workbenchData(), ...change })).toThrow()
  })
  it('keeps absent rate and invisible approvals as null, including omitted JSON nulls', () => {
    const data = workbenchData()
    data.total = '0'
    data.statuses = Object.fromEntries(Object.keys(data.statuses).map((s) => [s, '0']))
    data.successRateDenominator = '0'
    data.successRatePercent = null
    data.approvalsVisible = false
    data.pendingApprovals = null
    expect(parseWorkbench(data).successRatePercent).toBeNull()
    expect(
      parseWorkbench({ ...data, successRatePercent: undefined, pendingApprovals: undefined })
        .pendingApprovals,
    ).toBeNull()
    expect(barWidth('0', '0')).toBe(0)
  })
  it('distinguishes window root drilldown from all-time current backlog', () => {
    const window = recentWindow(7, new Date('2026-09-28T10:00:00Z'))
    expect(window).toEqual({ from: '2026-09-21T10:00:00', to: '2026-09-28T10:00:00' })
    expect(runLink('FAILED', window).query).toMatchObject({
      ...window,
      rootOnly: 'true',
      tab: 'instances',
      status: 'FAILED',
    })
    expect(runLink('RUNNING').query).toMatchObject({ from: '', to: '' })
  })
  it('requires a valid ordered UTC window of at most 31 days', () => {
    expect(validateWindow('2026-08-28T00:00:00Z', '2026-09-28T00:00:00Z').to).toBe(
      '2026-09-28T00:00:00',
    )
    for (const [from, to] of [
      ['', '2026-09-28T00:00:00'],
      ['2026-09-28T00:00:00', '2026-09-28T00:00:00'],
      ['2026-08-27T23:59:59', '2026-09-28T00:00:00'],
      ['2026-02-30T00:00:00', '2026-03-01T00:00:00'],
    ])
      expect(() => validateWindow(from!, to!)).toThrow()
  })
})
