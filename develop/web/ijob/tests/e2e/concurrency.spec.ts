import { test, expect, type Page } from '@playwright/test'
const id = '9007199254740993'
async function setup(page: Page, writer = true, active = 0) {
  const resource = {
    id,
    version: '9007199254740994',
    name: 'Report pool',
    code: 'report-pool',
    maxConcurrency: 8,
    enabled: true,
    description: 'Shared reports',
    referenceCount: 0,
    activeInstanceCount: active,
  }
  const task = {
    id,
    name: 'Daily report',
    pluginType: 'SQL',
    enabled: true,
    currentRevision: '3',
    currentTaskDefinitionId: '9007199254740995',
    groups: [],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
  }
  const state = {
    resources: [resource],
    plugins: [{ pluginType: 'SQL', version: '3', maxConcurrency: 10, activeInstanceCount: active }],
    task: {
      taskId: id,
      taskName: task.name,
      pluginType: 'SQL',
      version: '2',
      taskMaxConcurrency: 2,
      resources: [resource],
      activeInstanceCount: active,
    },
    fail: '',
    reads: 0,
    readUrls: [] as string[],
    writes: [] as { method: string; path: string; query: string; body: Record<string, unknown> }[],
  }
  await page.route(/\/api\/(?:auth|tasks|concurrency)(?:[/?]|$)/, async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname
    const ok = (data: unknown, status = 200) =>
      route.fulfill({ status, json: { success: true, code: status, data } })
    if (path === '/api/auth/me')
      return ok({
        loginId: 'concurrency-user',
        username: 'concurrency-user',
        displayName: 'Admin',
        roleCode: writer ? 'ADMIN' : 'VIEWER',
        mustChangePassword: false,
        permissions: [
          'SUMMARY_READ',
          'DEFINITION_READ',
          ...(writer ? ['CONFIGURATION_WRITE', 'SENSITIVE_READ'] : []),
        ],
      })
    if (req.method() !== 'GET') {
      const body = req.postData() ? req.postDataJSON() : {}
      state.writes.push({ method: req.method(), path, query: url.search, body })
      if (state.fail === 'network') return route.abort()
      if (state.fail === 'conflict' || (state.fail === 'partial' && path.endsWith('94'))) {
        state.resources[0]!.version = '9007199254740995'
        state.resources[0]!.maxConcurrency = 16
        return route.fulfill({
          status: 409,
          json: {
            success: false,
            code: 409,
            messageKey: 'error.concurrency.version',
            errorCode: 'IJOB-CONCURRENCY-VERSION',
          },
        })
      }
      if (path.startsWith('/api/concurrency/resources')) {
        if (req.method() === 'POST') {
          state.resources.push({ ...resource, ...body, id: '9007199254740996', version: '1' })
          return ok('9007199254740996', 201)
        }
        if (req.method() === 'PUT')
          Object.assign(
            state.resources.find((r) => path.endsWith(r.id))!,
            body,
            { version: '9007199254740995' },
          )
        if (req.method() === 'DELETE')
          state.resources = state.resources.filter((r) => !path.endsWith(r.id))
      }
      if (path.includes('/tasks/')) Object.assign(state.task, body, { version: '3' })
      return ok(null)
    }
    state.reads++
    state.readUrls.push(path + url.search)
    if (path === '/api/tasks') return ok({ list: [task], total: 1 })
    if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
    if (path === '/api/concurrency/resources')
      return route.fulfill({ status: 405, json: { success: false, code: 405 } })
    if (path === '/api/concurrency/resources/page') {
      const keyword = (url.searchParams.get('keyword') ?? '').toLowerCase()
      const enabled = url.searchParams.get('enabled') ?? ''
      const rows = state.resources.filter(
        (r) =>
          (r.name.toLowerCase().includes(keyword) || r.code.toLowerCase().includes(keyword)) &&
          (!enabled || String(r.enabled) === enabled),
      )
      const pageNum = Number(url.searchParams.get('pageNum') ?? 1),
        pageSize = Number(url.searchParams.get('pageSize') ?? 20)
      return ok({
        list: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize),
        total: rows.length,
        pageNum,
        pageSize,
        pages: Math.ceil(rows.length / pageSize),
      })
    }
    if (path === '/api/concurrency/resources/by-ids')
      return ok(
        state.resources.filter((r) =>
          (url.searchParams.get('ids') ?? '').split(',').includes(r.id),
        ),
      )
    if (path === '/api/concurrency/resources/by-code') {
      const found = state.resources.find((r) => r.code === url.searchParams.get('code'))
      return found ? ok(found) : route.fulfill({ status: 404, json: { success: false, code: 404 } })
    }
    if (path.startsWith('/api/concurrency/resources/'))
      return ok(state.resources.find((r) => path.endsWith(r.id)))
    if (path === '/api/concurrency/plugin-policies') return ok(state.plugins)
    if (path.startsWith('/api/concurrency/plugin-policies/'))
      return ok(state.plugins.find((p) => path.endsWith(p.pluginType)) ?? null)
    if (path === '/api/concurrency/tasks/' + id) return ok(state.task)
    if (path === '/api/concurrency/metadata')
      return ok({
        plugins: ['SQL', 'SCRIPT', 'EVENT'].map((type) => ({ type, activeInstanceCount: active })),
        minConcurrency: 1,
        maxConcurrency: 10000,
        permitWaitTimeoutMillis: 300000,
        retryIntervalMillis: 100,
        maxRetryIntervalMillis: 2000,
        retryJitterMillis: 100,
        configurationSource: 'API_NODE',
      })
    return route.fulfill({ status: 404, json: { success: false, code: 404 } })
  })
  return state
}
async function openResource(page: Page, en = false) {
  await page.goto('/concurrency')
  await page
    .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Report pool', exact: true })
    .click()
  await expect(page.locator('#concurrency-name')).toHaveValue('Report pool')
}
async function openTask(page: Page) {
  await page.goto('/tasks')
  await page.getByRole('button', { name: '更多 Daily report', exact: true }).click()
  await page.getByText('任务并发策略', { exact: true }).click()
  await expect(page.locator('#concurrency-maximum')).toHaveValue('2')
}
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`resource layout ${theme} ${locale}: vertical enabled and unified drawer`, async ({
      page,
    }, info) => {
      await setup(page)
      await page.setViewportSize({ width: 1440, height: 960 })
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await openResource(page, locale === 'en-US')
      const label = await page.locator('.concurrency-enabled label').boundingBox(),
        control = await page.locator('.concurrency-enabled .n-switch').boundingBox()
      expect(label && control).toBeTruthy()
      expect(control!.y).toBeGreaterThan(label!.y + label!.height)
      expect(Math.abs(control!.x - label!.x)).toBeLessThan(2)
      await expect(page.locator('.concurrency-fields > h3').first()).toHaveText(
        locale === 'en-US' ? 'Basic information' : '基本信息',
      )
      expect((await page.locator('.n-drawer').boundingBox())!.width).toBeCloseTo(520, 0)
      await expect(page.locator('#concurrency-name')).toHaveCSS('font-size', '13px')
      await page.screenshot({
        path: info.outputPath(`resource-${theme}-${locale}.png`),
        fullPage: true,
      })
    })
