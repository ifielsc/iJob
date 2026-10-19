// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { diagnostics, selection, drilldown } from '../../src/modules/diagnostics/model'
import { diagnosticData } from '../diagnostics-fixture'
const sample = () =>
  JSON.parse(JSON.stringify(diagnosticData(), (_key, value) => (value === '' ? undefined : value)))
describe('diagnostic facts and navigation', () => {
  it('accepts default filters and any bounded integer limit', () => {
    expect(selection(undefined, undefined)).toEqual({ category: 'LEASE_EXPIRED', limit: 20 })
    expect(selection('COMMAND_EXPIRED', '100').limit).toBe(100)
    for (const value of ['', '0', '01', '101', '1.5', ['20']])
      expect(() => selection(undefined, value)).toThrow()
    expect(() => selection('FUTURE', undefined)).toThrow()
  })
  it('keeps large identities exact and optional fields absent', () => {
    const parsed = diagnostics(sample(), selection(undefined, undefined))
    expect(parsed.items[0]!.rootInstanceId).toBe('9007199254740993')
    expect(parsed.items[0]!.instanceId).toBe('')
  })
  it('preserves unknown runtime status without inventing a meaning', () => {
    const data = sample()
    data.items[0].status = 'FUTURE'
    expect(diagnostics(data, selection(undefined, undefined)).items[0]!.status).toBe('FUTURE')
  })
  it('rejects malformed or mismatched responses instead of displaying an empty success', () => {
    for (const change of [
      { category: 'QUEUE_DELAYED' },
      { limit: 100 },
      { timeZone: 'local' },
      { hasMore: true },
      { items: null },
      { observedAt: '2026-02-30T00:00:00' },
      { queueDelaySeconds: 0 },
    ])
      expect(() =>
        diagnostics({ ...sample(), ...change }, selection(undefined, undefined)),
      ).toThrow()
    const duplicate = sample()
    duplicate.items[1] = duplicate.items[0]
    expect(() => diagnostics(duplicate, selection(undefined, undefined))).toThrow()
  })
  it('retains fixed return filters and sends exact root IDs to the existing detail', () => {
    const row = diagnostics(sample(), selection(undefined, undefined)).items[0]!
    expect(drilldown(row, 'LEASE_EXPIRED', '/diagnostics?limit=50')).toMatchObject({
      name: 'run-detail',
      params: { id: '9007199254740993' },
      query: { back: '/diagnostics?limit=50' },
    })
    expect(
      drilldown({ ...row, rootInstanceId: '' }, 'QUEUE_DELAYED', '/diagnostics'),
    ).toMatchObject({ name: 'runs', query: { inspectTrigger: '30471', from: '', to: '' } })
  })
  it('only passes command request IDs accepted by the existing detail route', () => {
    const row = {
      ...diagnostics(sample(), selection(undefined, undefined)).items[0]!,
      instanceId: '9007199254740993',
      id: 'cmd-1',
    }
    expect(drilldown(row, 'COMMAND_EXPIRED', '/diagnostics')!.query).toHaveProperty(
      'requestId',
      'cmd-1',
    )
    expect(
      drilldown({ ...row, id: 'legacy/request' }, 'COMMAND_EXPIRED', '/diagnostics')!.query,
    ).not.toHaveProperty('requestId')
  })
})
