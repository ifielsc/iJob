import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

for (const [type, label, webhook] of [
  ['WECOM', '企业微信', 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=sample'],
  ['FEISHU', '飞书', 'https://open.feishu.cn/open-apis/bot/v2/hook/sample'],
  ['SLACK', 'Slack', 'https://hooks.slack.com/services/T1/B1/sample'],
  ['DISCORD', 'Discord', 'https://discord.com/api/webhooks/123/sample'],
] as const) {
  test('notification channels create and preview ' + type, async ({ page }, info) => {
    const writes = await setup(page)
    await page.goto('/notifications')
    await page.getByRole('button', { name: '新增通知配置', exact: true }).click()
    await page.locator('#config-name').fill('New ' + label)
    await page.locator('.n-drawer').getByRole('combobox', { name: '类型', exact: true }).click()
    await page.getByRole('option', { name: label, exact: true }).click()
    await page.locator('#config-webhook').fill(webhook)
    await expect(page.locator('.notification-protection')).toContainText('同一机器人或频道')
    const previewAction = page.getByRole('button', { name: '预览内容', exact: true })
    await expect(previewAction.locator('svg')).toBeVisible()
    const footer = (await page.locator('.configuration-footer').boundingBox())!
    const action = (await previewAction.boundingBox())!
    expect(Math.abs(footer.x - action.x)).toBeLessThan(2)
    await previewAction.click()
    await expect(page.locator('.notification-preview-card')).toContainText('998')
    expect(writes).toHaveLength(0)
    await page
      .locator('.n-drawer')
      .last()
      .getByRole('button', { name: '关闭', exact: true })
      .last()
      .click()
    await page.screenshot({ path: info.outputPath(type + '-editor.png'), animations: 'disabled' })
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.n-drawer')).toHaveCount(0)
    expect(writes).toHaveLength(1)
    expect(writes[0]!.body.type).toBe(type)
    expect((writes[0]!.body.webhookConfig as Record<string, unknown>).webhook).toBe(webhook)
    expect(writes[0]!.path).not.toContain('test-send')
    expect(writes[0]!.body.dingTalkConfig).toBeUndefined()
  })
}

for (const [locale, theme, width] of [
  ['zh-CN', 'light', 1366],
  ['en-US', 'dark', 1366],
  ['zh-CN', 'light', 390],
  ['en-US', 'dark', 360],
] as const) {
  test(`notification channels waiting detail ${locale} ${theme} ${width}`, async ({
    page,
  }, info) => {
    await page.addInitScript(
      ({ locale, theme }) =>
        localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ locale, theme })),
      { locale, theme },
    )
    const writes = await setup(page)
    const item = {
      id: '123',
      notifyConfigId: '7',
      configName: 'Ops bot',
      notifyType: 'DINGTALK',
      sourceType: 'MONITOR',
      subject: 'Server alert',
      targetId: '36101',
      instanceId: '46101',
      status: 'RETRY_WAIT',
      attemptCount: 0,
      maxAttempts: 5,
      failureCode: 'RATE_WAIT',
      failureSummary: 'waiting for shared notification budget',
      httpStatus: null,
      availableAt: '2026-10-17T02:24:03',
      createTime: '2026-10-17T02:24:00',
      updateTime: '2026-10-17T02:24:00',
      deliveredAt: null,
    }
    await page.route('**/api/notification-deliveries**', (route) =>
      route.fulfill({
        json: {
          success: true,
          code: 200,
          data: new URL(route.request().url()).pathname.endsWith('/123')
            ? item
            : { list: [item], total: 1, pageNum: 1, pageSize: 20, pages: 1 },
        },
      }),
    )
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/notifications?tab=records')
    await page
      .getByRole('button', { name: locale === 'zh-CN' ? '详情' : 'Details', exact: true })
      .click()
    await expect(page.locator('.n-drawer')).toContainText('0 / 5')
    await expect(page.locator('.n-drawer')).toContainText(
      locale === 'zh-CN' ? '不计入尝试次数' : 'does not consume an attempt',
    )
    await page.screenshot({
      path: info.outputPath('waiting-' + width + '.png'),
      fullPage: true,
      animations: 'disabled',
    })
    const overflow = await page.evaluate(() => ({
      width: document.documentElement.scrollWidth,
      elements: [...document.querySelectorAll('body *')]
        .filter((e) => e.getBoundingClientRect().right > innerWidth + 1)
        .slice(0, 12)
        .map((e) => ({
          tag: e.tagName,
          class: e.className,
          right: e.getBoundingClientRect().right,
        })),
    }))
    expect(overflow.width, JSON.stringify(overflow)).toBeLessThanOrEqual(width + 1)
    expect(writes).toHaveLength(0)
  })
}

test('wrapped resource names align left with exact copyable ID below', async ({
  page,
  context,
}, info) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await setup(page)
  const name = '订单数据库 这是一条用于验证名称自动换行后每行左对齐的长资源名称'
  await page.route('**/api/resources?*', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: [{ ...resource, name }], total: 1, pageNum: 1, pageSize: 20, pages: 1 },
      },
    }),
  )
  await page.goto('/resources')
  const cell = page.locator('.resource-identity')
  const label = cell.locator('> .n-button .n-button__content')
  await expect(label).toHaveCSS('text-align', 'left')
  await expect(cell.locator('.identity-text')).toContainText(resource.id)
  const nameBox = await label.boundingBox(),
    idBox = await cell.locator('.identity-text').boundingBox()
  expect(idBox!.y).toBeGreaterThanOrEqual(nameBox!.y + nameBox!.height)
  await cell.getByRole('button', { name: '复制 ID ' + resource.id, exact: true }).click()
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(resource.id)
  await page.screenshot({ path: info.outputPath('resource-name-id.png') })
})

test('QA14 saved configuration enables save only for effective changes and list filters survive navigation', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources')
  await page.getByRole('button', { name: '编辑 Demo orders', exact: true }).click()
  const save = page.getByRole('button', { name: '保存', exact: true })
  await expect(save).toBeDisabled()
  await page.locator('#config-name').fill(' Demo orders ')
  await expect(save).toBeDisabled()
  await page.locator('#config-name').fill('Changed orders')
  await expect(save).toBeEnabled()
  await page.locator('#config-name').fill('Demo orders')
  await expect(save).toBeDisabled()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.resource-filter input').first().fill('orders')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.applied-filters')).toContainText('orders')
  await page.getByRole('link', { name: '日历管理', exact: true }).click()
  await page.getByRole('link', { name: '数据库资源', exact: true }).click()
  await expect(page.locator('.applied-filters')).toContainText('orders')
  expect(writes).toHaveLength(0)
})

