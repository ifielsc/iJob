// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const id = '9007199254740993'
async function setup(page: Page, theme = 'light', locale = 'zh-CN') {
  const reads: string[] = [],
    writes: string[] = []
  await page.addInitScript(
    (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
    { theme, locale },
  )
  const task = {
    id,
    name: '统一体验任务',
    pluginType: 'SQL',
    enabled: true,
    currentRevision: '2',
    currentTaskDefinitionId: '9007199254740995',
    updateTime: '2026-09-28T17:00:00',
    groups: ['finance', 'batch'],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
    pluginConfig: '{"resourceId":"123","script":"SELECT 2"}',
  }
  const job = {
    id,
    name: '统一体验作业',
    enabled: true,
    currentDefinitionId: '9007199254740995',
    currentRevision: '2',
    updateTime: task.updateTime,
    groups: task.groups,
    timeout: null,
    description: '',
    nodeCount: 3,
  }
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      u = new URL(req.url()),
      path = u.pathname
    if (!path.startsWith('/api/')) return route.continue()
    reads.push(path + u.search)
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
    if (path === '/api/reference-summaries')
      return ok([
        { kind: 'RESOURCE', id: '123', name: 'Referenced database', type: 'DB', enabled: true },
      ])
    if (req.method() !== 'GET') {
      writes.push(path)
      return route.fulfill({ status: 405 })
    }
    if (path === '/api/auth/me')
      return ok({
        loginId: 'review',
        username: 'review',
        displayName: 'Reviewer',
        roleCode: 'ADMIN',
        mustChangePassword: false,
        permissions: [
          'DEFINITION_READ',
          'SUMMARY_READ',
          'SENSITIVE_READ',
          'CONFIGURATION_WRITE',
          'EXECUTION_CONTROL',
          'USER_MANAGE',
          'AUDIT_READ',
        ],
      })
    if (path === '/api/schedules/metadata')
      return ok({
        zoneId: 'Asia/Shanghai',
        defaultMisfirePolicy: 'SKIP',
        defaultMisfireGraceSec: 1,
        defaultMaxCatchupCount: 1,
        maxCatchupWindowSec: 3600,
        maxPreviewCount: 100,
        maxIntervalSec: 86400,
        maxCatchupCount: 100,
        maxVariableCount: 100,
        maxVariableBytes: 65536,
      })
    if (path.endsWith('/groups')) return ok({ list: task.groups, total: 2 })
    if (path === '/api/tasks') return ok({ list: [task], total: 1 })
    if (path === '/api/jobs')
      return ok({
        list: [job, { ...job, id: '9007199254740996', name: '停用作业', enabled: false }],
        total: 2,
      })
    if (path === `/api/tasks/${id}`) return ok(task)
    if (path === `/api/tasks/${id}/versions`)
      return ok({
        list: [
          {
            taskId: id,
            definitionId: '9007199254740994',
            revision: '1',
            name: '旧名称',
            pluginType: 'SQL',
            enabled: true,
            publishedAt: task.updateTime,
            current: false,
          },
        ],
        total: 1,
      })
    if (path === `/api/tasks/${id}/versions/9007199254740994`)
      return ok({
        ...task,
        name: '旧名称',
        currentRevision: '1',
        currentTaskDefinitionId: '9007199254740994',
        pluginConfig: '{"script":"SELECT 1"}',
      })
    if (path === '/api/resources')
      return ok({
        list: [{ id: '123', name: 'DB', type: 'DB', code: 'res-db', enabled: true }],
        total: 1,
      })
    if (path === '/api/schedules') return ok({ list: [], total: 0 })
    if (path === '/api/audits') return ok({ list: [], total: 0 })
    return route.fulfill({ status: 404, json: { success: false, code: 404 } })
  })
  return { reads, writes }
}

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`unified lists, drawers and exact IDs ${theme} ${locale}`, async ({ page }, info) => {
      const { reads, writes } = await setup(page, theme, locale),
        en = locale === 'en-US'
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.setViewportSize({ width: 1440, height: 960 })
      await page.goto('/tasks')
      await expect(page.locator('.task-table')).toContainText('2026-09-29 01:00:00')
      await expect(page.locator('.task-table thead')).not.toContainText(
        en ? 'Current version' : '当前版本',
      )
      await expect(page.locator('.task-table')).toContainText('finance')
      await page.locator('.advanced-filter').click()
      await page.locator('#tasks-id').fill(id)
      await page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }).click()
      await expect
        .poll(() => reads.some((p) => p.startsWith('/api/tasks?') && p.includes('id=' + id)))
        .toBe(true)
      await page.screenshot({ path: info.outputPath('tasks.png'), fullPage: true })
      await page
        .getByRole('button', { name: (en ? 'Details' : '详情') + ' 统一体验任务', exact: true })
        .click()
      await expect(page.locator('.task-drawer')).toContainText('SELECT 2')
      await expect(
        page
          .locator('.task-drawer')
          .getByRole('button', { name: en ? 'Save' : '保存', exact: true }),
      ).toHaveCount(0)
      await page.screenshot({ path: info.outputPath('task-details.png'), fullPage: true })
      await page
        .locator('.task-drawer')
        .getByRole('button', { name: en ? 'Close' : '关闭', exact: true })
        .last()
        .click()
      await page
        .getByRole('button', { name: (en ? 'More' : '更多') + ' 统一体验任务', exact: true })
        .click()
      await page.getByText(en ? 'Version history' : '版本历史', { exact: true }).click()
      await expect(page.locator('.task-drawer')).toContainText('旧名称')
      expect(
        await page
          .locator('.task-drawer .task-table-scroll')
          .evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
      ).toBe(true)
      await page
        .locator('.task-drawer')
        .getByRole('button', { name: en ? 'Details' : '详情', exact: true })
        .click()
      await expect(page.locator('.task-drawer').filter({ hasText: 'SELECT 1' })).toBeVisible()
      const historical = page.locator('.task-drawer').filter({ hasText: 'SELECT 1' })
      await expect(historical.locator('.plugin-missing')).toContainText(
        en ? 'Fields not recorded in this version' : '本版本未记录的配置项',
      )
      await expect(historical).not.toContainText('SELECT 2')
      await page.goto('/jobs')
      await expect(page.locator('.job-table thead')).toContainText(en ? 'Nodes' : '节点数')
      await expect(page.locator('.job-table')).toContainText('finance')
      for (const [selector, variable] of [
        ['.job-enabled', '--success'],
        ['.job-disabled', '--error'],
      ]) {
        const status = page.locator(selector!).first()
        await expect(status).toBeVisible()
        expect(
          await status.evaluate((el, token) => {
            const sample = document.createElement('span')
            sample.style.color = `var(${token})`
            el.append(sample)
            const matches = getComputedStyle(el).color === getComputedStyle(sample).color
            sample.remove()
            return matches
          }, variable!),
        ).toBe(true)
      }
      await page.locator('.advanced-filter').click()
      await page.locator('#jobs-id').fill(id)
      await page.getByRole('button', { name: en ? 'Search' : '查询', exact: true }).click()
      await expect
        .poll(() => reads.some((p) => p.startsWith('/api/jobs?') && p.includes('id=' + id)))
        .toBe(true)
      await page.screenshot({ path: info.outputPath('jobs.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({ path: info.outputPath('jobs-mobile.png'), fullPage: true })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      expect(writes).toEqual([])
      expect(errors).toEqual([])
    })
  }

