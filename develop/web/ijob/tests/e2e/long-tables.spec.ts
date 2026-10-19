// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupBatchEtl } from '../batch-etl-fixture'

test.use({ viewport: { width: 1440, height: 1000 } })
for (const count of [0, 5, 20, 100, 500]) {
  test(`batch editing and details with ${count} rows`, async ({ page }, info) => {
    const state = await setupBatchEtl(page)
    const names = Array.from({ length: count }, (_, i) => `source_${String(i).padStart(3, '0')}`)
    state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
      '["orders","customers"]',
      JSON.stringify(names),
    )
    await page.goto('/tasks')
    await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
    const table = page.locator('[id="task-batchEtl.tables"]')
    await expect(table).toHaveCSS('margin-top', '12px')
    const scroll = table.locator('.etl-table-scroll')
    if (count > 6) {
      expect(
        await scroll.evaluate((el) => el.scrollHeight > el.clientHeight && el.clientHeight <= 320),
      ).toBe(true)
      await expect(table.locator('thead th').first()).toHaveCSS('position', 'sticky')
      await table.getByRole('textbox', { name: '搜索列表内容' }).fill(`ods_${names.at(-1)}`)
      await expect(table.locator('tbody tr')).toHaveCount(1)
      await expect(table.locator('tbody tr')).toContainText(String(count))
      await expect(
        page.getByRole('button', { name: `第 ${count} 行 · 上移`, exact: true }),
      ).toBeDisabled()
      await table.getByRole('textbox', { name: '搜索列表内容' }).fill('')
      const last = page.locator(`[id="task-batchEtl.tables.${count - 1}"]`)
      await last.focus()
      const pos = await scroll.evaluate((el) => el.scrollTop)
      expect(pos).toBeGreaterThan(0)
      await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
      await page.getByRole('tab', { name: '表与资源', exact: true }).click()
      expect(await scroll.evaluate((el) => el.scrollTop)).toBeGreaterThanOrEqual(pos - 2)
    } else {
      expect(await scroll.evaluate((el) => el.scrollHeight <= el.clientHeight + 1)).toBe(true)
    }
    await page.screenshot({ path: info.outputPath('editor.png') })
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: '详情 Batch ETL 0', exact: true }).click()
    if (count > 8) {
      const tools = page.locator('.plugin-list-tools').first()
      await tools.getByRole('searchbox').fill(`ods_${names.at(-1)}`)
      const container = tools.locator('..')
      await expect(container.locator('thead th').first()).toHaveCSS('border-bottom-width', '1px')
      await expect(container.locator('tbody > tr')).toHaveCount(1)
      await expect(container).toContainText(names.at(-1)!)
      await expect(container).toContainText(`ods_${names.at(-1)}`)
      await expect(container.locator('.plugin-table-scroll').first()).toHaveClass(
        /plugin-table-bounded/,
      )
      await tools.getByRole('searchbox').fill('absent')
      await expect(container.locator('tbody > tr')).toHaveCount(0)
    }
    await page.screenshot({ path: info.outputPath('details.png') })
    expect(state.writes).toHaveLength(0)
  })
}

test('search edits original row, invalid hidden rows focus, and add reveals the new row', async ({
  page,
}) => {
  const state = await setupBatchEtl(page)
  const names = Array.from({ length: 100 }, (_, i) => `source_${i}`)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '["orders","customers"]',
    JSON.stringify(names),
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  const search = page.locator('.batch-search input')
  await search.fill('source_99')
  await page.locator('[id="task-batchEtl.tables.99"]').fill('changed_last')
  await expect(page.locator('[id="task-batchEtl.tables.99"]')).toBeFocused()
  await search.fill('source_88')
  await page.locator('[id="task-batchEtl.tables.88"]').fill('invalid name')
  await search.fill('source_1')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(search).toHaveValue('')
  await expect(page.locator('[id="task-batchEtl.tables.88"]')).toBeFocused()
  expect(state.writes).toHaveLength(0)
  await page.locator('[id="task-batchEtl.tables.88"]').fill('fixed_name')
  await page.getByRole('button', { name: '添加表', exact: true }).click()
  await expect(page.locator('[id="task-batchEtl.tables.100"]')).toBeFocused()
  await page.locator('[id="task-batchEtl.tables.100"]').fill('added_table')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  const saved = JSON.parse(String(state.writes[0]!.body.pluginConfig))
  expect(saved.tables).toHaveLength(101)
  expect(saved.tables[0]).toBe('source_0')
  expect(saved.tables[88]).toBe('fixed_name')
  expect(saved.tables[99]).toBe('changed_last')
  expect(saved.tables[100]).toBe('added_table')
})