test('QA14 calendar entries scroll independently with fixed search and collapse on mobile', async ({
  page,
}, info) => {
  await setup(page)
  await page.route('**/api/calendars?*', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          list: Array.from({ length: 80 }, (_, i) => ({
            ...calendar,
            id: String(i + 1),
            name: '业务日历 ' + String(i + 1).padStart(3, '0') + ' 财务结算与跨部门协同工作日期',
          })),
          total: 80,
          pageNum: 1,
          pageSize: 100,
          pages: 1,
        },
      },
    }),
  )
  await page.goto('/calendars')
  const list = page.locator('.calendar-choices'),
    search = page.locator('.calendar-search')
  await expect(list.locator('.calendar-choice')).toHaveCount(80)
  const before = await search.boundingBox()
  expect(await list.evaluate((el) => el.scrollHeight > el.clientHeight)).toBe(true)
  await list.evaluate((el) => (el.scrollTop = el.scrollHeight))
  expect((await search.boundingBox())!.y).toBe(before!.y)
  await page.screenshot({ path: info.outputPath('calendar-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await list.locator('button').last().click()
  await expect(list).toBeHidden()
  await page.locator('.calendar-mobile-toggle').click()
  await expect(list).toBeVisible()
  await page.screenshot({ path: info.outputPath('calendar-mobile.png') })
})

test('resource format errors identify each field and focus the first invalid input', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/resources')
  await page.getByRole('button', { name: '新增数据库资源', exact: true }).click()
  await page.locator('#config-name').fill('Database')
  await page.locator('#config-code').fill('中文标识')
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.getByRole('option', { name: 'MySQL', exact: true }).click()
  await page.locator('#config-jdbcUrl').fill('localhost:3306/demo')
  await page.locator('#config-username').fill('demo')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#config-code')).toBeFocused()
  await expect(page.locator('#config-code-error')).toContainText('资源标识')
  await expect(page.locator('#config-jdbcUrl-error')).toContainText('jdbc:mysql:')
  await page.screenshot({
    path: info.outputPath('resource-field-errors.png'),
    animations: 'disabled',
  })
  expect(writes).toHaveLength(0)
  await page.locator('#config-code').fill('demo-db')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#config-jdbcUrl')).toBeFocused()
  await page.locator('#config-jdbcUrl').fill('jdbc:mysql://localhost:3306/demo')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
})

test('calendar sidebar has no pagination, defaults use business date and weekdays appear only in headers', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/calendars')
  await expect(page.locator('.calendar-sidebar').getByText('每页条数')).toHaveCount(0)
  await expect(page.getByPlaceholder('搜索日历名称')).toBeVisible()
  await expect(page.locator('#preview-from')).toHaveValue('2026-09-21')
  await expect(page.locator('.calendar-range-end')).toHaveCount(0)
  await expect(page.locator('#preview-days')).toHaveValue('14')
  await expect(page.getByRole('columnheader', { name: '周一', exact: true })).toHaveCount(1)
  await expect(page.locator('.calendar-day').getByText('周一', { exact: true })).toHaveCount(0)
  for (let column = 0; column < 7; column++) {
    const heading = await page.locator('.calendar-week-head > div').nth(column).boundingBox()
    const cell = await page
      .locator('.calendar-week-row')
      .first()
      .locator('.calendar-day')
      .nth(column)
      .boundingBox()
    expect(Math.abs(heading!.x - cell!.x)).toBeLessThan(1)
    expect(Math.abs(heading!.width - cell!.width)).toBeLessThan(1)
  }
  // Re-enter via client routing: cached context/list must still initialize the preview.
  await page.getByRole('link', { name: '数据库资源', exact: true }).click()
  await page.getByRole('link', { name: '日历管理', exact: true }).click()
  await expect(page.locator('#preview-from')).toHaveValue('2026-09-21')
  await expect(page.locator('.calendar-day[aria-label="2026-09-21"]')).toBeVisible()
  await page.getByRole('button', { name: '新增日历', exact: true }).click()
  await page.getByRole('button', { name: '添加日期', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '日期 1', exact: true })).toHaveValue('2026-09-21')
  const dateBox = await page.getByRole('textbox', { name: '日期 1', exact: true }).boundingBox()
  const deleteBox = await page
    .getByRole('button', { name: '删除日期 1', exact: true })
    .boundingBox()
  expect(Math.abs(dateBox!.y - deleteBox!.y)).toBeLessThan(4)
})

test('calendar pagination preserves exception rows outside the visible page', async ({ page }) => {
  const writes = await setup(page)
  const exceptions = Array.from({ length: 24 }, (_, index) => ({
    date: new Date(Date.UTC(2026, 8, 1 + index)).toISOString().slice(0, 10),
    type: 'HOLIDAY',
  }))
  await page.route('**/api/calendars/' + calendar.id, (route) =>
    route.request().method() === 'GET'
      ? route.fulfill({ json: { success: true, code: 200, data: { ...calendar, exceptions } } })
      : route.fallback(),
  )
  await page.goto('/calendars')
  await page.getByRole('button', { name: 'Demo calendar', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.locator('.calendar-exception')).toHaveCount(20)
  await page.locator('#calendar-name').fill('Keep every date')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.exceptions).toEqual(exceptions)
})

for (const locale of ['zh-CN', 'en-US'])
  test(`calendar dates apply only after confirmation ${locale}`, async ({ page }, info) => {
    const en = locale === 'en-US'
    const warnings: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'warning' && message.text().includes('[Vue warn]'))
        warnings.push(message.text())
    })
    await page.addInitScript(
      (locale) =>
        localStorage.setItem(
          'ijob:preferences:v1:guest',
          JSON.stringify({ theme: 'light', locale }),
        ),
      locale,
    )
    await setup(page)
    await page.goto('/calendars')
    await page.getByRole('button', { name: 'Demo calendar', exact: true }).click()
    await page.getByRole('button', { name: en ? 'Edit' : '编辑', exact: true }).click()
    const input = page.getByRole('textbox', { name: en ? 'Date 1' : '日期 1', exact: true })
    await input.press('Alt+ArrowDown')
    const panel = page.locator('.business-date-panel')
    await expect(panel).toBeVisible()
    await panel.locator('.n-date-panel-date').getByText('24', { exact: true }).click()
    await expect(input).toHaveValue('2026-09-25')
    await panel.press('Escape')
    await expect(input).toHaveValue('2026-09-25')
    await input.press('Alt+ArrowDown')
    await panel.locator('.n-date-panel-date').getByText('24', { exact: true }).click()
    await panel.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }).click()
    await expect(input).toHaveValue('2026-09-24')
    await page.setViewportSize({ width: 390, height: 844 })
    await input.scrollIntoViewIfNeeded()
    await input.press('Alt+ArrowDown')
    await expect(
      panel.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }),
    ).toBeInViewport()
    await page.screenshot({
      animations: 'disabled',
      path: info.outputPath('calendar-date-mobile.png'),
    })
    await panel.getByRole('button', { name: en ? 'Clear' : '清除', exact: true }).click()
    await expect(input).toHaveValue('')
    expect(warnings).toEqual([])
  })
