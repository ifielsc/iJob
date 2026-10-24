// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect, type Page } from '@playwright/test'
const errors = new WeakMap<Page, string[]>()
test.beforeEach(({ page }) => {
  const list: string[] = []
  errors.set(page, list)
  page.on('pageerror', (e) => list.push(e.message))
})
test.afterEach(({ page }) => expect(errors.get(page)).toEqual([]))
function routingLabel(value: string, locale = 'zh-CN') {
  const [direction, mode] = value.split(':')
  return locale === 'zh-CN'
    ? `${direction === 'LR' ? '从左到右' : '从上到下'} · ${mode === 'avoid' ? '自动避障' : '简洁曲线'}`
    : `${direction === 'LR' ? 'Horizontal' : 'Vertical'} · ${mode === 'avoid' ? 'Avoid obstacles' : 'Simple curves'}`
}
async function selectRouting(page: Page, value: string, locale = 'zh-CN') {
  await page.locator('.job-routing-select').click()
  await page.getByRole('option', { name: routingLabel(value, locale), exact: true }).click()
}
const jobId = '9223372036854775807'
const row = {
  id: jobId,
  name: '每日订单汇总',
  enabled: true,
  currentDefinitionId: '23010',
  currentRevision: '3',
  groups: ['daily'],
  timeout: 60,
  description: '示例作业',
  updateTime: '2026-09-26T08:20:00',
}
const policy = { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' }
const detail = {
  ...row,
  publishedAt: '2026-09-26T08:20:00',
  current: true,
  nodes: [
    {
      id: '301',
      nodeKey: 'read',
      name: '读取订单',
      kind: 'EXECUTION',
      nodeType: 'TASK',
      refId: '19040',
      refName: '订单读取任务',
      refDefinitionId: '19041',
      refRevision: '2',
      currentRefName: '订单读取任务',
      currentRefRevision: '3',
      referenceAvailable: true,
      policy,
    },
    {
      id: '302',
      nodeKey: 'gate',
      name: '条件网关',
      kind: 'GATEWAY',
      gateway: { mode: 'EXCLUSIVE' },
    },
    {
      id: '303',
      nodeKey: 'yes',
      name: '整理订单',
      kind: 'EXECUTION',
      nodeType: 'TASK',
      refId: '19042',
      refName: '整理任务',
      refRevision: '1',
      referenceAvailable: true,
      policy,
    },
    {
      id: '304',
      nodeKey: 'no',
      name: '归档记录',
      kind: 'EXECUTION',
      nodeType: 'JOB',
      refId: '19043',
      refName: '归档作业',
      refDefinitionId: '19044',
      refRevision: '1',
      currentRefRevision: '1',
      referenceAvailable: true,
      policy,
    },
  ],
  edges: [
    { id: '401', fromNodeId: '301', toNodeId: '302' },
    {
      id: '402',
      fromNodeId: '302',
      toNodeId: '303',
      routeKey: 'has-orders',
      priority: 10,
      defaultRoute: false,
      condition: { op: 'GT', left: { var: 'nodes.read.count' }, right: { literal: 0 } },
    },
    {
      id: '403',
      fromNodeId: '302',
      toNodeId: '304',
      routeKey: 'fallback',
      priority: 100,
      defaultRoute: true,
    },
  ],
  layout: {
    version: 1,
    positions: {
      read: { x: 30, y: 180 },
      gate: { x: 300, y: 160 },
      yes: { x: 550, y: 40 },
      no: { x: 550, y: 300 },
    },
  },
}
async function setup(page: Page, viewer = false) {
  const state = {
    reads: [] as string[],
    writes: [] as { path: string; body: string }[],
    conflict: false,
    failMetadata: false,
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname,
        ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'job-admin',
          username: 'admin',
          displayName: 'Job admin',
          roleCode: viewer ? 'VIEWER' : 'ADMIN',
          permissions: viewer
            ? ['DEFINITION_READ']
            : [
                'DEFINITION_READ',
                'SENSITIVE_READ',
                'CONFIGURATION_WRITE',
                'SUMMARY_READ',
                'EXECUTION_CONTROL',
              ],
          mustChangePassword: false,
        })
      if (path === '/api/jobs/metadata')
        return state.failMetadata
          ? route.fulfill({ status: 500, json: { success: false, code: 500 } })
          : ok({ maxNodes: 500, maxEdges: 5000 })
      if (path === '/api/jobs/validate')
        return ok({
          valid: true,
          issues: [],
          references: [{ nodeKey: 'read', name: '订单读取任务', refId: '19040', revision: '3' }],
        })
      if (req.method() !== 'GET') {
        state.writes.push({ path, body: req.postData() ?? '' })
        return state.conflict
          ? route.fulfill({ status: 409, json: { success: false, code: 409 } })
          : ok({ jobId, definitionId: '23011', revision: '4' })
      }
      if (path === '/api/jobs') {
        state.reads.push(url.search)
        return ok({ list: [row], total: 21 })
      }
      if (path === '/api/jobs/groups') return ok({ list: ['daily'], total: 1 })
      if (path.endsWith('/versions'))
        return ok({
          list: [
            {
              jobId,
              definitionId: '23010',
              revision: '3',
              name: row.name,
              enabled: true,
              publishedAt: row.updateTime,
              current: true,
            },
          ],
          total: 1,
        })
      if (path.includes('/versions/')) return ok({ ...detail, current: false })
      if (path === '/api/jobs/' + jobId) return ok(detail)
      if (path === '/api/notify-bindings') return ok([])
      return route.fulfill({ status: 404, json: { success: false, code: 404 } })
    },
  )
  return state
}
test('list query preserves page and editor round-trips stable keys, layout and plain edges', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/jobs')
  await expect(page.locator('.job-table tbody tr')).toHaveCount(1)
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect.poll(() => state.reads.at(-1)).toContain('pageNum=2')
  const count = state.reads.length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.length).toBe(count + 1)
  await page.locator('#jobs-name').fill('订单')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.at(-1)).toContain('pageNum=1')
  await page.locator('.job-table').getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.locator('.vue-flow__node')).toHaveCount(4)
  await expect(page.locator('#job-name')).toHaveValue(row.name)
  await page.locator('#job-name').fill('修改后的作业')
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('订单读取任务')
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const body = JSON.parse(state.writes[0]!.body)
  expect(body.nodes[0].nodeId).toBe('read')
  expect(body.edges[0]).toEqual({ fromNodeId: 'read', toNodeId: 'gate' })
  expect(body.layout.positions.read).toEqual({ x: 30, y: 180 })
  expect(body.name).toBe('修改后的作业')
})
test('conflict retains draft and read-only history has no publication controls', async ({
  page,
}) => {
  const state = await setup(page)
  state.conflict = true
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.locator('#job-name')).toHaveValue(row.name)
  await page.locator('#job-name').fill('冲突草稿')
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('#job-name')).toHaveValue('冲突草稿')
  await expect(
    page.getByText('版本已变化，当前草稿已保留。重新加载会放弃当前修改，请先核对。'),
  ).toBeVisible()
  expect(state.writes).toHaveLength(1)
  await page.goto('/jobs/' + jobId + '/versions/23010')
  await expect(page.locator('.vue-flow__node')).toHaveCount(4)
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '删除节点', exact: true })).toHaveCount(0)
})
for (const suffix of ['', '/edit', '/versions/23010'])
  test(`overview restores full contrast and clears selection in ${suffix || 'detail'}`, async ({
    page,
  }) => {
    const state = await setup(page)
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto('/jobs/' + jobId + suffix)
    await expect(page.locator('.vue-flow__node')).toHaveCount(4)
    await expect(page.getByText('正在加载作业...', { exact: true })).toHaveCount(0)
    await expect(page.locator('.job-graph-panel > .job-note')).toHaveCount(0)
    const help = page.getByRole('button', { name: '画布说明', exact: true })
    const tooltip = page.getByRole('tooltip')
    await expect(help).toBeVisible()
    await expect(tooltip).toBeHidden()
    await expect(page.locator('.job-heading .job-reference-hint')).toHaveCount(0)
    const canvasBefore = await page.locator('.job-canvas').boundingBox()
    await help.hover()
    await expect(tooltip).toBeVisible()
    if (suffix === '/edit') await expect(tooltip).toContainText('Shift')
    else await expect(tooltip).toContainText('展示发布时的引用')
    if (suffix !== '/edit') {
      await expect(tooltip).toContainText(suffix ? '展示发布时的引用' : '只读拓扑')
      await expect(page.locator('.job-editor > .job-notice')).toHaveCount(0)
      if (suffix) await expect(tooltip).toContainText('#23010')
      const spacing = await page
        .locator('.job-workspace')
        .evaluate(
          (e) =>
            e.getBoundingClientRect().top -
            document.querySelector('.job-heading')!.getBoundingClientRect().bottom,
        )
      expect(spacing).toBeLessThanOrEqual(20)
    }
    await tooltip.hover()
    await expect(tooltip).toBeVisible()
    expect(await page.locator('.job-canvas').boundingBox()).toEqual(canvasBefore)
    await page.keyboard.press('Escape')
    await expect(tooltip).toBeHidden()
    await page.mouse.move(0, 0)
    await help.focus()
    await expect(tooltip).toBeVisible()
    await expect(help).toHaveAttribute('aria-describedby', (await tooltip.getAttribute('id')) || '')
    await page.keyboard.press('Escape')
    await expect(tooltip).toBeHidden()
    await help.press('Enter')
    await expect(tooltip).toBeVisible()
    await help.press('Tab')
    await expect(tooltip).toBeHidden()
    await expect(page.locator('.job-unrelated')).toHaveCount(0)
    await expect(page.locator('.vue-flow__node.selected')).toHaveCount(0)
    await expect(page.locator('.vue-flow__node[data-id="yes"]')).toHaveCSS('opacity', '1')

    const node = page.locator('.vue-flow__node[data-id="read"]')
    await node.click()
    await expect(page.locator('.vue-flow__node.job-unrelated')).toHaveCount(2)
    await page.getByRole('button', { name: '适应画布', exact: true }).click()
    await expect(page.locator('.job-unrelated')).toHaveCount(0)
    await expect(page.locator('.vue-flow__node.selected')).toHaveCount(0)
    if (suffix === '/edit') await expect(page.locator('#job-name')).toHaveValue(row.name)
    else await expect(page.locator('.job-readonly-facts')).toContainText(row.name)
    await page.locator('.job-canvas').press('Delete')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.locator('.vue-flow__node')).toHaveCount(4)

    await node.click()
    await expect(page.locator('.vue-flow__node.job-unrelated')).toHaveCount(2)
    await page.locator('.vue-flow__pane').click({ position: { x: 10, y: 10 } })
    await expect(page.locator('.job-unrelated')).toHaveCount(0)
    await expect(page.locator('.vue-flow__node.selected')).toHaveCount(0)
    expect(state.writes).toHaveLength(0)
  })

