// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { batchConfig } from '../batch-etl-fixture'
test('real isolated Batch ETL: publish, exact values, typed references and conflict', async ({
  page,
  request,
}) => {
  const password = process.env.IJOB_TEST_PASSWORD!,
    next = password + '-Changed'
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('ui-test-admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '首次登录，请修改密码' })).toBeVisible()
  await page.getByLabel('当前密码', { exact: true }).fill(password)
  await page.getByLabel('新密码', { exact: false }).first().fill(next)
  await page.getByLabel('确认新密码', { exact: true }).fill(next)
  await page.getByRole('button', { name: '更新密码并重新登录' }).click()
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  await page.getByLabel('用户名', { exact: true }).fill('ui-test-admin')
  await page.getByLabel('密码', { exact: true }).fill(next)
  const login = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const token = (await (await login).json()).data,
    headers = { [token.tokenName]: token.tokenValue }
  const resource = async (data: unknown) => {
    const response = await request.post('/api/resources', { headers, data })
    expect(response.status(), await response.text()).toBe(201)
    return String((await response.json()).data)
  }
  const dbConfig = {
    dbType: 'MYSQL',
    jdbcUrl: 'jdbc:mysql://127.0.0.1:1/fixture',
    username: 'fixture',
  }
  const source = await resource({
    name: 'Batch source',
    code: 'batch-source',
    type: 'DB',
    enabled: true,
    dbConfig,
  })
  const target = await resource({
    name: 'Batch target',
    code: 'batch-target',
    type: 'DB',
    enabled: true,
    dbConfig,
  })
  const disabled = await resource({
    name: 'Disabled DB',
    code: 'disabled-db',
    type: 'DB',
    enabled: false,
    dbConfig,
  })
  const file = await resource({
    name: 'Wrong type',
    code: 'wrong-type',
    type: 'FILE',
    enabled: true,
    fileConfig: { protocol: 'LOCAL', basePath: process.env.IJOB_TEST_DIRECTORY },
  })
  const config = batchConfig
    .replace('"9007199254740993"', '"' + source + '"')
    .replace('"9223372036854775807"', '"' + target + '"')
  const body = {
    name: 'Batch browser fixture',
    pluginType: 'BATCH_ETL',
    pluginConfig: config,
    enabled: true,
    retryTimes: 0,
    retryInterval: 0,
  }
  for (const invalidId of [disabled, file]) {
    const response = await request.post('/api/tasks', {
      headers,
      data: {
        ...body,
        pluginConfig: config.replace(
          '"targetResourceId":"' + target + '"',
          '"targetResourceId":"' + invalidId + '"',
        ),
      },
    })
    expect(response.status()).toBe(400)
  }
  const create = await request.post('/api/tasks', { headers, data: body })
  expect(create.status(), await create.text()).toBe(201)
  const task = (await create.json()).data
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '任务管理', exact: true })
    .click()
  await page.getByRole('button', { name: '编辑 Batch browser fixture', exact: true }).click()
  await expect(page.locator('.batch-etl-fields')).toContainText('Batch source')
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await page.locator('[id="task-batchEtl.initial"]').fill('9007199254740993.987654321')
  const published = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + task.taskId) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await published).status()).toBe(200)
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  const detail = (await (await request.get('/api/tasks/' + task.taskId, { headers })).json()).data
  expect(detail.pluginConfig).toContain('9007199254740993.987654321')
  expect(detail.pluginConfig).toContain('0.1234567890123456789')
  expect(String(detail.currentRevision)).toBe('2')
  await page.getByRole('button', { name: '编辑 Batch browser fixture', exact: true }).click()
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await expect(page.locator('[id="task-batchEtl.initial"]')).toHaveValue(
    '9007199254740993.987654321',
  )
  const concurrent = await request.put('/api/tasks/' + task.taskId + '?expectedRevision=2', {
    headers,
    data: {
      ...body,
      pluginConfig: detail.pluginConfig,
      name: 'Concurrent Batch',
      expectedRevision: '2',
    },
  })
  expect(concurrent.status()).toBe(200)
  await page.locator('#task-name').fill('Browser conflict draft')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  const conflict = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + task.taskId) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await conflict).status()).toBe(409)
  await expect(page.locator('[id="task-batchEtl.initial"]')).toHaveValue(
    '9007199254740993.987654321',
  )
  expect((await request.delete('/api/resources/' + source, { headers })).status()).toBe(409)
  expect(
    (await (await request.get('/api/triggers?pageSize=1', { headers })).json()).data.total,
  ).toBe(0)
})
