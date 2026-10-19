// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect, vi } from 'vitest'
import { validUsername, validPassword, validDisplayName } from '../../src/shared/auth/account-rules'
import {
  parseAccount,
  parseAudit,
  parsePage,
  parseRoles,
} from '../../src/modules/security-contracts'
import { normalizeRange, recentRange } from '../../src/modules/audits/filters'
import { createApiClient } from '../../src/shared/api/client'
import { createI18n } from 'vue-i18n'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
const account = {
  id: '9223372036854775807',
  loginId: 'user:a',
  username: 'admin',
  displayName: 'Admin',
  roleCode: 'ADMIN',
  enabled: true,
  mustChangePassword: true,
  revision: '9223372036854775806',
}
describe('security contracts and input boundaries', () => {
  it('compiles every bilingual message, including literal at signs', () => {
    for (const [locale, messages] of Object.entries({ 'zh-CN': zhCN, 'en-US': enUS })) {
      const i18n = createI18n({ legacy: false, locale, messages: { [locale]: messages } })
      for (const [section, values] of Object.entries(messages))
        for (const key of Object.keys(values))
          expect(() =>
            i18n.global.t(`${section}.${key}`, { page: 1, total: 20, id: 'test' }),
          ).not.toThrow()
    }
  })
  it.each(['admin', 'a_b', 'a.b-c@d', 'a'.repeat(64)])('accepts username %s', (value) =>
    expect(validUsername(value)).toBe(true),
  )
  it.each(['ab', '_abc', '中admin', 'a'.repeat(65)])('rejects invalid username %s', (value) =>
    expect(validUsername(value)).toBe(false),
  )
  it('matches UTF-16, UTF-8 and ISO control password rules without trimming', () => {
    expect(validPassword('中'.repeat(24))).toBe(true)
    expect(validPassword('中'.repeat(25))).toBe(false)
    expect(validPassword('😀'.repeat(18))).toBe(true)
    expect(validPassword('😀'.repeat(19))).toBe(false)
    expect(validPassword(' '.repeat(12))).toBe(false)
    expect(validPassword(' allowed space ')).toBe(true)
    expect(validPassword('abcdefghijk\u0085')).toBe(false)
    expect(validDisplayName('')).toBe(true)
    expect(validDisplayName('a'.repeat(129))).toBe(false)
  })
  it('keeps snowflake IDs and revisions as strings and rejects unsafe numeric forms', () => {
    expect(parseAccount(account)).toEqual(account)
    expect(() => parseAccount({ ...account, revision: 1 })).toThrow()
    expect(() => parseAccount({ ...account, id: Number('9223372036854775807') })).toThrow()
    expect(() => parseAccount({ ...account, roleCode: 'ROOT' })).toThrow()
  })
  it('validates pages, dates, nullable details and role permissions', () => {
    expect(
      parsePage({ list: [account], total: 1, pageNum: 1, pageSize: 20, pages: 1 }, parseAccount)
        .list,
    ).toHaveLength(1)
    expect(() =>
      parsePage({ list: [], total: 2 ** 54, pageNum: 1, pageSize: 20, pages: 1 }, parseAccount),
    ).toThrow()
    expect(
      parseRoles([{ roleCode: 'VIEWER', permissions: ['SUMMARY_READ', 'FUTURE'] }])[0]?.permissions,
    ).toEqual(['SUMMARY_READ'])
    const audit = {
      id: '9007199254740999',
      occurredAt: '2026-09-20T00:00:00',
      actorLoginId: 'ANONYMOUS',
      actionCode: 'AUTH_LOGIN',
      targetType: 'ACCOUNT',
      resultCode: 'REJECTED',
    }
    expect(parseAudit(audit)).toEqual(audit)
    expect(() => parseAudit({ ...audit, occurredAt: '2026-02-30T00:00:00' })).toThrow()
  })
  it('uses UTC ranges independently of locale and limits to 90 days', () => {
    expect(recentRange(new Date('2026-09-20T00:00:00Z'))).toEqual({
      from: '2026-09-13T00:00:00',
      to: '2026-09-20T00:00:00',
    })
    expect(normalizeRange('2026-09-20T00:00', '2026-09-20T00:00').from).toBe('2026-09-20T00:00:00')
    expect(() => normalizeRange('2026-09-21T00:00', '2026-09-20T00:00')).toThrow()
    expect(() => normalizeRange('2026-01-01T00:00', '2026-09-20T00:00')).toThrow()
  })
  it('accepts Jackson non-null void envelopes; anonymous login never sends token or invalidates identity', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, code: 200 }), {
        headers: { 'content-type': 'application/json' },
      }),
    )
    const event = vi.fn()
    const request = createApiClient({
      fetch: fetcher,
      locale: () => 'zh-CN',
      token: () => ({ tokenName: 'ijob-token', tokenValue: 'test-only' }),
      revision: () => 0,
      onAccessError: event,
    })
    expect(await request('/api/auth/logout', { method: 'POST' })).toBeUndefined()
    fetcher.mockResolvedValue(
      new Response(JSON.stringify({ success: false, code: 401 }), {
        status: 401,
        headers: { 'content-type': 'application/json' },
      }),
    )
    await expect(
      request('/api/auth/login', { method: 'POST', anonymous: true }),
    ).rejects.toMatchObject({ kind: 'unauthorized' })
    expect((fetcher.mock.calls[1]?.[1]?.headers as Headers).has('ijob-token')).toBe(false)
    expect(event).not.toHaveBeenCalled()
  })
})