test('viewer sees version metadata without topology or write controls', async ({ page }) => {
  const state = await setup(page, true)
  await page.goto('/jobs')
  await expect(page.locator('.job-table tbody tr')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '新增作业', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '详情', exact: true })).toHaveCount(0)
  await page.goto('/jobs/' + jobId + '/versions')
  await expect(page.locator('.job-table')).toContainText('23010')
  await expect(page.getByRole('button', { name: '查看拓扑', exact: true })).toHaveCount(0)
  expect(state.writes).toHaveLength(0)
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`job visuals ${theme} ${locale}`, async ({ page }, testInfo) => {
      await setup(page)
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.addInitScript(
        ({ theme, locale }) => {
          for (const key of ['guest', 'user:job-admin'])
            localStorage.setItem('ijob:preferences:v1:' + key, JSON.stringify({ theme, locale }))
        },
        { theme, locale },
      )
      await page.goto('/jobs/' + jobId + '/edit')
      await expect(page.locator('.vue-flow__node')).toHaveCount(4)
      await expect(
        page.getByRole('button', {
          name: locale === 'zh-CN' ? '保存并发布' : 'Save & publish',
          exact: true,
        }),
      ).toBeDisabled()
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
      await expect(page.locator('.application')).toHaveClass(/compact-shell/)
      await expect(page.locator('#job-name')).toHaveCSS('font-size', '13px')
      await page.screenshot({ path: testInfo.outputPath('desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({ path: testInfo.outputPath('mobile.png'), fullPage: true })
      await page.goto('/jobs/' + jobId + '/versions/23010')
      await expect(page.locator('.vue-flow__node')).toHaveCount(4)
      await expect(
        page.getByText(locale === 'zh-CN' ? '正在加载作业...' : 'Loading job...', { exact: true }),
      ).toHaveCount(0)
      await expect(page.locator('.job-properties')).toHaveCount(0)
      await expect(
        page.getByRole('button', {
          name: locale === 'zh-CN' ? '展开属性' : 'Show properties',
          exact: true,
        }),
      ).toBeVisible()
      await page
        .getByRole('button', { name: locale === 'zh-CN' ? '自动布局' : 'Auto layout', exact: true })
        .click()
      await page.screenshot({ path: testInfo.outputPath('mobile-history.png'), fullPage: true })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
    })

test('invalid condition literal survives selection changes, blocks publication and can be corrected', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeDisabled()
  await page
    .locator('.vue-flow__edge[data-id="402"] .vue-flow__edge-interaction')
    .click({ force: true })
  const input = page.getByRole('textbox', { name: '字面量（JSON） right', exact: true })
  await input.fill('[')
  await page.locator('.vue-flow__node[data-id="read"]').click()
  await page
    .locator('.vue-flow__edge[data-id="402"] .vue-flow__edge-interaction')
    .click({ force: true })
  await expect(input).toHaveValue('[')
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await expect(page.locator('.job-issues')).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await expect(page.getByRole('button', { name: '撤销', exact: true })).toBeEnabled()
  await input.fill('9223372036854775807')
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(input).toHaveValue('[')
  await page.getByRole('button', { name: '重做', exact: true }).click()
  await expect(input).toHaveValue('9223372036854775807')
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body).toContain('9223372036854775807')
})

