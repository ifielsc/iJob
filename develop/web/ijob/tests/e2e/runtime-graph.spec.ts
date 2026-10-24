// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect, type Page } from '@playwright/test'
const rootId = '9007199254740993',
  taskId = '9007199254740994',
  nestedId = '9007199254740995',
  deepId = '9007199254740996'
async function setup(page: Page, multilevel = false) {
  const state = {
    status: 'WAITING_EVENT',
    nodeStatus: 'WAITING_EVENT',
    reads: 0,
    fail: false,
    childInstance: true,
    paths: [] as string[],
  }
  const root = {
    id: rootId,
    rootInstanceId: rootId,
    targetId: '11',
    targetType: 'JOB',
    jobDefinitionId: '21',
    status: 'WAITING_EVENT',
  }
  const task = { ...root, id: taskId, parentInstanceId: rootId, targetType: 'TASK', targetId: '12' }
  const nested = {
    ...root,
    id: nestedId,
    parentInstanceId: rootId,
    targetId: '13',
    jobDefinitionId: '22',
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      state.paths.push(path)
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'graph-viewer',
          username: 'viewer',
          displayName: 'Viewer',
          roleCode: 'VIEWER',
          permissions: ['SUMMARY_READ'],
          mustChangePassword: false,
        })
      if (path.endsWith('/graph')) {
        state.reads++
        if (state.fail) return route.fulfill({ status: 500, json: { success: false, code: 500 } })
        const deep = path.includes(deepId)
        const child = path.includes(nestedId) || deep
        return ok({
          instanceId: deep ? deepId : child ? nestedId : rootId,
          rootInstanceId: rootId,
          definitionId: child ? '22' : '21',
          name: deep ? '第二层子作业' : child ? '嵌套作业' : '审批演示执行版本',
          status: state.status,
          layout: {
            positions: {
              prepare: { x: 20, y: 100 },
              approval: { x: 310, y: 100 },
              later: { x: 600, y: 100 },
            },
          },
          nodes: child
            ? [
                {
                  id: '201',
                  nodeKey: 'prepare',
                  name: '子作业步骤',
                  kind: 'EXECUTION',
                  nodeType: 'TASK',
                  refId: '14',
                  status: 'NOT_STARTED',
                },
                ...(!deep && multilevel
                  ? [
                      {
                        id: '202',
                        nodeKey: 'deeper',
                        name: '再次嵌套',
                        kind: 'EXECUTION',
                        nodeType: 'JOB',
                        refId: '15',
                        instanceId: deepId,
                        status: 'WAITING',
                      },
                    ]
                  : []),
              ]
            : [
                {
                  id: '101',
                  nodeKey: 'prepare',
                  name: '准备报表',
                  kind: 'EXECUTION',
                  nodeType: 'TASK',
                  refId: '12',
                  instanceId: taskId,
                  status: 'SUCCESS',
                },
                {
                  id: '102',
                  nodeKey: 'approval',
                  name: '人工审批',
                  kind: 'EXECUTION',
                  nodeType: 'TASK',
                  refId: '12',
                  status: state.nodeStatus,
                },
                {
                  id: '103',
                  nodeKey: 'later',
                  name: '后续子作业',
                  kind: 'EXECUTION',
                  nodeType: 'JOB',
                  refId: '13',
                  instanceId: state.childInstance ? nestedId : undefined,
                  status: 'WAITING',
                },
              ],
          edges: child
            ? []
            : [
                { id: '301', fromNodeId: '101', toNodeId: '102' },
                { id: '302', fromNodeId: '102', toNodeId: '103' },
              ],
        })
      }
      if (path.endsWith('/tree'))
        return ok({
          rootInstanceId: rootId,
          nodes: [
            { ...root, status: state.status },
            task,
            nested,
            ...(multilevel
              ? [{ ...nested, id: deepId, parentInstanceId: nestedId, targetId: '15' }]
              : []),
          ],
          gatewayDecisions: [],
          truncated: false,
        })
      return ok({ ...root, status: state.status })
    },
  )
  await page.goto('/runs/instances/' + rootId)
  await expect(page.locator('.run-graph-canvas .job-node')).toHaveCount(3)
  return state
}
test('complete graph polls by default, preserves zoom and selected node, and stops at terminal state', async ({
  page,
}) => {
  const state = await setup(page)
  const routing = page.locator('.run-graph-tools .job-routing-select')
  await expect(routing.locator('.n-base-selection')).toBeVisible()
  expect(await routing.evaluate((e) => e.previousElementSibling?.textContent)).toContain('定位选中')
  for (const mode of ['simple', 'avoid']) {
    await routing.click()
    await page
      .getByRole('option', { name: mode === 'simple' ? '简洁曲线' : '自动避障', exact: true })
      .click()
    await expect(page.locator('.run-graph-canvas .job-canvas')).toHaveAttribute(
      'data-routing-mode',
      mode,
    )
  }
  await page.locator('.run-graph-canvas .job-node').filter({ hasText: '准备报表' }).click()
  await page.locator('.run-graph-canvas .job-canvas-controls button').first().click()
  const zoom = await page.locator('.run-graph-canvas .job-zoom').innerText()
  state.nodeStatus = 'SUCCESS'
  await expect(
    page.locator('.run-graph-canvas .job-node').filter({ hasText: '人工审批' }),
  ).toContainText('成功', { timeout: 7000 })
  await expect(page.locator('.run-graph-canvas .job-zoom')).toHaveText(zoom)
  await expect(page.locator('.run-graph-canvas .job-node.is-selected')).toContainText('准备报表')
  state.status = 'SUCCESS'
  await expect.poll(() => state.reads).toBeGreaterThan(2)
  await page.waitForTimeout(2300)
  const before = state.reads
  await page.waitForTimeout(2300)
  expect(state.reads).toBe(before)
  expect(state.paths.some((p) => p.startsWith('/api/jobs/'))).toBe(false)
})
test('job selection stays in scope; explicit entry and double click restore root viewport', async ({
  page,
}) => {
  const state = await setup(page)
  const nodes = page.locator('.run-graph-canvas .job-node')
  const child = nodes.filter({ hasText: '后续子作业' })
  await child.click()
  await expect(nodes).toHaveCount(3)
  await expect(child).toHaveClass(/is-selected/)
  await expect(page.locator('.run-graph-selection')).toContainText('双击节点')
  const reads = state.reads
  await expect.poll(() => state.reads).toBeGreaterThan(reads)
  expect(state.paths).not.toContain('/api/instances/' + nestedId + '/graph')
  await page.locator('.run-graph-canvas .job-canvas-controls button').first().click()
  await page.waitForTimeout(300)
  const pane = page.locator('.run-graph-canvas .vue-flow__transformationpane')
  const viewport = await pane.getAttribute('style')
  await page
    .locator('.run-graph-navigation')
    .getByRole('button', { name: '进入子作业', exact: true })
    .click()
  await expect(nodes).toHaveCount(1)
  await expect(nodes).toContainText('子作业步骤')
  await expect(page.getByRole('button', { name: '返回根作业' })).toHaveClass(
    /n-button--primary-type/,
  )
  await page.getByRole('button', { name: '返回根作业' }).click()
  await expect(nodes).toHaveCount(3)
  await expect(pane).toHaveAttribute('style', viewport!)
  await expect(child).toHaveClass(/is-selected/)
  await expect(page.locator('.run-node[aria-current="true"]')).toContainText(nestedId)
  await expect(page.locator('.run-graph-selection')).toContainText(nestedId)
  await expect(page.getByRole('button', { name: '进入子作业', exact: true })).toHaveClass(
    /n-button--primary-type/,
  )
  await child.dblclick()
  await expect(nodes).toHaveCount(1)
  await page.getByRole('button', { name: '返回根作业' }).click()
  await expect(pane).toHaveAttribute('style', viewport!)
  await expect(child).toHaveClass(/is-selected/)
  await expect(page.locator('.run-node[aria-current="true"]')).toContainText(nestedId)
  await expect(page.locator('.run-graph-selection')).toContainText(nestedId)
  await expect(page.getByRole('button', { name: '进入子作业', exact: true })).toHaveClass(
    /n-button--primary-type/,
  )
})