test('resource saves exact version; invalid maximum focuses the field', async ({ page }) => {
  const state = await setup(page)
  await openResource(page)
  await page.locator('#concurrency-maximum').fill('1.5')
  await expect(page.locator('#concurrency-maximum')).toHaveValue('8')
  await page.locator('#concurrency-maximum').fill('abc')
  await expect(page.locator('#concurrency-maximum')).toHaveValue('8')
  await page.locator('#concurrency-maximum').fill('')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#concurrency-maximum')).toBeFocused()
  expect(state.writes).toHaveLength(0)
  await page.locator('#concurrency-maximum').fill('12')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(state.writes[0]!.body.expectedVersion).toBe('9007199254740994')
  expect(state.writes[0]!.body.maxConcurrency).toBe(12)
})
test('resource activity allows increase but blocks lowering and disabling', async ({ page }) => {
  const state = await setup(page, true, 2)
  await openResource(page)
  await expect(page.getByText('已有活动实例', { exact: false })).toBeVisible()
  await page.locator('#concurrency-maximum').fill('3')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.locator('#concurrency-maximum').fill('12')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  await page.getByRole('switch', { name: '启用', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(0)
})
test('version conflict keeps draft and requires explicit reapply', async ({ page }) => {
  const state = await setup(page)
  await openResource(page)
  state.fail = 'conflict'
  await page.locator('#concurrency-maximum').fill('12')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#concurrency-maximum')).toHaveValue('12')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核实最新状态', exact: true }).click()
  await expect(page.locator('.concurrency-warning table')).toContainText('16')
  expect(state.writes).toHaveLength(1)
  await page.getByRole('button', { name: '按最新版本重新应用草稿', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  state.fail = ''
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(state.writes[1]!.body.expectedVersion).toBe('9007199254740995')
})
test('unknown result stays frozen after read and reopening', async ({ page }) => {
  const state = await setup(page)
  await openResource(page)
  state.fail = 'network'
  await page.locator('#concurrency-maximum').fill('12')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('保存结果待核实', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '核实最新状态', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(1)
  await page
    .locator('.concurrency-footer')
    .getByRole('button', { name: '关闭', exact: true })
    .click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByRole('button', { name: '编辑 Report pool', exact: true }).click()
  await expect(page.getByText('保存结果待核实', { exact: true })).toBeVisible()
  await expect(page.locator('#concurrency-maximum')).toHaveValue('12')
  expect(state.writes).toHaveLength(1)
})
test('task policy is independent, shows resource names and sends full exact set', async ({
  page,
}) => {
  const state = await setup(page)
  await openTask(page)
  await expect(page.locator('.concurrency-form')).toContainText('保存仅更新并发策略')
  await expect(page.locator('.concurrency-form')).toContainText('Report pool')
  await page.locator('#concurrency-maximum').fill('4')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0]!.path).toBe('/api/concurrency/tasks/' + id)
  expect(state.writes[0]!.body.resourceIds).toEqual([id])
  expect(state.writes[0]!.body.expectedVersion).toBe('2')
})
test('viewer reads task policy without sensitive access or mutation controls', async ({ page }) => {
  const state = await setup(page, false)
  await openTask(page)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
  await expect(page.locator('#concurrency-maximum')).toBeDisabled()
  expect(state.writes).toHaveLength(0)
})
test('plugin no-policy rows configure and active plugin cannot change', async ({ page }) => {
  await setup(page, true, 1)
  await page.goto('/concurrency')
  await page.getByRole('tab', { name: '插件限制', exact: true }).click()
  await page.getByRole('button', { name: '设置 SCRIPT', exact: true }).click()
  await expect(page.locator('#concurrency-maximum')).toHaveValue('')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('.concurrency-form')).toContainText('存在活动实例')
})
test('search filters complete collection before paging and same query keeps page', async ({
  page,
}) => {
  const state = await setup(page)
  state.resources = Array.from({ length: 45 }, (_, i) => ({
    ...state.resources[0]!,
    id: String(100 + i),
    code: 'pool-' + String(i).padStart(2, '0'),
    name: i === 44 ? 'Needle pool' : 'Pool ' + i,
  }))
  await page.goto('/concurrency')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.compact-pagination')).toContainText('2')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.compact-pagination')).toContainText('2')
  await page.locator('#concurrency-filter-name').fill('Needle')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await expect(page.locator('tbody')).toContainText('Needle pool')
  expect(state.reads).toBeGreaterThan(6)
})
test('dirty close protects input and Esc closes select before drawer', async ({ page }) => {
  await setup(page)
  await openTask(page)
  await page.locator('#concurrency-mode').click()
  await page.keyboard.press('Escape')
  await expect(page.locator('.n-drawer')).toBeVisible()
  await page.locator('#concurrency-maximum').fill('6')
  await page
    .locator('.concurrency-footer')
    .getByRole('button', { name: '取消', exact: true })
    .click()
  await expect(page.locator('.n-dialog')).toBeVisible()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('#concurrency-maximum')).toHaveValue('6')
})
for (const locale of ['zh-CN', 'en-US'])
  test(`narrow screen unknown result ${locale}`, async ({ page }, info) => {
    const state = await setup(page)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.addInitScript(
      (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
      { theme: 'dark', locale },
    )
    await openResource(page, locale === 'en-US')
    state.fail = 'network'
    await page.locator('#concurrency-maximum').fill('12')
    await page
      .getByRole('button', { name: locale === 'en-US' ? 'Save' : '保存', exact: true })
      .click()
    await expect(
      page.getByText(locale === 'en-US' ? 'Write result needs verification' : '保存结果待核实', {
        exact: true,
      }),
    ).toBeVisible()
    expect((await page.locator('.n-drawer').boundingBox())!.width).toBe(390)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
    await page.screenshot({ path: info.outputPath(`narrow-${locale}.png`), fullPage: true })
  })

test('list deletion confirms directly, preserves exact version and never opens editor', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/concurrency')
  await page
    .locator('.concurrency-list tbody')
    .getByRole('button', { name: '删除', exact: true })
    .click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(page.locator('.n-modal')).toContainText('Report pool')
  await expect(page.locator('.n-dialog.n-modal')).toBeVisible()
  await expect(page.locator('.n-modal.n-card')).toHaveCount(0)
  await page.locator('.n-modal').getByRole('button', { name: '取消', exact: true }).click()
  expect(state.writes).toHaveLength(0)
  await page
    .locator('.concurrency-list tbody')
    .getByRole('button', { name: '删除', exact: true })
    .click()
  await page.locator('.n-modal').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.n-modal')).toHaveCount(0)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0]!.query).toBe('?expectedVersion=9007199254740994')
  expect(state.resources).toHaveLength(0)
})

