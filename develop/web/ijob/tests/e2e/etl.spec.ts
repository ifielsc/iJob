// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupEtl } from '../etl-fixture'
import { transformExamples, validationExamples } from '../../src/modules/tasks/etl-model'
import { parse, stringify } from 'lossless-json'
test.use({ viewport: { width: 1440, height: 1100 } })

test('SQL rows and paired sort fields preserve ordering without publishing on row actions', async ({
  page,
}, info) => {
  const state = await setupEtl(page)
  const config = parse(state.tasks[1]!.pluginConfig) as {
    source: { sql: string; beforeSql: string[]; parameters: object }
    incremental: { tieBreakerFields: string[]; initialTieBreakerValues: unknown[] }
  }
  config.source.beforeSql = ['DELETE FROM stage_orders', 'UPDATE sync_log SET status=0']
  config.source.parameters = { batchId: '${batchId!}', flag: false }
  config.source.sql += ' AND order_id > :etlLastTie1 AND region > :etlLastTie2'
  config.incremental.tieBreakerFields = ['order_id', 'region']
  config.incremental.initialTieBreakerValues = [0, 'CN']
  state.tasks[1]!.pluginConfig = stringify(config)!
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Database ETL', exact: true }).click()
  await page.locator('.n-collapse-item__header').filter({ hasText: '高级' }).first().click()
  const sql = page.locator('[data-json-context="task-etl.source.beforeSql"]')
  await sql.getByRole('button', { name: '2', exact: true }).click()
  await sql.getByRole('button', { name: '上移' }).click()
  expect(state.writes).toHaveLength(0)
  await sql.locator('.json-current').scrollIntoViewIfNeeded()
  await page.screenshot({ path: info.outputPath('sql-array.png'), fullPage: true })
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  const ties = page.locator('[data-json-context="ETL_TIES"]')
  await ties.getByRole('button', { name: 'region', exact: true }).click()
  await ties.getByRole('button', { name: '上移' }).click()
  await ties.getByRole('button', { name: 'order_id', exact: true }).click()
  await expect(ties.getByRole('textbox', { name: '值', exact: true })).toHaveValue('0')
  expect(state.writes).toHaveLength(0)
  await page.screenshot({ path: info.outputPath('paired-initial-values.png'), fullPage: true })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const saved = JSON.parse(String(state.writes[0]!.body.pluginConfig))
  expect(saved.source.beforeSql).toEqual([
    'UPDATE sync_log SET status=0',
    'DELETE FROM stage_orders',
  ])
  expect(saved.incremental.tieBreakerFields).toEqual(['region', 'order_id'])
  expect(saved.incremental.initialTieBreakerValues).toEqual(['CN', 0])
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    for (const name of ['File ETL', 'Database ETL']) {
      const en = locale === 'en-US'
      test(name + ' ' + theme + ' ' + locale, async ({ page }, info) => {
        const state = await setupEtl(page)
        const errors: string[] = []
        page.on('pageerror', (e) => errors.push(e.message))
        await page.addInitScript(
          (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
          { theme, locale },
        )
        await page.goto('/tasks')
        await page
          .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' ' + name, exact: true })
          .click()
        await expect(page.locator('.etl-fields')).toBeVisible()
        await expect(page.locator('.task-drawer')).toHaveCSS('width', '640px')
        await page.screenshot({ path: info.outputPath('source.png'), fullPage: true })
        await page
          .getByRole('tab', { name: en ? 'Transform & validate' : '转换与校验', exact: true })
          .click()
        await expect(page.locator('.etl-rule-section > .etl-table-section')).toHaveCount(2)
        await expect(page.locator('.etl-rule-section .n-collapse')).toHaveCount(0)
        await page.screenshot({ path: info.outputPath('rule-tables.png'), fullPage: true })
        await page.getByRole('tab', { name: en ? 'Target' : '目标', exact: true }).click()
        await expect(page.locator('#etl-column-target-0')).toHaveValue('id')
        await page.screenshot({ path: info.outputPath('target.png'), fullPage: true })
        await page
          .getByRole('tab', { name: en ? 'Incremental & limits' : '增量与限制', exact: true })
          .click()
        await expect(page.locator('[id="task-etl.incremental.initialWatermark"]')).toHaveValue(
          name === 'File ETL' ? '9007199254740993' : '0',
        )
        await expect(page.locator('[id="task-etl.file.markerFormat"]')).toHaveCount(0)
        await page.screenshot({ path: info.outputPath('incremental.png'), fullPage: true })
        await page.setViewportSize({ width: 320, height: 844 })
        expect((await page.locator('.task-drawer').boundingBox())!.width).toBeLessThanOrEqual(320)
        expect(
          await page.locator('.etl-fields').evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
        ).toBe(true)
        await page.screenshot({ path: info.outputPath('narrow.png'), fullPage: true })
        await page
          .locator('#task-name')
          .fill((await page.locator('#task-name').inputValue()) + ' updated')
        await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
        await expect.poll(() => state.writes.length).toBe(1)
        const config = String(state.writes[0]!.body.pluginConfig)
        expect(config).toContain('9223372036854775807')
        if (name === 'File ETL') {
          expect(config).toContain('9007199254740993')
          expect(config).toContain('0.1234567890123456789')
        }
        expect(errors).toEqual([])
        expect(state.tests).toEqual([])
      })
    }
test('invalid hidden target opens correct tab and focuses its field', async ({ page }) => {
  const state = await setupEtl(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '"logicalKeys":["id"]',
    '"logicalKeys":[]',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('tab', { name: '目标', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await expect(page.locator('[id="task-etl.target.logicalKeys"]')).toBeFocused()
  expect(state.writes).toHaveLength(0)
})

test('calendar-style mapping rows add and delete inline while preserving remaining fields', async ({
  page,
}) => {
  const state = await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  const table = page.getByRole('table', { name: '字段映射', exact: true })
  await expect(table.getByRole('columnheader')).toHaveText(['来源字段 *', '目标列 *', '操作'])
  await page.getByRole('button', { name: '添加字段', exact: true }).click()
  await expect(table.locator('tbody > tr')).toHaveCount(3)
  await page.locator('#etl-column-source-2').fill('status_code')
  await page.locator('#etl-column-target-2').fill('status')
  await table.locator('tbody > tr').nth(1).getByRole('button').click()
  await expect(table.locator('tbody > tr')).toHaveCount(2)
  await expect(page.locator('#etl-column-source-1')).toHaveValue('status_code')
  await page.setViewportSize({ width: 320, height: 844 })
  expect(
    await page.locator('.etl-fields').evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
  ).toBe(true)
  const actions = table.locator('tbody > tr').last().locator('td').last()
  await expect(actions).toHaveCSS('position', 'sticky')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig)).target.columns).toEqual({
    id: 'order_id',
    status: 'status_code',
  })
})
test('switching marker type exposes date format and keeps exact values across tabs', async ({
  page,
}) => {
  await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await page.locator('[id="task-etl.incremental.watermarkType"]').click()
  await page.getByRole('option', { name: '日期（DATE）', exact: true }).click()
  await page.locator('[id="task-etl.file.markerFormat"]').fill('yyyyMMdd')
  await page.locator('[id="task-etl.incremental.initialWatermark"]').fill('2026-09-01')
  await page.locator('[id="task-etl.file.lookback"]').fill('P2D')
  await page.getByRole('tab', { name: '来源', exact: true }).click()
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await expect(page.locator('[id="task-etl.file.markerFormat"]')).toHaveValue('yyyyMMdd')
  await page.locator('[id="task-etl.incremental.watermarkType"]').click()
  await page.getByRole('option', { name: '字符串（STRING）', exact: true }).click()
  await expect(page.locator('[id="task-etl.file.lookback"]')).toHaveCount(0)
  await expect(page.locator('[id="task-etl.file.markerFormat"]')).toHaveCount(0)
  await page.locator('[id="task-etl.file.selectionMode"]').click()
  await expect(page.getByRole('option', { name: '回看补取', exact: true })).toHaveCount(0)
})
test('rule order and mappings are editable without executing source operations', async ({
  page,
}, info) => {
  const state = await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  await page.getByRole('button', { name: '添加规则', exact: true }).first().click()
  await expect(page.locator('[data-etl-key="transforms.1"]')).toBeVisible()
  await page
    .locator('.etl-rule-section')
    .first()
    .locator('tbody > tr')
    .nth(1)
    .getByRole('button', { name: '上移', exact: true })
    .click()
  await page.screenshot({ path: info.outputPath('rules.png'), fullPage: true })
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await page.locator('#etl-column-source-1').fill('net_amount')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const c = JSON.parse(String(state.writes[0]!.body.pluginConfig))
  expect(c.transforms[0].type).toBe('TRIM')
  expect(c.target.columns.amount).toBe('net_amount')
  expect(state.tests).toEqual([])
})
test('unknown save result disables repeat publication', async ({ page }) => {
  const state = await setupEtl(page, true, 'network')
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('view-only user cannot edit ETL tasks', async ({ page }) => {
  await setupEtl(page, false)
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '编辑 File ETL', exact: true })).toHaveCount(0)
})
test('unsupported ETL version cannot be overwritten', async ({ page }) => {
  const state = await setupEtl(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace('"version":1', '"version":2')
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(0)
})

test('typed rule forms persist precise edits across tabs and block incomplete numbers', async ({
  page,
}, info) => {
  const state = await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  const multiply = page.locator('#etl-rule-transforms-0-multiply')
  await expect(multiply).toHaveValue('0.1234567890123456789')
  await multiply.fill('-')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#etl-rule-transforms-0-multiply-error')).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await multiply.fill('0.9876543210987654321')
  await page.getByRole('button', { name: '添加规则', exact: true }).last().click()
  await page.locator('#etl-rule-validations-0-fields-value-0').fill('order_id')
  await page
    .locator('[data-etl-key="validations.0"]')
    .getByRole('button', { name: '添加一项', exact: true })
    .click()
  await page.locator('#etl-rule-validations-0-fields-value-1').fill('amount')
  await expect(page.locator('.etl-fields .cm-content')).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('rule-forms.png'), fullPage: true })
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await page.getByRole('button', { name: '添加字段', exact: true }).click()
  await page.locator('#etl-column-source-2').fill('status')
  await page.locator('#etl-column-target-2').fill('status')
  await page.screenshot({ path: info.outputPath('mapping-forms.png'), fullPage: true })
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  await expect(multiply).toHaveValue('0.9876543210987654321')
  await expect(page.locator('#etl-rule-validations-0-fields-value-1')).toHaveValue('amount')
  await page.setViewportSize({ width: 320, height: 844 })
  expect(
    await page.locator('.etl-fields').evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
  ).toBe(true)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const config = String(state.writes[0]!.body.pluginConfig)
  expect(config).toContain('0.9876543210987654321')
  expect(JSON.parse(config).validations[0].properties.fields).toEqual(['order_id', 'amount'])
  expect(JSON.parse(config).target.columns.status).toBe('status')
})