test('compact properties do not scroll their tabs; search locates nodes and edges', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1440, height: 700 })
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  await expect(page.locator('.job-properties')).toHaveCSS('width', '360px')
  const tabs = page.locator('.job-properties .line-tabs')
  expect(await tabs.evaluate((e) => e.scrollHeight <= e.clientHeight + 1)).toBe(true)
  await page.locator('.job-search input').fill('yes')
  await page.getByRole('option').filter({ hasText: '整理订单 · yes' }).click()
  await expect(page.locator('#node-name')).toHaveValue('整理订单')
  const resizer = page.getByRole('separator', { name: '调整属性面板宽度' })
  await resizer.press('ArrowLeft')
  await expect(page.locator('.job-properties')).toHaveCSS('width', '376px')
  await page.locator('.job-search input').fill('fallback')
  await page.getByRole('option').filter({ hasText: 'fallback' }).click()
  await expect(page.getByRole('heading', { name: '连线属性' })).toBeVisible()
  await expect(page.locator('.vue-flow__edge.selected')).toHaveAttribute('data-id', '403')
})

test('all four ports can send and receive; drag order and saved ports remain directed', async ({
  page,
}) => {
  test.setTimeout(90000)
  const state = await setup(page)
  const graph = {
    ...detail,
    nodes: [detail.nodes[0], detail.nodes[2]],
    edges: [],
    layout: { version: 1, positions: { read: { x: 64, y: 224 }, yes: { x: 656, y: 224 } } },
  }
  await page.route('**/api/jobs/' + jobId, (route) =>
    route.fulfill({ json: { success: true, code: 200, data: graph } }),
  )
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '校验', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '收起属性', exact: true }).click()
  await page.getByRole('button', { name: '适应画布', exact: true }).click()
  await page.waitForTimeout(220)
  const port = (node: string, side: string) =>
    page.locator(`.vue-flow__node[data-id="${node}"] [data-handleid="${side}"]`)
  async function drag(from: string, source: string, to: string, target: string, valid = true) {
    const a = (await port(from, source).boundingBox())!,
      b = (await port(to, target).boundingBox())!
    await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2)
    await page.mouse.down()
    await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 8 })
    if (valid) await expect(port(to, target)).toHaveClass(/vue-flow__handle-valid/)
    else await expect(port(to, target)).not.toHaveClass(/vue-flow__handle-valid/)
    await page.mouse.up()
  }
  for (const source of ['left', 'top', 'right', 'bottom'])
    for (const target of ['left', 'top', 'right', 'bottom']) {
      await drag('read', source, 'yes', target)
      await expect(page.locator('.vue-flow__edge')).toHaveCount(1)
      await expect(page.locator('.vue-flow__edge [data-routing]')).toHaveAttribute(
        'data-routing',
        /^(direct|curve|detour)$/,
      )
      await page.getByRole('button', { name: '撤销', exact: true }).click()
      await expect(page.locator('.vue-flow__edge')).toHaveCount(0)
    }
  // Start from the old input side of the right-hand node: direction follows the drag.
  await drag('yes', 'left', 'read', 'bottom')
  await expect(page.locator('.vue-flow__edge')).toHaveCount(1)
  await drag('read', 'top', 'yes', 'right', false) // would close a cycle
  await drag('yes', 'top', 'read', 'right', false) // same directed pair, different ports
  await drag('read', 'left', 'read', 'right', false) // self-link
  await expect(page.locator('.vue-flow__edge')).toHaveCount(1)
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const body = JSON.parse(state.writes[0]!.body)
  expect(body.edges).toEqual([{ fromNodeId: 'yes', toNodeId: 'read' }])
  expect(body.layout.connections).toEqual([
    { fromNodeId: 'yes', toNodeId: 'read', sourceHandle: 'left', targetHandle: 'bottom' },
  ])
  // Simulate the saved definition's read API and open it again.
  await page.route('**/api/jobs/' + jobId, (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          ...graph,
          edges: [{ id: '499', fromNodeId: '303', toNodeId: '301' }],
          layout: body.layout,
        },
      },
    }),
  )
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.locator('.vue-flow__edge [data-routing]')).toHaveAttribute(
    'data-routing',
    /^(direct|curve|detour)$/,
  )
  await page.locator('#job-name').fill('QA14 ports republished')
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(2)
  expect(JSON.parse(state.writes[1]!.body).layout.connections).toEqual(body.layout.connections)
})

