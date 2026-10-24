// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { describe, it, expect } from 'vitest'
import {
  actionTotals,
  displayOrder,
  type Plan,
  bundle,
  clone,
  differences,
  filename,
  json,
  totals,
  upload,
  type Entry,
} from '../../src/modules/packages/model'
import { htmlReport } from '../../src/modules/packages/report'
import { parseExact } from '../../src/shared/ui/json-form'
describe('portable packages', () => {
  it('preserves exact business numbers and explicit nulls in configuration edits', () => {
    const value = clone(
      parseExact('{"amount":9007199254740993,"optional":null}') as Record<string, unknown>,
    )
    expect(json(value)).toContain('9007199254740993')
    expect(value.optional).toBeNull()
  })
  it('rejects oversized upload before reading it', async () => {
    const file = {
      size: 16 * 1024 * 1024 + 1,
      arrayBuffer: () => {
        throw Error('should not read')
      },
    } as unknown as File
    await expect(upload(file)).rejects.toThrow('size')
  })
  it('requires the inspected manifest shape', () => {
    expect(() => bundle({ objects: [{ key: 'x', name: 'example' }], roots: [] })).toThrow()
  })
  it('normalizes download filename and keeps a single extension', () => {
    expect(filename('../selected.ijob.zip')).toBe('..-selected.ijob.zip')
    expect(filename('jobs.zip')).toBe('jobs.ijob.zip')
  })
  it('summarizes actual objects and displays changed fields only', () => {
    expect(totals([{ kind: 'TASK' }, { kind: 'TASK' }, { kind: 'RESOURCE' }] as Entry[])).toEqual([
      { kind: 'TASK', count: 2 },
      { kind: 'RESOURCE', count: 1 },
    ])
    expect(differences({ timeout: 60, enabled: false }, { timeout: 120, enabled: false })).toEqual([
      { key: 'timeout', before: '60', after: '120' },
    ])
  })
})

it('counts only completed actions and produces escaped metadata-only HTML', () => {
  const value: Plan = {
    id: 'report-id',
    status: 'PARTIAL',
    createdAt: '2026-10-23T10:00:00Z',
    sourceTimezone: 'UTC',
    targetTimezone: 'Asia/Shanghai',
    actor: '<operator>',
    steps: [
      {
        key: 'a',
        kind: 'TASK',
        name: '<img src=x onerror=alert(1)>',
        action: 'CREATE',
        status: 'DONE',
        targetId: '1',
        issues: [],
        after: { secret: 'private-configuration-value' },
      },
      {
        key: 'b',
        kind: 'JOB',
        name: 'failed job',
        action: 'CREATE',
        status: 'FAILED',
        issues: ['<unsafe>'],
      },
      { key: 'c', kind: 'RESOURCE', name: 'existing', action: 'REUSE', status: 'DONE', issues: [] },
      { key: 'd', kind: 'TASK', name: 'updated', action: 'UPDATE', status: 'DONE', issues: [] },
    ],
  }
  expect(actionTotals(value.steps)).toEqual([
    { action: 'CREATE', count: 1 },
    { action: 'UPDATE', count: 1 },
    { action: 'REUSE', count: 1 },
  ])
  expect(actionTotals(value.steps, false)[0]!.count).toBe(2)
  const html = htmlReport(value, {
    title: 'Report',
    locale: 'en-US',
    timezone: 'Asia/Shanghai',
    label: (s) => s,
    time: (s) => s ?? 'Not recorded',
    issue: (s) => s,
  })
  expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;')
  expect(html).toContain('&lt;operator&gt;')
  expect(html).toContain('&lt;unsafe&gt;')
  expect(html).not.toContain('<img')
  expect(html).not.toContain('private-configuration-value')
  expect(html).toContain('Not recorded')
})

it('orders presentation by jobs, tasks, resources and others without mutating execution order', () => {
  const execution = [
    { kind: 'CALENDAR' },
    { kind: 'RESOURCE' },
    { kind: 'CONCURRENCY_RESOURCE' },
    { kind: 'TASK' },
    { kind: 'JOB' },
  ] as Entry[]
  expect(displayOrder(execution).map((e) => e.kind)).toEqual([
    'JOB',
    'TASK',
    'RESOURCE',
    'CONCURRENCY_RESOURCE',
    'CALENDAR',
  ])
  expect(execution.map((e) => e.kind)).toEqual([
    'CALENDAR',
    'RESOURCE',
    'CONCURRENCY_RESOURCE',
    'TASK',
    'JOB',
  ])
})
