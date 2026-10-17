import { test, expect, type Page } from '@playwright/test'
const tpl = {
  id: '9223372036854775806',
  revision: '1',
  name: '每日处理参数',
  description: '共享参数',
  fields: [
    {
      name: 'batchSize',
      type: 'INTEGER',
      required: true,
      hasDefault: true,
      defaultValue: 1000,
      description: '每批处理条数',
    },
    { name: 'dryRun', type: 'BOOLEAN', hasDefault: true, defaultValue: false },
    { name: 'remark', type: 'STRING', hasDefault: false },
  ],
}
const task = {
  id: '2049',
  name: 'Demo SQL',
  pluginType: 'SQL',
  enabled: true,
  currentRevision: '1',
  currentTaskDefinitionId: '2050',
  updateTime: '2026-10-09T10:00:00',
  groups: [],
  retryTimes: 0,
  retryInterval: 0,
  timeout: 120,
  description: '',
  pluginConfig: '{"resourceId":"1005","script":"SELECT 1"}',
}
const schedule = {
  id: '42',
  targetType: 'TASK',
  targetId: '2049',
  targetName: 'Demo SQL',
  scheduleType: 'INTERVAL',
  intervalSec: 60,
  enabled: false,
  variables: { batchSize: 27, dryRun: false },
  parameterForm: { templateId: tpl.id, fields: tpl.fields },
}
async function setup(page: Page, writer = true, bound = true, extra = false) {
  const writes: { path: string; body: Record<string, unknown>; method: string }[] = []
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        path = new URL(req.url()).pathname
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (req.method() !== 'GET') {
        writes.push({ path, body: req.postDataJSON() ?? {}, method: req.method() })
        return ok(path.endsWith('manual-trigger') ? '55' : tpl.id)
      }
      if (path === '/api/auth/me')
        return ok({
          loginId: 'parameter-admin',
          username: 'parameter-admin',
          displayName: 'Parameter admin',
          roleCode: writer ? 'ADMIN' : 'OPERATOR',
          mustChangePassword: false,
          permissions: [
            'SUMMARY_READ',
            'DEFINITION_READ',
            'EXECUTION_CONTROL',
            ...(writer ? ['CONFIGURATION_WRITE', 'SENSITIVE_READ'] : []),
          ],
        })
      if (path === '/api/parameter-templates')
        return ok({
          list: extra ? [tpl, { ...tpl, id: '9001', name: '第二模板' }] : [tpl],
          total: extra ? 2 : 1,
        })
      if (path === '/api/parameter-templates/9001')
        return ok({
          ...tpl,
          id: '9001',
          name: '第二模板',
          fields: [{ ...tpl.fields[0], defaultValue: 2000 }],
        })
      if (path.startsWith('/api/parameter-templates/bindings/')) return ok(bound ? tpl : null)
      if (path.startsWith('/api/parameter-templates/')) return ok(tpl)
      if (path === '/api/jobs')
        return ok({ list: [{ ...task, name: 'Demo Job', currentDefinitionId: '2051' }], total: 1 })
      if (path === '/api/jobs/2049/execution-context')
        return ok({ name: 'Demo Job', revision: '1', enabled: true })
      if (path === '/api/tasks') return ok({ list: [task], total: 1 })
      if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
      if (path === '/api/tasks/2049') return ok(task)
      if (path === '/api/schedules/metadata')
        return ok({
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
        })
      if (path === '/api/schedules') return ok({ list: [schedule], total: 1 })
      if (path === '/api/schedules/42') return ok(schedule)
      if (path === '/api/calendars') return ok({ list: [], total: 0 })
      if (path.startsWith('/api/triggers/'))
        return ok({
          id: '55',
          status: 'WAITING',
          taskDefinitionId: '2050',
          createTime: '2026-10-09T10:00:00',
        })
      return ok({ list: [], total: 0 })
    },
  )
  return writes
}
test('manual execution uses typed defaults, permits overrides and explicit save as template', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('1000')
  expect(
    (await page.locator('.parameter-execution-dialog').boundingBox())!.width,
  ).toBeLessThanOrEqual(760)
  await page.getByRole('textbox', { name: 'batchSize', exact: true }).fill('250')
  await page.getByRole('button', { name: '保存为模板', exact: true }).click()
  await page.getByRole('textbox', { name: '模板名称', exact: true }).fill('本次参数')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body).toMatchObject({ name: '本次参数' })
  expect(
    (writes[0]!.body.fields as Record<string, unknown>[]).every((f) => !f.hasDefault),
  ).toBeTruthy()
  await expect(page.getByRole('textbox', { name: '模板名称', exact: true })).toHaveCount(0)
  await page.screenshot({
    path: info.outputPath('manual-parameters.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect.poll(() => writes.filter((w) => w.path.endsWith('manual-trigger')).length).toBe(1)
  const body = writes.find((w) => w.path.endsWith('manual-trigger'))!.body
  expect(body.variables).toEqual({ batchSize: 250, dryRun: false })
  expect(body.parameterForm).toMatchObject({ templateId: tpl.id })
})
test('operator can fill a template but cannot modify shared definitions or bindings', async ({
  page,
}) => {
  const writes = await setup(page, false)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('1000')
  await expect(page.getByRole('button', { name: '保存为模板', exact: true })).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: '设为此任务／作业的默认模板', exact: true }),
  ).toHaveCount(0)
  await page.getByRole('textbox', { name: 'batchSize', exact: true }).fill('bad')
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  expect(writes).toHaveLength(0)
})
test('template management retains exact ID and edit revision, with responsive form', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/parameters')
  await expect(page.getByText('ID: ' + tpl.id)).toBeVisible()
  await page.getByRole('button', { name: '编辑 每日处理参数', exact: true }).click()
  await page
    .locator('.parameter-drawer')
    .getByRole('textbox', { name: '模板名称', exact: true })
    .fill('已更新')
  await page.setViewportSize({ width: 420, height: 900 })
  await page.screenshot({
    path: info.outputPath('template-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body).toMatchObject({ name: '已更新', revision: '1' })
  expect(writes[0]!.path).toBe('/api/parameter-templates/' + tpl.id)
})
test('editing a saved schedule preserves its values instead of latest template defaults', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/schedules')
  await page.getByRole('button', { name: /编辑/ }).first().click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('27')
  await page.getByRole('textbox', { name: 'batchSize', exact: true }).fill('28')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.filter((w) => w.path === '/api/schedules/42').length).toBe(1)
  expect(writes.find((w) => w.path === '/api/schedules/42')!.body.variables).toEqual({
    batchSize: 28,
    dryRun: false,
  })
})

