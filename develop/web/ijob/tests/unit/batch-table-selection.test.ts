// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { describe, expect, it } from 'vitest'
import { newTableNames } from '../../src/modules/tasks/batch-table-selection'
describe('batch table selection', () => {
  it('preserves existing rows and rejects duplicate identities across pages', () => {
    const rows = [{ name: 'Orders' }, { name: 'customers' }]
    expect(newTableNames(rows, ['orders', 'CUSTOMERS', 'items', 'ITEMS', 'refunds'])).toEqual([
      'items',
      'refunds',
    ])
    expect(rows).toEqual([{ name: 'Orders' }, { name: 'customers' }])
  })
  it('repeated addition is idempotent and keeps selected order', () => {
    const rows = [{ name: 'existing' }]
    const selected = ['second', 'first', 'second']
    const added = newTableNames(rows, selected)
    expect(added).toEqual(['second', 'first'])
    expect(newTableNames([...rows, ...added.map((name) => ({ name }))], selected)).toEqual([])
  })
})
