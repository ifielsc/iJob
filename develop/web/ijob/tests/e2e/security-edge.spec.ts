// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
const me = {
  loginId: 'user:test',
  username: 'test',
  displayName: 'Test',
  roleCode: 'ADMIN',
  permissions: ['USER_MANAGE', 'AUDIT_READ'],
  mustChangePassword: false,
}
test('403 clears protected content and exposes a translated error without retry', async ({
  page,
}) => {
  let calls = 0
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({ json: { success: true, code: 200, data: me } }),
  )
  await page.route('**/api/roles', (r) =>
    r.fulfill({ json: { success: true, code: 200, data: [] } }),
  )
  await page.route('**/api/users?*', (r) => {
    calls++
    return r.fulfill({
      status: 403,
      json: {
        success: false,
        code: 403,
        errorCode: 'IJOB-SECURITY-0403',
        traceId: 'forbidden-trace',
      },
    })
  })
  await page.goto('/users')
  await expect(page.getByRole('alert').first()).toContainText('forbidden-trace')
  await expect(page.getByRole('button', { name: '新增用户', exact: true })).toBeDisabled()
  await expect(page.locator('tbody tr')).toHaveCount(0)
  expect(calls).toBe(1)
})
test('wrong login stays on login, clears password, and sends selected language', async ({
  page,
}) => {
  let calls = 0
  await page.route('**/api/auth/login', (r) => {
    calls++
    expect(r.request().headers()['accept-language']).toBe('en-US')
    return r.fulfill({
      status: 401,
      json: {
        success: false,
        code: 401,
        messageKey: 'error.auth.invalid-credentials',
        traceId: 'login-trace',
      },
    })
  })
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({ status: 401, json: { success: false, code: 401 } }),
  )
  await page.goto('/login')
  await page.getByRole('button', { name: '语言: 中文', exact: true }).click()
  await page.getByLabel('Username', { exact: true }).fill('test_user')
  await page.getByLabel('Password', { exact: true }).fill('test-only-wrong-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Invalid username or password')
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('Username', { exact: true })).toHaveValue('test_user')
  expect(calls).toBe(1)
})
test('empty users, roles and audits remain accessible on narrow English screen', async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() =>
    localStorage.setItem(
      'ijob:preferences:v1:guest',
      JSON.stringify({ theme: 'dark', locale: 'en-US' }),
    ),
  )
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({ json: { success: true, code: 200, data: me } }),
  )
  await page.route('**/api/users?*', (r) =>
    r.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 },
      },
    }),
  )
  await page.route('**/api/roles', (r) =>
    r.fulfill({ json: { success: true, code: 200, data: [] } }),
  )
  await page.route('**/api/audits?*', (r) =>
    r.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 },
      },
    }),
  )
  await page.goto('/users')
  await expect(page.getByText('No records', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'New user', exact: true }).click()
  await expect(page.locator('.n-drawer')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({
    path: info.outputPath('mobile-user-drawer.png'),
    animations: 'disabled',
    fullPage: true,
  })
  await page.locator('.n-drawer-footer').getByRole('button', { name: 'Cancel' }).click()
  await page.getByRole('link', { name: 'Operation audit', exact: true }).click()
  await expect(page).toHaveURL(/\/audits$/)
  await expect(page.getByRole('heading', { name: 'Operation audit', exact: true })).toBeVisible()
  await expect(page.getByText('No records', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({
    path: info.outputPath('mobile-audit.png'),
    animations: 'disabled',
    fullPage: true,
  })
})