test('vertical drag connection, confirmed Delete and undo/redo retain directions', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  await page.getByRole('button', { name: '收起属性', exact: true }).click()
  await page.getByRole('button', { name: '适应画布', exact: true }).click()
  const from = page.locator(
    '.vue-flow__node[data-id="yes"] .vue-flow__handle[data-handleid="bottom"]',
  )
  const to = page.locator('.vue-flow__node[data-id="no"] .vue-flow__handle[data-handleid="top"]')
  await expect(from).toBeVisible()
  await page.waitForTimeout(220)
  const a = (await from.boundingBox())!,
    b = (await to.boundingBox())!
  await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2)
  await page.mouse.down()
  await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 12 })
  await expect(to).toHaveClass(/vue-flow__handle-valid/)
  await expect(page.locator('.vue-flow__connection-path')).toHaveAttribute(
    'data-routing',
    'preview',
  )
  expect(await page.locator('.vue-flow__connection-path').getAttribute('d')).toContain('C')
  await page.mouse.up()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(4)
  await page.locator('.job-canvas').focus()
  await page.keyboard.press('Delete')
  await expect(page.getByRole('dialog')).toContainText('整理订单 → 归档记录')
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.vue-flow__edge')).toHaveCount(4)
  await page.locator('.job-canvas').focus()
  await page.keyboard.press('Delete')
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  await expect(page.getByRole('button', { name: '撤销', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(4)
  await page.getByRole('button', { name: '重做', exact: true }).click()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  await page.locator('.job-search input').fill('read')
  await page.getByRole('option').filter({ hasText: '读取订单 · read' }).click()
  await page.locator('#node-name').press('Home')
  await page.locator('#node-name').press('Delete')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.vue-flow__node')).toHaveCount(4)
})

for (const count of [100, 500])
  test(`${count} nodes support search, minimap and large-graph layout`, async ({ page }) => {
    test.setTimeout(90000)
    await setup(page)
    const many = {
      ...detail,
      nodes: Array.from({ length: count }, (_, i) => ({
        ...detail.nodes[0],
        id: String(1000 + i),
        nodeKey: 'n' + i,
        name: '批次处理 ' + i,
      })),
      edges: Array.from({ length: count - 1 }, (_, i) => ({
        id: String(3000 + i),
        fromNodeId: String(1000 + i),
        toNodeId: String(1001 + i),
      })),
      layout: {
        version: 1,
        positions: Object.fromEntries(
          Array.from({ length: count }, (_, i) => [
            'n' + i,
            { x: (i % 10) * 260, y: Math.floor(i / 10) * 150 },
          ]),
        ),
      },
    }
    await page.route('**/api/jobs/' + jobId, (route) =>
      route.fulfill({ json: { success: true, code: 200, data: many } }),
    )
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto('/jobs/' + jobId + '/edit')
    await expect(page.locator('.vue-flow__node')).toHaveCount(count)
    await expect(page.locator('.vue-flow__edge')).toHaveCount(count - 1)
    await expect(page.locator('.vue-flow__edge [data-routing="pending"]')).toHaveCount(0, {
      timeout: 30000,
    })
    await page.locator('.job-search input').fill('n' + (count - 1))
    await page
      .getByRole('option')
      .filter({ hasText: '批次处理 ' + (count - 1) + ' · n' + (count - 1) })
      .click()
    await expect(page.locator('#node-name')).toHaveValue('批次处理 ' + (count - 1))
    await expect(page.locator('.job-zoom')).toHaveText('100%')
    await page.getByRole('button', { name: '小地图', exact: true }).click()
    await expect(page.locator('.job-minimap')).toBeVisible()
    await page.getByRole('button', { name: '最大化画布', exact: true }).click()
    await expect(page.locator('.job-editor')).toHaveClass(/job-maximized/)
    await selectRouting(page, 'TB:avoid')
    await page.getByRole('button', { name: '自动布局', exact: true }).click()
    await expect(page.locator('.vue-flow__edge')).toHaveCount(count - 1)
  })

test('Shift selection moves and deletes a group with one confirmation and can undo', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '校验', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '收起属性', exact: true }).click()
  await page.getByRole('button', { name: '适应画布', exact: true }).click()
  await page.waitForTimeout(200)
  const yes = page.locator('.vue-flow__node[data-id="yes"]'),
    no = page.locator('.vue-flow__node[data-id="no"]')
  await yes.click({ modifiers: ['Shift'] })
  await no.click({ modifiers: ['Shift'] })
  await expect(page.locator('.vue-flow__node.selected')).toHaveCount(2)
  const first = (await yes.boundingBox())!,
    second = (await no.boundingBox())!
  await page.mouse.move(first.x + first.width / 2, first.y + first.height / 2)
  await page.mouse.down()
  await page.mouse.move(first.x + first.width / 2 + 48, first.y + first.height / 2 + 32, {
    steps: 8,
  })
  await page.mouse.up()
  expect((await yes.boundingBox())!.x).toBeGreaterThan(first.x + 20)
  expect((await no.boundingBox())!.x).toBeGreaterThan(second.x + 20)
  await expect(page.getByRole('button', { name: '撤销', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await expect
    .poll(() => yes.evaluate((e) => e.style.transform))
    .toBe(`translate(${detail.layout.positions.yes.x}px, ${detail.layout.positions.yes.y}px)`)
  // Select a fresh group after restoring the snapshot.
  await yes.click({ modifiers: ['Shift'] })
  await no.click({ modifiers: ['Shift'] })
  await expect(page.locator('.vue-flow__node.selected')).toHaveCount(2)
  await page.locator('.job-canvas').focus()
  await page.keyboard.press('Delete')
  await expect(page.getByRole('dialog')).toContainText('2 个节点')
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.vue-flow__node')).toHaveCount(2)
  await expect(page.getByRole('button', { name: '撤销', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(page.locator('.vue-flow__node')).toHaveCount(4)
})

test('automatic routes avoid unrelated nodes, reroute after dragging and follow undo', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1600, height: 1000 })
  const graph = {
    ...detail,
    nodes: ['a', 'b', 'c'].map((key, i) => ({
      ...detail.nodes[0],
      id: String(301 + i),
      nodeKey: key,
      name: '避障节点 ' + key,
    })),
    edges: [{ id: '401', fromNodeId: '301', toNodeId: '303' }],
    layout: {
      version: 1,
      positions: { a: { x: 64, y: 224 }, b: { x: 384, y: 224 }, c: { x: 720, y: 224 } },
    },
  }
  await page.route('**/api/jobs/' + jobId, (route) =>
    route.fulfill({ json: { success: true, code: 200, data: graph } }),
  )
  await page.goto('/jobs/' + jobId + '/edit')
  const edge = page.locator('.vue-flow__edge[data-id="401"]')
  await expect(edge.locator('[data-routing]')).toHaveAttribute('data-routing', 'detour')
  const path = edge.locator('.vue-flow__edge-path')
  expect(
    await path.evaluate((el) => {
      const p = el as SVGPathElement,
        length = p.getTotalLength()
      return Array.from({ length: 501 }, (_, i) => p.getPointAtLength((length * i) / 500)).every(
        (p) => !(p.x > 376 && p.x < 572 && p.y > 216 && p.y < 308),
      )
    }),
  ).toBe(true)
  const before = await path.getAttribute('d')
  await page.waitForTimeout(250)
  const node = page.locator('.vue-flow__node[data-id="b"]'),
    rect = (await node.boundingBox())!
  await page.mouse.move(rect.x + rect.width / 2, rect.y + rect.height / 2)
  await page.mouse.down()
  await page.mouse.move(rect.x + rect.width / 2, rect.y + rect.height / 2 + 150, { steps: 10 })
  await expect(page.locator('.job-canvas')).toHaveAttribute('data-dragging', 'true')
  await page.waitForTimeout(150)
  await expect(path).toHaveAttribute('d', before!)
  await page.mouse.up()
  await expect(page.locator('.job-canvas')).toHaveAttribute('data-dragging', 'false')
  await expect(edge.locator('[data-routing]')).toHaveAttribute('data-routing', 'direct')
  expect(await path.getAttribute('d')).not.toBe(before)
  await expect(page.getByRole('button', { name: '撤销', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(edge.locator('[data-routing]')).toHaveAttribute('data-routing', 'detour')
  await expect(path).toHaveAttribute('d', before!)
  const click = await path.evaluate((el) => {
    const p = el as SVGPathElement,
      point = p.getPointAtLength(p.getTotalLength() / 2)
    const screen = new DOMPoint(point.x, point.y).matrixTransform(p.getScreenCTM()!)
    return { x: screen.x, y: screen.y }
  })
  await page.mouse.click(click.x, click.y)
  await expect(edge).toHaveClass(/selected/)
  await page.locator('.job-canvas').press('Delete')
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(edge).toBeVisible()
  // Incident edges follow the moved endpoint with a simple preview until release.
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const sourceNode = page.locator('.vue-flow__node[data-id="a"]')
  await sourceNode.hover()
  const source = (await sourceNode.boundingBox())!
  await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2)
  await page.mouse.down()
  await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2 - 50, {
    steps: 8,
  })
  await expect(page.locator('.job-canvas')).toHaveAttribute('data-dragging', 'true')
  await expect(edge.locator('[data-routing]')).toHaveAttribute('data-routing', 'preview')
  await page.mouse.up()
  await expect(edge.locator('[data-routing]')).toHaveAttribute(
    'data-routing',
    /^(direct|curve|detour)$/,
  )
})

test('routing preference changes only the view, persists per account and works in read-only history', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.getByRole('combobox', { name: '布局与连线', exact: true })).toBeVisible()
  await expect(page.locator('.job-routing-select')).toContainText(routingLabel('LR:avoid'))
  await page.locator('.vue-flow__node[data-id="read"]').click()
  const view = await page.locator('.vue-flow__transformationpane').getAttribute('style')
  await selectRouting(page, 'LR:simple')
  await expect(page.locator('.vue-flow__edge [data-routing="simple"]')).toHaveCount(3)
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem('ijob:preferences:v1:user:job-admin') ?? 'null')
          ?.edgeRouting,
    ),
  ).toBe('simple')
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem('ijob:preferences:v1:guest') ?? 'null')?.edgeRouting,
    ),
  ).toBeUndefined()
  await expect(page.locator('.vue-flow__node[data-id="read"]')).toHaveClass(/selected/)
  expect(await page.locator('.vue-flow__transformationpane').getAttribute('style')).toBe(view)
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '撤销', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(0)
  await page.reload()
  await expect(page.locator('.job-routing-select')).toContainText(routingLabel('LR:simple'))
  await expect(page.locator('.vue-flow__edge [data-routing="simple"]')).toHaveCount(3)
  await page.goto('/jobs/' + jobId + '/versions/23010')
  await expect(page.locator('.job-routing-select')).toContainText(routingLabel('LR:simple'))
  await selectRouting(page, 'LR:avoid')
  await expect(page.locator('.job-canvas')).toHaveAttribute('data-routing-pending', 'false')
  await expect(page.locator('.vue-flow__edge [data-routing="simple"]')).toHaveCount(0)
  await selectRouting(page, 'LR:simple')
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          loginId: 'another-viewer',
          username: 'viewer',
          displayName: 'Viewer',
          roleCode: 'VIEWER',
          permissions: ['DEFINITION_READ', 'SENSITIVE_READ'],
          mustChangePassword: false,
        },
      },
    }),
  )
  await page.reload()
  await expect(page.locator('.job-routing-select')).toContainText(routingLabel('LR:avoid'))
  await selectRouting(page, 'LR:simple')
  await selectRouting(page, 'LR:avoid')
  await page.unroute('**/api/auth/me')
  await page.reload()
  await expect(page.locator('.job-routing-select')).toContainText(routingLabel('LR:simple'))
  expect(state.writes).toHaveLength(0)
})

