// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'
import type { Locator } from '@playwright/test'

async function layer(locator: Locator) {
  return locator.evaluate((element) => {
    let rank = 0
    for (let current: Element | null = element; current; current = current.parentElement) {
      rank = Math.max(rank, Number(getComputedStyle(current).zIndex) || 0)
    }
    return rank
  })
}

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    for (const width of [1366, 390, 320]) {
      test(`independent overlay directives ${theme}/${locale}/${width}`, async ({ page }, info) => {
        const en = locale === 'en-US'
        const errors: string[] = [],
          writes: string[] = []
        page.on('pageerror', (error) => errors.push(error.message))
        await page.addInitScript(
          (preferences) =>
            localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(preferences)),
          { theme, locale },
        )
        await page.route('**/api/**', (route) => {
          const request = route.request()
          const path = new URL(request.url()).pathname
          if (!path.startsWith('/api/')) return route.continue()
          if (request.method() !== 'GET') writes.push(request.url())
          const data =
            path === '/api/auth/me'
              ? {
                  loginId: 'user:admin',
                  username: 'admin',
                  displayName: 'Admin',
                  roleCode: 'ADMIN',
                  permissions: ['USER_MANAGE'],
                  mustChangePassword: false,
                }
              : path === '/api/users'
                ? { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 }
                : []
          return route.fulfill({ json: { success: true, code: 200, data } })
        })
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/users')
        const open = page.getByRole('button', { name: en ? 'New user' : '新增用户', exact: true })
        await open.click()
        const drawer = page.locator('.users-drawer')
        await drawer.locator('#user-username').fill('directive-draft')
        const role = drawer.locator('#user-role')
        await role.click()
        const menu = page.locator('.n-base-select-menu:visible')
        await expect(menu).toBeVisible()
        const firstRank = await layer(menu)
        expect(firstRank).toBeGreaterThan(await layer(drawer))

        // A drag beginning in the teleported menu must not act as an outside click.
        const start = await menu.getByRole('option').first().boundingBox()
        const end = await drawer.locator('#user-username').boundingBox()
        await page.mouse.move(start!.x + start!.width / 2, start!.y + start!.height / 2)
        await page.mouse.down()
        await page.mouse.move(end!.x + 8, end!.y + 8)
        await page.mouse.up()
        await expect(menu).toBeVisible()
        await drawer.locator('#user-username').click()
        await expect(menu).toHaveCount(0)
        await role.click()
        await expect(menu).toBeVisible()
        expect(await layer(menu)).toBeGreaterThan(firstRank)
        await page.keyboard.press('Escape')
        await expect(menu).toHaveCount(0)
        await expect(drawer).toBeVisible()
        await expect(drawer.locator('#user-username')).toHaveValue('directive-draft')

        await drawer.getByRole('button', { name: en ? 'Close editor' : '关闭编辑' }).click()
        const dialog = page.locator('.n-dialog')
        await expect(dialog).toBeVisible()
        expect(await layer(dialog)).toBeGreaterThan(await layer(drawer))
        await dialog.getByRole('button', { name: en ? 'Cancel' : '取消', exact: true }).click()
        await expect(dialog).toHaveCount(0)
        await expect(drawer.locator('#user-username')).toHaveValue('directive-draft')
        await expect
          .poll(() => drawer.evaluate((element) => element.scrollWidth <= element.clientWidth + 1))
          .toBe(true)
        await page.screenshot({
          path: info.outputPath('guarded-draft.png'),
          animations: 'disabled',
        })
        await drawer.getByRole('button', { name: en ? 'Close editor' : '关闭编辑' }).click()
        await dialog.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }).click()
        await expect(drawer).toHaveCount(0)
        await expect(open).toBeFocused()
        expect(writes).toEqual([])
        expect(errors).toEqual([])
      })
    }
