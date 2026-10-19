// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect, type Page } from '@playwright/test'

const channel = {
  id: '9223372036854775806',
  revision: '1',
  name: 'Team mail',
  enabled: true,
  host: 'smtp.example.test',
  port: 465,
  security: 'TLS',
  authentication: true,
  username: 'team@example.test',
  from: 'team@example.test',
  passwordConfigured: true,
}
const email = {
  id: '9223372036854775805',
  name: 'Daily notification',
  type: 'EMAIL',
  enabled: true,
  config: {
    fromConfigured: true,
    from: 'old@example.test',
    to: ['recipient@example.test'],
    cc: [],
    toCount: 1,
    ccCount: 0,
  },
}

test('mail channel candidates paginate beyond the first fifty and retain exact IDs', async ({
  page,
}, info) => {
  const writes = await setup(page)
  const requested: number[] = []
  await page.route('**/api/mail-channels?**', async (route) => {
    const params = new URL(route.request().url()).searchParams
    const pageNum = Number(params.get('pageNum') ?? '1')
    const pageSize = Number(params.get('pageSize') ?? '50')
    requested.push(pageNum)
    const start = (pageNum - 1) * pageSize
    const rows = Array.from(
      { length: Math.max(0, Math.min(pageSize, 55 - start)) },
      (_, index) => ({
        ...channel,
        id: String(100 + start + index),
        name: `Candidate ${start + index + 1}`,
      }),
    )
    await route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          list: rows,
          total: 55,
          pageNum,
          pageSize,
          pages: Math.ceil(55 / pageSize),
        },
      },
    })
  })
  await page.goto('/notifications')
  await page.getByRole('button', { name: '编辑 Daily notification', exact: true }).click()
  await page.locator('#config-mailChannel').click()
  await expect(page.getByText('Candidate 1 · team@example.test', { exact: true })).toBeVisible()
  await page
    .locator('.mail-channel-candidate-footer')
    .getByRole('button', { name: '下一页', exact: true })
    .click()
  await expect(page.getByText('Candidate 51 · team@example.test', { exact: true })).toBeVisible()
  await page.screenshot({
    path: info.outputPath('mail-candidates-desktop.png'),
    animations: 'disabled',
  })
  await page.setViewportSize({ width: 390, height: 900 })
  await expect(page.locator('.mail-channel-candidate-footer')).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
  await page.screenshot({
    path: info.outputPath('mail-candidates-mobile.png'),
    animations: 'disabled',
  })
  await page.getByText('Candidate 51 · team@example.test', { exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]?.body.emailConfig).toMatchObject({ mailChannelId: '150' })
  expect(requested).toContain(2)
})

test('mail deletion requires confirmation and batch failure preserves selection', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications/mail-channels')
  await page.getByRole('button', { name: '删除 Team mail', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '删除 Team mail', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]?.path).toBe('/api/mail-channels/' + channel.id)
  await page.getByRole('checkbox', { name: '选择邮件通道 Team mail', exact: true }).check()
  await page.getByRole('checkbox', { name: '选择邮件通道 Disabled mail', exact: true }).check()
  let ids: string[] = []
  await page.route('**/api/mail-channels/batch', async (route) => {
    ids = route.request().postDataJSON().ids
    await route.fulfill({
      status: 409,
      json: { success: false, code: 409, message: '邮件通道仍被通知配置引用' },
    })
  })
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect.poll(() => ids).toEqual([channel.id, '42'])
  await expect(
    page.getByRole('checkbox', { name: '选择邮件通道 Team mail', exact: true }),
  ).toBeChecked()
  await expect(
    page.getByRole('checkbox', { name: '选择邮件通道 Disabled mail', exact: true }),
  ).toBeChecked()
})

test('read-only mail channel users have no deletion controls', async ({ page }) => {
  await setup(page, false)
  await page.goto('/notifications/mail-channels')
  await expect(page.locator('tbody tr')).toHaveCount(2)
  await expect(page.getByRole('checkbox')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /删除/ })).toHaveCount(0)
})
async function setup(page: Page, writable = true) {
  const writes: { path: string; body: Record<string, unknown> }[] = []
  await page.route('**/api/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    const list = (rows: unknown[]) => ({
      list: rows,
      total: rows.length,
      pageNum: 1,
      pageSize: 20,
      pages: 1,
    })
    if (request.method() !== 'GET') {
      writes.push({ path, body: request.postDataJSON() ?? {} })
      return route.fulfill({
        json: {
          success: true,
          code: 200,
          data: path.endsWith('test-connection') ? { connected: true } : channel.id,
        },
      })
    }
    const data =
      path === '/api/auth/me'
        ? {
            loginId: 'mail-admin',
            username: 'mail-admin',
            displayName: 'Mail admin',
            roleCode: 'ADMIN',
            mustChangePassword: false,
            permissions: [
              'SUMMARY_READ',
              'SENSITIVE_READ',
              ...(writable ? ['CONFIGURATION_WRITE'] : []),
            ],
          }
        : path === '/api/mail-channels'
          ? list([channel, { ...channel, id: '42', name: 'Disabled mail', enabled: false }])
          : path === '/api/notify-configs'
            ? list([email])
            : path.endsWith(email.id)
              ? email
              : channel
    return route.fulfill({ json: { success: true, code: 200, data } })
  })
  return writes
}

