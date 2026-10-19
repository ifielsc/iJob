// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it, vi } from 'vitest'
import { createApiClient } from '../../src/shared/api/client'
import type { AppLocale } from '../../src/shared/preferences/preferences'

const envelope = (data: unknown, extra = {}) => ({ success: true, code: 200, data, ...extra })
const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json' } })

describe('HTTP contract', () => {
  it('sends dynamic token header and language, omits cookies, preserves string IDs', async () => {
    let locale: AppLocale = 'zh-CN'
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(response(envelope({ id: '9223372036854775807' })))
    const request = createApiClient({
      fetch: fetcher,
      locale: () => locale,
      token: () => ({ tokenName: 'ijob-token', tokenValue: 'test-token' }),
      revision: () => 1,
      onAccessError: vi.fn(),
    })
    expect(await request('/api/users')).toEqual({ id: '9223372036854775807' })
    const init = fetcher.mock.calls[0]![1]!
    expect((init.headers as Headers).get('ijob-token')).toBe('test-token')
    expect((init.headers as Headers).get('Authorization')).toBeNull()
    expect(init.credentials).toBe('omit')
    expect((init.headers as Headers).get('Accept-Language')).toBe('zh-CN')
    locale = 'en-US'
    fetcher.mockResolvedValue(response(envelope(null)))
    await request('/api/auth/logout', { method: 'POST' })
    expect((fetcher.mock.calls[1]![1]!.headers as Headers).get('Accept-Language')).toBe('en-US')
  })
  it.each([
    [401, null, 'unauthorized'],
    [403, null, 'forbidden'],
    [403, 'IJOB-SECURITY-0405', 'passwordRequired'],
    [409, 'IJOB-SECURITY-0409', 'conflict'],
  ] as const)('classifies %s / %s without retrying writes', async (status, code, kind) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      response(
        envelope(null, {
          success: false,
          code: status,
          errorCode: code,
          messageKey: 'error.security.conflict',
          messageArgs: [],
          traceId: 'trace-test',
        }),
        status,
      ),
    )
    const event = vi.fn()
    const request = createApiClient({
      fetch: fetcher,
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => 1,
      onAccessError: event,
    })
    await expect(
      request('/api/users', { method: 'POST', body: { username: 'test' } }),
    ).rejects.toMatchObject({ kind, envelope: { traceId: 'trace-test', messageArgs: [] } })
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(event).toHaveBeenCalledTimes(status === 409 ? 0 : 1)
  })
  it('handles legacy HTTP 200 business errors', async () => {
    const request = createApiClient({
      fetch: vi.fn().mockResolvedValue(response(envelope(null, { success: false, code: 409 }))),
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => 1,
      onAccessError: vi.fn(),
    })
    await expect(request('/api/users')).rejects.toMatchObject({ kind: 'conflict' })
  })
  it('rejects SPA fallback HTML and malformed JSON', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        new Response('<html>fallback</html>', { headers: { 'content-type': 'text/html' } }),
      )
    const request = createApiClient({
      fetch: fetcher,
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => 0,
      onAccessError: vi.fn(),
    })
    await expect(request('/api/auth/me')).rejects.toMatchObject({ kind: 'protocol' })
    fetcher.mockResolvedValue(
      new Response('{bad', { headers: { 'content-type': 'application/json' } }),
    )
    await expect(request('/api/auth/me')).rejects.toMatchObject({ kind: 'protocol' })
  })
  it('does not send tokens to arbitrary URLs', async () => {
    const fetcher = vi.fn()
    const request = createApiClient({
      fetch: fetcher,
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => 0,
      onAccessError: vi.fn(),
    })
    await expect(request('https://example.com/api/users')).rejects.toMatchObject({
      kind: 'protocol',
    })
    await expect(request('/api/../outside')).rejects.toMatchObject({ kind: 'protocol' })
    expect(fetcher).not.toHaveBeenCalled()
  })
  it('does not invalidate a new session when an old request returns 401', async () => {
    let revision = 1
    const event = vi.fn()
    const request = createApiClient({
      fetch: vi.fn(async () => {
        revision = 2
        return response(envelope(null, { success: false }), 401)
      }),
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => revision,
      onAccessError: event,
    })
    await expect(request('/api/users')).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(event).not.toHaveBeenCalled()
  })
  it('discards old identity data after a session change', async () => {
    let revision = 1
    const request = createApiClient({
      fetch: vi.fn(async () => {
        revision = 2
        return response(envelope({ secret: 'old-user' }))
      }),
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => revision,
      onAccessError: vi.fn(),
    })
    await expect(request('/api/users')).rejects.toMatchObject({ name: 'AbortError' })
  })
  it('supports timeouts without replaying an uncertain operation', async () => {
    const fetcher = vi.fn<typeof fetch>(
      (_input, init) =>
        new Promise((_resolve, reject) => {
          init!.signal!.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError')),
          )
        }),
    )
    const request = createApiClient({
      fetch: fetcher,
      timeoutMs: 5,
      locale: () => 'zh-CN',
      token: () => null,
      revision: () => 1,
      onAccessError: vi.fn(),
    })
    await expect(request('/api/trigger', { method: 'POST' })).rejects.toMatchObject({
      kind: 'timeout',
    })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