test('database type switching can restore a draft and cancelling close retains input', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources')
  await page.getByRole('button', { name: '编辑 Demo orders', exact: true }).click()
  await page.locator('#config-username').fill('draft-user')
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.getByRole('option', { name: 'MySQL', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('#config-username')).toHaveValue('')
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.getByRole('option', { name: 'PostgreSQL', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('#config-username')).toHaveValue('draft-user')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(page.locator('#config-username')).toHaveValue('draft-user')
  expect(writes).toHaveLength(0)
})

const resource = {
  id: '9223372036854775807',
  name: 'Demo orders',
  code: 'demo-orders',
  type: 'DB',
  enabled: true,
  description: '',
  config: {
    dbType: 'POSTGRESQL',
    jdbcUrl: 'jdbc:postgresql://localhost:5432/demo',
    username: 'demo-user',
    jdbcUrlConfigured: true,
    usernameConfigured: true,
    passwordConfigured: true,
    schema: 'public',
  },
}
const email = {
  id: '9223372036854775806',
  name: 'Demo email',
  type: 'EMAIL',
  enabled: true,
  config: {
    mailChannelId: '1001',
    mailChannelName: 'Demo channel',
    from: 'sender@example.test',
    to: ['to1@example.test', 'to2@example.test'],
    cc: ['cc@example.test'],
    fromConfigured: true,
    toCount: 2,
    ccCount: 1,
  },
}
const ding = {
  id: '9223372036854775805',
  name: 'Demo DingTalk',
  type: 'DINGTALK',
  enabled: true,
  config: {
    webhookDisplay: 'https://example.test/hook?••••',
    atMobiles: ['13800000000', '13900000000'],
    webhookConfigured: true,
    secretConfigured: true,
    atAll: false,
    atMobileCount: 2,
  },
}
const calendar = {
  id: '9223372036854775804',
  name: 'Demo calendar',
  workdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  description: '',
  exceptions: [
    { date: '2026-09-25', type: 'HOLIDAY' },
    { date: '2026-09-26', type: 'WORKDAY' },
  ],
}
const admin = {
  loginId: 'configuration-admin',
  username: 'demo-admin',
  displayName: 'Demo admin',
  roleCode: 'ADMIN',
  permissions: [
    'SUMMARY_READ',
    'DEFINITION_READ',
    'SENSITIVE_READ',
    'CONFIGURATION_WRITE',
    'SELF_PASSWORD_CHANGE',
  ],
  mustChangePassword: false,
}
export async function setup(page: Page, permissions = admin.permissions) {
  const writes: { path: string; body: Record<string, unknown> }[] = []
  await page.route('**/api/notification-preview', async (route) => {
    const r = route.request().postDataJSON()
    expect(r).not.toHaveProperty('webhook')
    expect(r).not.toHaveProperty('secret')
    const text = '### iJob · Preview\n\n**Actual template body**\n\n- Value: 998'
    const payload =
      r.type === 'WECOM'
        ? r.format === 'text'
          ? {
              msgtype: 'text',
              text: {
                content: 'Actual template body 998',
                mentioned_list: r.members ?? [],
                mentioned_mobile_list: r.mobiles ?? [],
              },
            }
          : { msgtype: 'markdown', markdown: { content: text } }
        : r.type === 'FEISHU'
          ? {
              card: {
                header: { title: { content: 'Card title' } },
                elements: [{ text: { content: text } }],
              },
            }
          : r.type === 'SLACK'
            ? { blocks: [{ type: 'section', text: { type: 'plain_text', text } }] }
            : r.type === 'DISCORD'
              ? { content: '', embeds: [{ title: 'Embed title', description: text }] }
              : r.type === 'EMAIL'
                ? { html: '<html><body><h1>Actual email</h1>998</body></html>' }
                : { markdown: { text }, at: { isAtAll: false, atMobiles: [] } }
    await route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { type: r.type, source: r.source, subject: 'Preview', payload },
      },
    })
  })
  await page.route(
    /\/api\/(?:auth|resources|calendars|notify-configs)(?:[/?]|$)/,
    async (route) => {
      const req = route.request(),
        url = new URL(req.url())
      if (req.method() !== 'GET') {
        writes.push({ path: url.pathname, body: req.postDataJSON() ?? {} })
        return route.fulfill({
          json: {
            success: true,
            code: 200,
            data: url.pathname.endsWith('test-send')
              ? { status: 'DELIVERED' }
              : url.pathname.endsWith('connection-test')
                ? { successful: true }
                : '1001',
          },
        })
      }
      const list = (rows: unknown[]) => ({
        list: rows,
        total: rows.length,
        pageNum: 1,
        pageSize: 20,
        pages: 1,
      })
      const data =
        url.pathname === '/api/auth/me'
          ? { ...admin, permissions }
          : url.pathname === '/api/calendars/context'
            ? { today: '2026-09-21', timezone: 'Asia/Shanghai' }
            : url.pathname === '/api/resources'
              ? list([resource])
              : url.pathname === '/api/notify-configs'
                ? list([email, ding])
                : url.pathname === '/api/calendars'
                  ? list([calendar])
                  : url.pathname.endsWith('/preview')
                    ? Array.from({ length: 14 }, (_, i) => {
                        const date = new Date(Date.UTC(2026, 8, 21 + i)),
                          text = date.toISOString().slice(0, 10),
                          exceptionType =
                            text === '2026-09-25'
                              ? 'HOLIDAY'
                              : text === '2026-09-26'
                                ? 'WORKDAY'
                                : null
                        return {
                          date: text,
                          allowed: exceptionType
                            ? exceptionType === 'WORKDAY'
                            : ![0, 6].includes(date.getUTCDay()),
                          exceptionType,
                        }
                      })
                    : url.pathname.endsWith(resource.id)
                      ? resource
                      : url.pathname.endsWith(email.id)
                        ? email
                        : url.pathname.endsWith(ding.id)
                          ? ding
                          : calendar
      return route.fulfill({ json: { success: true, code: 200, data } })
    },
  )
  return writes
}
test('calendar read-only and revoked access hide write actions and old content', async ({
  page,
}) => {
  await setup(page, ['DEFINITION_READ'])
  await page.goto('/calendars')
  await expect(page.locator('.calendar-day')).toHaveCount(14)
  await expect(page.getByRole('button', { name: '新增日历', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0)
  await page.unroute(/\/api\/(?:auth|resources|calendars|notify-configs)(?:[/?]|$)/)
  await setup(page)
  await page.reload()
  await page.getByRole('button', { name: '新增日历', exact: true }).click()
  await page.locator('#calendar-name').fill('Access revoked')
  await page.route('**/api/calendars', (route) =>
    route.fulfill({ status: 403, json: { success: false, code: 403 } }),
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(page.locator('.calendar-layout')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新增日历', exact: true })).toHaveCount(0)
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`configuration surfaces ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US',
        errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.goto('/resources')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo orders', exact: true })
        .click()
      await expect(
        page.getByText(en ? 'Configured · Keep existing' : '已配置 · 保持不变').first(),
      ).toBeVisible()
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('resource-edit.png'),
        fullPage: true,
      })
      await page
        .locator('.n-drawer-footer')
        .getByRole('button', { name: en ? 'Cancel' : '取消', exact: true })
        .click()
      await page.goto('/notifications')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo email', exact: true })
        .click()
      await expect(page.locator('#config-from')).toHaveValue(email.config.from)
      await expect(page.locator('#config-to')).toHaveValue(email.config.to.join('\n'))
      await expect(page.locator('#config-cc')).toHaveValue(email.config.cc.join('\n'))
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('email-edit.png'),
        fullPage: true,
      })
      await page.setViewportSize({ width: 390, height: 844 })
      await expect(
        page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }),
      ).toBeInViewport()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('email-mobile.png'),
        fullPage: true,
      })
      await page
        .locator('.n-drawer-footer')
        .getByRole('button', { name: en ? 'Cancel' : '取消', exact: true })
        .click()
      await page.setViewportSize({ width: 1366, height: 768 })
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo DingTalk', exact: true })
        .click()
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('ding-edit.png'),
        fullPage: true,
      })
      await page
        .locator('.n-drawer-footer')
        .getByRole('button', { name: en ? 'Cancel' : '取消', exact: true })
        .click()
      await page.goto('/calendars')
      await page.getByRole('button', { name: 'Demo calendar', exact: true }).click()
      await expect(page.locator('.calendar-day')).toHaveCount(14)
      await expect(page.locator('.calendar-day[aria-label="2026-09-25"]')).toContainText(
        en ? 'Rest' : '休息',
      )
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('calendar.png'),
        fullPage: true,
      })
      await page.getByRole('button', { name: en ? 'Edit' : '编辑', exact: true }).click()
      await expect(page.locator('.calendar-exception')).toHaveCount(2)
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('calendar-edit.png'),
        fullPage: true,
      })
      expect(errors).toEqual([])
    })
  }
test('resource edit submits visible connection fields and explicit password clear', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources')
  await page.getByRole('button', { name: '编辑 Demo orders', exact: true }).click()
  await page.getByLabel('名称', { exact: false }).last().fill('Renamed')
  await page.getByRole('button', { name: '清除', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.path).toBe('/api/resources/' + resource.id)
  expect(writes[0]?.body.dbConfig).toEqual({
    preserveExisting: true,
    dbType: 'POSTGRESQL',
    schema: 'public',
    jdbcUrl: resource.config.jdbcUrl,
    username: resource.config.username,
    clearPassword: true,
  })
  expect(writes).toHaveLength(1)
})
test('mail edit preserves recipients, clears cc and never sends on save', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '编辑 Demo email', exact: true }).click()
  await page.locator('#config-cc').fill('')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.emailConfig).toEqual({
    mailChannelId: '1001',
    preserveExisting: true,
    from: email.config.from,
    to: email.config.to,
    cc: [],
  })
  expect(writes).toHaveLength(1)
})
test('empty new resource shows inline requirements and focuses name', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/resources')
  await page.getByRole('button', { name: '新增数据库资源', exact: true }).click()
  await expect(page.getByText('必填', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('必填', { exact: true })).toHaveCount(4)
  await expect(page.locator('#config-name')).toBeFocused()
  expect(writes).toHaveLength(0)
})
test('new notification defaults to email; optional DingTalk secret can be omitted and type switching retains drafts', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '新增通知配置', exact: true }).click()
  const drawer = page.locator('.n-drawer')
  await expect(drawer.locator('#config-from')).toBeVisible()
  await expect(drawer.locator('#config-from')).toBeDisabled()
  await drawer.locator('#config-to').fill('draft@example.test')
  await drawer.locator('#config-name').fill('New bot')
  await drawer.getByRole('combobox', { name: '类型', exact: true }).click()
  await page.getByRole('option', { name: '钉钉', exact: true }).click()
  await drawer.locator('#config-webhook').fill('https://example.test/hook')
  await drawer.getByRole('combobox', { name: '类型', exact: true }).click()
  await page.getByRole('option', { name: '邮件', exact: true }).click()
  await expect(drawer.locator('#config-to')).toHaveValue('draft@example.test')
  await drawer.getByRole('combobox', { name: '类型', exact: true }).click()
  await page.getByRole('option', { name: '钉钉', exact: true }).click()
  await expect(drawer.locator('#config-webhook')).toHaveValue('https://example.test/hook')
  await expect(drawer.locator('#config-name')).toHaveValue('New bot')
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.dingTalkConfig).toEqual({
    preserveExisting: false,
    webhook: 'https://example.test/hook',
    clearSecret: false,
    atAll: false,
    atMobiles: [],
  })
})
test('calendar rejects duplicates and preserves both exceptions on name change', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/calendars')
  await page.getByRole('button', { name: 'Demo calendar', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByLabel('日期 2', { exact: true }).fill('2026-09-25')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.error-summary')).toContainText('重复')
  expect(writes).toHaveLength(0)
  await page.getByLabel('日期 2', { exact: true }).fill('2026-09-26')
  await page.locator('#calendar-name').fill('Renamed calendar')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.exceptions).toEqual(calendar.exceptions)
})
test('tests require explicit action and test send validates without automatic retries', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '测试发送', exact: true }).first().click()
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '发送测试消息', exact: true }).click()
  await expect(page.getByText('必填', { exact: true })).toHaveCount(2)
  expect(writes).toHaveLength(0)
  await page.locator('#test-subject').fill('Test only')
  await page.locator('#test-content').fill('Demonstration message')
  await page.getByRole('button', { name: '发送测试消息', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
  expect(writes[0]?.path).toBe('/api/notify-configs/' + email.id + '/test-send')
})
test('read-only permissions hide writes; 403 clears sensitive drawer and list', async ({
  page,
}) => {
  await setup(page, ['DEFINITION_READ', 'SENSITIVE_READ'])
  await page.goto('/resources')
  await expect(page.locator('.resource-identity').filter({ hasText: 'Demo orders' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新增数据库资源', exact: true })).toHaveCount(0)
  await page.unroute(/\/api\/(?:auth|resources|calendars|notify-configs)(?:[/?]|$)/)
  await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '编辑 Demo email', exact: true }).click()
  await page.route('**/api/notify-configs/' + email.id, (route) =>
    route.fulfill({ status: 403, json: { success: false, code: 403 } }),
  )
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.locator('#config-name').fill('Changed before permission rejection')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(page.getByRole('cell', { name: 'Demo email', exact: true })).toHaveCount(0)
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`unified calendar table and compact preview ${theme}/${locale}`, async ({
      page,
    }, info) => {
      const en = locale === 'en-US'
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/calendars')
      await expect(page.locator('.calendar-day')).toHaveCount(14)
      expect(
        (await page.locator('.calendar-choice').first().boundingBox())!.height,
      ).toBeLessThanOrEqual(52)
      expect(
        (await page.locator('.calendar-day').first().boundingBox())!.height,
      ).toBeLessThanOrEqual(64)
      const start = (await page.locator('#preview-from').boundingBox())!
      const hint = (await page.locator('.calendar-preview-hint').boundingBox())!
      expect(hint.y).toBeGreaterThan(start.y + start.height)
      expect(
        Math.abs(hint.x - (await page.locator('.calendar-preview-toolbar').boundingBox())!.x),
      ).toBeLessThan(2)
      await page.screenshot({ path: info.outputPath('calendar-unified.png'), fullPage: true })
      const edit = page.getByRole('button', { name: en ? 'Edit' : '编辑', exact: true })
      await edit.click()
      const drawer = page.locator('.calendar-drawer')
      await expect(drawer).toHaveCSS('width', '640px')
      await expect(
        drawer.getByRole('button', { name: /Language:|语言:|Theme:|主题:/ }),
      ).toHaveCount(0)
      await expect(drawer.locator('table tbody tr')).toHaveCount(2)
      await page.screenshot({ path: info.outputPath('calendar-table.png'), animations: 'disabled' })
      for (const viewport of [
        { width: 655, height: 480 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await drawer
          .getByRole('textbox', { name: en ? 'Date 2' : '日期 2', exact: true })
          .scrollIntoViewIfNeeded()
        await expect(drawer.locator('.calendar-editor-close')).toBeInViewport()
        await expect(
          drawer.getByRole('button', { name: en ? 'Save' : '保存', exact: true }),
        ).toBeInViewport()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(viewport.width)
        await page.screenshot({
          path: info.outputPath(`calendar-table-${viewport.width}.png`),
          animations: 'disabled',
        })
      }
      await page.keyboard.press('Escape')
      await expect(drawer).toHaveCount(0)
      await expect(edit).toBeFocused()
    })
  }

test('calendar range addition preserves existing types and all pages, validates hidden errors and keeps search after save', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/calendars')
  await page.locator('#calendar-filter').fill('Demo')
  await page.locator('.calendar-search').getByRole('button').click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const drawer = page.locator('.calendar-drawer')
  await drawer.getByRole('button', { name: '批量添加', exact: true }).click()
  await drawer.locator('#exception-range-from').fill('2026-09-24')
  await drawer.locator('#exception-range-to').fill('2026-10-20')
  await drawer.getByRole('button', { name: '添加到表格', exact: true }).click()
  await expect(drawer.getByRole('status')).toContainText('已添加 25 条，跳过已有日期 2 条')
  await page.screenshot({ path: info.outputPath('calendar-batch-add.png'), animations: 'disabled' })
  expect(writes).toHaveLength(0)
  await drawer.getByRole('button', { name: '添加到表格', exact: true }).click()
  await expect(drawer.getByRole('status')).toContainText('已添加 0 条，跳过已有日期 27 条')
  await drawer.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(drawer.locator('.calendar-exception')).toHaveCount(7)
  await drawer.getByRole('textbox', { name: '日期 27', exact: true }).fill('')
  await drawer.getByRole('button', { name: '上一页', exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer.getByRole('textbox', { name: '日期 27', exact: true })).toBeFocused()
  expect(writes).toHaveLength(0)
  await drawer.getByRole('textbox', { name: '日期 27', exact: true }).fill('2026-10-20')
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toHaveCount(0)
  await expect(page.locator('.n-message').getByRole('status')).toContainText('已保存')
  await expect(page.locator('#calendar-filter')).toHaveValue('Demo')
  expect(writes).toHaveLength(1)
  const exceptions = writes[0]!.body.exceptions as { date: string; type: string }[]
  expect(exceptions).toHaveLength(27)
  expect(new Set(exceptions.map((entry) => entry.date)).size).toBe(27)
  expect(exceptions.find((entry) => entry.date === '2026-09-26')?.type).toBe('WORKDAY')
})

test('calendar drawer protects drafts, prioritizes popup Escape and retains input after a failed save', async ({
  page,
}) => {
  await setup(page)
  let fail = true,
    calls = 0
  let release: (() => void) | undefined
  await page.route('**/api/calendars/' + calendar.id, async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback()
    calls++
    if (fail)
      return route.fulfill({
        status: 400,
        json: { success: false, code: 400, message: 'Calendar input rejected' },
      })
    await new Promise<void>((resolve) => {
      release = resolve
    })
    return route.fulfill({ json: { success: true, code: 200, data: null } })
  })
  await page.goto('/calendars')
  const edit = page.getByRole('button', { name: '编辑', exact: true })
  await edit.click()
  const drawer = page.locator('.calendar-drawer')
  await page.locator('.n-drawer-mask').click({ position: { x: 20, y: 100 } })
  await expect(drawer).toBeVisible()
  const date = drawer.getByRole('textbox', { name: '日期 1', exact: true })
  await date.press('Alt+ArrowDown')
  await expect(page.locator('.business-date-panel')).toBeVisible()
  await date.press('Escape')
  await expect(page.locator('.business-date-panel')).not.toBeVisible()
  await expect(drawer).toBeVisible()
  await drawer.getByRole('combobox', { name: '类型 1', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(drawer).toBeVisible()
  await drawer.locator('#calendar-name').fill('Draft stays')
  await drawer.locator('.calendar-editor-close').click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(drawer.locator('#calendar-name')).toHaveValue('Draft stays')
  await page.keyboard.press('Escape')
  await expect(page.locator('.n-dialog')).toContainText('放弃')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-message').getByRole('alert')).toBeVisible()
  await expect(drawer.locator('#calendar-name')).toHaveValue('Draft stays')
  fail = false
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer.locator('.calendar-editor-close')).toBeDisabled()
  await expect(drawer.getByRole('button', { name: '取消', exact: true })).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(drawer).toBeVisible()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  expect(calls).toBe(2)
  release!()
  await expect(drawer).toHaveCount(0)
  await expect(edit).toBeFocused()
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`unified database resources ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/resources/databases')
      await expect(
        page.getByRole('heading', { name: en ? 'Database resources' : '数据库资源', exact: true }),
      ).toBeVisible()
      const parent = page.getByRole('button', { name: en ? 'Resources' : '资源管理', exact: true })
      const child = page.getByRole('link', {
        name: en ? 'Database resources' : '数据库资源',
        exact: true,
      })
      await expect(parent).toHaveAttribute('aria-expanded', 'true')
      await parent.click()
      await expect(child).toBeHidden()
      await parent.click()
      await expect(child).toBeVisible()
      await expect(child).toHaveAttribute('href', '/resources/databases')
      await expect(
        page.getByRole('columnheader', { name: en ? 'Type' : '类型', exact: true }),
      ).toHaveCount(0)
      expect(
        (await page.locator('.resource-table tbody tr').boundingBox())!.height,
      ).toBeLessThanOrEqual(64)
      await page.screenshot({
        path: info.outputPath('resources-unified.png'),
        animations: 'disabled',
      })
      const edit = page.getByRole('button', {
        name: (en ? 'Edit' : '编辑') + ' Demo orders',
        exact: true,
      })
      await edit.click()
      await expect(page.locator('.resource-drawer')).toBeVisible()
      expect((await page.locator('.resource-drawer').boundingBox())!.width).toBe(520)
      await expect(page.locator('#config-type')).toHaveCount(0)
      await expect(page.locator('#config-jdbcUrl')).toHaveValue(resource.config.jdbcUrl)
      await expect(page.locator('#config-username')).toHaveValue(resource.config.username)
      const enableLabel = await page.locator('.resource-enabled label').boundingBox()
      const enableSwitch = await page.locator('.resource-enabled').getByRole('switch').boundingBox()
      expect(enableSwitch!.y).toBeGreaterThan(enableLabel!.y + enableLabel!.height)
      expect(Math.abs(enableSwitch!.x - enableLabel!.x)).toBeLessThan(2)
      await expect(page.locator('.resource-drawer .compact-preferences')).toHaveCount(0)
      await expect(page.locator('#config-jdbcUrl-hint')).toContainText('jdbc:postgresql://')
      await page.screenshot({
        path: info.outputPath('resources-editor.png'),
        animations: 'disabled',
      })
      await page.setViewportSize({ width: 655, height: 480 })
      await expect(
        page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }),
      ).toBeInViewport()
      await page.screenshot({
        path: info.outputPath('resources-editor-short.png'),
        animations: 'disabled',
      })
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      await page.screenshot({
        path: info.outputPath('resources-editor-mobile.png'),
        animations: 'disabled',
      })
      await page.keyboard.press('Escape')
      await expect(page.locator('.n-drawer')).toHaveCount(0)
      await expect(edit).toBeFocused()
      const scroll = page.locator('.resource-table-scroll')
      await scroll.evaluate((el) => {
        el.scrollLeft = el.scrollWidth
      })
      await expect(edit).toBeInViewport()
      const cell = await page.locator('td.resource-actions-cell').boundingBox(),
        box = await scroll.boundingBox()
      expect(Math.abs(cell!.x + cell!.width - box!.x - box!.width)).toBeLessThanOrEqual(2)
      await page.screenshot({
        path: info.outputPath('resources-list-mobile.png'),
        animations: 'disabled',
      })
      await page.goto('/resources')
      await expect(
        page.getByRole('heading', { name: en ? 'Database resources' : '数据库资源', exact: true }),
      ).toBeVisible()
    })
  }

test('database JDBC help follows selection, remains visible after typing, and restores drafts', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/resources/databases')
  await page.getByRole('button', { name: '新增数据库资源', exact: true }).click()
  await expect(page.locator('#config-type')).toHaveCount(0)
  await expect(page.locator('#config-jdbcUrl-hint')).toContainText('选择数据库')
  await page.locator('#config-name').fill('Database hints')
  const choices = [
    ['MySQL', 'jdbc:mysql://localhost:3306/database'],
    ['PostgreSQL', 'jdbc:postgresql://localhost:5432/database'],
    ['Oracle', 'jdbc:oracle:thin:@//localhost:1521/service'],
    ['SQL Server', 'jdbc:sqlserver://localhost:1433;databaseName=database'],
  ]
  for (const [index, [name, example]] of choices.entries()) {
    await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
    await page.getByRole('option', { name: name!, exact: true }).click()
    if (index) {
      await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
      await expect(page.locator('.n-dialog')).toHaveCount(0)
    }
    await expect(page.locator('#config-jdbcUrl-hint')).toContainText(example!)
    await expect(page.locator('#config-jdbcUrl')).toHaveValue('')
    await page.locator('#config-jdbcUrl').fill(example!)
    await page.locator('#config-username').fill('demo')
    await expect(page.locator('#config-jdbcUrl-hint')).toBeVisible()
    await page.screenshot({
      path: info.outputPath('jdbc-' + index + '.png'),
      animations: 'disabled',
    })
  }
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.getByRole('option', { name: 'MySQL', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(page.locator('#config-jdbcUrl-hint')).toContainText('jdbc:sqlserver:')
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.getByRole('option', { name: 'MySQL', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(page.locator('#config-jdbcUrl')).toHaveValue(choices[0]![1]!)
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.type).toBe('DB')
  expect(writes[0]?.body.dbConfig).toMatchObject({
    dbType: 'MYSQL',
    jdbcUrl: choices[0]![1],
    username: 'demo',
  })
})

test('resource bulk deletion retains failures and clears selection on refresh and search', async ({
  page,
}) => {
  await setup(page)
  const rows = [
    { ...resource, id: '41', name: 'Unused database' },
    { ...resource, id: '42', name: 'Referenced database' },
  ]
  const requests: URL[] = [],
    deletes: string[] = []
  await page.route('**/api/resources?*', (route) => {
    requests.push(new URL(route.request().url()))
    return route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: rows, total: rows.length, pageNum: 1, pageSize: 20, pages: 1 },
      },
    })
  })
  await page.route(/\/api\/resources\/(41|42)$/, (route) => {
    const id = route.request().url().split('/').at(-1)!
    deletes.push(id)
    if (id === '42')
      return route.fulfill({
        status: 409,
        json: {
          success: false,
          code: 409,
          messageKey: 'error.business.resource-is-still-referenced-by-task-definitions',
        },
      })
    rows.splice(0, 1)
    return route.fulfill({ json: { success: true, code: 200 } })
  })
  await page.goto('/resources/databases')
  await page.getByRole('checkbox', { name: '选择当前页', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('Unused database、Referenced database')
  await page.locator('.n-dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('已删除 1 项')
  await expect(page.getByRole('alert')).toContainText('Referenced database')
  await expect(page.getByRole('alert')).toContainText('资源仍被任务引用')
  await expect(
    page.getByRole('checkbox', { name: '选择 Referenced database', exact: true }),
  ).toBeChecked()
  expect(deletes).toEqual(['41', '42'])
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(
    page.getByRole('checkbox', { name: '选择 Referenced database', exact: true }),
  ).not.toBeChecked()
  await page.getByRole('checkbox', { name: '选择当前页', exact: true }).check()
  await page.locator('#filter-name').fill('Exact name')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: '选择当前页', exact: true })).not.toBeChecked()
  await expect.poll(() => requests.at(-1)?.searchParams.get('name')).toBe('Exact name')
  expect(requests.every((url) => url.searchParams.get('type') === 'DB')).toBe(true)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.locator('#filter-name')).toHaveValue('')
  await expect.poll(() => requests.at(-1)?.searchParams.get('name')).toBeNull()
})