test('tree and search select child jobs without navigation; jobs without instances cannot enter', async ({
  page,
}) => {
  const state = await setup(page)
  await page.locator('.run-node').filter({ hasText: nestedId }).click()
  await expect(page.locator('.run-graph-canvas .job-node')).toHaveCount(3)
  await expect(page.getByRole('button', { name: '进入子作业', exact: true })).toBeVisible()
  await page.locator('.run-graph-search input').fill('later')
  await page.getByRole('option', { name: '后续子作业 · later', exact: true }).click()
  await expect(page.locator('.run-graph-canvas .job-node')).toHaveCount(3)
  expect(state.paths).not.toContain('/api/instances/' + nestedId + '/graph')
  state.childInstance = false
  const reads = state.reads
  await expect.poll(() => state.reads).toBeGreaterThan(reads)
  await page.locator('.run-graph-canvas .job-node').filter({ hasText: '后续子作业' }).dblclick()
  await expect(page.locator('.run-graph-selection')).toContainText('尚无运行实例')
  await expect(page.getByRole('button', { name: '进入子作业', exact: true })).toHaveCount(0)
  await expect(page.locator('.run-graph-canvas .job-node')).toHaveCount(3)
})

test('runtime search locates nodes and exact edges and preserves selection across polling', async ({
  page,
}) => {
  const state = await setup(page)
  await page.locator('.run-graph-search input').fill('approval')
  await page.getByRole('option', { name: '人工审批 · approval', exact: true }).click()
  await expect(page.locator('.run-graph-canvas .job-node.is-selected')).toContainText('人工审批')
  await expect(page.locator('.run-graph-selection')).toContainText('人工审批')
  await page.locator('.run-graph-search input').fill('301')
  await page.getByRole('option', { name: '准备报表 → 人工审批 · 301', exact: true }).click()
  await expect(page.locator('.run-graph-selection')).toContainText('准备报表 → 人工审批 · 301')
  await expect(page.locator('.run-graph-canvas .vue-flow__edge[data-id="301"]')).toHaveClass(
    /selected/,
  )
  await expect(page.locator('.run-graph-canvas .job-node.is-selected')).toHaveCount(0)
  await page.getByRole('button', { name: '定位选中', exact: true }).click()
  await page.waitForTimeout(300)
  const viewport = await page
    .locator('.run-graph-canvas .vue-flow__transformationpane')
    .getAttribute('style')
  const reads = state.reads
  state.nodeStatus = 'RUNNING'
  await expect.poll(() => state.reads).toBeGreaterThan(reads)
  await expect(page.locator('.run-graph-selection')).toContainText('· 301')
  await expect(page.locator('.run-graph-canvas .vue-flow__transformationpane')).toHaveAttribute(
    'style',
    viewport!,
  )
  await page.locator('.run-graph-search input').fill('no-such-node')
  // Autocomplete results, excluding the native canvas display selector's options.
  await expect(page.locator('[role="option"]')).toHaveCount(0)
  await page.keyboard.press('Escape')
  expect(state.paths.some((p) => p.startsWith('/api/jobs/'))).toBe(false)
})
test('refresh failure preserves last graph with a warning; auto switch stops polling', async ({
  page,
}) => {
  const state = await setup(page)
  state.fail = true
  await expect(page.locator('.run-graph-panel')).toContainText('本次刷新失败', { timeout: 7000 })
  await expect(page.locator('.run-graph-canvas .job-node')).toHaveCount(3)
  await page.getByRole('checkbox').uncheck()
  const before = state.reads
  await page.waitForTimeout(2300)
  expect(state.reads).toBe(before)
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`graph visual ${theme} ${locale}`, async ({ page }, info) => {
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await page.setViewportSize({ width: 1440, height: 1000 })
      await setup(page)
      await expect(page.locator('.run-graph-canvas .vue-flow__edge')).toHaveCount(2)
      await page.screenshot({ path: info.outputPath('desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await expect
        .poll(async () => (await page.locator('.run-graph-canvas').boundingBox())!.width)
        .toBeLessThan(300)
      await page.locator('.run-graph-canvas .job-canvas-controls button').nth(2).click()
      await expect
        .poll(async () =>
          Number((await page.locator('.run-graph-canvas .job-zoom').innerText()).replace('%', '')),
        )
        .toBeLessThan(50)
      await page.locator('.run-graph-panel h2').click()
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 900 })
        expect(
          await page
            .locator('.run-graph-tools')
            .evaluate((e) => e.scrollWidth <= e.clientWidth + 1),
        ).toBe(true)
        await expect(page.locator('.run-graph-tools .job-routing-select')).toBeVisible()
      }
    })

test('maximized graph retains selection and zoom through refresh and Escape', async ({ page }) => {
  const state = await setup(page)
  await page.locator('.run-graph-search input').fill('人工审批')
  await page.getByRole('option', { name: '人工审批 · approval', exact: true }).click()
  await expect(page.locator('.run-graph-selection')).toContainText('人工审批')
  await page.waitForTimeout(300)
  const zoom = await page.locator('.run-graph-canvas .job-zoom').innerText()
  const width = (await page.locator('.run-graph-canvas').boundingBox())!.width
  await page.getByRole('button', { name: '最大化画布', exact: true }).click()
  await expect(page.locator('.run-graph-panel')).toHaveClass(/run-graph-maximized/)
  await expect
    .poll(async () => (await page.locator('.run-graph-canvas').boundingBox())!.width)
    .toBeGreaterThan(width)
  await expect(page.locator('.run-graph-canvas .job-zoom')).toHaveText(zoom)
  const reads = state.reads
  state.nodeStatus = 'RUNNING'
  await expect.poll(() => state.reads).toBeGreaterThan(reads)
  await expect(page.locator('.run-graph-selection')).toContainText('人工审批')
  await expect(page.locator('.run-graph-canvas .job-zoom')).toHaveText(zoom)
  await page.keyboard.press('Escape')
  await expect(page.locator('.run-graph-panel')).not.toHaveClass(/run-graph-maximized/)
  await expect(page.getByRole('button', { name: '最大化画布', exact: true })).toBeFocused()
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe('hidden')
})

test('returning from multiple levels restores each scope selection without navigating again on refresh', async ({
  page,
}) => {
  await setup(page, true)
  const nodes = page.locator('.run-graph-canvas .job-node')
  await nodes.filter({ hasText: '后续子作业' }).dblclick()
  await nodes.filter({ hasText: '再次嵌套' }).dblclick()
  await expect(page.locator('.run-graph-panel h2')).toContainText('第二层子作业')
  await page.getByRole('button', { name: '返回根作业', exact: true }).click()
  await expect(nodes.filter({ hasText: '后续子作业' })).toHaveClass(/is-selected/)
  await expect(page.locator('.run-node[aria-current="true"]')).toContainText(nestedId)
  await page.getByRole('button', { name: '进入子作业', exact: true }).click()
  await expect(nodes.filter({ hasText: '再次嵌套' })).toHaveClass(/is-selected/)
  await expect(page.locator('.run-node[aria-current="true"]')).toContainText(deepId)
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.locator('.run-graph-panel h2')).toContainText('嵌套作业')
  await expect(nodes.filter({ hasText: '再次嵌套' })).toHaveClass(/is-selected/)
})