test('default binding from task menu is explicit and never executes the task', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '更多 Demo SQL', exact: true }).click()
  await page.getByText('参数模板', { exact: true }).last().click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('1000')
  await page.getByRole('button', { name: '设为此任务／作业的默认模板', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]).toMatchObject({
    method: 'PUT',
    path: '/api/parameter-templates/bindings/TASK/2049',
    body: { templateId: tpl.id },
  })
  expect(writes.some((w) => w.path.endsWith('manual-trigger'))).toBe(false)
})

test('row selection, defaults and JSON roundtrip preserve typed definitions', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  const batch = page.getByRole('textbox', { name: 'batchSize', exact: true })
  await batch.fill('250')
  await page.getByRole('button', { name: 'dryRun', exact: true }).click()
  await expect(batch).toHaveCount(0)
  const dryRun = page.getByRole('switch', { name: 'dryRun', exact: true })
  await expect(dryRun).not.toBeChecked()
  await dryRun.click()
  await page.getByRole('button', { name: '恢复默认值', exact: true }).click()
  await expect(dryRun).not.toBeChecked()
  await page.getByRole('button', { name: 'batchSize', exact: true }).click()
  await expect(batch).toHaveValue('250')
  await page.getByRole('button', { name: '高级 JSON', exact: true }).click()
  await page.getByRole('button', { name: '表单编辑', exact: true }).click()
  await expect(batch).toHaveValue('250')
  await expect(
    page.getByRole('checkbox', { name: '传入此参数 batchSize', exact: true }),
  ).toBeDisabled()
  await expect(page.getByRole('button', { name: '编辑 每日处理参数', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body.variables).toEqual({ batchSize: 250, dryRun: false })
  expect(writes[0]!.body.parameterForm).toMatchObject({ fields: tpl.fields })
})

test('template drawer bounds its table and supports duplicate, delete and keyboard selection', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/parameters')
  await page.getByRole('button', { name: '编辑 每日处理参数', exact: true }).click()
  const modal = page.locator('.parameter-drawer')
  await modal.getByRole('button', { name: '复制 batchSize', exact: true }).click()
  await expect(modal.getByRole('textbox', { name: '参数名', exact: true })).toHaveValue(
    'batchSize_1',
  )
  await modal.getByRole('textbox', { name: '默认值', exact: true }).fill('9223372036854775806')
  for (let i = 0; i < 12; i++)
    await modal.getByRole('button', { name: '复制 batchSize', exact: true }).click()
  const scroll = modal.locator('.parameter-table-scroll')
  expect(await scroll.evaluate((el) => el.scrollHeight > el.clientHeight + 1)).toBe(true)
  await modal.getByRole('button', { name: 'batchSize_1', exact: true }).click()
  await expect(modal.getByRole('textbox', { name: '默认值', exact: true })).toHaveValue(
    '9223372036854775806',
  )
  await modal.getByRole('button', { name: 'batchSize', exact: true }).focus()
  await page.keyboard.press('ArrowDown')
  await expect(modal.getByRole('textbox', { name: '参数名', exact: true })).toHaveValue('dryRun')
  await modal.getByRole('button', { name: '删除 remark', exact: true }).click()
  await expect(modal.getByRole('button', { name: 'remark', exact: true })).toHaveCount(0)
  const bounds = await modal.boundingBox()
  expect(bounds!.y).toBeGreaterThanOrEqual(0)
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(page.viewportSize()!.height)
  await modal.screenshot({ path: info.outputPath('template-desktop.png'), animations: 'disabled' })
  await expect(modal.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
  await modal.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body.fields).toHaveLength(15)
})

