import { test, expect, type Page } from '@playwright/test'
const scheduleId = '9223372036854775807'
const metadata = {
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
const example = {
  id: scheduleId,
  targetId: '19040',
  targetName: '每日汇总任务',
  targetType: 'TASK',
  scheduleType: 'CRON',
  cronExpr: '0 0 9 * * *',
  intervalSec: null,
  calendarId: '2002',
  enabled: false,
  startTime: '2026-09-28T00:00:00',
  endTime: null,
  nextFireTime: null,
  lastFireTime: null,
  createTime: '2026-09-26T00:00:00',
  updateTime: '2026-09-26T00:00:00',
  misfirePolicy: null,
  misfireGraceSec: null,
  maxCatchupCount: null,
  maxQueueDelaySec: null,
  variables: { region: 'local' },
}
async function setup(page: Page, readOnly = false) {
  const state = {
    writes: [] as { method: string; body: string; path: string }[],
    reads: [] as string[],
    preview: [] as string[],
    failMetadata: false,
    failCalendar: false,
    failSave: false,
    deleted: false,
    enabled: false,
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'schedule-admin',
          username: 'admin',
          displayName: 'Schedule admin',
          roleCode: readOnly ? 'VIEWER' : 'ADMIN',
          permissions: readOnly
            ? ['DEFINITION_READ']
            : ['DEFINITION_READ', 'CONFIGURATION_WRITE', 'SENSITIVE_READ'],
          mustChangePassword: false,
        })
      if (path === '/api/parameter-templates') return ok({ list: [], total: 0 })
      if (path.startsWith('/api/parameter-templates/bindings/')) return ok(null)
      if (path === '/api/calendars/2002')
        return state.failCalendar
          ? route.fulfill({ status: 500, json: { success: false, code: 500 } })
          : ok({ id: '2002', name: '工作日日历', workdays: ['MONDAY'] })
      if (path === '/api/schedules/metadata')
        return state.failMetadata
          ? route.fulfill({ status: 500, json: { success: false, code: 500 } })
          : ok(metadata)
      if (path === '/api/schedules/preview') {
        state.preview.push(req.postData()!)
        return ok({ items: [{ fireTime: '2026-09-29T09:00:00', calendarAllowed: false }] })
      }
      if (req.method() !== 'GET') {
        state.writes.push({ path, method: req.method(), body: req.postData() ?? '' })
        if (state.failSave)
          return route.fulfill({ status: 409, json: { success: false, code: 409 } })
        if (req.method() === 'DELETE') state.deleted = true
        if (req.method() === 'PATCH') state.enabled = JSON.parse(req.postData()!).enabled
        return ok('21002')
      }
      if (path === '/api/schedules') {
        state.reads.push(url.search)
        return ok({
          list: state.deleted ? [] : [{ ...example, enabled: state.enabled }],
          total: state.deleted ? 0 : 21,
        })
      }
      if (path === '/api/schedules/' + scheduleId) {
        const { variables, ...safe } = example
        return ok(readOnly ? safe : { ...safe, variables })
      }
      if (['/api/tasks', '/api/jobs', '/api/calendars'].includes(path))
        return ok({ list: [{ id: '19040', name: 'Demo task', enabled: true }], total: 1 })
      return route.fulfill({ status: 404, json: { success: false, code: 404 } })
    },
  )
  return state
}
test('language and theme changes preserve the open schedule draft', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/schedules')
  await page.getByRole('button', { name: '编辑 ' + scheduleId, exact: true }).click()
  const cron = page.locator('#schedule-cronExpr')
  await expect(cron).toHaveValue(example.cronExpr)
  await cron.fill('0 15 8 * * *')
  for (const control of [/^(语言|Language):/, /^(主题|Theme):/]) {
    const button = page.getByRole('button', { name: control })
    await button.evaluate((element: HTMLButtonElement) => element.click())
    await expect(cron).toHaveValue('0 15 8 * * *')
    await button.evaluate((element: HTMLButtonElement) => element.click())
    await expect(cron).toHaveValue('0 15 8 * * *')
  }
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('放弃')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(cron).toHaveValue('0 15 8 * * *')
  expect(state.writes).toHaveLength(0)
})