test('opening rule and target forms does not mark an unchanged task dirty', async ({ page }) => {
  await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await page.getByText('事务与写入高级设置', { exact: true }).click()
  await page.locator('.n-drawer-footer').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  await expect(page.locator('.n-dialog')).toHaveCount(0)
})

test('all rule types and target protection use forms and publish their edited values', async ({
  page,
}, info) => {
  const state = await setupEtl(page)
  const config = JSON.parse(state.tasks[0]!.pluginConfig)
  // JSON.parse cannot represent this fixture's long resource ID; restore its exact text.
  config.target.resourceId = '9223372036854775807'
  config.incremental = { enabled: false }
  config.transforms = Object.entries(transformExamples).map(([type, properties]) => ({
    type,
    properties,
  }))
  config.validations = Object.entries(validationExamples).map(([type, properties], index) => ({
    type,
    properties,
    ruleId: 'v' + index,
    onFailure: 'FAIL_TASK',
  }))
  state.tasks[0]!.pluginConfig = JSON.stringify(config)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  await expect(
    page
      .locator('.etl-rule-section')
      .first()
      .locator(':scope > .etl-table-section > .etl-table-scroll > .etl-table > tbody > tr'),
  ).toHaveCount(10)
  await expect(
    page
      .locator('.etl-rule-section')
      .last()
      .locator(':scope > .etl-table-section > .etl-table-scroll > .etl-table > tbody > tr'),
  ).toHaveCount(7)
  await expect(page.locator('[data-etl-key^="transforms."]')).toHaveCount(1)
  await expect(page.locator('[data-etl-key^="validations."]')).toHaveCount(1)
  await expect(page.locator('.etl-fields .cm-content')).toHaveCount(0)
  await page.locator('#etl-rule-transforms-0-key-0').fill('mapped_id')
  await page.locator('#etl-rule-transforms-0-value-0').fill('source_id')
  await page
    .locator('.etl-rule-section')
    .first()
    .locator('tbody > tr')
    .nth(1)
    .getByRole('button')
    .first()
    .click()
  await page.locator('#etl-rule-transforms-1-value-0-kind').click()
  await page.getByRole('option', { name: '数值', exact: true }).click()
  await page.locator('#etl-rule-transforms-1-value-0').fill('9223372036854775807')
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await page.getByText('事务与写入高级设置', { exact: true }).click()
  const policies = page.locator('[data-etl-key="target.columnNullPolicies"]')
  await policies.getByRole('button', { name: '添加一项', exact: true }).click()
  await page.locator('#etl-target-columnNullPolicies-key-0').fill('amount')
  await page.locator('#etl-target-columnNullPolicies-value-0').click()
  await page.getByRole('option', { name: '覆盖', exact: true }).click()
  await page.locator('#etl-target-protection-enabled').click()
  await page.getByRole('option', { name: '启用', exact: true }).click()
  await page.locator('#etl-target-protection-column').fill('id')
  await page
    .locator('[data-etl-key="target.protection"]')
    .getByRole('button', { name: '添加一项', exact: true })
    .click()
  await page.locator('#etl-target-protection-protectedValues-value-0').fill('LOCKED')
  await page.screenshot({ path: info.outputPath('target-policy-forms.png'), fullPage: true })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const raw = String(state.writes[0]!.body.pluginConfig),
    result = JSON.parse(raw)
  expect(raw).toContain('"status":9223372036854775807')
  expect(result.transforms[0].properties).toEqual({ mapped_id: 'source_id' })
  expect(result.target.columnNullPolicies).toEqual({ amount: 'OVERWRITE' })
  expect(result.target.protection).toEqual({ column: 'id', protectedValues: ['LOCKED'] })
})