test('resource drawer preserves drafts on failure, guards close and restores focus and filters', async ({
  page,
}) => {
  await setup(page)
  let attempts = 0,
    release: () => void = () => {}
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/resources/' + resource.id, async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback()
    attempts++
    if (attempts === 1) return route.fulfill({ status: 400, json: { success: false, code: 400 } })
    await pending
    return route.fulfill({ json: { success: true, code: 200 } })
  })
  await page.goto('/resources')
  await page.locator('#filter-name').fill('Demo')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const edit = page.getByRole('button', { name: '编辑 Demo orders', exact: true })
  await edit.click()
  await page.locator('.n-drawer-mask').click({ position: { x: 10, y: 150 } })
  await expect(page.locator('.resource-drawer')).toBeVisible()
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('listbox')).toHaveCount(0)
  await expect(page.locator('.resource-drawer')).toBeVisible()
  await page.locator('#config-name').fill('Unsaved database')
  await page.locator('.resource-editor-close').click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await page.keyboard.press('Escape')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.locator('#config-name')).toHaveValue('Unsaved database')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.resource-editor-close')).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(page.locator('.resource-drawer')).toBeVisible()
  expect(attempts).toBe(2)
  release()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(edit).toBeFocused()
  await expect(page.locator('#filter-name')).toHaveValue('Demo')
})

