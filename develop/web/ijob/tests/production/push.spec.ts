// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupPush } from '../push-fixture'

test('production new PUSH selects exact references and only publishes', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const state = await setupPush(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('New push')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: 'PUSH · 数据推送', exact: true }).click()
  for (const [field, name] of [
    ['sourceResourceId', 'Orders DB'],
    ['resourceId', 'Orders Kafka'],
  ]) {
    await page.locator('[id="task-push.' + field + '"]').click()
    await page
      .locator('.n-base-select-option:visible')
      .filter({ hasText: new RegExp('^' + name + ' ·') })
      .click()
  }
  await page.locator('.push-fields .cm-content').fill('SELECT id FROM orders')
  await page.locator('[id="task-push.topic"]').fill('orders.ready')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.pluginConfig).toContain('"resourceId":9223372036854775807')
  expect(state.writes[0]!.path).toBe('/api/tasks')
  expect(state.tests).toEqual([])
  expect(errors).toEqual([])
})

test('production MQ resource chunks load and preserve hidden password', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const state = await setupPush(page)
  for (const [path, name] of [
    ['kafka', 'Kafka'],
    ['rabbitmq', 'Rabbit'],
  ]) {
    await page.goto('/resources/' + path)
    await page.getByRole('button', { name: '编辑 Orders ' + name, exact: true }).click()
    await expect(page.locator('.mq-drawer')).toBeVisible()
    await expect(page.locator('#mq-password')).toHaveCount(0)
    await page.locator('#mq-description').fill('Orders ' + name + ' updated')
    await page.getByRole('button', { name: '保存', exact: true }).click()
  }
  await expect.poll(() => state.writes.length).toBe(2)
  expect((state.writes[1]!.body.rabbitConfig as Record<string, unknown>).password).toBeUndefined()
  expect(errors).toEqual([])
})
