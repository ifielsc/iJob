// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const admin = {
  loginId: 'user:admin',
  username: 'preview-admin',
  displayName: 'Admin',
  roleCode: 'ADMIN',
  permissions: ['SUMMARY_READ', 'USER_MANAGE', 'AUDIT_READ', 'SELF_PASSWORD_CHANGE'],
  mustChangePassword: false,
}
const audit = {
  id: '9223372036854775806',
  occurredAt: '2026-03-08T02:30:45.123',
  actorLoginId: 'user:admin',
  actorName: 'Admin',
  actionCode: 'ACCOUNT_PROFILE_UPDATE',
  targetType: 'ACCOUNT',
  targetId: '9007199254740999',
  resultCode: 'SUCCESS',
  traceId: 'trace-' + '0123456789abcdef'.repeat(6),
  requestId: 'request-test-01',
  changedFields: 'displayName, enabled',
}
const rows = ['SUCCESS', 'REJECTED', 'FAILED', 'ATTEMPTED', 'ACCEPTED', 'REPLAYED'].map(
  (resultCode, i) => ({
    ...audit,
    id: String(BigInt(audit.id) - BigInt(i)),
    resultCode,
    actionCode: i ? 'TASK_TRIGGER' : audit.actionCode,
  }),
)
async function setup(page: Page) {
  const requests: URL[] = []
  const writes: string[] = []
  await page.route(/\/api\/(?:auth|users|roles|audits)(?:[/?]|$)/, (route) => {
    const url = new URL(route.request().url())
    if (route.request().method() !== 'GET') writes.push(url.pathname)
    if (url.pathname === '/api/audits') requests.push(url)
    const data =
      url.pathname === '/api/auth/me'
        ? admin
        : url.pathname === '/api/audits'
          ? { list: rows, total: 120, pageNum: 1, pageSize: 20, pages: 6 }
          : url.pathname.startsWith('/api/audits/')
            ? audit
            : url.pathname === '/api/users'
              ? { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 }
              : []
    return route.fulfill({ json: { success: true, code: 200, data } })
  })
  return { requests, writes }
}

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`compact audit surfaces ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      const { requests, writes } = await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/audits')
      await expect(page.locator('.audit-table tbody tr')).toHaveCount(6)
      await expect(page.locator('.sidebar')).toHaveCSS('width', '208px')
      await expect(page.locator('#audit-advanced')).not.toBeVisible()
      await expect(page.locator('.n-form-item-label__asterisk')).toHaveCount(2)
      await expect(page.locator('.auth-required')).toHaveCount(0)
      await expect(
        page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }),
      ).not.toHaveClass(/n-button--loading/)
      await page.screenshot({ path: info.outputPath('audits-desktop.png'), fullPage: true })
      await page
        .getByRole('button', {
          name: en ? 'Advanced filters (exact match)' : '高级筛选（精确匹配）',
          exact: true,
        })
        .click()
      await page.locator('#audit-actorLoginId').fill('user:中文')
      await page.locator('#audit-actionCode').fill('ACCOUNT_PROFILE_UPDATE')
      await page.locator('#audit-targetType').fill('ACCOUNT')
      await page.locator('#audit-targetId').fill('9007199254740999')
      await page.getByRole('combobox', { name: en ? 'Result' : '结果', exact: true }).click()
      await page.screenshot({ path: info.outputPath('result-menu.png'), animations: 'disabled' })
      await page.getByRole('option', { name: en ? 'Succeeded' : '成功', exact: true }).click()
      await page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('actorLoginId')).toBe('user:中文')
      for (const [key, value] of Object.entries({
        actionCode: 'ACCOUNT_PROFILE_UPDATE',
        targetType: 'ACCOUNT',
        targetId: '9007199254740999',
        resultCode: 'SUCCESS',
      }))
        expect(requests.at(-1)!.searchParams.get(key)).toBe(value)
      const advancedButton = page.getByRole('button', { name: /高级筛选|Advanced filters/ })
      await advancedButton.click()
      await expect(advancedButton).toContainText('(4)')
      await expect(page.locator('#audit-advanced')).not.toBeVisible()
      await page.getByRole('button', { name: en ? 'Next' : '下一页', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('pageNum')).toBe('2')
      await page.locator('.audit-sort').click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('sortDirection')).toBe('asc')
      expect(requests.at(-1)?.searchParams.get('sortBy')).toBe('occurredAt')
      expect(requests.at(-1)?.searchParams.get('pageNum')).toBe('1')
      expect(requests.at(-1)?.searchParams.get('actorLoginId')).toBe('user:中文')
      await expect(page.locator('th[aria-sort]')).toHaveAttribute('aria-sort', 'ascending')
      await page.locator('.audit-sort').press('Enter')
      await expect(page.locator('th[aria-sort]')).toHaveAttribute('aria-sort', 'descending')
      await advancedButton.click()
      await page
        .getByRole('combobox', { name: en ? 'Rows per page' : '每页条数', exact: true })
        .click()
      await page.getByRole('option', { name: '50', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('pageSize')).toBe('50')
      await page.getByRole('button', { name: en ? 'Next' : '下一页', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('pageNum')).toBe('2')
      await page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }).click()
      await expect(page.locator('.compact-pagination')).toContainText(en ? 'Page 2' : '第 2 页')
      // The same query refreshes the current page without returning to page 1.
      await page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('pageNum')).toBe('2')
      await page.screenshot({ path: info.outputPath('audits-advanced.png'), fullPage: true })
      await page
        .getByRole('button', { name: `${en ? 'Details' : '详情'} ${audit.id}`, exact: true })
        .click()
      const drawer = page.locator('.audit-drawer')
      await expect(drawer).toHaveCSS('width', '520px')
      await expect(drawer.locator('.detail-list')).toContainText(audit.traceId)
      const expectedPanel = theme === 'light' ? 'rgb(255, 255, 255)' : 'rgb(38, 59, 88)'
      await expect(drawer).toHaveCSS('background-color', expectedPanel)
      await page.screenshot({ path: info.outputPath('audit-detail.png'), animations: 'disabled' })
      await expect(
        drawer.getByRole('button', { name: /Language:|语言:|Theme:|主题:/ }),
      ).toHaveCount(0)
      await expect(drawer.locator('.audit-close')).toBeVisible()
      const detailOpener = page.getByRole('button', {
        name: `${en ? 'Details' : '详情'} ${audit.id}`,
        exact: true,
      })
      await drawer
        .locator('.n-drawer-footer')
        .getByRole('button', { name: en ? 'Close' : '关闭', exact: true })
        .click()
      await expect(drawer).toHaveCount(0)
      await expect(detailOpener).toBeFocused()
      await detailOpener.click()
      await page.locator('.n-drawer-mask').click({ position: { x: 20, y: 100 } })
      await expect(drawer).toHaveCount(0)
      await expect(detailOpener).toBeFocused()
      await detailOpener.click()

      for (const viewport of [
        { width: 390, height: 844 },
        { width: 655, height: 480 },
      ]) {
        await page.setViewportSize(viewport)
        const close = drawer
          .locator('.n-drawer-footer')
          .getByRole('button', { name: en ? 'Close' : '关闭', exact: true })
        await expect(close).toBeInViewport()
        await expect(drawer.getByText(audit.changedFields, { exact: true })).toBeAttached()
        await drawer.getByText(audit.changedFields, { exact: true }).scrollIntoViewIfNeeded()
        await expect(close).toBeInViewport()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(viewport.width)
        await page.screenshot({
          path: info.outputPath(`audit-detail-${viewport.width}.png`),
          animations: 'disabled',
        })
      }
      await page.keyboard.press('Escape')
      await expect(drawer).toHaveCount(0)
      const opener = page.getByRole('button', {
        name: `${en ? 'Details' : '详情'} ${audit.id}`,
        exact: true,
      })
      await expect(opener).toBeFocused()
      await opener.click()
      await drawer.locator('.audit-close').click()
      await expect(drawer).toHaveCount(0)
      await expect(opener).toBeFocused()
      await page.setViewportSize({ width: 390, height: 844 })
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
      expect(
        (await page.locator('.audit-table tbody tr').first().boundingBox())!.height,
      ).toBeLessThanOrEqual(80)
      const scroll = page.locator('.audit-table-scroll')
      const actions = page.locator('.audit-table tbody .audit-actions-cell').first()
      const before = (await actions.boundingBox())!
      await scroll.evaluate((element) => {
        element.scrollLeft = element.scrollWidth
      })
      await expect(actions).toHaveCSS('position', 'sticky')
      expect(Math.abs((await actions.boundingBox())!.x - before.x)).toBeLessThan(2)
      await expect(page.locator('.audit-table thead .audit-actions-cell')).toHaveCSS(
        'position',
        'sticky',
      )
      await page.getByRole('heading', { level: 1 }).click()
      await page.screenshot({ path: info.outputPath('audits-mobile.png'), fullPage: true })
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.getByRole('button', { name: en ? 'Reset' : '重置', exact: true }).click()
      await expect(page.locator('#audit-actorLoginId')).toHaveValue('')
      await expect(page.locator('th[aria-sort]')).toHaveAttribute('aria-sort', 'descending')
      await expect.poll(() => requests.at(-1)?.searchParams.has('resultCode')).toBe(false)
      await page.getByRole('link', { name: en ? 'Users' : '用户管理', exact: true }).click()
      await expect(page.locator('.sidebar')).toHaveCSS('width', '208px')
      await page.getByRole('button', { name: en ? 'New user' : '新增用户', exact: true }).click()
      await expect(page.locator('.users-drawer')).toHaveCSS('background-color', expectedPanel)
      await page
        .locator('.users-drawer')
        .getByRole('button', { name: en ? 'Cancel' : '取消', exact: true })
        .click()
      await page
        .getByRole('link', { name: en ? 'Operation audit' : '操作审计', exact: true })
        .click()
      await expect(page.locator('.sidebar')).toHaveCSS('width', '208px')
      expect(writes).toEqual([])
      expect(errors).toEqual([])
    })
  }

for (const timezoneId of ['Asia/Shanghai', 'America/Los_Angeles', 'UTC']) {
  test.describe(`UTC wall time in ${timezoneId}`, () => {
    test.use({ timezoneId })
    test('keeps DST-gap input exact, validates empty and reversed range without requests', async ({
      page,
    }) => {
      const { requests, writes } = await setup(page)
      await page.goto('/audits')
      await expect(page.locator('.audit-table tbody tr')).toHaveCount(6)
      await page.locator('#audit-from').fill('')
      await page.locator('#audit-to').fill('')
      const count = requests.length
      await page.getByRole('button', { name: '查询', exact: true }).click()
      await expect(page.locator('.auth-required')).toHaveCount(2)
      await expect(page.locator('#audit-from')).toBeFocused()
      expect(requests).toHaveLength(count)
      expect(await page.locator('form').evaluate((el: HTMLFormElement) => el.noValidate)).toBe(true)
      await page.getByRole('button', { name: '语言: 中文', exact: true }).click()
      await expect(page.locator('.auth-required').first()).toHaveText('Required')
      await page.locator('#audit-from').fill('2026-03-08T02:30:45')
      await page.locator('#audit-to').fill('2026-03-08T03:30:46')
      await page.getByRole('button', { name: 'Search', exact: true }).click()
      await expect.poll(() => requests.at(-1)?.searchParams.get('from')).toBe('2026-03-08T02:30:45')
      expect(requests.at(-1)?.searchParams.get('to')).toBe('2026-03-08T03:30:46')
      await expect(page.locator('.auth-required')).toHaveCount(0)
      await expect(page.locator('.audit-table .time-cell').first()).toContainText('02:30:45')
      await page.locator('#audit-to').fill('2026-03-08T01:30:45')
      const validCount = requests.length
      await page.getByRole('button', { name: 'Search', exact: true }).click()
      await expect(page.locator('#audit-range-error')).toContainText('90 days')
      expect(requests).toHaveLength(validCount)
      expect(writes).toEqual([])
    })
  })
}

test('audit list and detail failures can retry; revoked access clears the open detail', async ({
  page,
}) => {
  await setup(page)
  let listFailed = true
  await page.route('**/api/audits?*', (r) =>
    listFailed
      ? r.fulfill({ status: 500, json: { success: false, code: 500, traceId: 'list-failure' } })
      : r.fulfill({
          json: {
            success: true,
            code: 200,
            data: { list: [audit], total: 1, pageNum: 1, pageSize: 20, pages: 1 },
          },
        }),
  )
  await page.goto('/audits')
  await expect(page.getByRole('alert')).toContainText('list-failure')
  listFailed = false
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.audit-table tbody tr')).toHaveCount(1)
  let detailStatus = 500
  await page.route(`**/api/audits/${audit.id}`, (r) =>
    r.fulfill({
      status: detailStatus,
      json: {
        success: false,
        code: detailStatus,
        errorCode: detailStatus === 403 ? 'IJOB-SECURITY-0403' : undefined,
        traceId: 'detail-failure',
      },
    }),
  )
  await page.getByRole('button', { name: `详情 ${audit.id}`, exact: true }).click()
  await expect(page.locator('.audit-drawer').getByRole('alert')).toContainText('detail-failure')
  await expect(page.locator('.audit-drawer .detail-list')).toHaveCount(0)
  detailStatus = 403
  await page.locator('.audit-drawer').getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.audit-drawer')).toHaveCount(0)
  await expect(page.locator('.audit-table')).toHaveCount(0)
})
