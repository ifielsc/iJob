// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { verifyJobs } from './job-helper'
import { verifySchedules } from './schedule-helper'
import { expect, test } from '@playwright/test'
import type { Page, APIRequestContext } from '@playwright/test'
import { verifyScriptTasks } from './script-helper'
import { verifyApiCallTasks } from './api-call-helper'
import { verifyNotifyBindings } from './notify-binding-helper'
import { verifyRuntime } from './runtime-helper'
// These scenarios share the disposable account lifecycle and run in order.
test.describe.configure({ mode: 'serial' })
const initial = process.env.IJOB_TEST_PASSWORD!

async function verifySqlTask(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
) {
  // The integration runner owns this disposable loopback-only PostgreSQL cluster.
  const resource = await request.post('/api/resources', {
    headers,
    data: {
      name: 'Isolated SQL target',
      code: 'sql-browser-fixture',
      type: 'DB',
      enabled: true,
      dbConfig: {
        dbType: 'POSTGRESQL',
        jdbcUrl: 'jdbc:postgresql://127.0.0.1:15438/postgres',
        username: 'fixture',
        password: initial,
      },
    },
  })
  expect(resource.status()).toBe(201)
  const sqlResourceId = (await resource.json()).data
  expect(typeof sqlResourceId).toBe('string')
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '任务管理', exact: true })
    .click()
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('Isolated SQL task')
  await page.locator('#task-resourceId').click()
  await page.locator('.n-base-select-option').filter({ hasText: 'Isolated SQL target' }).click()
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  await page
    .locator('dialog.sql-large-editor .cm-content')
    .fill('SELECT COUNT(*) FROM demo_orders;')
  await page.getByRole('button', { name: '完成编辑', exact: true }).click()
  const created = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/tasks' && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const publication = (await (await created).json()).data
  expect(publication.revision).toBe('1')
  expect(typeof publication.taskId).toBe('string')
  expect(typeof publication.definitionId).toBe('string')
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  const historyBefore = (
    await (
      await request.get('/api/triggers?targetType=TASK&targetId=' + publication.taskId, { headers })
    ).json()
  ).data
  expect(historyBefore.total).toBe(0)
  await page.getByRole('button', { name: '编辑 Isolated SQL task', exact: true }).click()
  await expect(page.locator('#task-script')).toContainText('SELECT COUNT(*) FROM demo_orders;')
  const current = (
    await (await request.get('/api/tasks/' + publication.taskId, { headers })).json()
  ).data
  expect(JSON.parse(current.pluginConfig).resourceId).toBe(sqlResourceId)
  // Real compare-and-publish conflict while the browser retains its draft.
  const concurrent = await request.put('/api/tasks/' + publication.taskId + '?expectedRevision=1', {
    headers,
    data: { ...current, name: 'Isolated SQL task' },
  })
  expect(concurrent.ok()).toBe(true)
  await page.locator('#task-name').fill('Browser conflict draft')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('#task-name')).toHaveValue('Browser conflict draft')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await page.getByRole('button', { name: '执行 Isolated SQL task', exact: true }).click()
  const accepted = page.waitForResponse((r) => r.url().endsWith('/manual-trigger'))
  await page.locator('.n-dialog').getByRole('button', { name: '开始执行', exact: true }).click()
  const acceptedResponse = await accepted
  expect(acceptedResponse.status()).toBe(202)
  const triggerId = (await acceptedResponse.json()).data
  expect(typeof triggerId).toBe('string')
  await expect(page.locator('.task-results').getByRole('status')).toHaveText('执行成功', {
    timeout: 30000,
  })
  await expect(page.locator('.task-logs')).toContainText('SQL statement completed')
  const trigger = (await (await request.get('/api/triggers/' + triggerId, { headers })).json()).data
  expect(typeof trigger.rootInstanceId).toBe('string')
  expect(typeof trigger.taskDefinitionId).toBe('string')
  const instance = (
    await (await request.get('/api/instances/' + trigger.rootInstanceId, { headers })).json()
  ).data
  expect(instance.timeoutAt ?? null).toBeNull()
  const logs = (
    await (
      await request.get('/api/instances/' + trigger.rootInstanceId + '/logs', { headers })
    ).json()
  ).data
  expect(logs.length).toBeGreaterThan(0)
  expect(typeof logs[0].id).toBe('string')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('button', { name: '编辑 Isolated SQL task', exact: true }).click()
  await page.locator('#task-script').fill('SELECT * FROM missing_test_table;')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '执行 Isolated SQL task', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '开始执行', exact: true }).click()
  await expect(page.locator('.task-results').getByRole('status')).toHaveText('执行失败', {
    timeout: 30000,
  })
  await page.locator('.n-drawer-footer').getByRole('button', { name: '关闭', exact: true }).click()
  // Candidates follow current definitions, include disabled tasks and deduplicate across tasks.
  await page.getByRole('button', { name: '编辑 Isolated SQL task', exact: true }).click()
  await page.getByText('高级配置', { exact: true }).click()
  await page.locator('#task-groups').fill('Finance_Ops')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.locator('#tasks-group').click()
  await page.locator('.n-base-select-option').filter({ hasText: 'finance_ops' }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.task-table tbody tr')).toHaveCount(1)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  const groups = async (suffix = '') =>
    (await (await request.get('/api/tasks/groups' + suffix, { headers })).json()).data
  expect((await groups()).list).toEqual(['finance_ops'])
  expect((await request.get('/api/tasks/groups')).status()).toBe(401)
  expect((await request.get('/api/tasks/groups?prefix=%25', { headers })).status()).toBe(400)
  const grouped = (
    await (await request.get('/api/tasks/' + publication.taskId, { headers })).json()
  ).data
  const duplicate = await request.post('/api/tasks', {
    headers,
    data: {
      ...grouped,
      name: 'Group candidate fixture',
      enabled: false,
      groups: ['finance_ops', 'finance_x'],
    },
  })
  expect(duplicate.status()).toBe(201)
  const duplicateId = (await duplicate.json()).data.taskId
  expect(await groups('?pageSize=1&pageNum=2')).toMatchObject({ list: ['finance_x'], total: 2 })
  expect((await groups('?prefix=FINANCE_')).list).toEqual(['finance_ops', 'finance_x'])
  expect((await groups('?prefix=finance.')).list).toEqual([])
  expect(
    (
      await request.put(
        '/api/tasks/' + publication.taskId + '?expectedRevision=' + grouped.currentRevision,
        { headers, data: { ...grouped, groups: ['other'] } },
      )
    ).ok(),
  ).toBe(true)
  expect((await groups()).list).toEqual(['finance_ops', 'finance_x', 'other'])
  expect((await request.delete('/api/tasks/' + duplicateId, { headers })).ok()).toBe(true)
  expect((await groups()).list).toEqual(['other'])
  await verifyNotifyBindings(page, request, headers, publication.taskId)
  await verifyRuntime(page, request, headers, publication.taskId)
  await verifySchedules(page, request, headers, publication.taskId)
  await verifyJobs(page, request, headers, publication.taskId)
  await page.getByRole('button', { name: '更多 Isolated SQL task', exact: true }).click()
  await page.getByText('删除', { exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.task-table tbody tr')).toHaveCount(0)
  expect((await groups()).list).toEqual([])
  // Historical definitions deliberately retain the resource reference; the temporary fixture is discarded as a whole.
}

const changed = `${initial.slice(0, 30)}-changed-admin`
const viewerInitial = `${initial.slice(0, 30)}-initial-viewer`
const viewerReset = `${initial.slice(0, 30)}-reset-viewer`
const viewerChanged = `${initial.slice(0, 30)}-changed-viewer`

async function signIn(page: Page, username: string, password: string) {
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  return (await (await response).json()).data as { tokenName: string; tokenValue: string }
}
async function changePassword(page: Page, current: string, next: string) {
  await expect(page.getByRole('heading', { name: '首次登录，请修改密码' })).toBeVisible()
  await page.getByLabel('当前密码', { exact: true }).fill(current)
  await page.getByLabel('新密码', { exact: false }).first().fill(next)
  await page.getByLabel('确认新密码', { exact: true }).fill(next)
  await page.getByRole('button', { name: '更新密码并重新登录' }).click()
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
}
test('real isolated SQLite: login, mandatory change, accounts, revision conflict, access, reset, revocation and audits', async ({
  page,
  request,
}) => {
  test.setTimeout(90000)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/login')
  await signIn(page, 'ui-test-admin', initial)
  await changePassword(page, initial, changed)
  const token = await signIn(page, 'ui-test-admin', changed)
  const headers = { [token.tokenName]: token.tokenValue }
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '用户管理', exact: true })
    .click()
  await expect(page.getByRole('cell', { name: 'ui-test-admin', exact: true }).first()).toBeVisible()
  // Backend is an isolated fresh database, and last-admin protection remains authoritative.
  await page.getByRole('button', { name: '编辑 ui-test-admin', exact: true }).click()
  await page.getByRole('tab', { name: '角色与状态', exact: true }).click()
  await expect(page.getByLabel('启用', { exact: true })).toBeDisabled()
  await page.locator('.users-drawer').getByRole('combobox', { name: '角色', exact: true }).click()
  await page.getByRole('option', { name: '只读用户', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.users-drawer').getByRole('alert')).toContainText(
    '至少需要保留一个启用的管理员',
  )
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await page.getByRole('button', { name: '新增用户', exact: true }).click()
  await page.getByLabel('用户名', { exact: false }).fill('ui-test-viewer')
  await page.getByLabel('初始密码', { exact: true }).fill(viewerInitial)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  const listResponse = await request.get('/api/users?pageNum=1&pageSize=20', { headers })
  const records = (await listResponse.json()).data.list as {
    id: string
    username: string
    revision: string
  }[]
  const viewer = records.find((v) => v.username === 'ui-test-viewer')!
  expect(typeof viewer.id).toBe('string')
  await page.getByRole('button', { name: '编辑 ui-test-viewer', exact: true }).click()
  await page.getByLabel('显示名称', { exact: false }).fill('Browser draft')
  // A second administrator changes the same revision while the drawer stays open.
  const concurrent = await request.put(`/api/users/${viewer.id}/profile`, {
    headers,
    data: { expectedRevision: viewer.revision, displayName: 'Concurrent update' },
  })
  expect(concurrent.ok()).toBe(true)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByLabel('显示名称', { exact: false })).toHaveValue('Browser draft')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '读取最新记录' }).click()
  await expect(page.locator('.detail-list')).toContainText('Concurrent update')
  await page.getByRole('button', { name: '采用最新记录并重新编辑' }).click()
  await page.getByLabel('显示名称', { exact: false }).fill('Reviewed user')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '编辑 ui-test-viewer', exact: true }).click()
  await page.getByRole('tab', { name: '角色与状态', exact: true }).click()
  await page.locator('.users-drawer').getByRole('combobox', { name: '角色', exact: true }).click()
  await page.getByRole('option', { name: '操作员', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '编辑 ui-test-viewer', exact: true }).click()
  await page.getByRole('tab', { name: '重置密码', exact: true }).click()
  await page.getByLabel('新密码', { exact: true }).fill(viewerReset)
  await page
    .locator('.n-drawer-footer')
    .getByRole('button', { name: '重置密码', exact: true })
    .click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await signIn(page, 'ui-test-viewer', viewerReset)
  await changePassword(page, viewerReset, viewerChanged)
  const viewerToken = await signIn(page, 'ui-test-viewer', viewerChanged)
  await expect(page.getByRole('heading', { name: '工作空间', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '用户管理', exact: true })).toHaveCount(0)
  const denied = await request.get('/api/users', {
    headers: { [viewerToken.tokenName]: viewerToken.tokenValue },
  })
  expect(denied.status()).toBe(403)
  // Sign the administrator back in through a separate API session to revoke the open viewer.
  const adminLogin = await request.post('/api/auth/login', {
    data: { username: 'ui-test-admin', password: changed },
  })
  const adminToken = (await adminLogin.json()).data
  const adminHeaders = { [adminToken.tokenName]: adminToken.tokenValue }
  const latest = (
    await (await request.get('/api/users', { headers: adminHeaders })).json()
  ).data.list.find((v: { id: string }) => v.id === viewer.id)
  const revoked = await request.put(`/api/users/${viewer.id}/access`, {
    headers: adminHeaders,
    data: { expectedRevision: latest.revision, roleCode: 'VIEWER', enabled: true },
  })
  expect(revoked.ok()).toBe(true)
  // A protected page request observes revocation from the workspace landing page.
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '任务管理', exact: true })
    .click()
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  await signIn(page, 'ui-test-admin', changed)
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '操作审计', exact: true })
    .click()
  await expect(page.locator('tbody tr').first()).toBeVisible()
  const sortedResponse = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return url.pathname === '/api/audits' && url.searchParams.get('sortDirection') === 'asc'
  })
  await page.locator('.audit-sort').click()
  const sorted = (await (await sortedResponse).json()).data.list as {
    id: string
    occurredAt: string
  }[]
  expect(sorted.length).toBeGreaterThan(1)
  for (let index = 1; index < sorted.length; index++) {
    const previous = sorted[index - 1]!,
      current = sorted[index]!
    expect(previous.occurredAt <= current.occurredAt).toBe(true)
    if (previous.occurredAt === current.occurredAt)
      expect(BigInt(previous.id) < BigInt(current.id)).toBe(true)
  }
  await expect(page.locator('th[aria-sort]')).toHaveAttribute('aria-sort', 'ascending')
  await page.locator('tbody tr').first().getByRole('button').click()
  await expect(page.locator('.detail-list')).toBeVisible()
  await expect(page.locator('.detail-list')).not.toContainText(initial)
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toContain(token.tokenValue)
  expect(errors).toEqual([])
})