test('resource read-only and revoked access hide writes and old content', async ({ page }) => {
  await setup(page, ['DEFINITION_READ'])
  await page.goto('/resources/databases')
  await expect(page.locator('.resource-identity').filter({ hasText: 'Demo orders' })).toBeVisible()
  await expect(page.locator('.resource-create')).toHaveCount(0)
  await expect(page.getByRole('checkbox')).toHaveCount(0)
  await page.unroute(/\/api\/(?:auth|resources|calendars|notify-configs)(?:[/?]|$)/)
  await setup(page)
  await page.reload()
  await page.getByRole('button', { name: '编辑 Demo orders', exact: true }).click()
  await page.route('**/api/resources/' + resource.id, (route) =>
    route.fulfill({ status: 403, json: { success: false, code: 403 } }),
  )
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.locator('#config-name').fill('Changed before permission rejection')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(page.locator('.resource-list-panel')).toHaveCount(0)
  await expect(page.locator('.resource-create')).toHaveCount(0)
})

test('resource page selection never crosses pages and connection test requires confirmation', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.route('**/api/resources?*', (route) => {
    const url = new URL(route.request().url()),
      pageNum = Number(url.searchParams.get('pageNum'))
    const rows = Array.from({ length: pageNum === 1 ? 20 : 1 }, (_, index) => ({
      ...resource,
      id: String((pageNum - 1) * 20 + index + 1),
      name: 'Database ' + ((pageNum - 1) * 20 + index + 1),
    }))
    return route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: rows, total: 21, pageNum, pageSize: 20, pages: 2 },
      },
    })
  })
  await page.goto('/resources/databases')
  await page.getByRole('checkbox', { name: '选择当前页', exact: true }).check()
  await expect(page.locator('.resource-toolbar')).toContainText('已选 20 项')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.resource-identity').filter({ hasText: 'Database 21' })).toBeVisible()
  await expect(page.locator('.resource-toolbar')).toContainText('已选 0 项')
  await page.getByRole('checkbox', { name: '选择当前页', exact: true }).check()
  await page.getByRole('combobox', { name: '每页条数', exact: true }).click()
  await page.getByRole('option', { name: '50', exact: true }).click()
  await expect(page.locator('.resource-toolbar')).toContainText('已选 0 项')
  await page.getByRole('button', { name: '连接测试', exact: true }).first().click()
  expect(writes).toHaveLength(0)
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '连接测试', exact: true }).first().click()
  await page.locator('.n-dialog').getByRole('button', { name: '连接测试', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('本次连接测试成功')
  expect(writes.map((row) => row.path)).toEqual(['/api/resources/1/connection-test'])
})