test('job execution uses the same selected-row form and submits typed values', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/jobs')
  await page
    .getByRole('row')
    .filter({ hasText: 'Demo Job' })
    .getByRole('button', { name: '执行', exact: true })
    .click()
  await expect(page.getByText('执行作业', { exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: 'batchSize', exact: true }).fill('0')
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.path).toBe('/api/jobs/2049/manual-trigger')
  expect(writes[0]!.body.variables).toEqual({ batchSize: 0, dryRun: false })
})

test('execution footer stays visible on a narrow viewport and cancel does not submit', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.setViewportSize({ width: 420, height: 740 })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('1000')
  await expect(page.getByRole('button', { name: '开始执行', exact: true })).toBeInViewport()
  await expect(page.getByRole('button', { name: '保存为模板', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page
    .getByRole('dialog')
    .screenshot({ path: info.outputPath('execution-mobile.png'), animations: 'disabled' })
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(writes).toHaveLength(0)
})

test('template list uses shared compact layout, filter reset and pagination', async ({
  page,
}, info) => {
  await setup(page)
  const urls: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/parameter-templates') urls.push(request.url())
  })
  await page.goto('/parameters')
  await expect(page.locator('.application')).toHaveClass(/compact-shell/)
  await expect(page.getByRole('banner').getByText('参数模板', { exact: true })).toBeVisible()
  await expect(page.locator('.compact-pagination')).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '操作', exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: '模板名称', exact: true }).fill('每日')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect
    .poll(() => urls.some((url) => new URL(url).searchParams.get('name') === '每日'))
    .toBe(true)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '模板名称', exact: true })).toHaveValue('')
  await expect.poll(() => new URL(urls.at(-1)!).searchParams.get('name')).toBe('')
  await page.screenshot({ path: info.outputPath('template-list.png'), animations: 'disabled' })
  await page.getByRole('button', { name: '新增模板', exact: true }).click()
  await expect(page.locator('.parameter-drawer')).toBeVisible()
  await expect(page.locator('.n-modal')).toHaveCount(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.parameter-drawer')).toHaveCount(0)
})

