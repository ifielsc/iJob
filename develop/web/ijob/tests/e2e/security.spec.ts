// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
const admin = {
  loginId: 'user:admin',
  username: 'admin',
  displayName: 'Platform admin',
  roleCode: 'ADMIN',
  permissions: ['SUMMARY_READ', 'USER_MANAGE', 'AUDIT_READ', 'SELF_PASSWORD_CHANGE'],
  mustChangePassword: false,
}
const account = {
  id: '9223372036854775807',
  loginId: 'user:other',
  username: 'reader01',
  displayName: 'Reader',
  roleCode: 'VIEWER',
  enabled: true,
  mustChangePassword: false,
  revision: '9007199254740999',
}
const audit = {
  id: '9223372036854775806',
  occurredAt: '2026-09-20T12:34:56.123',
  actorLoginId: 'user:admin',
  actorName: 'Platform admin',
  actionCode: 'ACCOUNT_PROFILE_UPDATE',
  targetType: 'ACCOUNT',
  targetId: account.id,
  resultCode: 'SUCCESS',
  traceId: 'trace-security-fixture-001',
  changedFields: 'displayName',
}
const results = (list: unknown[]) => ({
  list,
  total: list.length,
  pageNum: 1,
  pageSize: 20,
  pages: list.length ? 1 : 0,
})
async function setup(page: Page) {
  await page.route(/\/api\/(?:auth|users|roles|audits)(?:[/?]|$)/, (route) => {
    const url = new URL(route.request().url())
    const data =
      url.pathname === '/api/auth/me'
        ? admin
        : url.pathname === '/api/users'
          ? results([account])
          : url.pathname === '/api/roles'
            ? [
                { roleCode: 'ADMIN', permissions: admin.permissions },
                { roleCode: 'OPERATOR', permissions: ['SUMMARY_READ', 'EXECUTION_CONTROL'] },
                { roleCode: 'VIEWER', permissions: ['SUMMARY_READ'] },
              ]
            : url.pathname === '/api/audits'
              ? results([audit])
              : url.pathname === `/api/audits/${audit.id}`
                ? audit
                : null
    return route.fulfill({ json: { success: true, code: 200, data } })
  })
}
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`approved security surfaces ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US',
        errors: string[] = []
      page.on('pageerror', (error) => errors.push(error.message))
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.goto('/login')
      await expect(page.getByRole('heading', { name: en ? 'Sign in' : '登录' })).toBeVisible()
      await page.screenshot({
        path: info.outputPath('login.png'),
        animations: 'disabled',
        fullPage: true,
      })
      await page.route('**/api/auth/me', (route) =>
        route.fulfill({
          json: { success: true, code: 200, data: { ...admin, mustChangePassword: true } },
        }),
      )
      await page.goto('/change-password')
      await expect(
        page.getByRole('heading', {
          name: en ? 'Change your initial password' : '首次登录，请修改密码',
        }),
      ).toBeVisible()
      await page.screenshot({
        path: info.outputPath('first-password.png'),
        animations: 'disabled',
        fullPage: true,
      })
      await page.unroute('**/api/auth/me')
      await page.goto('/users')
      await expect(page.getByRole('cell', { name: 'reader01', exact: true })).toBeVisible()
      await page.screenshot({
        path: info.outputPath('users.png'),
        animations: 'disabled',
        fullPage: true,
      })
      await page.getByRole('button', { name: en ? 'New user' : '新增用户', exact: true }).click()
      const drawer = page.locator('.n-drawer')
      await expect(drawer).toBeVisible()
      await drawer.getByLabel(en ? 'Username' : '用户名', { exact: false }).fill('draft_user')
      await drawer
        .getByLabel(en ? 'Display name' : '显示名称', { exact: false })
        .fill('Unsaved 草稿')
      await drawer
        .locator('input')
        .nth(2)
        .evaluate((el: HTMLInputElement) => el.setSelectionRange(2, 5))
      expect(
        await drawer.getByLabel(en ? 'Initial password' : '初始密码', { exact: true }).inputValue(),
      ).toBe('')
      await expect(drawer.locator('#user-role')).toBeVisible()
      await expect(drawer.getByText(en ? 'Viewer' : '只读用户', { exact: true })).toBeVisible()
      await expect(drawer.getByLabel(en ? 'Enable' : '启用', { exact: true })).toBeChecked()
      await expect(
        drawer.getByRole('button', { name: new RegExp(en ? '^Theme:' : '^主题:') }),
      ).toHaveCount(0)
      await expect(
        drawer.getByRole('button', { name: new RegExp(en ? '^Language:' : '^语言:') }),
      ).toHaveCount(0)
      await expect(drawer.locator('input').nth(0)).toHaveValue('draft_user')
      await expect(drawer.locator('input').nth(2)).toHaveValue('Unsaved 草稿')
      expect(
        await drawer
          .locator('input')
          .nth(2)
          .evaluate((el: HTMLInputElement) => [el.selectionStart, el.selectionEnd]),
      ).toEqual([2, 5])
      await page.screenshot({
        path: info.outputPath('new-user-draft.png'),
        animations: 'disabled',
        fullPage: true,
      })
      await page.goto('/audits')
      await expect(
        page.getByRole('cell', { name: 'ACCOUNT_PROFILE_UPDATE', exact: true }),
      ).toBeVisible()
      await page.screenshot({
        path: info.outputPath('audits.png'),
        animations: 'disabled',
        fullPage: true,
      })
      await page.getByRole('button', { name: new RegExp(audit.id) }).click()
      await expect(page.locator('.detail-list')).toContainText(audit.traceId)
      await page.screenshot({
        path: info.outputPath('audit-detail.png'),
        animations: 'disabled',
        fullPage: true,
      })
      expect(errors).toEqual([])
    })
  }
test('409 preserves draft, explicit latest adoption sends the new string revision only once', async ({
  page,
}) => {
  await setup(page)
  let revision = account.revision,
    writes = 0
  await page.route('**/api/users?*', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: results([
          {
            ...account,
            displayName: revision === account.revision ? 'Reader' : 'Server edit',
            revision,
          },
        ]),
      },
    }),
  )
  await page.route(`**/api/users/${account.id}/profile`, async (route) => {
    writes++
    const body = route.request().postDataJSON()
    if (writes === 1) {
      expect(body.expectedRevision).toBe(account.revision)
      revision = '9007199254741000'
      return route.fulfill({
        status: 409,
        json: {
          success: false,
          code: 409,
          errorCode: 'IJOB-SECURITY-0409',
          messageKey: 'error.security.conflict',
          traceId: 'conflict-trace',
        },
      })
    }
    expect(body).toEqual({ expectedRevision: revision, displayName: 'Reviewed edit' })
    return route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { ...account, revision, displayName: body.displayName },
      },
    })
  })
  await page.goto('/users')
  await page.getByRole('button', { name: '编辑 reader01', exact: true }).click()
  await page.getByLabel('显示名称', { exact: false }).fill('My draft')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByLabel('显示名称', { exact: false })).toHaveValue('My draft')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('.users-drawer').getByRole('alert')).toContainText('conflict-trace')
  await page.getByRole('button', { name: '读取最新记录', exact: true }).click()
  await expect(page.locator('.detail-list')).toContainText('Server edit')
  await expect(page.getByLabel('显示名称', { exact: false })).toHaveValue('My draft')
  await page.getByRole('button', { name: '采用最新记录并重新编辑' }).click()
  await page.getByLabel('显示名称', { exact: false }).fill('Reviewed edit')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes).toBe(2)
})
test('create minimal defaults; password never goes into browser storage', async ({ page }) => {
  await setup(page)
  let body: unknown
  await page.route('**/api/users', async (route) => {
    body = route.request().postDataJSON()
    return route.fulfill({ json: { success: true, code: 200, data: account } })
  })
  await page.goto('/users')
  await page.getByRole('button', { name: '新增用户', exact: true }).click()
  await page.getByLabel('用户名', { exact: false }).fill('new_reader')
  await page.getByLabel('初始密码', { exact: true }).fill('Test-only-password-123')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(body).toEqual({
    username: 'new_reader',
    password: 'Test-only-password-123',
    displayName: '',
    roleCode: 'VIEWER',
    enabled: true,
  })
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toContain('Test-only-password')
})
test('audit UTC exact filters and range validation never send invalid requests', async ({
  page,
}) => {
  await setup(page)
  const requests: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('/api/audits?')) requests.push(r.url())
  })
  await page.goto('/audits')
  await expect(
    page.getByRole('cell', { name: 'ACCOUNT_PROFILE_UPDATE', exact: true }),
  ).toBeVisible()
  await page.getByLabel('开始时间', { exact: true }).fill('2026-01-01T00:00')
  await page.getByLabel('结束时间', { exact: true }).fill('2026-09-20T00:00')
  const count = requests.length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('90 天')
  expect(requests).toHaveLength(count)
  await page.getByLabel('开始时间', { exact: true }).fill('2026-09-19T00:00')
  await page.getByText('高级筛选（精确匹配）', { exact: true }).click()
  await page.getByLabel('操作人登录标识', { exact: true }).fill('user:中文')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect
    .poll(() =>
      requests.some((url) => new URL(url).searchParams.get('actorLoginId') === 'user:中文'),
    )
    .toBe(true)
  const last = new URL(requests.at(-1)!)
  expect(last.searchParams.get('from')).toBe('2026-09-19T00:00:00')
  expect(last.searchParams.get('pageSize')).toBe('20')
})
test('forbidden deep link does not fetch protected list', async ({ page }) => {
  await setup(page)
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({
      json: { success: true, code: 200, data: { ...admin, permissions: ['SUMMARY_READ'] } },
    }),
  )
  let usersRequests = 0
  page.on('request', (r) => {
    if (r.url().includes('/api/users')) usersRequests++
  })
  await page.goto('/users')
  await expect(page.getByRole('alert')).toContainText('访问权限')
  expect(usersRequests).toBe(0)
})
test('401 during a protected query closes business screen and does not retry', async ({ page }) => {
  await setup(page)
  let calls = 0
  await page.route('**/api/users?*', (route) => {
    calls++
    return route.fulfill({ status: 401, json: { success: false, code: 401 } })
  })
  await page.goto('/users')
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  await expect(page.locator('.table-scroll')).toHaveCount(0)
  expect(calls).toBe(1)
})
