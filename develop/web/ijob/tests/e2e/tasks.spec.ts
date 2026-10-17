import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
const taskId = '9223372036854775801',
  resourceId = '9223372036854775802'

test('editor selects, copies and cuts text in both sizes with undo and no implicit save', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  const line = await page.locator('.cm-line').first().boundingBox()
  await page.mouse.move(line!.x + 2, line!.y + line!.height / 2)
  await page.mouse.down()
  await page.mouse.move(line!.x + 48, line!.y + line!.height / 2, { steps: 8 })
  await page.mouse.up()
  await expect(
    page.locator('.sql-editor-tools').getByRole('button', { name: '复制', exact: true }),
  ).toBeEnabled()
  expect(await page.evaluate(() => getSelection()?.toString().length)).toBeGreaterThan(0)
  for (const expanded of [false, true]) {
    if (expanded) await page.getByRole('button', { name: '放大编辑', exact: true }).click()
    const host = page.locator(expanded ? 'dialog.sql-large-editor' : '.sql-editor-tools')
    const editor = page.locator('.cm-content:visible')
    await host.getByRole('button', { name: '全选', exact: true }).click()
    await host.getByRole('button', { name: '复制', exact: true }).click()
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('SELECT 1;')
    await host.getByRole('button', { name: '剪切', exact: true }).click()
    await expect(editor).toHaveText('')
    await editor.press('Control+z')
    await expect(editor).toHaveText('SELECT 1;')
    await editor.press('Control+Home')
    await editor.press('Control+Shift+ArrowRight')
    await editor.press('Control+c')
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('SELECT')
  }
  expect(writes).toHaveLength(0)
})

test('failed clipboard cut keeps text and HTTP clipboard fallback works', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  const host = page.locator('dialog.sql-large-editor')
  await page.evaluate(() =>
    Object.defineProperty(navigator.clipboard, 'writeText', {
      configurable: true,
      value: () => Promise.reject(new Error('Denied')),
    }),
  )
  await host.getByRole('button', { name: '全选', exact: true }).click()
  await host.getByRole('button', { name: '复制', exact: true }).click()
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('SELECT 1;')
  await page.evaluate(() => {
    document.execCommand = () => false
  })
  await host.getByRole('button', { name: '剪切', exact: true }).click()
  await expect(host.locator('.cm-content')).toHaveText('SELECT 1;')
  await expect(page.locator('.n-message')).toContainText(['复制'])
})

test('group filter selects server candidates beyond the current page and preserves manual queries', async ({
  page,
}, info) => {
  await setup(page)
  const prefixes: string[] = []
  await page.route('**/api/tasks/groups?*', async (route) => {
    const prefix = new URL(route.request().url()).searchParams.get('prefix') ?? ''
    prefixes.push(prefix)
    await route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: prefix ? ['finance_ops'] : ['finance_ops', 'remote_team'], total: 2 },
      },
    })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  const input = page.locator('#tasks-group')
  await input.click()
  await expect(
    page.locator('.n-base-select-option').filter({ hasText: 'remote_team' }),
  ).toBeVisible()
  await page.screenshot({ path: info.outputPath('group-options.png'), animations: 'disabled' })
  await page.locator('.n-base-select-option').filter({ hasText: 'remote_team' }).click()
  await expect(input).toHaveValue('remote_team')
  let queried = page.waitForRequest(
    (r) =>
      new URL(r.url()).pathname === '/api/tasks' &&
      new URL(r.url()).searchParams.get('group') === 'remote_team',
  )
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await queried
  await input.fill('finance_')
  await expect.poll(() => prefixes.includes('finance_')).toBe(true)
  await expect(
    page.locator('.n-base-select-option').filter({ hasText: 'finance_ops' }),
  ).toBeVisible()
  await input.fill('manual-only')
  queried = page.waitForRequest(
    (r) =>
      new URL(r.url()).pathname === '/api/tasks' &&
      new URL(r.url()).searchParams.get('group') === 'manual-only',
  )
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await queried
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(input).toHaveValue('')
})

