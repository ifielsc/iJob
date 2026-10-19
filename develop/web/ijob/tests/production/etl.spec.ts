// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupEtl } from '../etl-fixture'
test('production ETL lazy editors load and preserve exact file markers', async ({ page }) => {
  const state = await setupEtl(page),
    errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  await page.getByRole('button', { name: '添加规则', exact: true }).last().click()
  await expect(page.locator('#etl-rule-validations-0-fields-value-0')).toHaveValue('id')
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await expect(page.locator('[id="task-etl.incremental.initialWatermark"]')).toHaveValue(
    '9007199254740993',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(String(state.writes[0]!.body.pluginConfig)).toContain('9007199254740993')
  expect(errors).toEqual([])
})
test('production new ETL publishes structured database config without execution', async ({
  page,
}) => {
  const state = await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('New ETL')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: 'ETL · 数据抽取转换加载', exact: true }).click()
  await page.locator('[id="task-etl.source.resourceId"]').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: 'Analytics DB · #9223372036854775807' })
    .click()
  await page.locator('.etl-fields .cm-content').fill('SELECT id FROM orders')
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await page.locator('[id="task-etl.target.resourceId"]').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: 'Analytics DB · #9223372036854775807' })
    .click()
  await page.locator('[id="task-etl.target.table"]').fill('orders_copy')
  await page.locator('[id="task-etl.target.logicalKeys"]').fill('id')
  await expect(page.getByRole('table', { name: '自定义映射（可选）', exact: true })).toBeVisible()
  await page.locator('[id="task-etl.target.excludedFields"]').fill('internal_note')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.path).toBe('/api/tasks')
  expect(state.writes[0]!.body.pluginType).toBe('ETL')
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig)).target).toMatchObject({
    mappingMode: 'AUTO',
    columns: {},
    excludedFields: ['internal_note'],
  })
  expect(state.tests).toEqual([])
})
