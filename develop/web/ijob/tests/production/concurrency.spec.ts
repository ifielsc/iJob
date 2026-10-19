// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'

test('production concurrency chunk loads metadata and compact resource form', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      const data =
        path === '/api/auth/me'
          ? {
              loginId: 'production-concurrency',
              username: 'admin',
              displayName: 'Admin',
              roleCode: 'ADMIN',
              mustChangePassword: false,
              permissions: ['DEFINITION_READ', 'CONFIGURATION_WRITE'],
            }
          : path === '/api/concurrency/metadata'
            ? {
                plugins: [{ type: 'SQL', activeInstanceCount: 0 }],
                minConcurrency: 1,
                maxConcurrency: 10000,
                permitWaitTimeoutMillis: 300000,
                retryIntervalMillis: 100,
                maxRetryIntervalMillis: 2000,
                retryJitterMillis: 100,
                configurationSource: 'API_NODE',
              }
            : []
      await route.fulfill({ json: { success: true, code: 200, data } })
    },
  )
  await page.goto('/concurrency')
  await page.getByRole('button', { name: '新增资源', exact: true }).click()
  await expect(page.locator('#concurrency-code')).toHaveValue(/^con-/)
  await expect(page.locator('#concurrency-maximum')).toHaveValue('')
  await expect(page.locator('.concurrency-enabled .n-switch')).toHaveAttribute(
    'aria-checked',
    'true',
  )
  await expect(page.locator('#concurrency-name')).toHaveCSS('font-size', '13px')
  expect(errors).toEqual([])
})
