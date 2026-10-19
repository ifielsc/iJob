// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect, type Page } from '@playwright/test'

async function emptyApi(page: Page, locale: string, theme: string) {
  await page.addInitScript(
    ({ locale, theme }) =>
      localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ locale, theme })),
    { locale, theme },
  )
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    let data: unknown = { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 }
    if (path === '/api/auth/me')
      data = {
        loginId: 'empty-admin',
        username: 'empty-admin',
        displayName: 'Empty test',
        roleCode: 'ADMIN',
        permissions: [
          'SUMMARY_READ',
          'DEFINITION_READ',
          'SENSITIVE_READ',
          'CONFIGURATION_WRITE',
          'EXECUTION_CONTROL',
          'APPROVAL_DECIDE',
          'AUDIT_READ',
          'USER_MANAGE',
        ],
        mustChangePassword: false,
      }
    if (path === '/api/calendars/context') data = { today: '2026-10-17', timezone: 'UTC' }
    if (path.endsWith('/metadata')) data = { scopes: [], strategies: [], pluginTypes: [] }
    await route.fulfill({ json: { success: true, code: 200, data } })
  })
}

for (const [locale, theme, width] of [
  ['zh-CN', 'light', 1366],
  ['en-US', 'dark', 1366],
  ['zh-CN', 'light', 390],
] as const) {
  test(`empty lists are centered and consistent ${locale} ${theme} ${width}`, async ({
    page,
  }, info) => {
    await page.setViewportSize({ width, height: 900 })
    await emptyApi(page, locale, theme)
    const routes = [
      '/tasks',
      '/jobs',
      '/schedules',
      '/parameters',
      '/resources/databases',
      '/resources/services',
      '/notifications',
      '/notifications/mail-channels',
      '/events/approvals',
      '/audits',
      '/calendars',
      '/runs',
    ]
    for (const route of routes) {
      await page.goto(route)
      const empty = page.locator('.list-empty:not(.list-empty-compact)').first()
      await expect(empty, route).toBeVisible()
      const box = await empty.boundingBox()
      expect(box!.height, route).toBeGreaterThanOrEqual(180)
      expect((await empty.textContent())!.trim().length, route).toBeGreaterThan(0)
      const alignment = await empty.evaluate((element) => {
        const box = element.getBoundingClientRect(),
          text = element.querySelector('p')!.getBoundingClientRect()
        return {
          x: Math.abs(box.x + box.width / 2 - text.x - text.width / 2),
          y: Math.abs(box.y + box.height / 2 - text.y - text.height / 2),
        }
      })
      expect(alignment.x, route).toBeLessThan(2)
      expect(alignment.y, route).toBeLessThan(2)
      const textBox = await empty.locator('p').boundingBox()
      expect(textBox!.x, route).toBeGreaterThanOrEqual(0)
      expect(textBox!.x + textBox!.width, route).toBeLessThanOrEqual(width)
      if (route === '/tasks' || route === '/notifications/mail-channels')
        await page.screenshot({
          path: info.outputPath(route.replaceAll('/', '-') + '.png'),
          fullPage: true,
        })
    }
  })
}

test('loading and errors are not displayed as an empty list', async ({ page }) => {
  await emptyApi(page, 'zh-CN', 'light')
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/tasks?*', async (route) => {
    await gate
    await route.fulfill({ status: 500, json: { success: false, code: 500 } })
  })
  await page.goto('/tasks')
  await expect(page.locator('.list-empty')).toHaveCount(0)
  release()
  await expect(page.locator('.request-error, [role="alert"]').first()).toBeVisible()
  await expect(page.locator('.list-empty')).toHaveCount(0)
})