test('first template selection immediately loads fields in binding and execution dialogs', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const writes = await setup(page, true, false)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '更多 Demo SQL', exact: true }).click()
  await page.getByText('参数模板', { exact: true }).last().click()
  await page.locator('.parameter-form .n-select').first().click()
  await page.getByText('每日处理参数', { exact: true }).last().click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('1000')
  await expect(
    page.locator('.n-dialog__action').getByRole('button', { name: '保存为模板', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await page.locator('.parameter-form .n-select').first().click()
  await page.getByText('每日处理参数', { exact: true }).last().click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('1000')
  expect(writes).toHaveLength(0)
  expect(errors).toEqual([])
})

test('template details are read-only and batch deletion uses the shared batch endpoint', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/parameters')
  await page.getByRole('button', { name: '详情 每日处理参数', exact: true }).click()
  const drawer = page.locator('.parameter-drawer')
  await expect(drawer.getByText('batchSize', { exact: true })).toBeVisible()
  await expect(drawer.getByRole('textbox')).toHaveCount(0)
  await expect(drawer.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
  expect((await drawer.boundingBox())!.width).toBeLessThanOrEqual(640)
  await drawer.locator('.drawer-close-button').click()
  await page.getByRole('checkbox', { name: '每日处理参数', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]).toMatchObject({
    path: '/api/parameter-templates/batch',
    method: 'DELETE',
    body: { ids: [tpl.id] },
  })
})

test('switching templates preserves edited values on cancel and replaces them on confirmation', async ({
  page,
}) => {
  await setup(page, true, true, true)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await page.getByRole('textbox', { name: 'batchSize', exact: true }).fill('250')
  await page.locator('.parameter-form .n-select').first().click()
  await page.getByText('第二模板', { exact: true }).last().click()
  await page.getByRole('dialog').last().getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(1)
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('250')
  await page.locator('.parameter-form .n-select').first().click()
  await page.getByText('第二模板', { exact: true }).last().click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'batchSize', exact: true })).toHaveValue('2000')
})

test('compact execution leaves space for at least eight parameter rows', async ({ page }, info) => {
  await setup(page)
  await page.route(
    (url) => url.pathname.startsWith('/api/parameter-templates/bindings/'),
    (route) =>
      route.fulfill({
        json: {
          success: true,
          code: 200,
          data: {
            ...tpl,
            fields: Array.from({ length: 15 }, (_, index) => ({
              ...tpl.fields[0],
              name: 'batchSize' + index,
            })),
          },
        },
      }),
  )
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '执行 Demo SQL', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'batchSize0', exact: true })).toHaveValue('1000')
  const visible = await page.locator('.parameter-table-scroll').evaluate((el) => {
    const bounds = el.getBoundingClientRect()
    return [...el.querySelectorAll('tbody tr')].filter((row) => {
      const r = row.getBoundingClientRect()
      return r.top >= bounds.top && r.bottom <= bounds.bottom
    }).length
  })
  expect(visible).toBeGreaterThanOrEqual(8)
  await expect(page.getByRole('button', { name: '开始执行', exact: true })).toBeInViewport()
  await page
    .getByRole('dialog')
    .screenshot({ path: info.outputPath('compact-execution.png'), animations: 'disabled' })
})

test('template defaults retain partial numeric and JSON input and save typed values', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/parameters')
  await page.getByRole('button', { name: '新增模板', exact: true }).click()
  const drawer = page.locator('.parameter-drawer')
  await drawer.getByRole('textbox', { name: '模板名称', exact: true }).fill('Typed defaults')
  const cases = [
    { name: 'integer', type: '整数', text: '-12', value: -12 },
    { name: 'decimal', type: '小数', text: '-1.250', value: -1.25 },
    {
      name: 'object',
      type: '对象',
      text: '{ "count": 2, "nested": { "active": false } }',
      value: { count: 2, nested: { active: false } },
    },
    {
      name: 'array',
      type: '数组',
      text: '[1, { "value": false }, 3]',
      value: [1, { value: false }, 3],
    },
  ]
  for (const item of cases) {
    await drawer.getByRole('button', { name: /添加参数/ }).click()
    await drawer.getByRole('textbox', { name: '参数名', exact: true }).fill(item.name)
    await drawer.locator('.parameter-detail .n-select').first().click()
    await page.getByText(item.type, { exact: true }).last().click()
    await drawer.getByRole('checkbox', { name: '设置默认值', exact: true }).check()
    const input = drawer.getByRole('textbox', { name: '默认值', exact: true })
    let typed = ''
    for (const character of item.text) {
      await input.pressSequentially(character)
      typed += character
      await expect(input).toHaveValue(typed)
    }
  }
  await drawer.getByRole('button', { name: 'decimal', exact: true }).click()
  await expect(drawer.getByRole('textbox', { name: '默认值', exact: true })).toHaveValue('-1.250')
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body.fields).toEqual(
    cases.map((item) => expect.objectContaining({ name: item.name, defaultValue: item.value })),
  )
})

