// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it, vi } from 'vitest'
import {
  actions,
  command,
  instance,
  mergeLogs,
  range,
  sendRecord,
  tree,
  treeRows,
} from '../../src/modules/runs/model'
import { createApiClient } from '../../src/shared/api/client'
const id = '9007199254740993'
const root = () =>
  instance({
    id,
    rootInstanceId: id,
    status: 'RUNNING',
    targetId: '9223372036854775807',
    targetType: 'JOB',
  })
describe('Runtime contracts', () => {
  it('preserves nested identifiers, missing names and omitted variables', () => {
    const data = tree({
      rootInstanceId: id,
      nodes: [
        {
          id,
          rootInstanceId: id,
          status: 'RUNNING',
          variablesTruncated: true,
          inputVariables: null,
        },
      ],
      gatewayDecisions: [
        { id, jobInstanceId: id, definitionNodeId: id, selectedEdgeIds: '[9223372036854775807]' },
      ],
      maxNodes: 250,
      truncated: true,
    })
    expect(data.nodes[0]?.targetName).toBe('')
    expect(data.nodes[0]?.inputVariables).toBe('')
    expect(data.truncated).toBe(true)
    expect(data.decisions[0]?.selectedEdgeIds).toBe('[9223372036854775807]')
    expect(() => tree({ ...data, rootInstanceId: Number(id), gatewayDecisions: [] })).toThrow()
  })
  it('only exposes valid controls on explicit roots', () => {
    expect(actions(root())).toEqual(['pause', 'stop'])
    expect(actions({ ...root(), status: 'PAUSED' })).toEqual(['resume', 'stop'])
    expect(actions({ ...root(), status: 'FAILED' })).toEqual(['retry'])
    expect(actions({ ...root(), status: 'SUCCESS' })).toEqual([])
    expect(actions({ ...root(), parentInstanceId: '123' })).toEqual([])
    expect(actions({ ...root(), rootInstanceId: null })).toEqual([])
  })
  it('handles tree ordering, orphaned truncated nodes and cycles without recursion', () => {
    const child = { ...root(), id: '2', parentInstanceId: id },
      orphan = { ...root(), id: '3', parentInstanceId: '4' }
    expect(treeRows([child, root(), orphan]).map((n) => [n.node.id, n.depth])).toEqual([
      [id, 0],
      ['2', 1],
      ['3', 0],
    ])
    expect(
      treeRows([
        { ...child, parentInstanceId: '3' },
        { ...orphan, parentInstanceId: '2' },
      ]),
    ).toHaveLength(2)
  })
  it('deduplicates logs, keeps exact cursors and bounds memory while continuing', () => {
    const rows = Array.from({ length: 5000 }, (_, i) => ({
      id: String(BigInt(id) + BigInt(i)),
      logTime: '',
      level: 'INFO',
      content: String(i),
    }))
    const next = { ...rows[4999]!, id: String(BigInt(id) + 5000n) }
    const merged = mergeLogs(rows, [rows[4999]!, next])
    expect(merged.rows).toHaveLength(5000)
    expect(merged.cursor).toBe(next.id)
    expect(merged.truncated).toBe(true)
    expect(merged.rows[0]?.content).toBe('1')
  })
  it('applies different runtime and notification time constraints', () => {
    expect(() => range('2026-01-01T00:00:00', '2026-09-01T00:00:00')).not.toThrow()
    expect(() => range('2026-01-01T00:00:00', '2026-09-01T00:00:00', true)).toThrow()
    expect(() => range('', '', true)).toThrow()
    expect(() => range('2026-02-31T00:00:00', '')).toThrow()
    expect(() => range('2026-01-01T00:00:00', '2026-02-01T00:00:00', true)).not.toThrow()
  })
  it('keeps command states separate and notification durations numeric', () => {
    expect(command({ requestId: 'request-1', instanceId: id, status: 'PENDING' }).status).toBe(
      'PENDING',
    )
    expect(() => command({ requestId: 'request-1', instanceId: id, status: 'RUNNING' })).toThrow()
    expect(sendRecord({ id, notifyConfigId: id, durationMs: 308 }).durationMs).toBe(308)
    expect(() => sendRecord({ id: Number(id) })).toThrow()
  })
  it('transmits the same explicit idempotency key without auto retry', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new Error('network'))
    const request = createApiClient({
      fetch: fetcher,
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => 1,
      onAccessError: vi.fn(),
    })
    await expect(
      request('/api/instances/' + id + '/pause', { method: 'POST', idempotencyKey: 'same-key' }),
    ).rejects.toThrow()
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect((fetcher.mock.calls[0]![1]!.headers as Headers).get('Idempotency-Key')).toBe('same-key')
  })
})