for (const theme of ['light', 'dark'] as const)
  for (const locale of ['zh-CN', 'en-US'] as const)
    test(`routing controls remain readable at 390 and 320 pixels ${theme} ${locale}`, async ({
      page,
    }, info) => {
      await setup(page)
      await page.addInitScript(
        ({ theme, locale }) => {
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale }))
        },
        { theme, locale },
      )
      await page.goto('/jobs/' + jobId + '/edit')
      const select = page.getByRole('combobox', {
        name: locale === 'zh-CN' ? '布局与连线' : 'Layout and connections',
        exact: true,
      })
      await expect(select).toBeVisible()
      await page
        .getByRole('button', {
          name: locale === 'zh-CN' ? '收起属性' : 'Hide properties',
          exact: true,
        })
        .click()
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 900 })
        const help = page.getByRole('button', {
          name: locale === 'zh-CN' ? '画布说明' : 'Canvas help',
          exact: true,
        })
        await help.click()
        const tooltip = page.getByRole('tooltip')
        await page.mouse.move(0, 0)
        // A focused help button keeps its text available after the hover hide delay.
        await page.waitForTimeout(250)
        await expect(tooltip).toContainText('Ctrl+Z')
        await expect
          .poll(() =>
            tooltip.evaluate((e) => {
              const b = e.getBoundingClientRect()
              return {
                left: b.left >= 0,
                right: b.right <= innerWidth,
                fits: e.scrollWidth <= e.clientWidth + 1,
              }
            }),
          )
          .toEqual({ left: true, right: true, fits: true })
        await page.screenshot({ path: info.outputPath(`help-${width}.png`) })
        await page.locator('.job-heading h1').click()
        await expect(tooltip).toBeHidden()
        for (const mode of ['simple', 'avoid']) {
          await selectRouting(page, 'LR:' + mode, locale)
          await expect(page.locator('.job-routing-select')).toContainText(
            routingLabel('LR:' + mode, locale),
          )
          await page.locator('.job-heading h1').click()
          const selector = page.locator('.job-routing-select')
          expect((await selector.boundingBox())!.width).toBeLessThanOrEqual(
            locale === 'zh-CN' ? 176 : 224,
          )
          expect(
            await selector.locator('.n-base-selection-overlay__wrapper').evaluate((e) => {
              const range = document.createRange()
              range.selectNodeContents(e)
              return range.getBoundingClientRect().width <= e.getBoundingClientRect().width + 1
            }),
          ).toBe(true)
          expect(
            await page.locator('.job-routing-select').evaluate((element) => {
              const bounds = element.getBoundingClientRect(),
                canvas = element.closest('.job-toolbar')!.getBoundingClientRect()
              return (
                element.previousElementSibling!.tagName === 'BUTTON' &&
                element.parentElement!.scrollWidth <= element.parentElement!.clientWidth + 1 &&
                bounds.left >= canvas.left &&
                bounds.right <= canvas.right
              )
            }),
          ).toBe(true)
          expect(
            await page.locator('.job-canvas-count').evaluate((element) => {
              const count = element.getBoundingClientRect()
              const controls = element
                .closest('.job-canvas')!
                .querySelector('.job-canvas-controls')!
                .getBoundingClientRect()
              return count.bottom <= controls.top
            }),
          ).toBe(true)
          await page
            .locator('.job-editor')
            .screenshot({ path: info.outputPath(`canvas-${width}-${mode}.png`) })
        }
      }
    })