test('group candidate errors retain manual input and can recover on focus', async ({ page }) => {
  await setup(page)
  let fail = true
  await page.route('**/api/tasks/groups?*', async (route) => {
    await route.fulfill(
      fail
        ? { status: 500, json: { success: false, code: 500 } }
        : { json: { success: true, code: 200, data: { list: ['finance'], total: 1 } } },
    )
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.locator('#tasks-group').fill('finance')
  await expect(page.locator('#tasks-group-hint')).toContainText('加载失败')
  await expect(page.locator('#tasks-group')).toHaveValue('finance')
  fail = false
  await page.locator('h1').click()
  await page.locator('#tasks-group').click()
  await expect(page.locator('.n-base-select-option').filter({ hasText: 'finance' })).toBeVisible()
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(
      'task and run history table headers match ' + theme + ' ' + locale,
      async ({ page }, info) => {
        await page.addInitScript(
          ({ theme, locale }) =>
            localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
          { theme, locale },
        )
        await setup(page)
        await page.goto('/tasks')
        const heading = page.locator('.task-table th').nth(1)
        const styles = await heading.evaluate((el) => {
          const css = getComputedStyle(el)
          return {
            size: css.fontSize,
            weight: css.fontWeight,
            color: css.color,
            background: css.backgroundColor,
            padding: css.padding,
            lineHeight: css.lineHeight,
          }
        })
        expect(styles.size).toBe('13px')
        expect(styles.weight).toBe('600')
        await page
          .getByRole('button', {
            name: (locale === 'en-US' ? 'More' : '更多') + ' Demo SQL',
            exact: true,
          })
          .click()
        await page
          .getByText(locale === 'en-US' ? 'Run history' : '运行记录', { exact: true })
          .click()
        const run = page.locator('.task-run-table th').first()
        await expect(run).toBeVisible()
        expect(
          await run.evaluate((el) => {
            const css = getComputedStyle(el)
            return {
              size: css.fontSize,
              weight: css.fontWeight,
              color: css.color,
              background: css.backgroundColor,
              padding: css.padding,
              lineHeight: css.lineHeight,
            }
          }),
        ).toEqual(styles)
        await page.screenshot({ path: info.outputPath('run-headers.png'), animations: 'disabled' })
        await page.setViewportSize({ width: 390, height: 844 })
        await page.screenshot({
          path: info.outputPath('run-headers-mobile.png'),
          animations: 'disabled',
        })
      },
    )

const base = {
  id: taskId,
  name: 'Demo SQL',
  pluginType: 'SQL',
  enabled: true,
  currentRevision: '3',
  currentTaskDefinitionId: '9223372036854775803',
  updateTime: '2026-09-25T10:00:00',
  groups: ['demo'],
  retryTimes: 0,
  retryInterval: 0,
  timeout: null,
  description: '',
  pluginConfig:
    '{"resourceId":9223372036854775802,"script":"SELECT 1;","transaction":true,"continueOnError":false,"queryTimeoutSeconds":600,"delimiter":";"}',
}
async function setup(
  page: Page,
  permissions = [
    'SUMMARY_READ',
    'DEFINITION_READ',
    'SENSITIVE_READ',
    'CONFIGURATION_WRITE',
    'EXECUTION_CONTROL',
  ],
) {
  const writes: { path: string; body: Record<string, unknown>; method: string }[] = []
  let record = { ...base },
    polls = 0
  await page.route(
    /\/api\/(?:auth|tasks|resources|triggers|instances|parameter-templates)(?:[/?]|$)/,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        path = url.pathname
      const ok = (data: unknown, status = 200) =>
        route.fulfill({ status, json: { success: true, code: status, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'task-admin',
          username: 'demo-admin',
          displayName: 'Demo admin',
          roleCode: 'ADMIN',
          permissions,
          mustChangePassword: false,
        })
      if (request.method() !== 'GET') {
        writes.push({ path, body: request.postDataJSON() ?? {}, method: request.method() })
        if (path.endsWith('/manual-trigger')) return ok('9223372036854775804', 202)
        if (request.method() === 'DELETE') return ok(null)
        const body = request.postDataJSON()
        record = { ...record, ...body, currentRevision: '4' }
        return ok(
          { taskId, definitionId: '9223372036854775805', revision: '4' },
          request.method() === 'POST' ? 201 : 200,
        )
      }
      if (path === '/api/parameter-templates') return ok({ list: [], total: 0 })
      if (path.startsWith('/api/parameter-templates/bindings/')) return ok(null)
      if (path === '/api/tasks') return ok({ list: [record], total: 1 })
      if (path === '/api/tasks/groups')
        return ok({ list: record.groups, total: record.groups.length })
      if (path === '/api/tasks/' + taskId) return ok(record)
      if (path === '/api/resources')
        return ok({
          list: [{ id: resourceId, name: 'Demo database', type: 'DB', enabled: true }],
          total: 1,
        })
      if (path === '/api/triggers')
        return ok({
          list: [
            {
              id: '9223372036854775804',
              status: 'SUCCESS',
              rootInstanceId: '9223372036854775806',
              taskDefinitionId: '9223372036854775803',
              createTime: '2026-09-25T10:00:00',
            },
          ],
          total: 1,
        })
      if (path.startsWith('/api/triggers/'))
        return ok({
          id: '9223372036854775804',
          status: polls++ ? 'SUCCESS' : 'RUNNING',
          rootInstanceId: '9223372036854775806',
          taskDefinitionId: '9223372036854775803',
          createTime: '2026-09-25T10:00:00',
        })
      if (path.endsWith('/logs/snapshot'))
        return ok({
          rows: [
            {
              id: '9223372036854775807',
              logTime: '2026-09-25T10:00:01',
              level: 'INFO',
              content: 'SQL task completed',
            },
          ],
          truncated: false,
        })
      if (path.endsWith('/logs'))
        return ok(
          url.searchParams.get('afterId') === '0'
            ? [
                {
                  id: '9223372036854775807',
                  logTime: '2026-09-25T10:00:01',
                  level: 'INFO',
                  content: 'SQL task completed',
                },
              ]
            : [],
        )
      if (path.startsWith('/api/instances/'))
        return ok({
          id: '9223372036854775806',
          status: polls > 1 ? 'SUCCESS' : 'RUNNING',
          taskDefinitionId: '9223372036854775803',
          startTime: '2026-09-25T10:00:00',
          endTime: polls > 1 ? '2026-09-25T10:00:01' : null,
        })
      return route.fulfill({ status: 404, json: { success: false, code: 404 } })
    },
  )
  return writes
}
test('large editor shares draft and undo history, Escape closes search first and save only publishes', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await expect(page.locator('#task-script')).toContainText('SELECT 1;')
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  const modal = page.locator('dialog.sql-large-editor')
  await expect(modal).toBeVisible()
  const editor = modal.locator('.cm-content')
  await editor.press('Control+End')
  await editor.press('End')
  await editor.press('Enter')
  await editor.pressSequentially('SELECT 2;')
  await page.getByRole('button', { name: '查找／替换', exact: true }).click()
  await expect(modal.locator('.cm-search')).toBeVisible()
  await modal.locator('.cm-search input').first().press('Escape')
  await expect(modal.locator('.cm-search')).toHaveCount(0)
  await expect(modal).toBeVisible()
  await editor.press('Escape')
  await expect(modal).not.toBeVisible()
  await expect(page.locator('.task-drawer')).toBeVisible()
  await expect(page.locator('#task-script')).toContainText('SELECT 2;')
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  await editor.press('Control+z')
  await expect(editor).not.toContainText('SELECT 2;')
  await editor.press('Control+y')
  await expect(editor).toContainText('SELECT 2;')
  await page.getByRole('button', { name: '完成编辑', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
  expect(writes[0]!.method).toBe('PUT')
  const config = JSON.parse(writes[0]!.body.pluginConfig as string)
  expect(config.resourceId).toBe(resourceId)
  expect(config.script).toContain('SELECT 2;')
  expect(writes[0]!.body.groups).toEqual(['demo'])
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test('SQL visual and large editor ' + theme + ' ' + locale, async ({ page }, info) => {
      const en = locale === 'en-US'
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/tasks')
      await expect(page.locator('.task-table tbody tr')).toHaveCount(1)
      await expect(page.locator('.task-table th').nth(1)).toHaveCSS('font-size', '13px')
      await expect(page.locator('.task-table th').nth(1)).toHaveCSS('font-weight', '600')
      await expect(
        page.getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo SQL', exact: true }),
      ).toBeVisible()
      await page.screenshot({ path: info.outputPath('tasks-desktop.png'), animations: 'disabled' })
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo SQL', exact: true })
        .click()
      await expect(page.locator('.task-drawer')).toBeVisible()
      await expect(page.locator('#task-script')).toContainText('SELECT 1;')
      await page.screenshot({ path: info.outputPath('sql-editor.png'), animations: 'disabled' })
      await page
        .getByRole('button', { name: en ? 'Expand editor' : '放大编辑', exact: true })
        .click()
      const modal = page.locator('dialog.sql-large-editor')
      await expect(modal).toBeVisible()
      await page.screenshot({ path: info.outputPath('sql-expanded.png'), animations: 'disabled' })
      await page.setViewportSize({ width: 655, height: 480 })
      await expect(
        modal.getByRole('button', { name: en ? 'Done editing' : '完成编辑', exact: true }),
      ).toBeInViewport()
      await page.screenshot({
        path: info.outputPath('sql-expanded-short.png'),
        animations: 'disabled',
      })
      await page.setViewportSize({ width: 390, height: 844 })
      await expect(
        modal.getByRole('button', { name: en ? 'Done editing' : '完成编辑', exact: true }),
      ).toBeInViewport()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      await page.screenshot({
        path: info.outputPath('sql-expanded-mobile.png'),
        animations: 'disabled',
      })
      await modal.getByRole('button', { name: en ? 'Close' : '关闭', exact: true }).click()
      await expect(modal).not.toBeVisible()
      await page.setViewportSize({ width: 1366, height: 900 })
      await page.getByText(en ? 'Advanced settings' : '高级配置', { exact: true }).click()
      const grid = page.locator('.task-advanced-grid')
      await grid.scrollIntoViewIfNeeded()
      const first = await page.locator('#task-queryTimeoutSeconds').boundingBox()
      const second = await page.locator('#task-timeout').boundingBox()
      expect(Math.abs(first!.y - second!.y)).toBeLessThan(2)
      expect(second!.x).toBeGreaterThan(first!.x + first!.width)
      await page.screenshot({
        path: info.outputPath('advanced-desktop.png'),
        animations: 'disabled',
      })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.locator('#task-queryTimeoutSeconds').scrollIntoViewIfNeeded()
      const mobileFirst = await page.locator('#task-queryTimeoutSeconds').boundingBox()
      const mobileSecond = await page.locator('#task-timeout').boundingBox()
      expect(Math.abs(mobileFirst!.x - mobileSecond!.x)).toBeLessThan(2)
      expect(mobileSecond!.y).toBeGreaterThan(mobileFirst!.y + mobileFirst!.height)
      await page.screenshot({
        path: info.outputPath('advanced-mobile.png'),
        animations: 'disabled',
      })
      expect(errors).toEqual([])
    })
test('dirty closing, publish conflict and adopting latest preserve explicit choices', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.locator('#task-name').fill('Draft retained')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('#task-name')).toHaveValue('Draft retained')
  await page.route('**/api/tasks/' + taskId + '?*', (route) =>
    route.fulfill({ status: 409, json: { success: false, code: 409 } }),
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('#task-name')).toHaveValue('Draft retained')
  await page.getByRole('button', { name: '读取最新记录', exact: true }).click()
  await expect(page.locator('.task-conflict pre')).toContainText('Demo SQL')
})
test('required SQL fields focus first error and transaction prevents continue-on-error', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#task-name')).toBeFocused()
  await expect(page.locator('#task-name-error')).toContainText('必填')
  await page.getByText('高级配置', { exact: true }).click()
  await expect(page.getByRole('switch', { name: '出错后继续', exact: true })).toBeDisabled()
  expect(writes).toHaveLength(0)
})
test('explicit execution follows accepted trigger to final state with exact IDs and logs', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect(page.locator('.task-results').getByRole('status')).toContainText('执行成功', {
    timeout: 10000,
  })
  await expect(page.locator('.task-logs')).toContainText('SQL task completed')
  expect(writes).toHaveLength(1)
  expect(writes[0]!.body.requestId).toMatch(/^[0-9a-f-]{36}$/)
  expect(
    await page.locator('.task-logs').getByText('SQL task completed', { exact: false }).count(),
  ).toBe(1)
})

test('task run drawer refresh recovers a late small log ID after completion', async ({ page }) => {
  await setup(page)
  let late = false
  await page.route('**/api/instances/*/logs/snapshot?*', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          rows: [
            {
              id: '9223372036854775807',
              logTime: '2026-10-08T10:00:00',
              level: 'INFO',
              content: 'first large log',
            },
            ...(late
              ? [
                  {
                    id: '20',
                    logTime: '2026-10-08T10:00:01',
                    level: 'INFO',
                    content: 'late smaller log',
                  },
                ]
              : []),
          ],
          truncated: false,
        },
      },
    }),
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect(page.locator('.task-logs')).toContainText('first large log')
  late = true
  await page.getByRole('button', { name: '加载更多日志', exact: true }).click()
  await expect(page.locator('.task-logs')).toContainText('late smaller log')
  await expect(page.locator('.task-logs .run-log-line')).toHaveCount(2)
})
test('uncertain execution is not automatically retried and offers run history', async ({
  page,
}) => {
  const writes = await setup(page)
  let sends = 0
  await page.route('**/manual-trigger', (route) => {
    sends++
    return route.abort('failed')
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect(page.getByText('请求结果不确定', { exact: false })).toBeVisible()
  await expect(page.locator('.task-drawer-heading')).toContainText('运行记录')
  expect(sends).toBe(1)
  expect(writes).toHaveLength(0)
})

test('double execution confirmation sends only one pending trigger', async ({ page }) => {
  await setup(page)
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  let sends = 0
  await page.route('**/manual-trigger', async (route) => {
    sends++
    await pending
    await route.fulfill({
      status: 202,
      json: { success: true, code: 202, data: '9223372036854775804' },
    })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await page
    .getByRole('button', { name: '开始执行', exact: true })
    .evaluate((button: HTMLButtonElement) => {
      button.click()
      button.click()
    })
  await expect.poll(() => sends).toBe(1)
  await page.waitForTimeout(200)
  expect(sends).toBe(1)
  release()
  await expect(page.locator('.task-results').getByRole('status')).toContainText('执行成功', {
    timeout: 10000,
  })
  expect(sends).toBe(1)
})

test('slow task list distinguishes loading from empty and retry preserves filter input', async ({
  page,
}) => {
  await setup(page)
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  let calls = 0
  await page.route(
    (url) => url.pathname === '/api/tasks',
    async (route) => {
      calls++
      if (calls === 1) {
        await pending
        return route.fulfill({
          status: 500,
          json: { success: false, code: 500, traceId: 'task-read-failure' },
        })
      }
      return route.fulfill({ json: { success: true, code: 200, data: { list: [], total: 0 } } })
    },
  )
  await page.goto('/tasks')
  await expect(page.getByRole('status')).toContainText('加载')
  release()
  await expect(page.locator('main')).toContainText('task-read-failure')
  await page.locator('#tasks-name').fill('Preserve filter')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('main')).not.toContainText('task-read-failure')
  await expect(page.locator('tbody tr')).toHaveCount(0)
  await expect(page.locator('#tasks-name')).toHaveValue('Preserve filter')
  expect(calls).toBe(2)
})
test('partial deletion keeps failed selection and reports retirement impact', async ({ page }) => {
  await setup(page)
  await page.route('**/api/tasks?*', (route) =>
    route.fulfill({
      json: {
        success: true,
        data: { list: [base, { ...base, id: '42', name: 'Protected SQL' }], total: 2 },
      },
    }),
  )
  await page.route('**/api/tasks/42', (route) =>
    route.fulfill({ status: 409, json: { success: false, code: 409 } }),
  )
  await page.goto('/tasks')
  await page.getByRole('checkbox', { name: 'Demo SQL', exact: true }).check()
  await page.getByRole('checkbox', { name: 'Protected SQL', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('关联调度和事件绑定')
  await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: 'Protected SQL', exact: true })).toBeChecked()
  await expect(page.getByRole('checkbox', { name: 'Demo SQL', exact: true })).not.toBeChecked()
  await expect(page.locator('.task-failures')).toContainText('Protected SQL')
})
test('read-only permissions allow status without SQL or logs', async ({ page }) => {
  await setup(page, ['SUMMARY_READ', 'DEFINITION_READ'])
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '新增任务', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '更多 Demo SQL', exact: true }).click()
  await page.getByText('运行记录', { exact: true }).click()
  await page.getByRole('button', { name: '查看结果', exact: true }).click()
  await expect(page.getByText('当前权限可查看状态，不能读取日志。')).toBeVisible()
})

