// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupBatchEtl } from '../batch-etl-fixture'
test.use({ viewport: { width: 1440, height: 1100 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    test('batch ETL ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupBatchEtl(page)
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.goto('/tasks')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Batch ETL 0', exact: true })
        .click()
      await expect(page.locator('.batch-etl-fields')).toBeVisible()
      await expect(page.locator('.task-drawer')).toHaveCSS('width', '640px')
      await expect(
        page.getByRole('table', { name: en ? 'Tables' : '同步表', exact: true }),
      ).toContainText('ods_orders')
      await page.screenshot({ path: info.outputPath('tables.png'), fullPage: true })
      await page
        .getByRole('tab', { name: en ? 'Incremental & limits' : '增量与限制', exact: true })
        .click()
      await expect(page.locator('[id="task-batchEtl.initial"]')).toHaveValue(
        '9007199254740993.123456789',
      )
      await page.locator('[id="task-batchEtl.initial"]').fill('9007199254740993.987654321')
      await page.screenshot({ path: info.outputPath('incremental.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page
          .locator('.batch-etl-fields')
          .evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
      ).toBe(true)
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      expect(String(state.writes[0]!.body.pluginConfig)).toContain('9007199254740993.987654321')
      expect(String(state.writes[0]!.body.pluginConfig)).toContain('0.1234567890123456789')
      expect(errors).toEqual([])
      expect(state.tests).toEqual([])
    })
  }
test('hidden limit errors expand and focus', async ({ page }) => {
  const state = await setupBatchEtl(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '"version":1',
    '"version":1,"execution":{"writeBatchSize":0}',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('[id="task-batchEtl.limits.writeBatchSize"]')).toBeFocused()
  expect(state.writes).toHaveLength(0)
})
test('tables add delete and reorder inline', async ({ page }) => {
  const state = await setupBatchEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  await page.getByRole('button', { name: '添加表', exact: true }).click()
  await page.locator('[id="task-batchEtl.tables.2"]').fill('order_items')
  await page.getByRole('button', { name: '第 3 行 · 上移', exact: true }).click()
  await page.getByRole('button', { name: '第 3 行 · 删除', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig)).tables).toEqual([
    'orders',
    'order_items',
  ])
})
test('unknown write result prevents duplicate publication', async ({ page }) => {
  const state = await setupBatchEtl(page, true, 'network')
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('unknown enum blocks saving', async ({ page }) => {
  const state = await setupBatchEtl(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '"version":1',
    '"version":1,"loadMode":"FUTURE"',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('read only roles cannot edit', async ({ page }) => {
  await setupBatchEtl(page, false)
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true })).toHaveCount(0)
})

test('new batch task publishes only after explicit clear acknowledgement', async ({ page }) => {
  const state = await setupBatchEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('New batch task')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: 'BATCH_ETL · 批量 ETL', exact: true }).click()
  for (const key of ['sourceResourceId', 'targetResourceId']) {
    await page.locator('[id="task-batchEtl.' + key + '"]').click()
    await page
      .locator('.n-base-select-option:visible')
      .filter({ hasText: '#9223372036854775807' })
      .click()
    await expect(page.locator('.n-base-select-option:visible')).toHaveCount(0)
  }
  await page.getByRole('button', { name: '添加表', exact: true }).click()
  await page.locator('[id="task-batchEtl.tables.0"]').fill('orders')
  await page.getByRole('tab', { name: '写入策略', exact: true }).click()
  await page.getByRole('button', { name: '添加逻辑键', exact: true }).click()
  await page.locator('[id="task-batchEtl.keys.0.name"]').fill('id')
  await page.getByRole('switch', { name: '导入前清空', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('请明确确认此写入行为', { exact: true })).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await page
    .getByRole('checkbox', { name: '我已了解：删除后按批提交，失败可能保留部分结果', exact: true })
    .check()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.path).toBe('/api/tasks')
  expect(state.writes[0]!.body.pluginType).toBe('BATCH_ETL')
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig))).toMatchObject({
    extractMode: 'FULL',
    tables: ['orders'],
    logicalKeys: ['id'],
    clearBeforeImport: true,
    acknowledgePartialCommit: true,
  })
  expect(state.tests).toEqual([])
})

test('mode switching retains exact draft but incremental save excludes clearing', async ({
  page,
}) => {
  const state = await setupBatchEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await page.locator('[id="task-batchEtl.extractMode"]').click()
  await page.getByRole('option', { name: '全量提取', exact: true }).click()
  await page.getByRole('tab', { name: '写入策略', exact: true }).click()
  await page.getByRole('switch', { name: '导入前清空', exact: true }).click()
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await page.locator('[id="task-batchEtl.extractMode"]').click()
  await page.getByRole('option', { name: '增量提取', exact: true }).click()
  await expect(page.locator('[id="task-batchEtl.initial"]')).toHaveValue(
    '9007199254740993.123456789',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig)).clearBeforeImport).toBe(false)
})