test('same query refreshes current page; changed filters reset page; exact IDs and read failures', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/schedules')
  await expect(page.locator('.schedule-table tbody tr')).toHaveCount(1)
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect.poll(() => state.reads.at(-1)).toContain('pageNum=2')
  const before = state.reads.length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.length).toBe(before + 1)
  expect(state.reads.at(-1)).toContain('pageNum=2')
  await page.locator('#schedule-filter-targetName').fill('每日')
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.locator('#schedule-filter-targetId').fill(scheduleId)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.at(-1)).toContain('targetId=' + scheduleId)
  expect(new URLSearchParams(state.reads.at(-1)).get('targetName')).toBe('每日')
  expect(state.reads.at(-1)).toContain('pageNum=1')
  await page.locator('#schedule-filter-targetId').fill('9223372036854775808')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('#schedule-filter-error')).toBeVisible()
  expect(state.writes).toHaveLength(0)
})
test('preview is separate from save, invalid dates block it, stale results disappear; draft close is guarded', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/schedules')
  await page.getByRole('button', { name: '新增调度', exact: true }).click()
  await page.locator('#schedule-cronExpr').fill('0 0 9 * * *')
  await page.locator('#schedule-preview-from').fill('2026-02-29 00:00:00')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  expect(state.preview).toHaveLength(0)
  await page.locator('#schedule-preview-from').fill('2026-09-28 00:00:00')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.locator('.schedule-preview-table')).toContainText('日历排除')
  expect(state.writes).toHaveLength(0)
  expect(JSON.parse(state.preview[0]!)).not.toHaveProperty('targetId')
  await page.locator('#schedule-cronExpr').fill('0 0 10 * * *')
  await expect(page.locator('.schedule-preview-table')).toHaveCount(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('放弃未保存')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('#schedule-cronExpr')).toHaveValue('0 0 10 * * *')
})
test('save remains disabled by default, inactive rule is omitted and failure preserves draft', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/schedules')
  await page.getByRole('button', { name: '新增调度', exact: true }).click()
  await page.locator('#schedule-targetId').click()
  await page.locator('.n-base-select-option').filter({ hasText: 'Demo task' }).click()
  await page.locator('label.n-radio').filter({ hasText: '固定间隔' }).click()
  await page.locator('#schedule-intervalSec').fill('300')
  state.failSave = true
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.schedule-drawer .request-error')).toBeVisible()
  await expect(page.locator('#schedule-intervalSec')).toHaveValue('300')
  expect(state.writes).toHaveLength(1)
  const data = JSON.parse(state.writes[0]!.body)
  expect(data).toMatchObject({
    enabled: false,
    scheduleType: 'INTERVAL',
    intervalSec: 300,
    cronExpr: null,
    misfirePolicy: null,
    targetId: '19040',
  })
  state.failSave = false
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.schedule-drawer')).toHaveCount(0)
  expect(state.writes).toHaveLength(2)
})
test('readers see public preview but no variable data or write controls', async ({ page }) => {
  const state = await setup(page, true)
  await page.goto('/schedules')
  await expect(page.getByRole('button', { name: '新增调度', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '详情 ' + scheduleId, exact: true }).click()
  await expect(page.locator('.schedule-drawer')).toContainText('当前身份无权查看输入变量')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.locator('.schedule-preview-table')).toContainText('日历排除')
  expect(state.writes).toHaveLength(0)
})
test('metadata failure blocks edits and retry recovers; state mutations require confirmation', async ({
  page,
}) => {
  const state = await setup(page)
  state.failMetadata = true
  await page.goto('/schedules')
  await expect(page.getByRole('button', { name: '新增调度', exact: true })).toBeDisabled()
  state.failMetadata = false
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByRole('button', { name: '新增调度', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '更多 ' + scheduleId, exact: true }).click()
  await page.getByText('启用', { exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  expect(state.writes).toHaveLength(0)
  await page.getByRole('button', { name: '更多 ' + scheduleId, exact: true }).click()
  await page.getByText('启用', { exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.method).toBe('PATCH')
  await expect(page.locator('.schedule-state')).toHaveText('已启用')
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`schedule layout ${theme} ${locale}`, async ({ page }, info) => {
      await page.addInitScript(
        ({ theme, locale }) => {
          for (const key of ['guest', 'user:schedule-admin'])
            localStorage.setItem('ijob:preferences:v1:' + key, JSON.stringify({ theme, locale }))
        },
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/schedules')
      await expect(page.locator('.schedule-table tbody tr')).toHaveCount(1)
      await expect(page.locator('.application')).toHaveClass(/compact-shell/)
      await expect(page.locator('.schedule-target-name')).toHaveText('每日汇总任务')
      await expect(page.locator('#schedule-filter-targetName')).toHaveCSS('font-size', '13px')
      await page.screenshot({ path: info.outputPath('list.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      const target = (await page.locator('td.schedule-column-target').boundingBox())!
      const actions = (await page.locator('td.schedule-sticky').boundingBox())!
      expect(target.width).toBeGreaterThan(140)
      expect(target.x + target.width).toBeLessThanOrEqual(actions.x + 1)
      expect(actions.x + actions.width).toBeLessThanOrEqual(390)
      await expect(page.locator('.schedule-mobile-state')).toBeVisible()
      await page.screenshot({ path: info.outputPath('mobile-list.png'), fullPage: true })
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page
        .getByRole('button', {
          name: (locale === 'zh-CN' ? '编辑 ' : 'Edit ') + scheduleId,
          exact: true,
        })
        .click()
      await expect(page.locator('#schedule-cronExpr')).toHaveValue('0 0 9 * * *')
      await expect(page.locator('.schedule-drawer .n-base-selection').first()).toBeVisible()
      await expect(page.locator('.schedule-drawer')).toContainText('每日汇总任务')
      await expect(page.locator('#schedule-cronExpr')).toHaveCSS('font-size', '13px')
      await page.screenshot({ path: info.outputPath('editor.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      const drawer = await page.locator('.schedule-drawer').boundingBox()
      expect(drawer!.width).toBeLessThanOrEqual(390)
    })

test('start is optional for recurring rules; once requires its time; backend defaults are explained', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/schedules')
  await page.getByRole('button', { name: '新增调度', exact: true }).click()
  await expect(page.getByLabel('开始时间（可选）', { exact: true })).not.toHaveAttribute('required')
  await page.locator('label.n-radio').filter({ hasText: '单次' }).click()
  await expect(page.getByLabel('执行时间', { exact: true })).toHaveAttribute('required', '')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.schedule-drawer')).toContainText('必填')
  await expect(page.locator('.schedule-drawer')).toContainText('未覆盖的项随之变化')
  await expect(page.locator('.schedule-drawer')).toContainText(
    '默认：补发最新一次，宽限60秒，补发上限10',
  )
})

test('calendar name resolves without candidates; schedule JSON survives switching to the parameter form', async ({
  page,
}, info) => {
  await setup(page)
  await page.goto('/schedules')
  await page.getByRole('button', { name: '编辑 ' + scheduleId, exact: true }).click()
  await expect(page.locator('.schedule-drawer')).toContainText('工作日日历 · #2002')
  await page.locator('.parameter-table').scrollIntoViewIfNeeded()
  await page.getByRole('button', { name: '高级 JSON', exact: true }).click()
  const json = page.getByRole('textbox', { name: '高级 JSON', exact: true })
  await json.fill('{"kept":42}')
  await page.getByRole('button', { name: '表单编辑', exact: true }).click()
  await expect(page.locator('.parameter-table')).toContainText('kept')
  await page.getByRole('button', { name: '高级 JSON', exact: true }).click()
  expect(JSON.parse(await json.inputValue())).toEqual({ kept: 42 })
  await page.screenshot({ path: info.outputPath('calendar-and-json.png'), animations: 'disabled' })
})
test('failed calendar lookup keeps its ID and can retry in readonly details', async ({ page }) => {
  const state = await setup(page, true)
  state.failCalendar = true
  await page.goto('/schedules')
  await page.getByRole('button', { name: '详情 ' + scheduleId, exact: true }).click()
  await expect(page.locator('.schedule-meta')).toContainText('#2002')
  await expect(page.getByRole('button', { name: '重新读取日历名称', exact: true })).toBeVisible()
  state.failCalendar = false
  await page.getByRole('button', { name: '重新读取日历名称', exact: true }).click()
  await expect(page.locator('.schedule-meta')).toContainText('工作日日历 · #2002')
  expect(state.writes).toHaveLength(0)
})
