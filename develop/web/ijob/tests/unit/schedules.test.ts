import { describe, it, expect, vi } from 'vitest'
import { parse, stringify } from 'lossless-json'
import {
  body,
  draftOf,
  issues,
  previewBody,
  previewRows,
  schedule,
  metadata,
} from '../../src/modules/schedules/model'
import { nowInZone, wallTime } from '../../src/shared/time/wall-time'
import { createApiClient } from '../../src/shared/api/client'
const meta = metadata({
  zoneId: 'America/New_York',
  defaultMisfirePolicy: 'SKIP',
  defaultMisfireGraceSec: 7,
  defaultMaxCatchupCount: 3,
  maxCatchupWindowSec: 7200,
  maxPreviewCount: 50,
  maxIntervalSec: 31536000,
  maxCatchupCount: 1000,
  maxVariableCount: 100,
  maxVariableBytes: 65536,
})
describe('schedule contract', () => {
  it('keeps business wall time distinct from UTC including DST boundaries', () => {
    expect(nowInZone('America/New_York', new Date('2026-03-08T07:00:00Z'))).toBe(
      '2026-03-08T03:00:00',
    )
    expect(nowInZone('Asia/Shanghai', new Date('2026-03-08T07:00:00Z'))).toBe('2026-03-08T15:00:00')
    expect(wallTime('2026-03-08 02:30:00')).toBe('2026-03-08T02:30:00')
    expect(() => wallTime('2026-02-29T09:00:00')).toThrow()
    expect(() => wallTime('2026-09-28T09:00:00Z')).toThrow()
  })
  it('preserves long numeric variables and sends only the active rule', () => {
    const draft = draftOf()
    Object.assign(draft, {
      targetId: '9223372036854775807',
      cronExpr: '0 0 9 * * *',
      intervalSec: 300,
      variablesText: '{"large":9223372036854775807,"text":"0001","nested":{"x":[true,null,1]}}',
    })
    expect(issues(draft, meta)).toEqual({})
    expect(stringify(body(draft))).toContain('"large":9223372036854775807')
    expect(body(draft).intervalSec).toBeNull()
    draft.scheduleType = 'INTERVAL'
    expect(body(draft).cronExpr).toBeNull()
    expect(body(draft).misfirePolicy).toBeNull()
    expect(body(draft).enabled).toBe(false)
    expect(previewBody(draft)).not.toHaveProperty('variables')
  })
  it('rejects invalid once dates, policies and reserved or oversized variables', () => {
    const draft = draftOf()
    Object.assign(draft, {
      targetId: '1',
      scheduleType: 'ONCE',
      variablesText: '{"ijob.secret":1}',
      maxCatchupCount: 1001,
    })
    expect(issues(draft, meta)).toMatchObject({
      startTime: 'required',
      variablesText: 'invalidVariables',
      maxCatchupCount: 'invalidNumber',
    })
    draft.startTime = '2026-09-29T12:00:00'
    draft.endTime = '2026-09-28T12:00:00'
    expect(issues(draft, meta).endTime).toBe('timeOrder')
    draft.variablesText = JSON.stringify({ x: 'a'.repeat(65536) })
    expect(issues(draft, meta).variablesText).toBe('invalidVariables')
  })
  it('distinguishes projected-away variables from empty values and keeps excluded candidates', () => {
    const row = {
      id: '9223372036854775807',
      targetId: '1',
      targetType: 'TASK',
      scheduleType: 'CRON',
      enabled: false,
    }
    expect(schedule(row).variablesText).toBeUndefined()
    expect(schedule({ ...row, variables: {} }).variablesText).toBe('{}')
    expect(
      previewRows({ items: [{ fireTime: '2026-09-29T12:00:00', calendarAllowed: false }] }),
    ).toEqual([{ fireTime: '2026-09-29T12:00:00', calendarAllowed: false }])
    expect(() => previewRows({})).toThrow()
  })
  it('roundtrips lossless request and response JSON without changing ordinary requests', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response('{"success":true,"data":{"large":9223372036854775807,"small":2}}', {
        headers: { 'content-type': 'application/json' },
      }),
    )
    const request = createApiClient({
      fetch: fetcher,
      token: () => null,
      revision: () => 1,
      locale: () => 'en-US',
      onAccessError: vi.fn(),
    })
    const result = await request('/api/schedules/1', {
      method: 'PATCH',
      lossless: true,
      body: parse('{"large":9223372036854775807}'),
    })
    expect(fetcher.mock.calls[0]![1]!.body).toBe('{"large":9223372036854775807}')
    expect(stringify(result)).toBe('{"large":9223372036854775807,"small":2}')
  })
})