test('resource saved password stays hidden while only newly entered passwords can be revealed', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources/databases')
  await page.getByRole('button', { name: '编辑 Demo orders', exact: true }).click()
  await expect(page.locator('#config-password')).toHaveCount(0)
  await expect(page.locator('#config-jdbcUrl')).toHaveValue(resource.config.jdbcUrl)
  await expect(page.locator('#config-username')).toHaveValue(resource.config.username)
  await page
    .locator('.resource-drawer')
    .getByRole('button', { name: '修改密码', exact: true })
    .click()
  const password = page.locator('#config-password')
  await expect(password).toHaveValue('')
  await password.fill('new-test-password')
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '显示本次输入的密码', exact: true }).click()
  await expect(password).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '隐藏本次输入的密码', exact: true }).press('Enter')
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '保持不变', exact: true }).click()
  await expect(password).toHaveCount(0)
  await page
    .locator('.resource-drawer')
    .getByRole('button', { name: '修改密码', exact: true })
    .click()
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '保持不变', exact: true }).click()
  await page.locator('#config-username').fill('changed-user')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.dbConfig).toMatchObject({ username: 'changed-user', clearPassword: false })
  expect(writes[0]?.body.dbConfig).not.toHaveProperty('password')
  await page.getByRole('button', { name: '新增数据库资源', exact: true }).click()
  await password.fill('new-resource-password')
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '显示本次输入的密码', exact: true }).click()
  await expect(password).toHaveAttribute('type', 'text')
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`unified notification layout ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/notifications')
      await expect(page.locator('.notification-table tbody tr')).toHaveCount(2)
      expect(
        (await page.locator('.notification-table tbody tr').first().boundingBox())!.height,
      ).toBeLessThanOrEqual(48)
      await page.screenshot({
        path: info.outputPath('notifications-list.png'),
        animations: 'disabled',
      })
      const edit = page.getByRole('button', {
        name: (en ? 'Edit' : '编辑') + ' Demo DingTalk',
        exact: true,
      })
      await edit.click()
      const drawer = page.locator('.notification-drawer')
      await expect(drawer).toHaveCSS('width', '520px')
      await expect(
        drawer.getByRole('button', { name: /Language:|语言:|Theme:|主题:/ }),
      ).toHaveCount(0)
      await expect(page.locator('#config-mobiles')).toHaveValue(ding.config.atMobiles.join('\n'))
      await expect(page.locator('.masked-webhook')).toHaveText(ding.config.webhookDisplay)
      await expect(page.locator('#config-webhook')).toHaveCount(0)
      await expect(page.locator('#config-secret')).toHaveCount(0)
      for (const selector of ['.notification-enabled', '.notification-at-all']) {
        const label = (await page.locator(selector).locator('label').boundingBox())!
        const control = (await page.locator(selector).getByRole('switch').boundingBox())!
        if (selector === '.notification-enabled') {
          expect(control.y).toBeGreaterThan(label.y + label.height)
          expect(Math.abs(control.x - label.x)).toBeLessThan(2)
        } else {
          expect(control.x).toBeGreaterThan(label.x + label.width)
          expect(
            Math.abs(label.y + label.height / 2 - control.y - control.height / 2),
          ).toBeLessThan(2)
        }
      }
      await page.screenshot({
        path: info.outputPath('notifications-bot.png'),
        animations: 'disabled',
      })
      for (const viewport of [
        { width: 655, height: 480 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await page.locator('#config-mobiles').scrollIntoViewIfNeeded()
        await expect(drawer.locator('.notification-editor-close')).toBeInViewport()
        await expect(
          drawer.getByRole('button', { name: en ? 'Save' : '保存', exact: true }),
        ).toBeInViewport()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(viewport.width)
        await page.screenshot({
          path: info.outputPath(`notifications-bot-${viewport.width}.png`),
          animations: 'disabled',
        })
      }
      await page.keyboard.press('Escape')
      await expect(drawer).toHaveCount(0)
      await expect(edit).toBeFocused()
      const action = page.locator('tbody .notification-actions-cell').first()
      const before = (await action.boundingBox())!.x
      await page.locator('.notification-table-scroll').evaluate((el) => {
        el.scrollLeft = el.scrollWidth
      })
      expect(Math.abs((await action.boundingBox())!.x - before)).toBeLessThan(2)
      await expect(action).toHaveCSS('position', 'sticky')
    })
  }

test('DingTalk keeps saved credentials, echoes mobiles and reveals only the entered replacement URL', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/notifications')
  await page.getByRole('button', { name: '编辑 Demo DingTalk', exact: true }).click()
  await page.locator('#config-mobiles').fill('13700000000\n13600000000')
  await page.getByRole('switch', { name: '通知所有人', exact: true }).click()
  await expect(page.locator('#config-mobiles')).toHaveValue('13700000000\n13600000000')
  await expect(page.locator('.notification-drawer').getByRole('status')).toContainText(
    '已填写的指定手机号会保留',
  )
  await page.getByRole('button', { name: '替换地址', exact: true }).click()
  const url = page.locator('#config-webhook')
  await expect(url).toHaveValue('')
  await url.fill('https://example.test/new?access_token=new-token')
  await expect(url).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '显示本次输入的地址', exact: true }).click()
  await expect(url).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '隐藏本次输入的地址', exact: true }).press('Enter')
  await expect(url).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '保持不变', exact: true }).click()
  await expect(url).toHaveCount(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[0]?.body.dingTalkConfig).toEqual({
    preserveExisting: true,
    clearSecret: false,
    atAll: true,
    atMobiles: ['13700000000', '13600000000'],
  })
  expect(JSON.stringify(writes)).not.toContain('••••')
  expect(JSON.stringify(writes)).not.toContain('new-token')
  await page.getByRole('button', { name: '编辑 Demo DingTalk', exact: true }).click()
  await page.getByRole('button', { name: '替换地址', exact: true }).click()
  await url.fill('https://example.test/new?access_token=replacement')
  await page.getByRole('button', { name: '清除', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(writes[1]?.body.dingTalkConfig).toMatchObject({
    webhook: 'https://example.test/new?access_token=replacement',
    clearSecret: true,
  })
  expect(writes).toHaveLength(2)
})

test('notification bulk deletion retains failures and search clears selection with server filters', async ({
  page,
}) => {
  await setup(page)
  const rows = [
    { ...email, id: '41', name: 'Unused email' },
    { ...ding, id: '42', name: 'Referenced bot' },
  ]
  const requests: URL[] = [],
    deletes: string[] = []
  await page.route('**/api/notify-configs?*', (route) => {
    requests.push(new URL(route.request().url()))
    return route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: rows, total: rows.length, pageNum: 1, pageSize: 20, pages: 1 },
      },
    })
  })
  await page.route(/\/api\/notify-configs\/(41|42)$/, (route) => {
    const id = route.request().url().split('/').at(-1)!
    deletes.push(id)
    if (id === '42')
      return route.fulfill({
        status: 409,
        json: {
          success: false,
          code: 409,
          messageKey: 'error.detail.notify-config-is-still-referenced-by-bindings',
        },
      })
    rows.splice(0, 1)
    return route.fulfill({ json: { success: true, code: 200 } })
  })
  await page.goto('/notifications')
  await page.getByRole('checkbox', { name: '选择当前页', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('Unused email、Referenced bot')
  await page.locator('.n-dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Referenced bot')
  await expect(page.getByRole('alert')).toContainText('通知配置仍被绑定引用')
  await expect(
    page.getByRole('checkbox', { name: '选择 Referenced bot', exact: true }),
  ).toBeChecked()
  expect(deletes).toEqual(['41', '42'])
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(
    page.getByRole('checkbox', { name: '选择 Referenced bot', exact: true }),
  ).not.toBeChecked()
  await page.getByRole('checkbox', { name: '选择当前页', exact: true }).check()
  await page.locator('#filter-name').fill('bot')
  await page.getByRole('combobox', { name: '类型', exact: true }).click()
  await page.getByRole('option', { name: '钉钉', exact: true }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: '选择当前页', exact: true })).not.toBeChecked()
  await expect.poll(() => requests.at(-1)?.searchParams.get('type')).toBe('DINGTALK')
  expect(requests.at(-1)?.searchParams.get('name')).toBe('bot')
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('type')).toBeNull()
  await expect(page.locator('#filter-name')).toHaveValue('')
})

test('notification failures retain drafts, guard busy close and never retry test sending automatically', async ({
  page,
}) => {
  await setup(page)
  let attempts = 0,
    sends = 0,
    release: () => void = () => {}
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/notify-configs/' + email.id, async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback()
    attempts++
    if (attempts === 1) return route.fulfill({ status: 400, json: { success: false, code: 400 } })
    await pending
    return route.fulfill({ json: { success: true, code: 200 } })
  })
  await page.route('**/api/notify-configs/*/test-send', (route) => {
    sends++
    return route.fulfill({ status: 500, json: { success: false, code: 500 } })
  })
  await page.goto('/notifications')
  const edit = page.getByRole('button', { name: '编辑 Demo email', exact: true })
  await edit.click()
  await page.locator('.n-drawer-mask').click({ position: { x: 10, y: 150 } })
  await expect(page.locator('.notification-drawer')).toBeVisible()
  await page.locator('#config-cc').fill('changed@example.test')
  await page.keyboard.press('Escape')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.locator('#config-cc')).toHaveValue('changed@example.test')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.notification-editor-close')).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(page.locator('.notification-drawer')).toBeVisible()
  expect(attempts).toBe(2)
  release()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(edit).toBeFocused()
  await page.getByRole('button', { name: '测试发送', exact: true }).first().click()
  await page.locator('#test-subject').fill('Test only')
  await page.locator('#test-content').fill('Demonstration message')
  await page.getByRole('button', { name: '发送测试消息', exact: true }).click()
  await expect(page.getByRole('button', { name: '发送测试消息', exact: true })).toBeEnabled()
  await expect.poll(() => sends).toBe(1)
  await expect(page.locator('#test-content')).toHaveValue('Demonstration message')
  await page.keyboard.press('Escape')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  expect(sends).toBe(1)
})
