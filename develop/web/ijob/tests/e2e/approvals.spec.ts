// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect, type Page } from '@playwright/test'
const waitId = '9223372036854775807',
  instanceId = '9007199254740993'
async function setup(
  page: Page,
  permissions = ['SUMMARY_READ', 'DEFINITION_READ', 'APPROVAL_DECIDE'],
) {
  const state = {
    status: 'WAITING',
    mode: '',
    comment: '',
    actor: 'other-reviewer',
    reads: [] as string[],
    posts: [] as { path: string; comment: string }[],
    denied: false,
  }
  const row = () => ({
    id: waitId,
    instanceId,
    rootInstanceId: instanceId,
    taskId: '7',
    taskDefinitionId: '8',
    attemptNo: 0,
    waitType: state.mode === 'event' ? 'EVENT' : 'APPROVAL',
    status: state.status,
    taskName: '月末报表发布确认',
    rootTargetName: '月末财务汇总',
    createTime: '2026-09-27T08:00:00',
    timeoutAt: null,
    decisionBy: state.actor,
    decisionComment: state.comment,
    resolvedAt: state.status === 'WAITING' ? null : '2026-09-27T09:00:00',
  })
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      const fail = (status: number) =>
        route.fulfill({ status, json: { success: false, code: status } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'approver',
          username: 'approver',
          displayName: 'Approver',
          roleCode: 'OPERATOR',
          permissions,
          mustChangePassword: false,
        })
      if (path.startsWith('/api/event-waits')) {
        if (req.method() === 'POST') {
          state.posts.push({ path, comment: req.postDataJSON().comment })
          if (state.mode === 'unknown') return route.abort('failed')
          if (state.mode === 'conflict') {
            state.status = 'REJECTED'
            state.comment = 'Other decision'
            return fail(409)
          }
          state.status = path.endsWith('/approve')
            ? 'APPROVED'
            : path.endsWith('/reject')
              ? 'REJECTED'
              : 'WITHDRAWN'
          state.comment = state.mode === 'replay' ? 'Existing decision' : req.postDataJSON().comment
          return ok(row())
        }
        state.reads.push(path + url.search)
        if (path === '/api/event-waits') return ok({ list: [row()], total: 41 })
        if (state.denied) return fail(403)
        if (state.mode === 'missing') return fail(404)
        return ok(row())
      }
      if (path.startsWith('/api/instances/')) {
        const instance = {
          id: instanceId,
          rootInstanceId: instanceId,
          targetType: 'TASK',
          targetId: '7',
          status: 'WAITING_EVENT',
          taskDefinitionId: '8',
          createTime: '2026-09-27T08:00:00',
        }
        if (path.endsWith('/tree'))
          return ok({
            rootInstanceId: instanceId,
            nodes: [instance],
            gatewayDecisions: [],
            maxNodes: 250,
            truncated: false,
          })
        return ok(instance)
      }
      return fail(404)
    },
  )
  return state
}
async function open(page: Page) {
  await page.getByRole('button', { name: '查看 ' + waitId, exact: true }).click()
  await expect(page.locator('.approval-drawer')).toContainText('月末报表发布确认')
}
test('query preserves pagination, validates exact IDs and refreshes identical filters once', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/events/approvals?page=2')
  await expect(page.locator('.approval-table')).toContainText(waitId)
  const count = state.reads.length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.length).toBe(count + 1)
  expect(state.reads.at(-1)).toContain('pageNum=2')
  await page.locator('#approval-name').fill('报表')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.at(-1)).toContain('pageNum=1')
  await page.getByRole('button', { name: /高级筛选/ }).click()
  await page.locator('#approval-instanceId').fill('9223372036854775808')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('#approval-instanceId')).toBeFocused()
  await expect(page.getByText('请输入1至9223372036854775807的十进制整数ID。')).toBeVisible()
})
for (const action of ['通过', '拒绝', '撤回'])
  test(`decision ${action} confirms the exact record and returns actual actor`, async ({
    page,
  }) => {
    const state = await setup(page)
    state.mode = 'replay'
    await page.goto('/events/approvals')
    await open(page)
    await page.locator('#approval-comment').fill('My comment')
    await page
      .locator('.approval-footer')
      .getByRole('button', { name: action, exact: true })
      .click()
    await expect(page.locator('.n-dialog')).toContainText(waitId)
    await page
      .locator('.n-dialog')
      .getByRole('button', { name: '确认' + action, exact: true })
      .click()
    await expect(page.locator('.approval-drawer')).toContainText('Existing decision')
    await expect(page.locator('.approval-drawer')).toContainText('other-reviewer')
    expect(state.posts).toHaveLength(1)
    expect(state.posts[0]?.path).toContain(waitId)
    await expect(
      page.locator('.approval-footer').getByRole('button', { name: action, exact: true }),
    ).toHaveCount(0)
  })