test('real isolated SQLite: database, calendar and notification configuration preservation', async ({
  page,
  request,
}) => {
  test.setTimeout(90000)
  await page.addInitScript(() => {
    window.addEventListener('error', (event) => {
      console.error(event.message)
    })
  })
  await page.goto('/login')
  const token = await signIn(page, 'ui-test-admin', changed)
  const headers = { [token.tokenName]: token.tokenValue }
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('ResizeObserver'))
      errors.push(message.text())
  })
  page.on('pageerror', (e) => errors.push(e.message))
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '数据库资源', exact: true })
    .click()
  await page.getByRole('button', { name: '新增数据库资源', exact: true }).click()
  await page.locator('#config-name').fill('Isolated database')
  await page.getByRole('combobox', { name: '数据库类型', exact: true }).click()
  await page.getByRole('option', { name: 'PostgreSQL', exact: true }).click()
  await page.locator('#config-jdbcUrl').fill('jdbc:postgresql://127.0.0.1:1/demo')
  await page.locator('#config-username').fill('demo')
  await page.locator('#config-password').fill('test-only-db-value')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  const resources = (await (await request.get('/api/resources?type=DB', { headers })).json()).data
    .list
  const resourceId = resources[0].id
  expect(typeof resourceId).toBe('string')
  await page.getByRole('button', { name: '编辑 Isolated database', exact: true }).click()
  await expect(page.locator('#config-jdbcUrl')).toHaveValue('jdbc:postgresql://127.0.0.1:1/demo')
  await expect(page.locator('#config-username')).toHaveValue('demo')
  await expect(page.locator('#config-password')).toHaveCount(0)
  await page.locator('#config-name').fill('Renamed database')
  await page.locator('#config-jdbcUrl').fill('jdbc:postgresql://127.0.0.1:1/renamed')
  await page.locator('#config-username').fill('changed-user')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  const db = (await (await request.get('/api/resources/' + resourceId, { headers })).json()).data
  expect(db.config).toMatchObject({
    jdbcUrlConfigured: true,
    usernameConfigured: true,
    passwordConfigured: true,
    jdbcUrl: 'jdbc:postgresql://127.0.0.1:1/renamed',
    username: 'changed-user',
  })
  expect(JSON.stringify(db)).not.toContain('test-only-db-value')
  expect(db.config).not.toHaveProperty('password')
  await page.getByRole('button', { name: '编辑 Renamed database', exact: true }).click()
  await expect(page.locator('#config-jdbcUrl')).toHaveValue(db.config.jdbcUrl)
  await expect(page.locator('#config-username')).toHaveValue(db.config.username)
  await page.getByRole('button', { name: '清除', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(
    (await (await request.get('/api/resources/' + resourceId, { headers })).json()).data.config
      .passwordConfigured,
  ).toBe(false)

  const channel = await request.post('/api/mail-channels', {
    headers,
    data: {
      name: 'Isolated mail channel',
      enabled: true,
      host: 'smtp.example.test',
      port: 25,
      security: 'NONE',
      authentication: false,
      from: 'sender@example.test',
    },
  })
  expect(channel.ok()).toBe(true)
  const mailChannelId = (await channel.json()).data
  expect(typeof mailChannelId).toBe('string')
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '通知管理', exact: true })
    .click()
  await page.getByRole('button', { name: '新增通知配置', exact: true }).click()
  await page.locator('#config-name').fill('Isolated email')
  await expect(page.locator('#config-from')).toBeVisible()
  await page.locator('#config-mailChannel').click()
  await page.getByText('Isolated mail channel · sender@example.test', { exact: true }).click()
  await expect(page.locator('#config-from')).toBeDisabled()
  await expect(page.locator('#config-from')).toHaveValue('sender@example.test')
  await page.locator('#config-to').fill('to1@example.test\nto2@example.test')
  await page.locator('#config-cc').fill('cc@example.test')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '编辑 Isolated email', exact: true }).click()
  await page.locator('#config-name').fill('Renamed email')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  const notifications = (await (await request.get('/api/notify-configs', { headers })).json()).data
    .list
  const notifyId = notifications[0].id
  const email = (await (await request.get('/api/notify-configs/' + notifyId, { headers })).json())
    .data
  expect(email.config).toMatchObject({
    mailChannelId,
    fromConfigured: true,
    toCount: 2,
    ccCount: 1,
    from: 'sender@example.test',
    to: ['to1@example.test', 'to2@example.test'],
    cc: ['cc@example.test'],
  })
  await page.getByRole('button', { name: '编辑 Renamed email', exact: true }).click()
  await expect(page.locator('#config-from')).toHaveValue('sender@example.test')
  await expect(page.locator('#config-to')).toHaveValue('to1@example.test\nto2@example.test')
  await expect(page.locator('#config-cc')).toHaveValue('cc@example.test')
  await page.locator('#config-cc').fill('')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  expect(
    (await (await request.get('/api/notify-configs/' + notifyId, { headers })).json()).data.config,
  ).toMatchObject({
    mailChannelId,
    fromConfigured: true,
    toCount: 2,
    ccCount: 0,
    from: 'sender@example.test',
    to: ['to1@example.test', 'to2@example.test'],
    cc: [],
  })

  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '日历管理', exact: true })
    .click()
  await page.getByRole('button', { name: '新增日历', exact: true }).click()
  await page.locator('#calendar-name').fill('Isolated calendar')
  await page.getByRole('button', { name: '添加日期', exact: true }).click()
  await page.getByLabel('日期 1', { exact: true }).fill('2026-09-25')
  await page.getByRole('button', { name: '批量添加', exact: true }).click()
  await page.locator('#exception-range-from').fill('2026-09-24')
  await page.locator('#exception-range-to').fill('2026-09-27')
  await page.getByRole('button', { name: '添加到表格', exact: true }).click()
  await expect(page.locator('.calendar-batch-actions')).toContainText('已添加 3 条')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Isolated calendar', exact: true })).toBeVisible()
  await page.getByLabel('起始日期', { exact: true }).fill('2026-09-21')
  await page.locator('#preview-days').fill('14')
  await page.locator('form').last().getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.calendar-day[aria-label="2026-09-25"]')).toContainText('休息')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.locator('#calendar-name').fill('Renamed calendar')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  const calendars = (await (await request.get('/api/calendars', { headers })).json()).data.list
  const detail = (
    await (await request.get('/api/calendars/' + calendars[0].id, { headers })).json()
  ).data
  expect(detail.exceptions).toHaveLength(4)
  expect(typeof detail.exceptions[0].id).toBe('string')

  const dingCreate = await request.post('/api/notify-configs', {
    headers,
    data: {
      name: 'Isolated bot',
      type: 'DINGTALK',
      enabled: false,
      dingTalkConfig: {
        webhook: 'https://example.test/hook?access_token=test-only-webhook-token',
        secret: 'test-only-signature',
        atAll: true,
        atMobiles: ['13800000000'],
      },
    },
  })
  expect(dingCreate.status()).toBe(201)
  const dingId = (await dingCreate.json()).data
  expect(typeof dingId).toBe('string')
  expect(
    (
      await request.put('/api/notify-configs/' + dingId, {
        headers,
        data: {
          name: 'Renamed bot',
          type: 'DINGTALK',
          enabled: false,
          dingTalkConfig: { preserveExisting: true, atAll: true },
        },
      })
    ).ok(),
  ).toBe(true)
  const bot = (await (await request.get('/api/notify-configs/' + dingId, { headers })).json()).data
  expect(bot).toMatchObject({
    enabled: false,
    config: {
      webhookConfigured: true,
      secretConfigured: true,
      atMobileCount: 1,
      atAll: true,
      webhookDisplay: 'https://example.test/hook?••••',
      atMobiles: ['13800000000'],
    },
  })
  expect(JSON.stringify(bot)).not.toContain('test-only-webhook-token')
  expect(JSON.stringify(bot)).not.toContain('test-only-signature')
  expect(bot.config.webhook).toBeUndefined()
  expect(bot.config.secret).toBeUndefined()
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '通知管理', exact: true })
    .click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '编辑 Renamed bot', exact: true }).click()
  await expect(page.locator('.masked-webhook')).toHaveText('https://example.test/hook?••••')
  await expect(page.locator('#config-mobiles')).toHaveValue('13800000000')
  await page.locator('#config-mobiles').fill('13900000000')
  const botUpdate = page.waitForRequest(
    (req) => req.method() === 'PUT' && req.url().endsWith('/notify-configs/' + dingId),
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await botUpdate).postDataJSON().dingTalkConfig).toEqual({
    preserveExisting: true,
    clearSecret: false,
    atAll: true,
    atMobiles: ['13900000000'],
  })
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '编辑 Renamed bot', exact: true }).click()
  await expect(page.locator('#config-mobiles')).toHaveValue('13900000000')
  await expect(page.locator('.masked-webhook')).toHaveText('https://example.test/hook?••••')
  await page.keyboard.press('Escape')
  await expect(page.locator('.n-drawer')).toHaveCount(0)
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '数据库资源', exact: true })
    .click()
  await expect(page).toHaveURL(/\/resources\/databases$/)
  await expect(page.getByRole('columnheader', { name: '类型', exact: true })).toHaveCount(0)
  await page.getByRole('checkbox', { name: '选择 Renamed database', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'Renamed database', exact: true })).toHaveCount(0)
  expect((await request.get('/api/resources/' + resourceId, { headers })).status()).toBe(404)
  // These configuration targets are never contacted and no notification is sent.
  // SQL execution below uses only the runner-owned loopback fixture.
  for (const [path, id] of [
    ['notify-configs', notifyId],
    ['notify-configs', dingId],
    ['calendars', calendars[0].id],
  ])
    expect((await request.delete('/api/' + path + '/' + id, { headers })).ok()).toBe(true)
  test.setTimeout(180000)
  await verifySqlTask(page, request, headers)
  await verifyScriptTasks(page, request, headers)
  await verifyApiCallTasks(page, request, headers)
  expect(errors).toEqual([])
})

