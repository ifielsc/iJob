// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, it } from 'vitest'
import { logSnapshot } from '../../src/shared/runtime/log-snapshot'

it('preserves exact IDs and server ordering when newer logs have smaller IDs', () => {
  const rows = [
    {
      id: '9223372036854775807',
      logTime: '2026-10-08T10:00:00',
      level: 'INFO',
      content: 'earlier',
    },
    { id: '20', logTime: '2026-10-08T10:00:01', level: 'INFO', content: 'later' },
  ]
  expect(logSnapshot({ rows, truncated: true })).toEqual({ rows, truncated: true })
  expect(logSnapshot({ rows: [], truncated: false }).rows).toEqual([])
})

it('rejects malformed snapshots and oversized windows', () => {
  expect(() => logSnapshot([])).toThrow()
  expect(() => logSnapshot({ rows: [], truncated: 'false' })).toThrow()
  expect(() => logSnapshot({ rows: [{ id: '1' }], truncated: false })).toThrow()
  const row = { id: '1', logTime: '2026-10-08T10:00:00', level: 'INFO', content: 'line' }
  expect(() => logSnapshot({ rows: Array(5001).fill(row), truncated: true })).toThrow()
})