test('grouped menu and saved secret preservation with account-change validation', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications/mail-channels')
  await expect(page.getByRole('button', { name: '通知中心', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '通知管理', exact: true })).toBeVisible()
  await expect(page.locator('.resource-identity').first()).toContainText(channel.id)
  await page.getByRole('button', { name: '编辑 Team mail', exact: true }).click()
  await expect(page.locator('#mail-password')).toHaveValue('')
  await page.locator('#mail-name').fill('Renamed mail')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body).toMatchObject({ name: 'Renamed mail', revision: '1' })
  expect(writes[0]?.body).not.toHaveProperty('password')
  await page.getByRole('button', { name: '编辑 Team mail', exact: true }).click()
  await page.locator('#mail-username').fill('changed@example.test')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#mail-password')).toHaveAttribute('aria-invalid', 'true')
  expect(writes).toHaveLength(1)
  await page.locator('#mail-password').fill('synthetic-test-code')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[1]?.body.password).toBe('synthetic-test-code')
})

test('connection check is explicit, sends once, and disabled channels cannot be checked', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications/mail-channels')
  const checks = page.getByRole('button', { name: '连接检查', exact: true })
  await expect(checks.nth(1)).toBeDisabled()
  await checks.first().click()
  await expect(page.locator('.n-dialog')).toContainText('不发送邮件')
  expect(writes).toHaveLength(0)
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('连接与认证检查通过', { exact: true })).toBeVisible()
  expect(writes.map((w) => w.path)).toEqual([
    '/api/mail-channels/' + channel.id + '/test-connection',
  ])
})

test('read-only channel details contain no writable credential or action', async ({ page }) => {
  const writes = await setup(page, false)
  await page.goto('/notifications/mail-channels')
  await expect(page.getByRole('button', { name: '新增邮件通道', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '连接检查', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '详情 Team mail', exact: true }).click()
  await expect(page.locator('.mail-channel-details .unified-facts').first()).toContainText(
    'Team mail',
  )
  await expect(page.locator('#mail-password')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
  expect(writes).toHaveLength(0)
})

test('notification selects exact channel ID and uses its sender without sending mail on save', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '编辑 Daily notification', exact: true }).click()
  await expect(page.locator('.notification-drawer')).toContainText('请选择邮件通道')
  await page.locator('#config-name').fill('Needs channel')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect(writes).toHaveLength(0)
  await expect(page.locator('#config-mailChannel')).toHaveAttribute('aria-invalid', 'true')
  await page.locator('#config-mailChannel').click()
  await expect(page.getByText('部署默认邮件通道（兼容）', { exact: true })).toHaveCount(0)
  await page.getByText('Team mail · team@example.test', { exact: true }).click()
  await expect(page.locator('#config-from')).toHaveValue(channel.from)
  await expect(page.locator('#config-from')).toBeDisabled()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
  expect(writes[0]?.body.emailConfig).not.toHaveProperty('useDefaultMailChannel')
  expect(writes[0]?.body.emailConfig).toMatchObject({
    mailChannelId: channel.id,
    from: channel.from,
  })
})

test('unassigned old notifications cannot send until a mail channel is selected', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '测试发送', exact: true }).click()
  await expect(
    page.getByText('请选择邮件通道；尚未配置时，请先在邮件通道管理中新增。', { exact: true }),
  ).toBeVisible()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(0)
})

test('name opens unified read-only details and technical information is collapsed', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/notifications/mail-channels')
  const name = page.getByRole('button', { name: 'Team mail', exact: true })
  await name.click()
  const drawer = page.locator('.mail-channel-details')
  await expect(drawer).toHaveCSS('width', '520px')
  await expect(drawer.locator('h3').first()).toHaveCSS('border-left-width', '2px')
  await expect(drawer.locator('.unified-facts').first()).toHaveCSS('font-size', '13px')
  await expect(drawer.locator('input')).toHaveCount(0)
  await expect(drawer.locator('.unified-technical')).not.toHaveAttribute('open')
  await page.screenshot({ path: info.outputPath('mail-details.png'), animations: 'disabled' })
  await drawer.getByRole('button', { name: '关闭', exact: true }).last().click()
  await expect(name).toBeFocused()
  expect(writes).toHaveLength(0)
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`mail channel form and list fit desktop and mobile ${theme}/${locale}`, async ({
      page,
    }, info) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(error.message))
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.goto('/notifications')
      const baseline = await page.locator('.panel').evaluate((el) => {
        const css = getComputedStyle(el)
        return { padding: css.padding, borderRadius: css.borderRadius, fontSize: css.fontSize }
      })
      await page.goto('/notifications/mail-channels')
      await expect(page.locator('tbody tr')).toHaveCount(2)
      expect(
        await page.locator('.panel').evaluate((el) => {
          const css = getComputedStyle(el)
          return { padding: css.padding, borderRadius: css.borderRadius, fontSize: css.fontSize }
        }),
      ).toEqual(baseline)
      await expect(page.locator('tbody td').first()).toHaveCSS('font-size', '13px')
      await page.screenshot({ path: info.outputPath('list.png'), animations: 'disabled' })
      await page
        .getByRole('button', {
          name: locale === 'en-US' ? 'New mail channel' : '新增邮件通道',
          exact: true,
        })
        .click()
      await expect(page.locator('.mail-channel-drawer')).toHaveCSS('width', '520px')
      for (const width of [1280, 655, 390]) {
        await page.setViewportSize({ width, height: width === 655 ? 480 : 900 })
        await expect(page.locator('#mail-from')).toBeVisible()
        await expect(
          page.locator('.mail-channel-drawer .configuration-footer button').last(),
        ).toBeInViewport()
        await expect(
          page.locator('.mail-channel-drawer .notification-editor-close'),
        ).toBeInViewport()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
        await page.screenshot({
          path: info.outputPath('form-' + width + '.png'),
          animations: 'disabled',
        })
      }
      expect(errors).toEqual([])
    })
  }
