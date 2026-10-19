// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupEtl } from '../etl-fixture'

test('QA15 validation opens an unselected invalid rule without losing other drafts', async ({
  page,
}) => {
  const state = await setupEtl(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 File ETL', exact: true }).click()
  await page.locator('#etl-tab-rules').click()
  await page.locator('#etl-rule-transforms-0-multiply').fill('-')
  const table = page.locator('.etl-rule-section > .etl-table-section').first()
  await table.locator('.etl-table-toolbar button').click()
  await page.locator('#etl-rule-transforms-1-fields-value-0').fill('retained_field')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('#etl-rule-transforms-0-multiply-error')).toBeVisible()
  await expect(page.locator('#etl-rule-transforms-0-multiply')).toHaveValue('-')
  await table.locator('tbody tr').nth(1).getByRole('button').first().click()
  await expect(page.locator('#etl-rule-transforms-1-fields-value-0')).toHaveValue('retained_field')
  expect(state.writes).toEqual([])
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`QA15 selected ETL rules retain drafts and order ${theme} ${locale}`, async ({
      page,
    }, info) => {
      await setupEtl(page)
      const en = locale === 'en-US'
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/tasks')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' File ETL', exact: true })
        .click()
      await page.locator('#etl-tab-rules').click()
      const section = page.locator('.etl-rule-section').first()
      const table = section.locator(':scope > .etl-table-section')
      await expect(table.locator('input, textarea, [role=combobox]')).toHaveCount(0)
      await page.locator('#etl-rule-transforms-0-multiply').fill('2.5')
      await table.locator('.etl-table-toolbar button').click()
      await expect(section.locator('.etl-rule-editor')).toHaveAttribute(
        'data-etl-key',
        'transforms.1',
      )
      await page.locator('#etl-rule-transforms-1-fields-value-0').fill('customer_name')
      await table
        .locator('tbody tr')
        .nth(1)
        .getByRole('button', { name: en ? 'Move up' : '上移', exact: true })
        .click()
      await expect(page.locator('#etl-rule-transforms-0-fields-value-0')).toHaveValue(
        'customer_name',
      )
      await table.locator('tbody tr').nth(1).getByRole('button').first().click()
      await expect(page.locator('#etl-rule-transforms-1-multiply')).toHaveValue('2.5')
      await page.screenshot({ path: info.outputPath('etl-selected-rule.png'), fullPage: true })
      await table.locator('tbody tr').nth(1).getByRole('button', { name: /2/ }).last().click()
      await expect(page.locator('#etl-rule-transforms-0-fields-value-0')).toHaveValue(
        'customer_name',
      )
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      await page.screenshot({
        path: info.outputPath('etl-selected-rule-mobile.png'),
        fullPage: true,
      })
      await table.locator('tbody tr').first().getByRole('button', { name: /1/ }).last().click()
      await expect(section.locator('.etl-rule-editor')).toHaveCount(0)
    })