test('bulk deletion skips bound and active resources and reports partial conflict', async ({
  page,
}) => {
  const state = await setup(page)
  state.resources.push(
    ...[
      { id: '94', name: 'Changed pool' },
      { id: '95', name: 'Bound pool', referenceCount: 1 },
      { id: '96', name: 'Active pool', activeInstanceCount: 1 },
    ].map((overrides) => ({ ...state.resources[0]!, ...overrides })),
  )
  state.fail = 'partial'
  await page.goto('/concurrency')
  await expect(page.getByRole('checkbox', { name: '选择 Bound pool', exact: true })).toBeDisabled()
  await expect(page.getByRole('checkbox', { name: '选择 Active pool', exact: true })).toBeDisabled()
  await page.getByRole('checkbox', { name: '选择本页可删除资源', exact: true }).check()
  await expect(page.locator('.concurrency-toolbar')).toContainText('已选 2')
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.locator('.n-modal').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.concurrency-delete-results')).toContainText('操作已完成')
  await expect(page.locator('.concurrency-delete-results .field-error')).toBeVisible()
  expect(state.writes).toHaveLength(2)
  expect(state.resources.map((r) => r.id)).toEqual(['94', '95', '96'])
  await expect(page.locator('.n-drawer')).toHaveCount(0)
})