test('old manual task can switch to automatic mapping with overrides and exclusions', async ({
  page,
}, info) => {
  const state = await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await expect(page.getByRole('table', { name: '字段映射', exact: true })).toBeVisible()
  await page.locator('[id="task-etl.target.mappingMode"]').click()
  await page.getByRole('option', { name: '自动同名映射', exact: true }).click()
  const table = page.getByRole('table', { name: '自定义映射（可选）', exact: true })
  await expect(table.locator('tbody > tr')).toHaveCount(2)
  await table.getByRole('button').first().click()
  await page.locator('#etl-column-target-0').fill('total_amount')
  await page.locator('[id="task-etl.target.excludedFields"]').fill('amount')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(
    page.getByText('同一来源字段不能同时排除和配置自定义映射。', { exact: true }),
  ).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await page.locator('[id="task-etl.target.excludedFields"]').fill('source_time')
  await page.getByRole('tab', { name: '来源', exact: true }).click()
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await expect(page.locator('#etl-column-target-0')).toHaveValue('total_amount')
  await page.screenshot({ path: info.outputPath('auto-mapping.png'), fullPage: true })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig)).target).toMatchObject({
    mappingMode: 'AUTO',
    columns: { total_amount: 'amount' },
    excludedFields: ['source_time'],
    logicalKeys: ['id'],
  })
})