test('display timezone toggle preserves UTC audit query instants and system navigation', async ({
  page,
}) => {
  const { reads } = await setup(page)
  await page.goto('/audits')
  await expect(page.locator('.timezone-control')).toContainText('Asia/Shanghai')
  await page.locator('#audit-from').fill('2026-09-29 00:00:00')
  await page.locator('#audit-to').fill('2026-09-29 01:00:00')
  await expect(page.locator('#audit-from')).toHaveValue('2026-09-29 00:00:00')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const endpoint = () => reads.filter((v) => v.startsWith('/api/audits?')).at(-1) ?? ''
  await expect.poll(endpoint).toContain('2026-09-28T16')
  const before = endpoint()
  await page.locator('.timezone-control button').first().click()
  await expect(page.locator('#audit-from')).toHaveValue('2026-09-28 16:00:00')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  expect(endpoint()).toBe(before)
  await page.getByRole('button', { name: '系统管理', exact: true }).click()
  await expect(page.locator('#system-submenu')).toBeHidden()
  await page.getByRole('button', { name: '系统管理', exact: true }).click()
  await expect(page.locator('#system-submenu')).toBeVisible()
})

test('business timezone rejects ambiguous and nonexistent input without changing query instants', async ({
  page,
}) => {
  const { reads } = await setup(page)
  await page.route('**/api/schedules/metadata', (route) =>
    route.fulfill({ json: { success: true, code: 200, data: { zoneId: 'America/New_York' } } }),
  )
  await page.goto('/audits')
  await expect(page.locator('.timezone-control')).toContainText('America/New_York')
  await expect.poll(() => reads.filter((p) => p.startsWith('/api/audits?')).length).toBe(1)
  const count = reads.filter((p) => p.startsWith('/api/audits?')).length
  for (const value of ['2026-03-08 02:30:00', '2026-11-01 01:30:00']) {
    await page.locator('#audit-from').fill(value)
    await page.locator('#audit-to').fill(value.slice(0, 10) + ' 04:00:00')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.locator('#audit-from')).toHaveAttribute('aria-invalid', 'true')
    expect(reads.filter((p) => p.startsWith('/api/audits?')).length).toBe(count)
  }
  await page.locator('#audit-from').fill('2026-11-01 02:30:00.123456')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect
    .poll(() => decodeURIComponent(reads.filter((p) => p.startsWith('/api/audits?')).at(-1) ?? ''))
    .toContain('2026-11-01T07:30:00.123456')
})
