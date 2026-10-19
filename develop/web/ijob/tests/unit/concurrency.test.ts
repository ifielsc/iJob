// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import {
  issues,
  limit,
  resource,
  resourcePage,
  revision,
  type Draft,
} from '../../src/modules/concurrency/model'
const row = {
  id: '9007199254740993',
  version: '9223372036854775807',
  code: 'pool',
  name: 'Report',
  maxConcurrency: 8,
  enabled: true,
  referenceCount: 0,
  activeInstanceCount: 0,
}
describe('concurrency policy contract', () => {
  it('keeps exact ids and rejects lossy numeric revisions', () => {
    expect(resource(row).id).toBe(row.id)
    expect(revision('0', true)).toBe('0')
    expect(() => revision(Number('9007199254740993'))).toThrow()
    expect(() => revision('9223372036854775808')).toThrow()
  })
  it('validates integer caps and disabled selected resources', () => {
    for (const value of ['0', '10001', '1.2', '1e2', '']) expect(limit(value)).toBeUndefined()
    expect(limit('10000')).toBe(10000)
    const draft: Draft = {
      name: '',
      code: '',
      maximum: '',
      limited: false,
      enabled: true,
      description: '',
      resourceIds: [row.id],
    }
    expect(issues(draft, 'task', [{ ...resource(row), enabled: false }])).toEqual({
      resources: 'resourceError',
    })
    draft.resourceIds = []
    expect(issues(draft, 'task', [])).toEqual({})
  })
  it('filters all rows then sorts stably before slicing', () => {
    const rows = Array.from({ length: 30 }, (_, i) => ({
      ...resource(row),
      id: String(100 + i),
      name: i === 29 ? 'Needle' : 'Report',
      code: 'same',
    }))
    expect(resourcePage(rows, 'Needle', '', 1, 20).rows[0]!.id).toBe('129')
    expect(resourcePage(rows, '', '', 2, 20).rows[0]!.id).toBe('120')
    expect(resourcePage(rows, '', '', 2, 20).total).toBe(30)
  })
})