test.describe('touch canvas help', () => {
  test.use({ hasTouch: true, viewport: { width: 390, height: 900 } })
  test('opens on tap and closes outside without changing the draft', async ({ page }) => {
    const state = await setup(page)
    await page.goto('/jobs/' + jobId + '/edit')
    const help = page.getByRole('button', { name: '画布说明', exact: true })
    await help.tap()
    await page.waitForTimeout(250)
    await expect(page.getByRole('tooltip')).toContainText('预检不锁定引用版本')
    await expect(page.getByRole('tooltip')).toContainText('Shift')
    await page.locator('.job-heading h1').tap()
    await expect(page.getByRole('tooltip')).toBeHidden()
    await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeDisabled()
    expect(state.writes).toHaveLength(0)
  })
})

test('5000-edge boundary remains searchable without console failures', async ({ page }) => {
  test.setTimeout(90000)
  await setup(page)
  const count = 101,
    nodes = Array.from({ length: count }, (_, i) => ({
      ...detail.nodes[0],
      id: String(1000 + i),
      nodeKey: 'd' + i,
      name: '密集节点 ' + i,
    }))
  const edges: { id: string; fromNodeId: string; toNodeId: string }[] = []
  for (let i = 0; i < count; i++)
    for (let j = i + 1; j < count && edges.length < 5000; j++)
      edges.push({
        id: String(3000 + edges.length),
        fromNodeId: String(1000 + i),
        toNodeId: String(1000 + j),
      })
  await page.route('**/api/jobs/' + jobId, (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          ...detail,
          nodes,
          edges,
          layout: {
            version: 1,
            positions: Object.fromEntries(
              nodes.map((n, i) => [n.nodeKey, { x: (i % 10) * 270, y: Math.floor(i / 10) * 150 }]),
            ),
          },
        },
      },
    }),
  )
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.locator('.vue-flow__edge')).toHaveCount(5000, { timeout: 45000 })
  await expect(page.locator('.vue-flow__edge [data-routing="pending"]')).toHaveCount(0, {
    timeout: 45000,
  })
  await page.locator('.job-search input').fill('d100')
  await page.getByRole('option').filter({ hasText: '密集节点 100 · d100' }).click()
  await expect(page.locator('#node-name')).toHaveValue('密集节点 100')
})

