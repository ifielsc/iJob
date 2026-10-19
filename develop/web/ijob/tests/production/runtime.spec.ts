// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'
test('production runtime and notification record chunks load with exact identifiers', async ({
  page,
}) => {
  const id = '9223372036854775807',
    errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const instance = {
    id,
    rootInstanceId: id,
    targetType: 'TASK',
    targetId: '9007199254740993',
    status: 'SUCCESS',
  }
  await page.route(/\/api\/(auth|instances|notification-deliveries)(?:[/?]|$)/, async (route) => {
    const path = new URL(route.request().url()).pathname
    const data =
      path === '/api/auth/me'
        ? {
            loginId: 'prod-run',
            username: 'admin',
            displayName: 'Admin',
            roleCode: 'ADMIN',
            permissions: ['SUMMARY_READ', 'SENSITIVE_READ'],
            mustChangePassword: false,
          }
        : path.endsWith('/tree')
          ? { rootInstanceId: id, nodes: [instance], gatewayDecisions: [], maxNodes: 2000 }
          : path === '/api/notification-deliveries'
            ? { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 }
            : instance
    await route.fulfill({ json: { success: true, code: 200, data } })
  })
  await page.goto('/runs/instances/' + id)
  await expect(page.locator('.run-node')).toContainText(id)
  await page.getByRole('link', { name: '通知管理', exact: true }).click()
  await page.getByRole('tab', { name: '发送记录', exact: true }).click()
  await expect(
    page.getByRole('tabpanel', { name: '发送记录', exact: true }).getByRole('status'),
  ).toHaveText('暂无记录')
  expect(errors).toEqual([])
})

test('production schedule editor and business-time preview load', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      const data =
        path === '/api/auth/me'
          ? {
              loginId: 'prod-schedule',
              displayName: 'Admin',
              username: 'admin',
              roleCode: 'ADMIN',
              permissions: ['DEFINITION_READ', 'CONFIGURATION_WRITE', 'SENSITIVE_READ'],
              mustChangePassword: false,
            }
          : path === '/api/schedules/metadata'
            ? {
                zoneId: 'Asia/Shanghai',
                defaultMisfirePolicy: 'FIRE_ONCE',
                defaultMisfireGraceSec: 60,
                defaultMaxCatchupCount: 10,
                maxCatchupWindowSec: 86400,
                maxPreviewCount: 50,
                maxIntervalSec: 31536000,
                maxCatchupCount: 1000,
                maxVariableCount: 100,
                maxVariableBytes: 65536,
              }
            : path === '/api/schedules/preview'
              ? { items: [{ fireTime: '2031-09-28T09:00:00', calendarAllowed: true }] }
              : { list: [], total: 0 }
      await route.fulfill({ json: { success: true, code: 200, data } })
    },
  )
  await page.goto('/schedules')
  await page.getByRole('button', { name: '新增调度', exact: true }).click()
  await page.locator('#schedule-cronExpr').fill('0 0 9 * * *')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.locator('.schedule-preview-table')).toContainText('2031-09-28 09:00:00')
  await expect(page.locator('.parameter-table-compact')).toBeVisible()
  await page.getByRole('button', { name: '高级 JSON', exact: true }).click()
  const json = page.getByRole('textbox', { name: '高级 JSON', exact: true })
  await json.fill('{"region":"cn"}')
  await page.getByRole('button', { name: '表单编辑', exact: true }).click()
  await expect(page.locator('.parameter-table-compact')).toContainText('region')
  expect(errors).toEqual([])
})
