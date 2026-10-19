// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupFiles } from '../files-fixture'
import { setupMonitor } from '../monitor-fixture'
import { setupLocalJar } from '../local-jar-fixture'
import { setupEtl } from '../etl-fixture'
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`ETL detail sections match editor groups ${theme} ${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      const state = await setupEtl(page)
      await page.route('**/api/reference-summaries', (route) =>
        route.fulfill({
          json: {
            success: true,
            code: 200,
            data: state.resources.map((row) => ({
              kind: 'RESOURCE',
              id: row.id,
              name: row.name,
              type: row.type,
              enabled: true,
            })),
          },
        }),
      )
      await page.goto('/tasks')
      await page
        .getByRole('button', { name: (en ? 'Details' : '详情') + ' File ETL', exact: true })
        .click()
      const drawer = page.locator('.task-drawer')
      await expect(drawer.locator('.plugin-detail-sections h4')).toHaveText(
        en
          ? ['Source', 'Transform & validate', 'Target', 'Incremental & limits']
          : ['来源', '转换与校验', '目标', '增量与限制'],
      )
      await expect(
        drawer.getByRole('region', { name: en ? 'Source' : '来源', exact: true }),
      ).not.toContainText(en ? 'Source date format' : '原日期格式')
      await expect(drawer).toContainText('9007199254740993')
      await expect(drawer).toContainText('0.1234567890123456789')
      await page.screenshot({ path: info.outputPath('etl-details.png'), animations: 'disabled' })
      await page.setViewportSize({ width: 390, height: 844 })
      expect(await drawer.evaluate((el) => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
      expect(state.writes).toHaveLength(0)
    })
  }
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    test(`resource details use flat configuration and localized fields ${theme} ${locale}`, async ({
      page,
    }, info) => {
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      const state = await setupFiles(page)
      await page.goto('/resources/files')
      await page
        .locator('.resource-table tbody tr')
        .first()
        .locator('td')
        .nth(1)
        .getByRole('button', { name: 'LOCAL fixture', exact: true })
        .click()
      const drawer = page.locator('.configuration-drawer')
      await expect(drawer).toContainText(en ? 'Resource configuration' : '资源配置')
      await expect(drawer.locator('dt').filter({ hasText: /^file$/ })).toHaveCount(0)
      await expect(drawer).toContainText(en ? 'Configuration state' : '配置状态')
      await page.screenshot({ path: info.outputPath('file-details.png'), animations: 'disabled' })
      expect(state.writes).toHaveLength(0)
      await setupMonitor(page)
      await page.goto('/resources/monitor')
      await page.getByRole('button', { name: 'Monitor Agent', exact: true }).click()
      await expect(drawer.locator('dt').filter({ hasText: /^monitor$/ })).toHaveCount(0)
      await expect(drawer).toContainText(en ? 'Configuration state' : '配置状态')
      await expect(drawer).not.toContainText('tokenConfigured')
      await page.screenshot({
        path: info.outputPath('monitor-resource-details.png'),
        animations: 'disabled',
      })
    })
  }
test('Local JAR details localize schema labels while preserving business JSON', async ({
  page,
}) => {
  const state = await setupLocalJar(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '详情 Local JAR 0', exact: true }).click()
  const drawer = page.locator('.task-drawer')
  await expect(drawer).toContainText('业务参数')
  await expect(drawer.getByText('配置版本', { exact: true })).not.toBeVisible()
  await drawer.locator('.unified-technical > summary').click()
  await expect(drawer.getByText('配置版本', { exact: true })).toBeVisible()
  await expect(drawer).toContainText('9007199254740993')
  await expect(drawer).toContainText('0.1234567890123456789')
  await expect(drawer.locator('.json-viewer')).toContainText('"batchSize"')
  expect(state.writes).toHaveLength(0)
})