test('unknown outcome never auto retries, survives close, and verifies actual outcome', async ({
  page,
}) => {
  const state = await setup(page)
  state.mode = 'unknown'
  await page.goto('/events/approvals')
  await open(page)
  await page.locator('#approval-comment').fill('Keep my input')
  await page.locator('.approval-footer').getByRole('button', { name: '通过', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认通过' }).click()
  await expect(page.getByText('提交结果不明', { exact: true })).toBeVisible()
  await expect(
    page.locator('.approval-footer').getByRole('button', { name: '拒绝', exact: true }),
  ).toBeDisabled()
  await page.getByRole('button', { name: '核实最新状态' }).click()
  await expect(page.getByText('后台仍显示待审批，不能据此确认上次请求未提交。')).toBeVisible()
  expect(state.posts).toHaveLength(1)
  await page.locator('.approval-footer').getByRole('button', { name: '关闭', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.approval-drawer')).toHaveCount(0)
  await open(page)
  await expect(page.locator('#approval-comment')).toHaveValue('Keep my input')
  await expect(page.getByText('提交结果不明', { exact: true })).toBeVisible()
  state.status = 'APPROVED'
  state.comment = 'Saved remotely'
  await page.getByRole('button', { name: '核实最新状态' }).click()
  await expect(page.locator('.approval-drawer')).toContainText('Saved remotely')
  expect(state.posts).toHaveLength(1)
})

test('double approval confirmation sends only one pending decision', async ({ page }) => {
  const state = await setup(page)
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  let sends = 0
  await page.route('**/api/event-waits/*/approve', async (route) => {
    sends++
    await pending
    await route.fallback()
  })
  await page.goto('/events/approvals')
  await open(page)
  await page.locator('.approval-footer').getByRole('button', { name: '通过', exact: true }).click()
  await page
    .locator('.n-dialog')
    .getByRole('button', { name: '确认通过', exact: true })
    .evaluate((button: HTMLButtonElement) => {
      button.click()
      button.click()
    })
  await expect.poll(() => sends).toBe(1)
  await page.waitForTimeout(200)
  expect(sends).toBe(1)
  release()
  await expect.poll(() => state.posts.length).toBe(1)
  await expect(
    page.locator('.approval-footer').getByRole('button', { name: '通过', exact: true }),
  ).toHaveCount(0)
})
test('conflict preserves comment and refresh shows actual terminal state', async ({ page }) => {
  const state = await setup(page)
  state.mode = 'conflict'
  await page.goto('/events/approvals')
  await open(page)
  await page.locator('#approval-comment').fill('Draft')
  await page.locator('.approval-footer').getByRole('button', { name: '通过', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认通过' }).click()
  await expect(page.locator('#approval-comment')).toHaveValue('Draft')
  await page.getByRole('button', { name: '刷新状态', exact: true }).click()
  await expect(page.locator('.approval-drawer')).toContainText('Other decision')
})
test('membership denial is local and does not wipe the inbox; 404 is distinct', async ({
  page,
}) => {
  const state = await setup(page)
  state.denied = true
  await page.goto('/events/approvals')
  await page.getByRole('button', { name: '查看 ' + waitId, exact: true }).click()
  await expect(page.getByText('当前账户无权查看或处理这条审批。')).toBeVisible()
  await expect(page.locator('.approval-table')).toContainText(waitId)
  state.denied = false
  state.mode = 'missing'
  await page.getByRole('button', { name: '刷新状态', exact: true }).click()
  await expect(page.getByText('未找到审批等待记录。')).toBeVisible()
})
test('run detail opens the exact latest approval and event waits have no approval button', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/runs/instances/' + instanceId)
  await page.getByRole('button', { name: '审批详情', exact: true }).click()
  await expect(page.locator('.approval-drawer')).toContainText(waitId)
  expect(state.reads).toContain('/api/event-waits/' + waitId)
  state.mode = 'event'
  await page.goto('/runs/instances/' + instanceId)
  await expect(page.locator('.run-tree')).toBeVisible()
  await expect(page.getByRole('button', { name: '审批详情', exact: true })).toHaveCount(0)
})
test('draft close can be cancelled and oversized opinions never submit', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/events/approvals')
  await open(page)
  await page.locator('#approval-comment').fill('x'.repeat(501))
  await page.locator('.approval-footer').getByRole('button', { name: '通过', exact: true }).click()
  expect(state.posts).toHaveLength(0)
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await page.locator('.approval-footer').getByRole('button', { name: '关闭', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('#approval-comment')).toHaveValue('x'.repeat(501))
  await page.locator('.approval-footer').getByRole('button', { name: '关闭', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.approval-drawer')).toHaveCount(0)
})

test('viewer cannot enter approvals or see the menu', async ({ page }) => {
  const state = await setup(page, ['SUMMARY_READ', 'DEFINITION_READ'])
  await page.goto('/events/approvals')
  await expect(page.locator('.approval-table')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '事件审批', exact: true })).toHaveCount(0)
  expect(state.reads).toHaveLength(0)
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`approval visuals ${theme} ${locale}`, async ({ page }, info) => {
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/events/approvals?waitId=' + waitId)
      await expect(page.locator('.approval-drawer')).toContainText('月末报表发布确认')
      await expect(page.locator('#approval-comment')).toBeVisible()
      await page.locator('#approval-comment').fill('Preserved across layout changes')
      await page.screenshot({ path: info.outputPath('approval-desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await expect(page.locator('#approval-comment')).toHaveValue('Preserved across layout changes')
      await page.screenshot({ path: info.outputPath('approval-mobile.png'), fullPage: true })
      expect(
        await page
          .locator('.approval-drawer')
          .evaluate((el) => el.getBoundingClientRect().width <= innerWidth),
      ).toBe(true)
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      expect(
        await page
          .locator('.approval-drawer-title h2')
          .evaluate((el) => getComputedStyle(el).fontSize),
      ).toBe('18px')
    })