test('return from details retains exact ID, group and page size', async ({ page }) => {
  await setup(page)
  await page.goto('/jobs?id=' + jobId + '&group=daily&pageSize=50')
  await expect(page.locator('#jobs-id')).toHaveValue(jobId)
  await page.getByRole('button', { name: '详情', exact: true }).click()
  await expect(page.locator('.job-editor')).toBeVisible()
  await page.getByRole('button', { name: '返回作业', exact: true }).click()
  await expect(page.locator('#jobs-id')).toHaveValue(jobId)
  expect(new URL(page.url()).searchParams.get('group')).toBe('daily')
  expect(new URL(page.url()).searchParams.get('pageSize')).toBe('50')
})

test('editor canvas fills available height after resize and maximize with standard selects', async ({
  page,
}, info) => {
  await setup(page)
  await page.goto('/jobs/' + jobId + '/edit')
  await expect(page.locator('.job-routing-select.n-select .n-base-selection')).toBeVisible()
  for (const viewport of [
    { width: 1600, height: 1000 },
    { width: 1280, height: 800 },
    { width: 1600, height: 1200 },
  ]) {
    await page.setViewportSize(viewport)
    await expect
      .poll(() =>
        page
          .locator('.job-workspace')
          .evaluate((e) => innerHeight - e.getBoundingClientRect().bottom),
      )
      .toBeGreaterThanOrEqual(15)
    await expect
      .poll(() =>
        page
          .locator('.job-workspace')
          .evaluate((e) => innerHeight - e.getBoundingClientRect().bottom),
      )
      .toBeLessThanOrEqual(25)
  }
  await page.getByRole('button', { name: '最大化画布', exact: true }).click()
  await expect
    .poll(() =>
      page
        .locator('.job-workspace')
        .evaluate((e) => innerHeight - e.getBoundingClientRect().bottom),
    )
    .toBeLessThanOrEqual(17)
  await page.getByRole('button', { name: '还原画布', exact: true }).click()
  await page.locator('.job-routing-select').click()
  await expect(page.getByRole('option')).toHaveCount(4)
  await page.screenshot({ path: info.outputPath('editor-select-menu.png'), fullPage: true })
  await page.keyboard.press('Escape')
})

