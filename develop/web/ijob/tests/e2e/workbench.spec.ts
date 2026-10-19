// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupWorkbench } from '../workbench-fixture'
test.use({ viewport: { width: 1512, height: 1100 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test('workbench layout ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupWorkbench(page),
        errors: string[] = [],
        en = locale === 'en-US'
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.goto('/workbench')
      await expect(page.getByTestId('total')).toHaveText('128')
      await expect(page.getByTestId('rate')).toHaveText('93.33%')
      await expect(page.locator('.workbench-node')).toContainText(en ? 'Not configured' : '未配置')
      await expect(page.locator('.workbench-role')).toHaveText(en ? 'API' : '接口')
      await expect(page.locator('.workbench-approvals')).toContainText('2')
      await expect(page.locator('.application')).toHaveClass(/compact-shell/)
      await expect(page.locator('#workbench-from')).toHaveCSS('height', '32px')
      await page.screenshot({ path: info.outputPath('desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      await expect(
        page.getByRole('link', {
          name: en ? 'View run 9007199254740993' : '查看运行 9007199254740993',
          exact: true,
        }),
      ).toBeVisible()
      expect(state.writes).toEqual([])
      expect(errors).toEqual([])
    })
  }
test('viewer empty window hides approvals but retains current backlog', async ({ page }) => {
  const state = await setupWorkbench(page, true)
  state.data.total = '0'
  state.data.statuses = Object.fromEntries(Object.keys(state.data.statuses).map((s) => [s, '0']))
  state.data.successRateDenominator = '0'
  state.data.successRatePercent = null
  state.data.recentFailures = []
  await page.goto('/workbench')
  await expect(page.getByTestId('rate')).toHaveText('—')
  await expect(page.locator('.workbench-empty')).toContainText('此时间窗内没有根运行')
  await expect(page.locator('.workbench-approvals')).toHaveCount(0)
  await expect(page.locator('.workbench-backlog')).toContainText('3')
  await expect(
    page.getByRole('status').filter({ hasText: '此时间窗内没有失败或超时的根运行。' }),
  ).toBeVisible()
})
test('queries preserve fixed bounds and status links carry the correct time scope', async ({
  page,
}) => {
  const state = await setupWorkbench(page)
  await page.goto('/workbench')
  await expect(page.getByTestId('total')).toBeVisible()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.filter((p) => p.startsWith('/api/workbench')).length).toBe(2)
  await expect(page.getByTestId('total')).toBeVisible()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.filter((p) => p.startsWith('/api/workbench')).length).toBe(3)
  const queries = state.reads.filter((p) => p.startsWith('/api/workbench'))
  expect(queries[1]).toBe(queries[2])
  await page
    .locator('.workbench-distribution')
    .getByRole('link', { name: '查看执行失败根运行', exact: true })
    .click()
  await expect(page).toHaveURL(/tab=instances/)
  const window = new URL(page.url())
  expect(window.searchParams.get('from')).toBe('2026-09-27T10:00:00')
  expect(window.searchParams.get('status')).toBe('FAILED')
  await page.goBack()
  await expect(page.getByTestId('total')).toBeVisible()
  await page
    .locator('.workbench-backlog')
    .getByRole('link', { name: '查看运行中根运行', exact: true })
    .click()
  await expect(page).toHaveURL(/status=RUNNING/)
  expect(new URL(page.url()).searchParams.get('from')).toBe('')
  expect(new URL(page.url()).searchParams.get('to')).toBe('')
})
test('recent detail returns to the same workbench window', async ({ page }) => {
  await setupWorkbench(page)
  await page.goto('/workbench')
  await page.getByRole('link', { name: '查看运行 9007199254740993' }).click()
  await expect(page).toHaveURL(/runs\/instances\/9007199254740993/)
  await page.locator('a[href^="/workbench?"]').click()
  await expect(page.getByTestId('total')).toHaveText('128')
  expect(new URL(page.url()).searchParams.get('from')).toBe('2026-09-27T10:00:00')
})
test('invalid date range focuses first error without a request', async ({ page }) => {
  const state = await setupWorkbench(page)
  await page.goto('/workbench')
  await expect(page.getByTestId('total')).toBeVisible()
  await page.locator('#workbench-from').fill('2026-01-01 00:00:00')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('#workbench-from')).toBeFocused()
  await expect(page.locator('#workbench-time-error')).toBeVisible()
  expect(state.reads.filter((p) => p.split('?')[0] === '/api/workbench')).toHaveLength(1)
})
test('error is not displayed as zeros and retry recovers', async ({ page }) => {
  const state = await setupWorkbench(page)
  state.fail = 500
  await page.goto('/workbench')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByTestId('total')).toHaveCount(0)
  state.fail = 0
  await page.getByRole('button', { name: '重试查询' }).click()
  await expect(page.getByTestId('total')).toHaveText('128')
  state.fail = 500
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByTestId('total')).toHaveCount(0)
})
test('rapid shortcut changes cancel stale requests', async ({ page }) => {
  const state = await setupWorkbench(page)
  await page.goto('/workbench')
  await expect(page.getByTestId('total')).toBeVisible()
  state.delay = 700
  await page.getByRole('button', { name: '最近7天', exact: true }).click()
  await expect
    .poll(() => state.reads.filter((p) => p.split('?')[0] === '/api/workbench').length)
    .toBe(2)
  state.delay = 0
  state.data.total = '129'
  state.data.statuses.WAITING = '3'
  await page.getByRole('button', { name: '最近30天', exact: true }).click()
  await expect(page.getByTestId('total')).toHaveText('129')
  const from = new URL(page.url()).searchParams.get('from')!
  await expect(page.locator('#workbench-from')).toHaveValue(from.replace('T', ' '))
  await page.waitForTimeout(800)
  await expect(page.getByTestId('total')).toHaveText('129')
  await expect(page.locator('#workbench-from')).toHaveValue(from.replace('T', ' '))
})
test('large totals and unknown status remain exact and visible', async ({ page }) => {
  const state = await setupWorkbench(page)
  state.data.statuses.FUTURE = '9007199254740993'
  state.data.total = '9007199254741121'
  await page.goto('/workbench')
  await expect(page.getByTestId('total')).toHaveText('9,007,199,254,741,121')
  await expect(page.locator('.workbench-status-list')).toContainText('FUTURE')
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
    true,
  )
})
test('forbidden aggregate clears visible data', async ({ page }) => {
  const state = await setupWorkbench(page)
  await page.goto('/workbench')
  await expect(page.getByTestId('total')).toBeVisible()
  state.fail = 403
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByTestId('total')).toHaveCount(0)
  await expect(page.getByRole('alert').first()).toBeVisible()
})
