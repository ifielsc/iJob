import { test, expect, type Page } from '@playwright/test'
const id = '9007199254740993'
async function setup(
  page: Page,
  config: Record<string, unknown> = { action: 'WAIT_APPROVAL', allowedOperatorIds: ['user:exact'] },
  status = 'AVAILABLE',
) {
  const task = {
    id,
    name: 'Event demo',
    pluginType: 'EVENT',
    enabled: true,
    currentRevision: '2',
    currentTaskDefinitionId: '9007199254740994',
    groups: [],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
    pluginConfig: JSON.stringify(config),
  }
  const state = {
    task,
    status,
    writes: [] as { path: string; method: string; body: Record<string, unknown> }[],
    reads: [] as string[],
    fail: '',
    bindings: [
      {
        id: '9223372036854775807',
        targetType: 'TASK',
        targetId: id,
        eventCode: 'report.ready',
        enabled: true,
      },
    ],
  }
  await page.route(
    /\/api\/(?:auth|tasks|jobs|users|resources|event-bindings)(?:[/?]|$)/,
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown, code = 200) =>
        route.fulfill({ status: code, json: { success: true, code, data } })
      const fail = (code: number) => route.fulfill({ status: code, json: { success: false, code } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'event-admin',
          username: 'admin',
          displayName: 'Admin',
          roleCode: 'ADMIN',
          permissions: [
            'SUMMARY_READ',
            'DEFINITION_READ',
            'SENSITIVE_READ',
            'CONFIGURATION_WRITE',
            'EXECUTION_CONTROL',
          ],
          mustChangePassword: false,
        })
      if (path === '/api/users/approvers/resolve')
        return ok(
          (req.postDataJSON() as string[]).map((loginId) => ({
            loginId,
            username: 'zhangming',
            displayName: '张明',
            status: state.status,
          })),
        )
      if (path === '/api/users/approvers') {
        state.reads.push(url.search)
        return ok([{ loginId: 'user:exact', username: 'zhangming', displayName: '张明' }])
      }
      if (req.method() !== 'GET') {
        state.writes.push({ path, method: req.method(), body: req.postDataJSON() ?? {} })
        if (state.fail === 'network') return route.abort()
        if (state.fail === 'conflict') return fail(409)
        if (path.startsWith('/api/event-bindings')) {
          if (req.method() === 'POST') {
            state.bindings.push({ id: '9007199254740995', ...req.postDataJSON() })
            return ok('9007199254740995', 201)
          }
          if (req.method() === 'PUT') {
            state.bindings.find((x) => path.endsWith(x.id))!.enabled = req.postDataJSON().enabled
            return ok(null)
          }
          state.bindings = state.bindings.filter((x) => !path.endsWith(x.id))
          return ok(null)
        }
        return ok({ taskId: id, definitionId: '9007199254740996', revision: '3' })
      }
      if (path === '/api/resources') return ok({ list: [], total: 0 })
      if (path === '/api/tasks') return ok({ list: [task], total: 1 })
      if (path === '/api/tasks/' + id) return ok(task)
      if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
      if (path === '/api/jobs')
        return ok({
          list: [{ ...task, name: 'Event job', currentDefinitionId: '9007199254740994' }],
          total: 1,
        })
      if (path === '/api/event-bindings')
        return ok(
          state.bindings.map((r) => ({ ...r, targetType: url.searchParams.get('targetType') })),
        )
      return fail(404)
    },
  )
  return state
}
async function open(page: Page, en = false) {
  await page.goto('/tasks')
  await page
    .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Event demo', exact: true })
    .click()
  await expect(page.locator('.event-fields')).toBeVisible()
}
async function action(page: Page, label: string) {
  await page.locator('[id="task-event.action"]').click()
  await page.locator('.n-base-select-option').filter({ hasText: label }).click()
}
async function binding(page: Page, en = false) {
  await page.goto('/tasks')
  await page
    .getByRole('button', { name: (en ? 'More' : '更多') + ' Event demo', exact: true })
    .click()
  await page.getByText(en ? 'Event bindings' : '事件绑定', { exact: true }).click()
  await expect(page.locator('.event-binding-drawer')).toBeVisible()
}
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`event binding inline row ${theme} ${locale}`, async ({ page }, info) => {
      const state = await setup(page)
      const en = locale === 'en-US'
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await binding(page, en)
      const drawer = page.locator('.event-binding-drawer')
      await expect(drawer.locator('#event-binding-code')).toHaveCount(0)
      const tools = drawer.locator('.editable-table-tools')
      await expect(tools.getByRole('button')).toHaveCount(2)
      const refresh = await tools
        .getByRole('button', { name: en ? 'Refresh' : '刷新', exact: true })
        .boundingBox()
      const add = tools.getByRole('button', { name: en ? 'Add binding' : '添加绑定', exact: true })
      expect((await add.boundingBox())!.y).toBeCloseTo(refresh!.y, 0)
      await add.click()
      await expect(drawer.locator('.editable-table tbody tr')).toHaveCount(2)
      await expect(drawer.locator('#event-binding-code')).toBeFocused()
      await drawer.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect(drawer.locator('#event-binding-code')).toHaveAttribute('aria-invalid', 'true')
      await drawer.locator('#event-binding-code').fill('report.inline')
      await tools.getByRole('button', { name: en ? 'Refresh' : '刷新', exact: true }).click()
      await expect(drawer.locator('#event-binding-code')).toHaveValue('report.inline')
      await page.screenshot({ path: info.outputPath('binding-desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({ path: info.outputPath('binding-mobile.png'), fullPage: true })
      await drawer.getByRole('button', { name: en ? 'Cancel' : '取消', exact: true }).click()
      await page
        .locator('.n-dialog')
        .getByRole('button', { name: en ? 'Confirm' : '确认', exact: true })
        .click()
      await expect(drawer.locator('.event-binding-draft')).toHaveCount(0)
      expect(state.writes).toHaveLength(0)
    })
for (const theme of ['light', 'dark'])
  test(`new and existing approval forms expose the same fields (${theme})`, async ({
    page,
  }, info) => {
    const state = await setup(page)
    await page.addInitScript(
      (theme) =>
        localStorage.setItem(
          'ijob:preferences:v1:guest',
          JSON.stringify({ theme, locale: 'zh-CN' }),
        ),
      theme,
    )
    await open(page)
    await expect(page.locator('.event-fields')).toContainText('张明 · zhangming')
    const fieldLabels = () => page.locator('.event-fields .n-form-item-label').allTextContents()
    const existing = await fieldLabels()
    const tag = page.locator('.event-fields .n-base-selection-tag-wrapper .n-tag')
    await expect(tag.locator('.n-tag__border')).toHaveCSS(
      'border-top-color',
      theme === 'light' ? 'rgb(211, 223, 241)' : 'rgb(80, 103, 134)',
    )
    await page.screenshot({ path: info.outputPath(`approval-${theme}.png`), fullPage: true })
    await page.locator('.task-drawer').getByRole('button', { name: '关闭', exact: true }).click()
    await expect(page.locator('.task-drawer')).toHaveCount(0)
    await page.getByRole('button', { name: '新增任务', exact: true }).click()
    await page.locator('#task-pluginType').click()
    await page.getByRole('option', { name: 'EVENT · 事件处理', exact: true }).click()
    await expect(page.locator('.event-action-hint')).toContainText('等待审批：选择审批人员')
    await expect(page.locator('[id="task-event.allowedOperatorIds"]')).toHaveCount(0)
    await expect(page.locator('[id="task-event.businessKey-hint"]')).toContainText('ORDER-1001')
    await expect(page.locator('.event-fields')).toContainText('填写后随事件发出')
    await action(page, '等待审批')
    await expect(
      page
        .locator('.n-select')
        .filter({ has: page.locator('[id="task-event.allowedOperatorIds"]') }),
    ).toBeVisible()
    expect(await fieldLabels()).toEqual(existing)
    await page
      .locator('.n-select')
      .filter({ has: page.locator('[id="task-event.allowedOperatorIds"]') })
      .click()
    await page.locator('.n-base-select-option').filter({ hasText: '张明 · zhangming' }).click()
    await page.locator('.event-fields h3').click()
    await expect(page.locator('.n-base-select-menu:visible')).toHaveCount(0)
    await expect(tag).toContainText('张明 · zhangming')
    await page.screenshot({ path: info.outputPath(`new-approval-${theme}.png`), fullPage: true })
    expect(state.writes).toEqual([])
  })

test('approval round-trip uses loginId, preserves unknown fields and does not execute', async ({
  page,
}) => {
  const state = await setup(page, {
    action: 'WAIT_APPROVAL',
    allowedOperatorIds: ['user:exact'],
    extension: { keep: true },
  })
  await open(page)
  await expect(page.locator('.event-fields')).toContainText('张明 · zhangming')
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(state.writes).toHaveLength(1)
  expect(JSON.parse(state.writes[0]!.body.pluginConfig as string)).toMatchObject({
    allowedOperatorIds: ['user:exact'],
    extension: { keep: true },
  })
  expect(state.writes[0]!.body.timeout).toBeNull()
})
test('unavailable approver blocks publication and remains visible', async ({ page }) => {
  const state = await setup(page, undefined, 'DISABLED')
  await open(page)
  await expect(page.locator('.event-fields')).toContainText('已停用')
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.event-fields')).toContainText('请移除或替换')
  await page.getByRole('button', { name: '移除失效人员', exact: true }).click()
  await expect(page.locator('.event-fields')).toContainText('请至少选择一位')
  expect(state.writes).toHaveLength(0)
})
test('event payload form preserves array null, false, zero and exact decimal values', async ({
  page,
}) => {
  const state = await setup(page, {
    action: 'PUBLISH',
    eventCode: 'report.done',
    payload: [null, false, 0],
  })
  await open(page)
  const form = page.locator('[data-json-context="EVENT_PAYLOAD"]')
  await form.getByRole('button', { name: '3', exact: true }).click()
  await form.getByRole('textbox', { name: '值', exact: true }).fill('9007199254740993')
  await form.getByRole('button', { name: '高级 JSON', exact: true }).click()
  await expect(form.locator('.cm-content')).toHaveText('[null,false,9007199254740993]')
  await form.locator('.cm-content').fill('[null,false,0,0.1234567890123456789]')
  await form.getByRole('button', { name: '表单', exact: true }).click()
  expect(state.writes).toHaveLength(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.pluginConfig).toContain('[null,false,0,0.1234567890123456789]')
})

test('action switch confirms cleanup and task timeout then restores drafts', async ({ page }) => {
  const state = await setup(page, {
    action: 'PUBLISH',
    eventCode: 'report.done',
    payload: false,
    extension: true,
  })
  state.task.timeout = 60 as never
  await open(page)
  await action(page, '等待事件')
  await expect(page.locator('.n-dialog')).toContainText('任务执行超时')
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await page.locator('[id="task-event.eventCode"]').fill('report.ready')
  await action(page, '发布事件')
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('[id="task-event.eventCode"]')).toHaveValue('report.done')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.timeout).toBe(60)
  expect(JSON.parse(state.writes[0]!.body.pluginConfig as string).payload).toBe(false)
})
test('unsupported action retains raw configuration and disables saving', async ({ page }) => {
  const state = await setup(page, {
    action: 'FUTURE_ACTION',
    eventCode: 'files.ready',
    fileResourceId: '9223372036854775807',
  })
  await open(page)
  await expect(page.locator('.event-fields')).toContainText('9223372036854775807')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(0)
})
test('publication unknown result never resubmits automatically', async ({ page }) => {
  const state = await setup(page, { eventCode: 'report.ready' })
  state.fail = 'network'
  await open(page)
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toContainText('发布结果不明')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核实当前版本' }).click()
  await expect(page.locator('.task-drawer')).toContainText('v2')
  expect(state.writes).toHaveLength(1)
})
test('binding CRUD uses exact IDs, immediate confirms and no task publication', async ({
  page,
}) => {
  const state = await setup(page)
  await binding(page)
  await page.getByRole('switch', { name: '启用 report.ready', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]).toMatchObject({
    path: '/api/event-bindings/9223372036854775807',
    body: { enabled: false },
  })
  await page.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#event-binding-code').fill('report.done')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.event-binding-table')).toContainText('report.done')
  expect(state.writes[1]!.body.targetId).toBe(id)
  await page.getByRole('button', { name: '删除 report.done', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.event-binding-table')).not.toContainText('report.done')
  expect(state.writes).toHaveLength(3)
})
test('unknown binding write retains draft and requires read verification', async ({ page }) => {
  const state = await setup(page)
  state.fail = 'network'
  await binding(page)
  await page.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#event-binding-code').fill('report.unknown')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.event-binding-drawer')).toContainText('写入结果不明')
  await expect(page.locator('#event-binding-code')).toHaveValue('report.unknown')
  await page.getByRole('button', { name: '核实状态', exact: true }).click()
  await expect(page.locator('.event-binding-drawer')).toContainText('不代表上次请求必然成功')
  expect(state.writes).toHaveLength(1)
})
test('JOB entry scopes bindings to JOB identity', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/jobs')
  await page.locator('.job-actions').getByRole('button', { name: /更多/ }).click()
  await page.getByText('事件绑定', { exact: true }).click()
  await expect(page.locator('.event-binding-drawer')).toContainText('作业 · #' + id)
  await page.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#event-binding-code').fill('job.ready')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.targetType).toBe('JOB')
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`EVENT visuals ${theme} ${locale}`, async ({ page }, info) => {
      await setup(page, { action: 'WAIT_EVENT', eventCode: 'report.ready', lookbackSeconds: 0 })
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await open(page, locale === 'en-US')
      await expect(page.locator('.task-drawer')).toContainText(
        locale === 'en-US' ? 'Lookback' : '回看时间',
      )
      const box = await page.locator('.task-drawer').boundingBox()
      expect(box!.width).toBe(640)
      await page.screenshot({
        path: info.outputPath(`event-${theme}-${locale}.png`),
        fullPage: true,
      })
      await page.setViewportSize({ width: 390, height: 844 })
      await expect(page.locator('.task-drawer')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      expect(errors).toEqual([])
    })