test('editor header is compact and auto layout keeps branch labels close to their edges', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/jobs/' + jobId + '/edit')
  const back = page.getByRole('button', { name: '返回作业', exact: true })
  await expect(back.locator('svg')).toBeVisible()
  expect(
    await back.evaluate((e) => {
      const color = getComputedStyle(e).color.match(/\d+/g)!.map(Number)
      return color[2]! > color[0]!
    }),
  ).toBe(true)
  await page.getByRole('button', { name: '画布说明', exact: true }).hover()
  await expect(page.getByRole('tooltip')).toContainText('预检不锁定引用版本')
  await page.keyboard.press('Escape')
  await expect(page.locator('.job-editor > .job-notice')).toHaveCount(0)
  await page.getByRole('button', { name: '收起属性', exact: true }).click()
  for (const direction of ['从左到右', '从上到下']) {
    await selectRouting(page, direction === '从左到右' ? 'LR:avoid' : 'TB:avoid')
    await page.getByRole('button', { name: '自动布局', exact: true }).click()
    await expect(page.locator('.job-canvas')).toHaveAttribute('data-routing-pending', 'false')
    await expect(page.locator('.job-route-label')).toHaveCount(2)
    await expect
      .poll(() =>
        page
          .locator('.job-label-guide')
          .evaluateAll((lines) =>
            Math.max(...lines.map((line) => (line as SVGPathElement).getTotalLength())),
          ),
      )
      .toBeLessThanOrEqual(64)
  }
})

test('removed condition draft survives undo but does not block route after publish', async ({
  page,
}) => {
  const state = await setup(page)
  const graph = {
    ...detail,
    nodes: [...detail.nodes, { ...detail.nodes[2], id: '305', nodeKey: 'extra', name: '保留分支' }],
    edges: [
      ...detail.edges,
      { ...detail.edges[1], id: '404', toNodeId: '305', routeKey: 'extra', priority: 30 },
    ],
    layout: {
      ...detail.layout,
      positions: { ...detail.layout.positions, extra: { x: 820, y: 40 } },
    },
  }
  await page.route('**/api/jobs/' + jobId, (route) =>
    route.request().method() === 'GET'
      ? route.fulfill({ json: { success: true, code: 200, data: graph } })
      : route.fallback(),
  )
  await page.goto('/jobs/' + jobId + '/edit')
  const edge = page.locator('.vue-flow__edge[data-id="402"] .vue-flow__edge-interaction')
  await edge.dispatchEvent('click')
  const literal = page.getByRole('textbox', { name: '字面量（JSON） right', exact: true })
  await literal.fill('[')
  // Wait for the documented 300 ms history coalescing interval before the structural edit.
  await page.waitForTimeout(350)
  // Make two distinct history points, then remove the owning node and its condition edge.
  await expect
    .poll(() => page.getByRole('button', { name: '撤销', exact: true }).isEnabled())
    .toBe(true)
  await page.locator('.vue-flow__node[data-id="yes"]').click()
  await page.getByRole('button', { name: '删除节点', exact: true }).click()
  // Wait for the mouse-origin scale transition before clicking its moving button.
  await expect(page.locator('.n-dialog')).not.toHaveClass(/transition-enter/)
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(page.locator('.vue-flow__edge[data-id="402"]')).toHaveCount(0)
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await edge.dispatchEvent('click')
  await expect(literal).toHaveValue('[')
  await page.getByRole('button', { name: '重做', exact: true }).click()
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  // Wait for the mouse-origin scale transition before clicking its moving button.
  await expect(page.locator('.n-dialog')).not.toHaveClass(/transition-enter/)
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect.poll(() => state.writes.length).toBe(1)
  await expect(page).toHaveURL(new RegExp('/jobs/' + jobId + '(?:\\?.*)?$'))
  await expect(page.locator('.n-dialog')).toHaveCount(0)
})
