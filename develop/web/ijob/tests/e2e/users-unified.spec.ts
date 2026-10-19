// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
const admin = {
  loginId: 'user:admin',
  username: 'admin',
  displayName: 'Admin',
  roleCode: 'ADMIN',
  permissions: ['SUMMARY_READ', 'USER_MANAGE', 'SELF_PASSWORD_CHANGE'],
  mustChangePassword: false,
}
const fixtures = [
  {
    id: '1',
    loginId: admin.loginId,
    username: 'admin',
    displayName: 'Admin',
    roleCode: 'ADMIN',
    enabled: true,
    mustChangePassword: false,
    revision: '0',
  },
  {
    id: '2',
    loginId: 'user:alice',
    username: 'alice',
    displayName: 'Alice',
    roleCode: 'VIEWER',
    enabled: true,
    mustChangePassword: false,
    revision: '0',
  },
  {
    id: '3',
    loginId: 'user:bob',
    username: 'bob',
    displayName: 'Bob',
    roleCode: 'OPERATOR',
    enabled: false,
    mustChangePassword: false,
    revision: '0',
  },
]
async function setup(page: Page) {
  let rows = structuredClone(fixtures)
  const writes: string[] = [],
    searches: URL[] = []
  await page.route('**/api/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
    if (path === '/api/auth/me') return ok(admin)
    if (path === '/api/roles') return ok([])
    if (path === '/api/users' && request.method() === 'GET') {
      searches.push(url)
      const name = url.searchParams.get('name') ?? '',
        role = url.searchParams.get('roleCode'),
        enabled = url.searchParams.get('enabled')
      const found = rows.filter(
        (row) =>
          (row.username + ' ' + row.displayName).toLowerCase().includes(name.toLowerCase()) &&
          (!role || row.roleCode === role) &&
          (!enabled || String(row.enabled) === enabled),
      )
      return ok({ list: found, total: found.length, pageNum: 1, pageSize: 20, pages: 1 })
    }
    if (request.method() === 'DELETE') {
      writes.push(path)
      if (path.endsWith('/3'))
        return route.fulfill({
          status: 409,
          json: { success: false, code: 409, messageKey: 'error.security.conflict' },
        })
      rows = rows.filter((row) => path !== '/api/users/' + row.id)
      return ok(null)
    }
    if (path.endsWith('/profile')) {
      writes.push(path)
      return ok(null)
    }
    return ok(null)
  })
  await page.goto('/users')
  await expect(page.getByRole('button', { name: '编辑 alice', exact: true })).toBeVisible()
  return { writes, searches }
}

test('user filters use the query, selection clears, and bulk failure preserves the failed record', async ({
  page,
}, info) => {
  const { writes, searches } = await setup(page)
  await expect(page.getByRole('checkbox', { name: '选择用户 admin', exact: true })).toBeDisabled()
  await page.getByRole('checkbox', { name: '选择用户 alice', exact: true }).check()
  await page.getByLabel('名称', { exact: true }).fill('Bob')
  await page.locator('#users-status').click()
  await page.getByRole('option', { name: '已停用', exact: true }).click()
  await page.locator('#users-filter-role').click()
  await page.getByRole('option', { name: '操作员', exact: true }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('button', { name: '编辑 alice', exact: true })).toHaveCount(0)
  expect(searches.at(-1)!.searchParams.get('enabled')).toBe('false')
  expect(searches.at(-1)!.searchParams.get('roleCode')).toBe('OPERATOR')
  await expect(page.getByRole('button', { name: '批量删除', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('button', { name: '编辑 alice', exact: true })).toBeVisible()
  await page.getByRole('checkbox', { name: '选择当前页可删除用户', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  expect(writes).toEqual([])
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('button', { name: '编辑 alice', exact: true })).toHaveCount(0)
  await expect(page.getByRole('checkbox', { name: '选择用户 bob', exact: true })).toBeChecked()
  expect(writes).toEqual(['/api/users/2', '/api/users/3'])
  await expect(page.locator('.n-message')).toContainText(['已删除 1 个用户', 'bob'])
  await expect(page.locator('.users-list-panel [role="alert"]')).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('bulk-result.png'), animations: 'disabled' })
})

test('user drawer has fixed chrome, guarded close, restored focus and top success message', async ({
  page,
}, info) => {
  await setup(page)
  await page.setViewportSize({ width: 1100, height: 480 })
  await page.getByRole('button', { name: '新增用户', exact: true }).click()
  const drawer = page.locator('.users-drawer'),
    close = drawer.getByRole('button', { name: '关闭编辑' }),
    save = drawer.getByRole('button', { name: '保存', exact: true })
  await expect(drawer.getByRole('button', { name: /主题:|语言:/ })).toHaveCount(0)
  const headerBefore = await close.boundingBox(),
    footerBefore = await save.boundingBox()
  await drawer.getByRole('switch', { name: '启用', exact: true }).scrollIntoViewIfNeeded()
  expect((await close.boundingBox())!.y).toBe(headerBefore!.y)
  expect((await save.boundingBox())!.y).toBe(footerBefore!.y)
  await page.locator('.n-drawer-mask').click({ position: { x: 10, y: 100 } })
  await expect(drawer).toBeVisible()
  await drawer.locator('#user-username').fill('draft-name')
  await close.click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await drawer.locator('#user-role').click()
  await page.keyboard.press('Escape')
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(drawer).toBeVisible()
  await page.keyboard.press('Escape')
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(drawer).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新增用户', exact: true })).toBeFocused()
  await page.getByRole('button', { name: '编辑 alice', exact: true }).click()
  await drawer.locator('#user-display-name').fill('Updated Alice')
  await save.click()
  await expect(drawer).toHaveCount(0)
  await expect(page.locator('.n-message')).toContainText('已保存')
  await expect(page.locator('main > .notice')).toHaveCount(0)
  expect((await page.locator('.n-message').boundingBox())!.y).toBeLessThan(100)
  await page.screenshot({ path: info.outputPath('save-message.png'), animations: 'disabled' })
})

test('actions remain fixed at the right edge during horizontal scrolling', async ({
  page,
}, info) => {
  await setup(page)
  await page.setViewportSize({ width: 700, height: 700 })
  const scroll = page.locator('.users-table-scroll'),
    action = page.getByRole('button', { name: '编辑 alice', exact: true })
  const before = await action.boundingBox()
  await scroll.evaluate((element) => {
    element.scrollLeft = 220
  })
  await expect.poll(async () => (await action.boundingBox())!.x).toBe(before!.x)
  const frame = await scroll.boundingBox(),
    cell = await action.boundingBox()
  expect(cell!.x + cell!.width).toBeLessThan(frame!.x + frame!.width)
  await page.screenshot({ path: info.outputPath('fixed-actions.png'), animations: 'disabled' })
})