test('template detail closes from its mask and its close button is right aligned', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/parameters')
  await page.getByRole('button', { name: '详情 每日处理参数', exact: true }).click()
  const drawer = page.locator('.parameter-drawer')
  const bounds = (await drawer.boundingBox())!
  const close = (await drawer.locator('.drawer-close-button').boundingBox())!
  expect(bounds.x + bounds.width - close.x - close.width).toBeLessThan(40)
  await page.locator('.n-drawer-mask').click({ position: { x: 20, y: 200 } })
  await expect(drawer).toHaveCount(0)
})

test('task parameter popup shows at least four rows at 640px viewport height', async ({
  page,
}, info) => {
  await setup(page)
  await page.route(
    (url) => url.pathname.startsWith('/api/parameter-templates/bindings/'),
    (route) =>
      route.fulfill({
        json: {
          success: true,
          code: 200,
          data: {
            ...tpl,
            fields: Array.from({ length: 10 }, (_, i) => ({
              ...tpl.fields[0],
              name: 'batchSize' + i,
            })),
          },
        },
      }),
  )
  await page.setViewportSize({ width: 1280, height: 640 })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '更多 Demo SQL', exact: true }).click()
  await page.getByText('参数模板', { exact: true }).last().click()
  await expect(page.getByRole('textbox', { name: 'batchSize0', exact: true })).toHaveValue('1000')
  const visible = await page.locator('.parameter-table-scroll').evaluate((el) => {
    const bounds = el.getBoundingClientRect()
    return [...el.querySelectorAll('tbody tr')].filter((row) => {
      const r = row.getBoundingClientRect()
      return r.top >= bounds.top && r.bottom <= bounds.bottom
    }).length
  })
  expect(visible).toBeGreaterThanOrEqual(4)
  await expect(page.getByRole('button', { name: '关闭', exact: true })).toBeInViewport()
  await page
    .getByRole('dialog')
    .screenshot({ path: info.outputPath('short-parameter-popup.png'), animations: 'disabled' })
})

test('schedule parameter table scrolls within the drawer', async ({ page }, info) => {
  const writes = await setup(page)
  await page.route(
    (url) => url.pathname === '/api/schedules/42',
    (route) => {
      if (route.request().method() !== 'GET') return route.fallback()
      return route.fulfill({
        json: {
          success: true,
          code: 200,
          data: {
            ...schedule,
            parameterForm: {
              templateId: tpl.id,
              fields: Array.from({ length: 15 }, (_, i) => ({
                ...tpl.fields[0],
                name: 'batchSize' + i,
              })),
            },
          },
        },
      })
    },
  )
  await page.goto('/schedules')
  await page.getByRole('button', { name: /编辑/ }).first().click()
  const table = page.locator('.parameter-table-scroll')
  await expect(table.locator('tbody tr')).toHaveCount(17)
  expect(await table.evaluate((el) => el.scrollHeight > el.clientHeight + 1)).toBe(true)
  await page.getByRole('button', { name: 'batchSize14', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'batchSize14', exact: true })).toBeVisible()
  expect(writes).toHaveLength(0)
  await page
    .locator('.n-drawer')
    .screenshot({ path: info.outputPath('schedule-natural-scroll.png'), animations: 'disabled' })
})

async function loginForLeaveGuard(page: Page) {
  await page.route('**/api/auth/login', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: { tokenName: 'x-test-token', tokenValue: 'local-mock' },
      },
    }),
  )
  await page.goto('/login')
  await page.locator('#auth-username').fill('test')
  await page.locator('#auth-password').fill('Test-Password-Only')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}
