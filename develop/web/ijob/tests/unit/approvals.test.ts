// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it, vi } from 'vitest'
import {
  approval,
  emptyFilters,
  filterIssues,
  outcomeUnknown,
} from '../../src/modules/approvals/model'
import { decisionMemory } from '../../src/modules/approvals/decision-memory'
import { ApiError } from '../../src/shared/api/errors'
import { createApiClient } from '../../src/shared/api/client'
import type { Runtime } from '../../src/app/runtime'
import { reactive } from 'vue'

describe('approval contracts', () => {
  it('preserves long identifiers and rejects lossy or unknown values', () => {
    const row = {
      id: '9223372036854775807',
      instanceId: '9007199254740993',
      rootInstanceId: '1',
      waitType: 'APPROVAL',
      status: 'WAITING',
    }
    expect(approval(row).id).toBe(row.id)
    expect(approval(row).instanceId).toBe(row.instanceId)
    expect(() => approval({ ...row, id: Number(row.id) })).toThrow()
    expect(() => approval({ ...row, status: 'ACCEPTED' })).toThrow()
  })
  it('checks exact IDs without trimming and bounds name filters', () => {
    expect(filterIssues({ ...emptyFilters(), instanceId: '9223372036854775807' })).toEqual({})
    expect(
      filterIssues({ ...emptyFilters(), rootInstanceId: ' 1', taskName: 'x'.repeat(129) }),
    ).toEqual({ rootInstanceId: 'invalidId', taskName: 'nameLength' })
    expect(filterIssues({ ...emptyFilters(), status: 'ALL' })).toEqual({ status: 'invalidStatus' })
  })
  it('keeps unknown decisions in memory only for the current session', () => {
    const runtime = { session: reactive({ revision: 1 }) } as Runtime
    const entries = decisionMemory(runtime)
    entries.set('1', { action: 'approve', comment: 'review' })
    expect(decisionMemory(runtime).get('1')?.action).toBe('approve')
    runtime.session.revision++
    expect(entries.size).toBe(0)
    expect(decisionMemory(runtime).size).toBe(0)
  })
  it('treats transport/server/protocol failures as uncertain writes', () => {
    for (const error of [
      new ApiError('network'),
      new ApiError('timeout'),
      new ApiError('protocol'),
      new ApiError('generic', 500),
      new ApiError('generic', 408),
    ])
      expect(outcomeUnknown(error)).toBe(true)
    expect(outcomeUnknown(new ApiError('conflict', 409))).toBe(false)
  })
  it.each([
    [403, '', 0],
    [401, '', 1],
    [403, 'IJOB-SECURITY-0405', 1],
  ] as const)(
    'scopes membership errors but preserves identity protection: %s/%s',
    async (status, code, count) => {
      const event = vi.fn()
      const request = createApiClient({
        token: () => null,
        revision: () => 1,
        locale: () => 'zh-CN',
        onAccessError: event,
        fetch: vi.fn().mockResolvedValue(
          new Response(JSON.stringify({ success: false, code: status, errorCode: code }), {
            status,
            headers: { 'content-type': 'application/json' },
          }),
        ),
      })
      await expect(request('/api/event-waits/1', { localForbidden: true })).rejects.toBeInstanceOf(
        ApiError,
      )
      expect(event).toHaveBeenCalledTimes(count)
    },
  )
})