test('unknown deletion stops batch and verification never resends a write', async ({ page }) => {
  const state = await setup(page)
  state.resources.push({ ...state.resources[0]!, id: '94', name: 'Second pool' })
  state.fail = 'network'
  await page.goto('/concurrency')
  await page.getByRole('checkbox', { name: '选择本页可删除资源', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.locator('.n-modal').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.n-modal')).toContainText('删除结果待核实')
  await expect(page.locator('.n-modal')).toContainText('尚未提交删除')
  state.fail = ''
  await page.getByRole('button', { name: '核实最新状态', exact: true }).click()
  await expect(page.locator('.n-modal')).toContainText('资源仍存在')
  expect(state.writes).toHaveLength(1)
})

test('concurrency selection clears on search and tabs; viewer has no delete controls', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/concurrency')
  await page.getByRole('checkbox', { name: '选择本页可删除资源', exact: true }).check()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('button', { name: '批量删除', exact: true })).toBeDisabled()
  await page.getByRole('checkbox', { name: '选择本页可删除资源', exact: true }).check()
  await page.getByRole('tab', { name: '插件限制', exact: true }).click()
  await page.getByRole('tab', { name: '并发资源', exact: true }).click()
  await expect(page.getByRole('button', { name: '批量删除', exact: true })).toBeDisabled()
  await setup(page, false)
  await page.reload()
  await expect(page.getByRole('button', { name: '查看 Report pool', exact: true })).toBeVisible()
  await expect(page.getByRole('checkbox')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '批量删除', exact: true })).toHaveCount(0)
})