test('real isolated SQLite: user deletion confirms, enforces permissions and revisions, revokes sessions and retains audits', async ({
  page,
  request,
}) => {
  test.setTimeout(150000)
  // Extra role sessions exercise the production login budget. Honor its explicit retry window.
  async function fixtureLogin(username: string, password: string) {
    let response = await request.post('/api/auth/login', { data: { username, password } })
    if (response.status() === 429) {
      const seconds = Number(response.headers()['retry-after'])
      expect(seconds).toBeGreaterThan(0)
      expect(seconds).toBeLessThanOrEqual(60)
      await new Promise((resolve) => setTimeout(resolve, seconds * 1000))
      response = await request.post('/api/auth/login', { data: { username, password } })
    }
    expect(response.status()).toBe(200)
    return response
  }
  await page.goto('/login')
  const token = await signIn(page, 'ui-test-admin', changed)
  const headers = { [token.tokenName]: token.tokenValue }
  const created = await request.post('/api/users', {
    headers,
    data: { username: 'delete-fixture', password: viewerInitial, roleCode: 'VIEWER' },
  })
  expect(created.ok()).toBe(true)
  const account = (await created.json()).data
  const login = await fixtureLogin(account.username, viewerInitial)
  const disposableToken = (await login.json()).data
  const disposableHeaders = { [disposableToken.tokenName]: disposableToken.tokenValue }
  const viewerLogin = await fixtureLogin('ui-test-viewer', viewerChanged)
  const viewerToken = (await viewerLogin.json()).data
  const scheduleReader = { [viewerToken.tokenName]: viewerToken.tokenValue }
  const scheduleMetadata = await request.get('/api/schedules/metadata', { headers: scheduleReader })
  expect(scheduleMetadata.ok()).toBe(true)
  expect((await scheduleMetadata.json()).data.zoneId).toBe('Asia/Shanghai')
  const schedulePreview = await request.post('/api/schedules/preview', {
    headers: scheduleReader,
    data: { scheduleType: 'INTERVAL', intervalSec: 120, fromTime: '2031-09-28T10:00:00', count: 2 },
  })
  expect(schedulePreview.ok()).toBe(true)
  expect((await schedulePreview.json()).data.items).toEqual([
    { fireTime: '2031-09-28T10:02:00', calendarAllowed: true },
    { fireTime: '2031-09-28T10:04:00', calendarAllowed: true },
  ])
  expect(
    (await request.post('/api/schedules', { headers: scheduleReader, data: {} })).status(),
  ).toBe(403)
  expect(
    (
      await request.delete(`/api/users/${account.id}?expectedRevision=0`, {
        headers: { [viewerToken.tokenName]: viewerToken.tokenValue },
      })
    ).status(),
  ).toBe(403)
  const stale = await request.delete(`/api/users/${account.id}?expectedRevision=1`, { headers })
  expect(stale.status()).toBe(409)
  const accounts = (await (await request.get('/api/users', { headers })).json()).data.list
  const admin = accounts.find((row: { username: string }) => row.username === 'ui-test-admin')
  const self = await request.delete(`/api/users/${admin.id}?expectedRevision=${admin.revision}`, {
    headers,
  })
  expect(self.ok()).toBe(false)
  expect((await self.json()).messageKey).toBe('error.security.self-delete')
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '用户管理', exact: true })
    .click()
  await expect(page.getByRole('button', { name: '删除 ui-test-admin', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '删除 delete-fixture', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('button', { name: '删除 delete-fixture', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '删除 delete-fixture', exact: true }).click()
  await page.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('button', { name: '删除 delete-fixture', exact: true })).toHaveCount(
    0,
  )
  expect((await request.get('/api/auth/me', { headers: disposableHeaders })).status()).toBe(401)
  const audit = (
    await (await request.get('/api/audits?actionCode=ACCOUNT_DELETE', { headers })).json()
  ).data
  expect(
    audit.list.some(
      (row: { targetId: string; resultCode: string }) =>
        row.targetId === account.id && row.resultCode === 'SUCCESS',
    ),
  ).toBe(true)
  const recreated = (
    await (
      await request.post('/api/users', {
        headers,
        data: { username: account.username, password: viewerInitial },
      })
    ).json()
  ).data
  expect(recreated.loginId).not.toBe(account.loginId)
  // Reuse this authenticated browser to cover filters and bulk deletion without
  // adding another login to the same fixture's rate-limit window.
  for (const index of [1, 2]) {
    const response = await request.post('/api/users', {
      headers,
      data: {
        username: 'batch-fixture-' + index,
        displayName: 'Batch Team',
        password: viewerInitial,
        roleCode: 'VIEWER',
        enabled: true,
      },
    })
    expect(response.ok()).toBe(true)
  }
  const filtered = await request.get(
    '/api/users?name=Batch%20Team&roleCode=VIEWER&enabled=true&pageSize=1&pageNum=1',
    { headers },
  )
  expect((await filtered.json()).data).toMatchObject({ total: 2, pages: 2 })
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '用户管理', exact: true })
    .click()
  await page.getByLabel('名称', { exact: true }).fill('Batch Team')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.users-table tbody tr')).toHaveCount(2)
  await page.getByRole('checkbox', { name: '选择当前页可删除用户', exact: true }).check()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.users-table tbody tr')).toHaveCount(0)
  await expect(page.locator('.n-message').filter({ hasText: '已删除 2 个用户' })).toBeVisible()
  const after = (await (await request.get('/api/users?name=batch-fixture', { headers })).json())
    .data
  expect(after.total).toBe(0)
})
