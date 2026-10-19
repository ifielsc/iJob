// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'

test('production lazy authentication, user drawer and audit routes load correctly', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          loginId: 'user:production-test',
          username: 'test',
          displayName: 'Test',
          roleCode: 'ADMIN',
          permissions: [
            'USER_MANAGE',
            'AUDIT_READ',
            'DEFINITION_READ',
            'SENSITIVE_READ',
            'CONFIGURATION_WRITE',
          ],
          mustChangePassword: false,
        },
      },
    }),
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
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  await page.goto('/users')
  await page.getByRole('button', { name: '新增用户', exact: true }).click()
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消' }).click()
  await page.getByRole('link', { name: '操作审计', exact: true }).click()
  await expect(page.getByText('暂无记录', { exact: true })).toBeVisible()
  for (const resource of ['tasks', 'resources'])
    await page.route('**/api/' + resource + '?*', (route) =>
      route.fulfill({ json: { success: true, data: { list: [], total: 0 } } }),
    )
  await page.getByRole('link', { name: '任务管理', exact: true }).click()
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  await page.locator('dialog.sql-large-editor .cm-content').fill('SELECT 1;')
  await page.getByRole('button', { name: '完成编辑', exact: true }).click()
  await expect(page.locator('#task-script')).toContainText('SELECT 1;')
  expect(errors).toEqual([])
})

test('production build starts without backend and omits the development lab', async ({
  page,
}, info) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', {
      get() {
        throw new Error('Storage blocked')
      },
    })
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '工作空间', exact: true })).toBeVisible()
  await expect(page.locator('.identity-error')).toBeVisible()
  await expect(page.locator('.module-link')).toHaveCount(0)
  await page.getByRole('button', { name: /主题.*浅色/ }).click()
  await page.getByRole('button', { name: /语言.*中文/ }).click()
  await expect(page.getByRole('heading', { name: 'Workspace', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /Theme.*Dark/ })).toBeVisible()
  await expect(page.locator('.identity-error')).toBeVisible()
  await page.screenshot({
    path: info.outputPath('production-disconnected.png'),
    animations: 'disabled',
    fullPage: true,
  })
  await page.goto('/development/components')
  await expect(page).toHaveURL(/\/$/)
  await expect(page.locator('#verification-draft')).toHaveCount(0)
  expect(errors).toEqual([])
})
