// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupPush } from '../push-fixture'
test.use({ viewport: { width: 1440, height: 1100 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    for (const type of ['KAFKA', 'RABBITMQ'])
      test('PUSH ' + type + ' ' + theme + ' ' + locale, async ({ page }, info) => {
        const state = await setupPush(page)
        await page.addInitScript(
          (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
          { theme, locale },
        )
        await page.goto('/tasks')
        await page
          .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' ' + type + ' push', exact: true })
          .click()
        await expect(page.locator('.push-fields')).toBeVisible()
        await expect(page.locator('.push-fields .cm-content')).toContainText('SELECT')
        await expect(page.locator('.push-fields')).toContainText(
          type === 'KAFKA' ? 'Orders Kafka' : 'Orders Rabbit',
        )
        await expect(page.locator('.task-heading-identity')).toContainText('#900719925474099')
        const before = await page.locator('[id="task-push.type"]').boundingBox(),
          target = await page.locator('[id="task-push.resourceId"]').boundingBox()
        expect(Math.abs(before!.y - target!.y)).toBeLessThan(3)
        await page.screenshot({ path: info.outputPath('desktop.png'), fullPage: true })
        await page.setViewportSize({ width: 320, height: 844 })
        expect((await page.locator('.task-drawer').boundingBox())!.width).toBeLessThanOrEqual(320)
        expect(
          (await page.locator('[id="task-push.resourceId"]').boundingBox())!.y,
        ).toBeGreaterThan((await page.locator('[id="task-push.type"]').boundingBox())!.y)
        await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
        await page.locator('#task-name').fill(type + ' push updated')
        await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
        await expect.poll(() => state.writes.length).toBe(1)
        expect(state.writes[0]!.body.pluginConfig).toContain('9007199254740993')
        expect(state.writes[0]!.body.pluginConfig).toContain('0.1234567890123456789')
        expect(state.tests).toHaveLength(0)
      })
    for (const type of ['kafka', 'rabbitmq'])
      test('resource ' + type + ' ' + theme + ' ' + locale, async ({ page }, info) => {
        const state = await setupPush(page)
        await page.addInitScript(
          (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
          { theme, locale },
        )
        await page.goto('/resources/' + type)
        await page
          .getByRole('button', {
            name: (en ? 'Edit' : '编辑') + ' Orders ' + (type === 'kafka' ? 'Kafka' : 'Rabbit'),
            exact: true,
          })
          .click()
        await expect(page.locator('.mq-drawer')).toBeVisible()
        expect((await page.locator('.mq-drawer').boundingBox())!.width).toBe(520)
        const label = await page.locator('label[for="mq-enabled"]').boundingBox(),
          toggle = await page.locator('#mq-enabled').boundingBox()
        expect(toggle!.y).toBeGreaterThanOrEqual(label!.y + label!.height)
        expect(Math.abs(label!.x - toggle!.x)).toBeLessThan(3)
        if (type === 'rabbitmq') await expect(page.locator('#mq-password')).toHaveCount(0)
        await page.locator('#mq-name').fill('Renamed')
        await page.screenshot({ path: info.outputPath('desktop.png'), fullPage: true })
        await page.setViewportSize({ width: 320, height: 844 })
        expect((await page.locator('.mq-drawer').boundingBox())!.width).toBeLessThanOrEqual(320)
        await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
        await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
        await expect.poll(() => state.writes.length).toBe(1)
        const config = state.writes[0]!.body[
          type === 'kafka' ? 'kafkaConfig' : 'rabbitConfig'
        ] as Record<string, unknown>
        expect(config.preserveExisting).toBe(true)
        expect(config.password).toBeUndefined()
        expect(state.tests).toHaveLength(0)
      })
  }
test('unknown save result prevents a second publication', async ({ page }) => {
  const state = await setupPush(page, true, 'network')
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 KAFKA push', exact: true }).click()
  await page.locator('#task-name').fill('KAFKA push updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('connection failure allows resource editing and password replacement', async ({ page }) => {
  const state = await setupPush(page)
  await page.goto('/resources/rabbitmq')
  await page.getByRole('button', { name: '连接测试', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '连接测试', exact: true }).click()
  await expect.poll(() => state.tests.length).toBe(1)
  await page.getByRole('button', { name: '编辑 Orders Rabbit', exact: true }).click()
  await page.getByRole('button', { name: '替换', exact: true }).click()
  await page.locator('#mq-password').fill('new-fixture-secret')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect((state.writes[0]!.body.rabbitConfig as Record<string, unknown>).password).toBe(
    'new-fixture-secret',
  )
})
test('read only resource has no save or replacement action', async ({ page }) => {
  const state = await setupPush(page, false)
  await page.goto('/resources/rabbitmq')
  await page.getByRole('button', { name: '查看 Orders Rabbit', exact: true }).click()
  await expect(page.locator('#mq-name')).toBeDisabled()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
  expect(state.writes).toHaveLength(0)
})
test('target switch preserves routing drafts and close protects them', async ({ page }) => {
  await setupPush(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 KAFKA push', exact: true }).click()
  await page.locator('[id="task-push.topic"]').fill('edited.topic')
  await page.locator('[id="task-push.type"]').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^RabbitMQ$/ })
    .click()
  await page.locator('[id="task-push.routingKey"]').fill('edited.route')
  await page.locator('[id="task-push.type"]').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^Kafka$/ })
    .click()
  await expect(page.locator('[id="task-push.topic"]')).toHaveValue('edited.topic')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toBeVisible()
})

test('invalid collapsed limit expands and focuses without publishing', async ({ page }) => {
  const state = await setupPush(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '"extra":0.',
    '"batchSize":0,"extra":0.',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 KAFKA push', exact: true }).click()
  await page.locator('#task-name').fill('KAFKA push updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('[id="task-push.batchSize"]')).toBeFocused()
  expect(state.writes).toEqual([])
})

test('unknown PUSH format blocks lossy save', async ({ page }) => {
  const state = await setupPush(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '"sql":',
    '"serializer":"future","sql":',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 KAFKA push', exact: true }).click()
  await expect(page.locator('.push-fields [role="alert"]')).toBeVisible()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(state.writes).toEqual([])
})

test('selected resource failure remains retryable after list refresh', async ({ page }) => {
  const state = await setupPush(page)
  state.resources.splice(1, 1)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 KAFKA push', exact: true }).click()
  await expect(
    page.locator('.push-fields').getByRole('button', { name: '重试', exact: true }),
  ).toBeVisible()
  await expect(page.locator('.push-fields')).toContainText('9223372036854775807')
  expect(state.writes).toEqual([])
})

test('unknown resource write blocks duplicate save', async ({ page }) => {
  const state = await setupPush(page, true, 'network')
  await page.goto('/resources/kafka')
  await page.getByRole('button', { name: '编辑 Orders Kafka', exact: true }).click()
  await page.locator('#mq-name').fill('Orders Kafka updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('#mq-name')).toBeDisabled()
})
