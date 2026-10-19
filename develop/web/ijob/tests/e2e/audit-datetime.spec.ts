// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

async function setup(page: Page) {
  const requests: URL[] = []
  const audit = {
    id: '9007199254740999',
    occurredAt: '2026-09-22T13:00:09.123',
    actorLoginId: 'user:admin',
    actionCode: 'AUTH_LOGIN',
    targetType: 'ACCOUNT',
    resultCode: 'SUCCESS',
  }
  await page.route(/\/api\/(?:auth|audits)(?:[/?]|$)/, (r) => {
    const url = new URL(r.request().url())
    if (url.pathname === '/api/audits') requests.push(url)
    const data =
      url.pathname === '/api/auth/me'
        ? {
            loginId: 'user:admin',
            username: 'admin',
            displayName: 'Admin',
            roleCode: 'ADMIN',
            permissions: ['AUDIT_READ'],
            mustChangePassword: false,
          }
        : url.pathname === '/api/audits'
          ? { list: [audit], total: 1, pages: 1, pageNum: 1, pageSize: 20 }
          : audit
    return r.fulfill({ json: { success: true, code: 200, data } })
  })
  return requests
}

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`Naive UTC datetime ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      const requests = await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/audits')
      await expect(page.locator('.time-cell')).toHaveText('2026-09-22 13:00:09')
      const input = page.locator('#audit-from')
      await input.fill('2026-09-22 13:00:09')
      await page.locator('#audit-to').fill('2026-09-23 13:00:09')
      await expect(input).toHaveAttribute('type', 'text')
      await page
        .getByRole('button', {
          name: `${en ? 'Choose date and time · From' : '选择日期时间 · 开始时间'}`,
          exact: true,
        })
        .click()
      const panel = page.locator('#audit-from-picker')
      await expect(panel).toBeVisible()
      // Measure in one frame so the popover transition cannot skew sibling coordinates.
      await expect
        .poll(() =>
          panel.evaluate((el) => {
            const date = el.querySelector('.utc-picker-inputs > .n-input')!.getBoundingClientRect()
            const time = el
              .querySelector('.utc-picker-inputs > .n-time-picker')!
              .getBoundingClientRect()
            const calendar = el.querySelector('.n-date-panel')!.getBoundingClientRect()
            return Math.abs(date.y - time.y) < 2 && time.bottom < calendar.y
          }),
        )
        .toBe(true)
      await page.screenshot({
        path: info.outputPath('datetime-top-row.png'),
        animations: 'disabled',
      })
      for (const text of en ? ['Clear', 'Now', 'Confirm'] : ['清除', '此刻', '确认'])
        await expect(panel.getByRole('button', { name: text, exact: true })).toBeVisible()
      if (en) await expect(panel).not.toContainText(/清除|今天|此刻|确认/)
      await panel
        .locator('.n-date-panel-date:not(.n-date-panel-date--excluded)')
        .filter({ hasText: /^24$/ })
        .click()
      await expect(input).toHaveValue('2026-09-22 13:00:09')
      await panel.locator('.utc-picker-inputs .n-time-picker input').click()
      const timePanel = panel.locator('.n-time-picker-panel')
      await expect(timePanel).toBeVisible()
      await expect(panel.locator('.n-date-panel-date__trigger').first()).toHaveCSS(
        'font-size',
        '13px',
      )
      await expect(timePanel.locator('.n-time-picker-col__item').first()).toHaveCSS(
        'font-size',
        '13px',
      )
      await timePanel.locator('.n-time-picker-col').first().getByText('14', { exact: true }).click()
      await page.screenshot({
        path: info.outputPath('datetime-time-open.png'),
        animations: 'disabled',
      })
      await panel.locator('.utc-picker-heading').click()
      await panel.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }).click()
      await expect(panel).not.toBeVisible()
      await expect(input).toHaveValue('2026-09-24 14:00:09')
      await input.press('Alt+ArrowDown')
      await panel.getByRole('button', { name: en ? 'Now' : '此刻', exact: true }).click()
      await panel.press('Escape')
      await expect(input).toHaveValue('2026-09-24 14:00:09')
      await page.setViewportSize({ width: 390, height: 844 })
      await input.scrollIntoViewIfNeeded()
      await input.press('Alt+ArrowDown')
      await expect(panel).toBeInViewport()
      await expect(
        panel.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }),
      ).toBeInViewport()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
      await page.screenshot({
        path: info.outputPath('datetime-mobile.png'),
        animations: 'disabled',
      })
      await panel.getByRole('button', { name: en ? 'Clear' : '清除', exact: true }).click()
      await expect(input).toHaveValue('')
      const count = requests.length
      await page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }).click()
      await expect(page.locator('#audit-from-required')).toHaveText(en ? 'Required' : '必填')
      expect(requests).toHaveLength(count)
      await page
        .getByRole('button', { name: `${en ? 'Details' : '详情'} 9007199254740999`, exact: true })
        .click()
      await expect(page.locator('.audit-detail')).toContainText('2026-09-22 13:00:09')
      expect(errors).toEqual([])
    })
  }

for (const timezoneId of ['Asia/Shanghai', 'America/Los_Angeles', 'UTC'])
  test.describe(timezoneId, () => {
    test.use({ timezoneId })
    test('calendar confirmation preserves UTC DST-gap clock and Now uses UTC', async ({ page }) => {
      const requests = await setup(page)
      await page.clock.setFixedTime(new Date('2026-09-22T13:00:09Z'))
      await page.goto('/audits')
      const input = page.locator('#audit-from')
      await input.fill('2026-03-08 02:30:45')
      await page.locator('#audit-to').fill('2026-03-09 03:00:00')
      await input.press('Alt+ArrowDown')
      const panel = page.locator('#audit-from-picker')
      await expect(panel.locator('.utc-picker-inputs .n-time-picker input')).toHaveValue('02:30:45')
      await panel.locator('.utc-picker-inputs .n-time-picker input').click()
      const hours = panel.locator('.n-time-picker-col').first()
      await hours.getByText('01', { exact: true }).click()
      await hours.getByText('02', { exact: true }).click()
      await panel.locator('.utc-picker-heading').click()
      await panel.getByRole('button', { name: '确认', exact: true }).click()
      await expect(input).toHaveValue('2026-03-08 02:30:45')
      await page.getByRole('button', { name: '查询', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('from')).toBe('2026-03-08T02:30:45')
      await input.press('Alt+ArrowDown')
      await panel.getByRole('button', { name: '此刻', exact: true }).click()
      await panel.getByRole('button', { name: '确认', exact: true }).click()
      await expect(input).toHaveValue('2026-09-22 13:00:09')
    })
  })

test('top date input rejects invalid dates and commits only on confirmation', async ({ page }) => {
  await setup(page)
  await page.goto('/audits')
  const input = page.locator('#audit-from')
  await input.fill('2026-09-22 13:00:09')
  await input.press('Alt+ArrowDown')
  const panel = page.locator('#audit-from-picker')
  const date = panel.locator('.utc-picker-inputs > .n-input input')
  await date.fill('2026-02-30')
  await expect(panel.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  await expect(input).toHaveValue('2026-09-22 13:00:09')
  await date.fill('2026-10-01')
  await panel.getByRole('button', { name: '确认', exact: true }).click()
  await expect(input).toHaveValue('2026-10-01 13:00:09')
})