test('large editor handles long SQL, keyboard focus and retains the whole draft', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  const modal = page.locator('dialog.sql-large-editor')
  const content = modal.locator('.cm-content')
  const script = Array.from({ length: 300 }, (_, i) => `SELECT ${i + 1} AS row_number;`).join('\n')
  await content.fill(script)
  await content.press('Control+End')
  await expect(modal.locator('.cm-line').last()).toHaveText('SELECT 300 AS row_number;')
  await expect
    .poll(() => modal.locator('.cm-scroller').evaluate((el) => el.scrollTop))
    .toBeGreaterThan(0)
  await page.screenshot({ path: info.outputPath('sql-long-draft.png'), animations: 'disabled' })
  await modal.getByRole('button', { name: '完成编辑', exact: true }).focus()
  await page.keyboard.press('Tab')
  expect(await modal.evaluate((el) => el.contains(document.activeElement))).toBe(true)
  await modal.getByRole('button', { name: '完成编辑', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(JSON.parse(writes[0]!.body.pluginConfig as string).script).toBe(script)
})

test('publishing blocks repeated submission and closing until response', async ({ page }) => {
  await setup(page)
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  let sends = 0
  await page.route('**/api/tasks/' + taskId + '?*', async (route) => {
    sends++
    await gate
    await route.fulfill({
      json: { success: true, data: { taskId, definitionId: '92', revision: '4' } },
    })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.locator('#task-name').fill('Pending draft')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(
    page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }),
  ).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(page.locator('.task-drawer')).toBeVisible()
  expect(sends).toBe(1)
  release()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
})

test('generic task entry selects SQL and existing plugin type is fixed', async ({ page }) => {
  await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-pluginType').click()
  await expect(page.getByRole('option', { name: 'SQL · SQL 执行', exact: true })).toBeVisible()
  await expect(page.getByRole('option')).toHaveCount(11)
  await expect(
    page.getByRole('option', { name: 'ETL · 数据抽取转换加载', exact: true }),
  ).toBeVisible()
  await expect(
    page.getByRole('option', { name: 'FILE_TRANSFER · 文件传输', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('option', { name: 'PUSH · 数据推送', exact: true })).toBeVisible()
  await expect(
    page.getByRole('option', { name: 'DATA_CHECK · 数据检查', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('option', { name: 'EVENT · 事件处理', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: 'API_CALL · 接口调用', exact: true })).toBeVisible()
  await page.getByRole('option', { name: 'SQL · SQL 执行', exact: true }).click()
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await expect(page.locator('#task-pluginType')).toBeDisabled()
})

test('execution group tags normalize, deduplicate, remove and include pending input on save', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.getByText('高级配置', { exact: true }).click()
  const input = page.locator('#task-groups')
  await input.fill(' Finance ')
  await input.press('Enter')
  await input.fill('FINANCE')
  await input.press('Enter')
  await expect(page.locator('.task-group-tags .n-tag')).toHaveCount(2)
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '删除分组 demo', exact: true }).click()
  await input.fill('bad group')
  await input.press('Enter')
  await expect(page.locator('#task-groups-error')).toContainText('1–64')
  await expect(input).toHaveValue('bad group')
  await input.fill('Reporting')
  await page.screenshot({ path: info.outputPath('group-tags.png'), animations: 'disabled' })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(writes[0]!.body.groups).toEqual(['finance', 'reporting'])
  expect(writes[0]!.body.pluginType).toBe('SQL')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.getByText('高级配置', { exact: true }).click()
  await expect(page.locator('.task-group-tags')).toContainText('reporting')
  await page.getByRole('button', { name: '删除分组 finance', exact: true }).click()
  await page.getByRole('button', { name: '删除分组 reporting', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(writes[1]!.body.groups).toEqual([])
})

test('group limit and unfinished input participate in validation and dirty close guard', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.getByText('高级配置', { exact: true }).click()
  const input = page.locator('#task-groups')
  await input.fill('pending')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(input).toHaveValue('pending')
  for (let index = 1; index <= 15; index++) {
    await input.fill('group-' + index)
    await input.press('Enter')
    await expect(page.locator('.task-group-tags .n-tag')).toHaveCount(index + 1)
  }
  await input.fill('overflow')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#task-groups-error')).toContainText('16')
  await expect(input).toBeFocused()
  expect(writes).toHaveLength(0)
  await expect(page.locator('.task-group-tags .n-tag')).toHaveCount(16)
})

test('revoked access closes expanded SQL and clears sensitive content', async ({ page }) => {
  await setup(page)
  let reject!: () => void
  const gate = new Promise<void>((resolve) => {
    reject = resolve
  })
  await page.route('**/api/resources?*', async (route) => {
    await gate
    await route.fulfill({ status: 403, json: { success: false, code: 403 } })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo SQL', exact: true }).click()
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  await expect(page.locator('dialog.sql-large-editor')).toBeVisible()
  reject()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  await expect(page.locator('.cm-content')).toHaveCount(0)
})
