// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupDiagnostics } from '../diagnostics-fixture'
test.use({ viewport: { width: 1512, height: 1100 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test('diagnostics layout ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupDiagnostics(page),
        en = locale === 'en-US',
        errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.goto('/diagnostics')
      await expect(page.getByTestId('diagnostics-count')).toHaveText(en ? '2 shown' : '已显示 2 项')
      await expect(page.locator('.diagnostics-node')).toContainText('API-only')
      await expect(page.locator('h1')).toHaveCSS('font-size', '24px')
      await page.screenshot({
        path: info.outputPath('desktop.png'),
        fullPage: true,
        animations: 'disabled',
      })
      await page
        .getByRole('button', {
          name: en ? 'View evidence for 30471' : '查看 30471 的证据',
          exact: true,
        })
        .click()
      await expect(page.locator('.diagnostics-drawer')).toContainText('9007199254740993')
      await expect(page.locator('.diagnostics-drawer')).toHaveCSS('width', '520px')
      await page
        .locator('.diagnostics-drawer')
        .screenshot({ path: info.outputPath('drawer.png'), animations: 'disabled' })
      await page.keyboard.press('Escape')
      await expect(
        page.getByRole('button', {
          name: en ? 'View evidence for 30471' : '查看 30471 的证据',
          exact: true,
        }),
      ).toBeFocused()
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      await page
        .getByRole('button', {
          name: en ? 'View evidence for 30471' : '查看 30471 的证据',
          exact: true,
        })
        .click()
      await expect(page.locator('.diagnostics-drawer')).toHaveCSS('width', '390px')
      await expect
        .poll(() =>
          page
            .locator('.diagnostics-drawer')
            .evaluate((el) => Math.round(el.getBoundingClientRect().top)),
        )
        .toBe(0)
      await expect(page.locator('.diagnostics-drawer .n-drawer-footer')).toBeVisible()
      await page.screenshot({
        path: info.outputPath('mobile.png'),
        fullPage: false,
        animations: 'disabled',
      })
      expect(state.writes).toEqual([])
      expect(errors).toEqual([])
    })
  }
test('empty checks show no matches and correct category-specific time label', async ({ page }) => {
  const state = await setupDiagnostics(page)
  state.size = 0
  await page.goto('/diagnostics?category=QUEUE_DELAYED&limit=20')
  await expect(page.locator('.list-empty')).toContainText('本次检查未发现命中记录')
  await expect(page.getByRole('columnheader', { name: '可执行时间 (UTC)' })).toBeVisible()
  await expect(page.locator('.diagnostics-explanation')).toContainText('300 秒')
  await expect(page.locator('.list-empty')).toContainText('其他类别')
})
test('capped results state hasMore and larger selection keeps the category', async ({ page }) => {
  const state = await setupDiagnostics(page)
  state.size = 22
  await page.goto('/diagnostics?category=QUEUE_DELAYED&limit=20')
  await expect(page.locator('.diagnostics-more')).toBeVisible()
  await expect(page.locator('tbody tr')).toHaveCount(20)
  await page.locator('#diagnostics-limit').click()
  await page.getByRole('option', { name: '100', exact: true }).click()
  await expect(page.locator('tbody tr')).toHaveCount(22)
  await expect(page.locator('.diagnostics-more')).toHaveCount(0)
  await expect(page).toHaveURL(/category=QUEUE_DELAYED&limit=100/)
  await page.getByRole('button', { name: '刷新检查', exact: true }).click()
  await expect.poll(() => state.reads.length).toBe(3)
})
test('invalid route makes no diagnostic request and resets safely', async ({ page }) => {
  const state = await setupDiagnostics(page)
  await page.goto('/diagnostics?category=OTHER&limit=101')
  await expect(page.getByRole('alert')).toContainText('诊断类别或显示上限无效')
  expect(state.reads).toHaveLength(0)
  await page.getByRole('button', { name: '恢复默认条件' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(2)
})
test('failed refresh hides previous facts and retry recovers', async ({ page }) => {
  const state = await setupDiagnostics(page)
  await page.goto('/diagnostics')
  await expect(page.locator('tbody tr')).toHaveCount(2)
  state.fail = 500
  await page.getByRole('button', { name: '刷新检查', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.locator('tbody tr')).toHaveCount(0)
  await expect(page.locator('.list-empty')).toHaveCount(0)
  state.fail = 0
  await page.getByRole('button', { name: '重试查询' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(2)
})
test('rapid category changes discard stale facts', async ({ page }) => {
  const state = await setupDiagnostics(page)
  await page.goto('/diagnostics')
  await expect(page.locator('tbody tr')).toHaveCount(2)
  state.delay = 600
  await page.getByRole('tab', { name: '队列等待较久' }).click()
  await expect.poll(() => state.reads.length).toBe(2)
  state.delay = 0
  await page.getByRole('tab', { name: '根运行超期' }).click()
  await expect(page.locator('tbody')).toContainText('执行中')
  await page.waitForTimeout(700)
  await expect(page.getByRole('columnheader', { name: '运行截止 (UTC)' })).toBeVisible()
})
test('root drilldown returns to retained diagnostic filters', async ({ page }) => {
  await setupDiagnostics(page)
  await page.goto('/diagnostics?category=LEASE_EXPIRED&limit=50')
  await page.getByRole('button', { name: '查看 30471 的证据', exact: true }).click()
  await page.locator('.diagnostics-drawer').getByRole('link', { name: '查看根运行' }).click()
  await expect(page).toHaveURL(/runs\/instances\/9007199254740993/)
  await page.locator('a[href^="/diagnostics?"]').click()
  await expect(page).toHaveURL(/category=LEASE_EXPIRED&limit=50/)
  await expect(page.locator('tbody tr')).toHaveCount(2)
})
test('work without a root opens its exact trigger record and returns', async ({ page }) => {
  const state = await setupDiagnostics(page)
  state.root = false
  await page.goto('/diagnostics?category=QUEUE_DELAYED&limit=20')
  await page.getByRole('button', { name: '查看 30471 的证据', exact: true }).click()
  await page.locator('.diagnostics-drawer').getByRole('link', { name: '查看触发记录' }).click()
  await expect(page).toHaveURL(/inspectTrigger=30471/)
  await expect(page.locator('.run-drawer')).toContainText('订单同步')
  await page.keyboard.press('Escape')
  await page.getByRole('link', { name: '返回运行诊断', exact: false }).click()
  await expect(page.locator('tbody tr')).toHaveCount(2)
})
test('commands pass request identity to instance details', async ({ page }) => {
  await setupDiagnostics(page)
  await page.goto('/diagnostics?category=COMMAND_EXPIRED&limit=20')
  await page.getByRole('button', { name: '查看 cmd-0 的证据', exact: true }).click()
  await expect(page.locator('.diagnostics-drawer')).toContainText('STOP')
  await page.locator('.diagnostics-drawer').getByRole('link', { name: '查看关联实例' }).click()
  await expect(page).toHaveURL(/requestId=cmd-0/)
})
test('unknown states remain visible and forbidden reads clear facts', async ({ page }) => {
  const state = await setupDiagnostics(page)
  state.unknown = true
  await page.goto('/diagnostics')
  await expect(page.locator('tbody').first()).toContainText('FUTURE_STATUS')
  state.fail = 403
  await page.getByRole('button', { name: '刷新检查', exact: true }).click()
  await expect(page.locator('tbody tr')).toHaveCount(0)
  await expect(page.getByRole('alert').first()).toBeVisible()
})
