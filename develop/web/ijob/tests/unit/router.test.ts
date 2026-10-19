// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createRuntime } from '../../src/app/runtime'
import { installAccessGuard } from '../../src/app/router'
import { queryClient } from '../../src/shared/api/query'

describe('direct URL protection', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    queryClient.clear()
  })
  it.each([
    { permissions: ['USER_MANAGE'], mustChangePassword: false, expected: '/users' },
    { permissions: ['SUMMARY_READ'], mustChangePassword: false, expected: '/?access=forbidden' },
    {
      permissions: ['USER_MANAGE'],
      mustChangePassword: true,
      expected: '/change-password',
    },
  ])('checks effective permissions and mandatory change: $expected', async (row) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            code: 200,
            data: {
              loginId: 'user:test',
              username: 'test',
              displayName: 'test',
              roleCode: 'ADMIN',
              permissions: row.permissions,
              mustChangePassword: row.mustChangePassword,
            },
          }),
          { headers: { 'content-type': 'application/json' } },
        ),
      ),
    )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'foundation', component: { template: '<div />' } },
        { path: '/change-password', name: 'change-password', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: { template: '<div />' } },
        { path: '/users', component: { template: '<div />' }, meta: { permission: 'USER_MANAGE' } },
      ],
    })
    installAccessGuard(router, createRuntime())
    await router.push('/users')
    expect(router.currentRoute.value.fullPath).toBe(row.expected)
    vi.unstubAllGlobals()
  })
})
