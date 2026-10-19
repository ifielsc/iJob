// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
test('production EVENT editor preserves JSON and publishes exact task identity', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const writes: { path: string; body: Record<string, unknown> }[] = []
  const task = {
    id: '9007199254740993',
    name: 'Production event',
    pluginType: 'EVENT',
    enabled: true,
    currentRevision: '1',
    currentTaskDefinitionId: '9007199254740994',
    groups: [],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
    pluginConfig:
      '{"action":"PUBLISH","eventCode":"report.done","payload":{"id":9223372036854775807,"ok":false}}',
  }
  await page.route(/\/api\/(?:auth|tasks)(?:[/?]|$)/, async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname
    let data: unknown
    if (path === '/api/auth/me')
      data = {
        loginId: 'production',
        username: 'admin',
        displayName: 'Admin',
        roleCode: 'ADMIN',
        permissions: ['DEFINITION_READ', 'SENSITIVE_READ', 'CONFIGURATION_WRITE'],
        mustChangePassword: false,
      }
    else if (req.method() !== 'GET') {
      writes.push({ path, body: req.postDataJSON() })
      data = { taskId: task.id, definitionId: '9007199254740995', revision: '2' }
    } else
      data =
        path === '/api/tasks'
          ? { list: [task], total: 1 }
          : path.endsWith('/groups')
            ? { list: [], total: 0 }
            : task
    await route.fulfill({ json: { success: true, code: 200, data } })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Production event', exact: true }).click()
  await expect(page.locator('.event-fields')).toContainText('9223372036854775807')
  await page.locator('#task-name').fill('Production event updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.path).toBe('/api/tasks/9007199254740993')
  expect(writes[0]!.body.pluginConfig).toContain('9223372036854775807')
  expect(errors).toEqual([])
})
test('production JOB event binding uses shared drawer and server identity', async ({ page }) => {
  const writes: Record<string, unknown>[] = []
  const job = {
    id: '9007199254740993',
    name: 'Production job',
    enabled: true,
    currentRevision: '1',
    currentDefinitionId: '9007199254740994',
    groups: [],
    timeout: null,
    description: '',
  }
  await page.route(/\/api\/(?:auth|jobs|event-bindings)(?:[/?]|$)/, async (route) => {
    const req = route.request(),
      url = new URL(req.url())
    let data: unknown
    if (url.pathname === '/api/auth/me')
      data = {
        loginId: 'production',
        username: 'admin',
        displayName: 'Admin',
        roleCode: 'ADMIN',
        permissions: ['DEFINITION_READ', 'SENSITIVE_READ', 'CONFIGURATION_WRITE'],
        mustChangePassword: false,
      }
    else if (req.method() === 'POST') {
      writes.push(req.postDataJSON())
      data = '9223372036854775807'
    } else data = url.pathname === '/api/jobs' ? { list: [job], total: 1 } : []
    await route.fulfill({ json: { success: true, code: 200, data } })
  })
  await page.goto('/jobs')
  await page.locator('.job-actions').getByRole('button', { name: /更多/ }).click()
  await page.getByText('事件绑定', { exact: true }).click()
  await page.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#event-binding-code').fill('production.ready')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]).toMatchObject({
    targetType: 'JOB',
    targetId: job.id,
    eventCode: 'production.ready',
    enabled: true,
  })
})