for (const writer of [true, false])
  test('resource name opens read-only details: writer=' + writer, async ({ page }, info) => {
    const state = await setup(page, writer)
    await page.goto('/concurrency')
    await page.getByRole('button', { name: '详情 Report pool', exact: true }).click()
    const drawer = page.locator('.concurrency-drawer')
    await expect(drawer).toContainText('Report pool')
    await expect(drawer.locator('.unified-facts')).toHaveCount(2)
    await expect(drawer.getByRole('textbox')).toHaveCount(0)
    await expect(drawer.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
    await expect(drawer.locator('.unified-technical')).not.toHaveAttribute('open')
    await page.screenshot({
      path: info.outputPath('concurrency-details.png'),
      animations: 'disabled',
    })
    await drawer.getByRole('button', { name: '关闭', exact: true }).last().click()
    await expect(page.getByRole('button', { name: '详情 Report pool', exact: true })).toBeFocused()
    expect(state.writes).toHaveLength(0)
  })

test('server paging uses code search and hydrates selections beyond the first page', async ({
  page,
}) => {
  const state = await setup(page)
  state.resources = Array.from({ length: 45 }, (_, i) => ({
    ...state.resources[0]!,
    id: String(100 + i),
    code: 'pool-' + String(i).padStart(2, '0'),
    name: 'Pool ' + i,
  }))
  state.task.resources = [state.resources[44]!]
  await page.goto('/concurrency')
  await expect(page.locator('tbody tr')).toHaveCount(20)
  await page.locator('#concurrency-filter-name').fill('pool-44')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await expect(page.locator('tbody')).toContainText('Pool 44')
  await openTask(page)
  await expect(page.locator('.concurrency-form')).toContainText('Pool 44')
  await page.locator('#concurrency-maximum').fill('4')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.resourceIds).toEqual(['144'])
  expect(
    state.readUrls.some((url) => url.startsWith('/api/concurrency/resources/by-ids?ids=144')),
  ).toBe(true)
  expect(state.readUrls.includes('/api/concurrency/resources')).toBe(false)
})

test('unknown creation checks exact code without listing or replaying writes', async ({ page }) => {
  const state = await setup(page)
  state.fail = 'network'
  await page.goto('/concurrency')
  await page.getByRole('button', { name: '新增资源', exact: true }).click()
  await page.locator('#concurrency-name').fill('Uncertain new pool')
  await page.locator('#concurrency-maximum').fill('8')
  const code = await page.locator('#concurrency-code').inputValue()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  state.fail = ''
  state.resources.push({
    ...state.resources[0]!,
    id: '155',
    code,
    name: 'Uncertain new pool',
    version: '1',
  })
  await page.getByRole('button', { name: '核实最新状态', exact: true }).click()
  await expect(page.locator('.concurrency-warning table')).toContainText('Uncertain new pool')
  expect(
    state.readUrls.some((url) => url === '/api/concurrency/resources/by-code?code=' + code),
  ).toBe(true)
  expect(state.readUrls.includes('/api/concurrency/resources')).toBe(false)
  expect(state.writes).toHaveLength(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
