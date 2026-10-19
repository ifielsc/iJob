// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test, type Page } from '@playwright/test'
const taskId = '9007199254740993',
  configId = '9223372036854775806',
  bindingId = '9223372036854775807'
const task = {
  id: taskId,
  name: 'Binding task',
  pluginType: 'EVENT',
  enabled: true,
  currentRevision: '1',
  currentTaskDefinitionId: '9007199254740994',
  updateTime: '',
  groups: [],
  retryTimes: 0,
  retryInterval: 0,
  timeout: null,
}
const existing = {
  id: bindingId,
  targetType: 'TASK',
  targetId: taskId,
  notifyConfigId: configId,
  notifyConfigName: 'Ops email',
  notifyType: 'EMAIL',
  notifyConfigEnabled: true,
  notifyPolicy: 'ON_FAILURE',
}
async function setup(
  page: Page,
  records = [existing],
  permissions = ['SUMMARY_READ', 'DEFINITION_READ', 'SENSITIVE_READ', 'CONFIGURATION_WRITE'],
) {
  const state = {
    records: records.map((record) => ({ ...record })),
    reads: [] as string[],
    writes: [] as { method: string; path: string; body: Record<string, unknown> }[],
    failList: false,
    failSave: false,
    failCandidates: false,
  }
  await page.route(
    /\/api\/(?:auth|tasks|notify-bindings|notify-configs)(?:[/?]|$)/,
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown, status = 200) =>
        route.fulfill({ status, json: { success: true, code: status, data } })
      const fail = (status: number) =>
        route.fulfill({ status, json: { success: false, code: status } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'binding-admin',
          username: 'admin',
          displayName: 'Binding admin',
          roleCode: 'ADMIN',
          permissions,
          mustChangePassword: false,
        })
      if (req.method() !== 'GET') {
        const body = req.postDataJSON() ?? {}
        state.writes.push({ method: req.method(), path, body })
        if (state.failSave) return fail(409)
        if (req.method() === 'POST') {
          state.records.push({
            ...existing,
            id: '9007199254740995',
            notifyConfigId: body.notifyConfigId,
            notifyConfigName: 'Disabled bot',
            notifyType: 'DINGTALK',
            notifyConfigEnabled: false,
            notifyPolicy: body.notifyPolicy,
          })
          return ok('9007199254740995', 201)
        }
        if (req.method() === 'PUT') {
          const row = state.records.find((r) => path.endsWith('/' + r.id))
          if (row) row.notifyPolicy = body.notifyPolicy
          return ok(null)
        }
        state.records = state.records.filter((r) => !path.endsWith('/' + r.id))
        return ok(null)
      }
      state.reads.push(path + url.search)
      if (path === '/api/tasks') return ok({ list: [task], total: 1 })
      if (path === '/api/notify-bindings') return state.failList ? fail(500) : ok(state.records)
      if (path === '/api/notify-configs') {
        if (state.failCandidates) return fail(500)
        const second = url.searchParams.get('pageNum') === '2',
          name = url.searchParams.get('name')
        return ok({
          list:
            second || name
              ? [{ id: '9007199254740996', name: 'Disabled bot', type: 'DINGTALK', enabled: false }]
              : [{ id: configId, name: 'Ops email', type: 'EMAIL', enabled: true }],
          total: name ? 1 : 21,
        })
      }
      return fail(404)
    },
  )
  return state
}
async function open(page: Page, en = false) {
  await page.goto('/tasks')
  await page
    .getByRole('button', { name: `${en ? 'More' : '更多'} Binding task`, exact: true })
    .click()
  await page.getByText(en ? 'Notification bindings' : '通知绑定', { exact: true }).click()
  await expect(page.locator('.binding-drawer')).toBeVisible()
}
async function chooseBot(page: Page) {
  await page.locator('#binding-config').fill('Disabled')
  await page.locator('.n-base-select-option').filter({ hasText: 'Disabled bot' }).click()
}
test('CRUD preserves exact IDs, edits policy only, keeps task revision untouched and returns focus', async ({
  page,
}) => {
  const state = await setup(page)
  await open(page)
  const drawer = page.locator('.binding-drawer')
  await expect(drawer).toContainText('Ops email')
  await drawer.getByRole('button', { name: '添加绑定', exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#binding-config-error')).toHaveText('请选择通知配置。')
  await expect(page.locator('#binding-config')).toBeFocused()
  await chooseBot(page)
  await expect(drawer).toContainText('停用期间不会产生新的通知')
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer.getByRole('button', { name: '修改 Disabled bot', exact: true })).toBeEnabled()
  expect(state.writes[0]).toEqual({
    method: 'POST',
    path: '/api/notify-bindings',
    body: {
      targetType: 'TASK',
      targetId: taskId,
      notifyConfigId: '9007199254740996',
      notifyPolicy: 'ON_FAILURE',
    },
  })
  await drawer.getByRole('button', { name: '修改 Ops email', exact: true }).click()
  await page.locator('#binding-policy-' + bindingId).click()
  await page.getByRole('option', { name: '不通知', exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toContainText('不通知')
  expect(state.writes[1]).toEqual({
    method: 'PUT',
    path: '/api/notify-bindings/' + bindingId,
    body: { notifyPolicy: 'NEVER' },
  })
  await drawer.getByRole('button', { name: '移除 Ops email', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('不删除通知配置')
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(drawer.getByRole('button', { name: '修改 Ops email', exact: true })).toHaveCount(0)
  expect(state.writes.map((w) => w.path).every((p) => p.startsWith('/api/notify-bindings'))).toBe(
    true,
  )
  expect(state.reads.some((p) => /^\/api\/notify-configs\//.test(p))).toBe(false)
  await drawer
    .locator('.n-drawer-footer')
    .getByRole('button', { name: '关闭', exact: true })
    .click()
  await expect(page.getByRole('button', { name: '更多 Binding task', exact: true })).toBeFocused()
})
test('errors are distinct from empty state; paged candidates mark duplicates and retry without losing draft', async ({
  page,
}) => {
  const state = await setup(page)
  state.failList = true
  await open(page)
  const drawer = page.locator('.binding-drawer')
  await expect(drawer.getByRole('alert')).toBeVisible()
  await expect(drawer.getByText('暂无记录')).toHaveCount(0)
  state.failList = false
  await drawer.getByRole('button', { name: '刷新', exact: true }).click()
  await drawer.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#binding-config').click()
  const bound = page.locator('.n-base-select-option').filter({ hasText: 'Ops email' })
  await expect(bound).toContainText('已绑定')
  await expect(bound).toHaveClass(/disabled/)
  await page
    .locator('.binding-candidate-footer')
    .getByRole('button', { name: '下一页', exact: true })
    .click()
  await page.locator('.n-base-select-option').filter({ hasText: 'Disabled bot' }).click()
  state.failSave = true
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toContainText('重新读取会保留当前草稿')
  await drawer.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(drawer.locator('.n-drawer-footer')).toContainText('Disabled bot')
  state.failSave = false
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer.getByRole('button', { name: '添加绑定', exact: true })).toBeEnabled()
  expect(state.reads.some((p) => p.includes('pageNum=2'))).toBe(true)
})
test('candidate failures can retry and dropdown Escape precedes dirty-close confirmation', async ({
  page,
}) => {
  const state = await setup(page, [])
  state.failCandidates = true
  await open(page)
  const drawer = page.locator('.binding-drawer')
  await drawer.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#binding-config').click()
  await expect(page.locator('.binding-candidate-footer').getByRole('alert')).toBeVisible()
  state.failCandidates = false
  await page
    .locator('.binding-candidate-footer')
    .getByRole('button', { name: '重试', exact: true })
    .click()
  await expect(page.locator('.n-base-select-option').filter({ hasText: 'Ops email' })).toBeVisible()
  await expect(page.locator('#binding-config')).toHaveAttribute('aria-expanded', 'true')
  await page.locator('#binding-config').press('Escape')
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(drawer).toBeVisible()
  await chooseBot(page)
  await drawer
    .locator('.n-drawer-header')
    .getByRole('button', { name: '关闭', exact: true })
    .click()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(drawer.locator('.n-drawer-footer')).toContainText('Disabled bot')
  await drawer
    .locator('.n-drawer-footer')
    .getByRole('button', { name: '取消', exact: true })
    .click()
  await expect(drawer).toContainText('暂无记录')
  expect(state.writes).toHaveLength(0)
})
test('client pagination retains every binding and refresh keeps a removed row draft visible', async ({
  page,
}) => {
  const records = Array.from({ length: 21 }, (_, i) => ({
    ...existing,
    id: String(i + 1),
    notifyConfigId: String(i + 100),
    notifyConfigName: 'Config ' + (i + 1),
  }))
  const state = await setup(page, records)
  await open(page)
  const drawer = page.locator('.binding-drawer')
  await expect(drawer.locator('tbody tr')).toHaveCount(20)
  await drawer.getByRole('button', { name: '下一页', exact: true }).click()
  await drawer.getByRole('button', { name: '修改 Config 21', exact: true }).click()
  await page.locator('#binding-policy-21').click()
  await page.getByRole('option', { name: '始终通知', exact: true }).click()
  state.records = records.slice(0, 20)
  await drawer.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(drawer).toContainText('此绑定已被移除')
  await expect(page.locator('#binding-policy-21')).toHaveCount(1)
  await expect(drawer.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('pending saves disable closing and repeat submission; access revocation clears content', async ({
  page,
}) => {
  await setup(page, [])
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  let writes = 0
  await page.route('**/api/notify-bindings', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++
    await gate
    await route.fulfill({ status: 403, json: { success: false, code: 403 } })
  })
  await open(page)
  const drawer = page.locator('.binding-drawer')
  await drawer.getByRole('button', { name: '添加绑定', exact: true }).click()
  await chooseBot(page)
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer.getByRole('button', { name: '取消', exact: true })).toBeDisabled()
  await expect(drawer.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(drawer).toBeVisible()
  expect(writes).toBe(1)
  release()
  await expect(drawer).toHaveCount(0)
  await expect(page.getByText('Disabled bot')).toHaveCount(0)
})
test('reader has no binding menu and task listing never fans out binding reads', async ({
  page,
}) => {
  const state = await setup(page, [], ['SUMMARY_READ', 'DEFINITION_READ'])
  await page.goto('/tasks')
  await expect(page.getByText('Binding task', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '更多 Binding task', exact: true }).click()
  await expect(page.getByText('通知绑定', { exact: true })).toHaveCount(0)
  await expect(page.getByText('任务并发策略', { exact: true })).toBeVisible()
  expect(state.reads.filter((p) => p.startsWith('/api/notify-bindings'))).toHaveLength(0)
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`bindings visual ${theme} ${locale}`, async ({ page }, info) => {
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      const en = locale === 'en-US'
      await open(page, en)
      const drawer = page.locator('.binding-drawer')
      await expect(
        drawer.getByRole('button', { name: `${en ? 'Modify' : '修改'} Ops email`, exact: true }),
      ).toBeVisible()
      expect((await drawer.boundingBox())!.width).toBe(520)
      await page.screenshot({
        path: info.outputPath('bindings-desktop.png'),
        animations: 'disabled',
      })
      await drawer
        .getByRole('button', { name: en ? 'Add binding' : '添加绑定', exact: true })
        .click()
      await page.locator('#binding-config').click()
      await page.screenshot({ path: info.outputPath('bindings-add.png'), animations: 'disabled' })
      await page.keyboard.press('Escape')
      await page.setViewportSize({ width: 390, height: 844 })
      await expect(drawer).toBeVisible()
      expect((await drawer.boundingBox())!.width).toBe(390)
      const scroll = drawer.locator('.editable-table-scroll')
      expect(await scroll.evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(true)
      await scroll.evaluate((el) => {
        el.scrollLeft = el.scrollWidth
      })
      const action = drawer.locator('tbody tr').last().locator('td').last()
      expect(
        (await action.boundingBox())!.x + (await action.boundingBox())!.width,
      ).toBeLessThanOrEqual(390)
      await page.screenshot({
        path: info.outputPath('bindings-mobile.png'),
        animations: 'disabled',
      })
    })