test('template dirty route leave can cancel then discard without a write', async ({ page }) => {
  const writes = await setup(page)
  await loginForLeaveGuard(page)
  await page.getByRole('link', { name: '参数模板', exact: true }).first().click()
  await page.getByRole('button', { name: '编辑 每日处理参数', exact: true }).click()
  await page
    .locator('.parameter-drawer')
    .getByRole('textbox', { name: '模板名称', exact: true })
    .fill('Pending template')
  await page.evaluate(() => {
    const e = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(e)
    if (!e.defaultPrevented) throw Error('unload was not guarded')
  })
  await page.getByRole('link', { name: '任务管理', exact: true }).dispatchEvent('click')
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page).toHaveURL(/\/parameters$/)
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(
    page.locator('.parameter-drawer').getByRole('textbox', { name: '模板名称', exact: true }),
  ).toHaveValue('Pending template')
  await page.getByRole('link', { name: '任务管理', exact: true }).dispatchEvent('click')
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page).toHaveURL(/\/tasks$/)
  expect(writes).toHaveLength(0)
})
test('schedule invalid unpublished JSON protects cancel, Esc and route leave', async ({ page }) => {
  const writes = await setup(page)
  await page.route('**/api/schedules/42', (route) =>
    route.fulfill({
      json: { success: true, code: 200, data: { ...schedule, parameterForm: null } },
    }),
  )
  await loginForLeaveGuard(page)
  await page.getByRole('link', { name: '调度管理', exact: true }).first().click()
  await page.getByRole('button', { name: /编辑/ }).first().click()
  await page.getByRole('button', { name: '高级 JSON', exact: true }).click()
  const input = page.getByRole('textbox', { name: '高级 JSON', exact: true })
  const original = await input.inputValue()
  await input.fill('{')
  for (const leave of [
    () =>
      page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click(),
    () => page.keyboard.press('Escape'),
    () => page.getByRole('link', { name: '任务管理', exact: true }).dispatchEvent('click'),
  ]) {
    await leave()
    await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
    await expect(input).toHaveValue('{')
    await expect(page.locator('.n-dialog')).toHaveCount(0)
  }
  await input.fill(original)
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.schedule-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(0)
})

test('long template search edits original row and reveals newly added fields', async ({ page }) => {
  const writes = await setup(page)
  const fields = Array.from({ length: 90 }, (_, i) => ({
    ...tpl.fields[0],
    name: 'field' + i,
    description: 'description-' + i,
  }))
  await page.route('**/api/parameter-templates/9223372036854775806', (route) =>
    route.request().method() === 'GET'
      ? route.fulfill({ json: { success: true, code: 200, data: { ...tpl, fields } } })
      : route.fallback(),
  )
  await page.goto('/parameters')
  await page.getByRole('button', { name: '编辑 每日处理参数', exact: true }).click()
  const table = page.locator('.parameter-table-scroll')
  await expect(table.locator('tbody tr')).toHaveCount(90)
  expect(
    await table.evaluate((el) => el.scrollHeight > el.clientHeight && el.clientHeight <= 320),
  ).toBe(true)
  const search = page.getByRole('searchbox', { name: '搜索列表内容' })
  await search.fill('description-89')
  await expect(table.locator('tbody tr')).toHaveCount(1)
  await table.getByRole('button', { name: 'field89', exact: true }).click()
  await page.getByRole('textbox', { name: '参数名', exact: true }).fill('edited_last')
  await page.getByRole('button', { name: '＋ 添加参数', exact: true }).click()
  await expect(search).toHaveValue('')
  await expect(table.locator('tr.is-selected')).toBeInViewport()
  await page.getByRole('textbox', { name: '参数名', exact: true }).fill('new_field')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  const saved = writes[0]!.body.fields as { name: string }[]
  expect(saved).toHaveLength(91)
  expect(saved[0]!.name).toBe('field0')
  expect(saved[89]!.name).toBe('edited_last')
  expect(saved[90]!.name).toBe('new_field')
})

test('template at the 100-field limit explains disabled actions and deletion re-enables adding', async ({
  page,
}) => {
  const writes = await setup(page)
  const fields = Array.from({ length: 100 }, (_, i) => ({ ...tpl.fields[0], name: 'field' + i }))
  await page.route('**/api/parameter-templates/9223372036854775806', (route) =>
    route.request().method() === 'GET'
      ? route.fulfill({ json: { success: true, code: 200, data: { ...tpl, fields } } })
      : route.fallback(),
  )
  await page.goto('/parameters')
  await page.getByRole('button', { name: '编辑 每日处理参数', exact: true }).click()
  const add = page.getByRole('button', { name: '＋ 添加参数', exact: true })
  await expect(add).toBeDisabled()
  await expect(page.getByRole('button', { name: '复制 field0', exact: true })).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('已达到 100 个参数上限')
  await page.getByRole('button', { name: '删除 field0', exact: true }).click()
  await expect(add).toBeEnabled()
  await add.click()
  await expect(add).toBeDisabled()
  await page.getByRole('textbox', { name: '参数名', exact: true }).fill('replacement')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  const saved = writes[0]!.body.fields as { name: string }[]
  expect(saved).toHaveLength(100)
  expect(saved[0]!.name).toBe('field1')
  expect(saved[99]!.name).toBe('replacement')
})
