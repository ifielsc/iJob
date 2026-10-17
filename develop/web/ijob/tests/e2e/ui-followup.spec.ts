import { test, expect } from '@playwright/test'
import { setupMonitor } from '../monitor-fixture'
import { setupBatchEtl } from '../batch-etl-fixture'

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    test(`monitor details, resource switch and bulk colors ${theme} ${locale}`, async ({
      page,
    }, info) => {
      const state = await setupMonitor(page)
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/tasks')
      const taskDelete = page.getByRole('button', {
        name: en ? 'Delete selected' : '批量删除',
        exact: true,
      })
      const taskColor = await taskDelete
        .locator('.n-button__content')
        .evaluate((el) => getComputedStyle(el).color)
      await page
        .getByRole('button', { name: (en ? 'Details' : '详情') + ' Monitor 0', exact: true })
        .click()
      const drawer = page.locator('.task-drawer')
      await expect(drawer).toContainText(en ? 'Threshold rules' : '阈值规则')
      await expect(drawer).not.toContainText('日历规则')
      await expect(drawer).toContainText(en ? 'Memory (MiB)' : '内存 (MiB)')
      await expect(drawer).toContainText('0.1234567890123456789')
      await expect(drawer).toContainText(en ? 'Command contains' : '命令行包含')
      await page.screenshot({ path: info.outputPath('monitor-details.png') })
      await page.goto('/resources/monitor')
      const bulk = page.getByRole('button', {
        name: en ? 'Delete selected' : '批量删除',
        exact: true,
      })
      expect(
        await bulk.locator('.n-button__content').evaluate((el) => getComputedStyle(el).color),
      ).toBe(taskColor)
      await page.locator('.resource-table tbody .n-checkbox').first().click()
      const colors = await bulk.evaluate((el) => ({
        button: getComputedStyle(el).color,
        text: getComputedStyle(el.querySelector('.n-button__content')!).color,
      }))
      expect(colors.text).toBe(colors.button)
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Monitor Agent', exact: true })
        .click()
      const label = await page.locator('.resource-enabled label').boundingBox()
      const control = await page.locator('.resource-enabled .n-switch').boundingBox()
      expect(control!.y).toBeGreaterThan(label!.y + label!.height)
      expect(Math.abs(control!.x - label!.x)).toBeLessThan(2)
      await page.screenshot({ path: info.outputPath('resource-enabled.png') })
      expect(state.writes).toEqual([])
    })
  }

test('batch ETL drawer has compact section and tab spacing', async ({ page }, info) => {
  await setupBatchEtl(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Batch ETL 0', exact: true }).click()
  const note = await page.locator('.task-form > .task-note').boundingBox()
  const heading = await page.locator('.task-form form > h3').first().boundingBox()
  const field = await page.locator('.task-form form > .n-form-item').first().boundingBox()
  expect(heading!.y - note!.y - note!.height).toBeLessThanOrEqual(13)
  expect(field!.y - heading!.y - heading!.height).toBeLessThanOrEqual(17)
  const tabs = await page.locator('.batch-etl-fields .line-tabs').boundingBox()
  const hint = await page.locator('.batch-panel > div:visible > .task-note').first().boundingBox()
  expect(hint!.y - tabs!.y - tabs!.height).toBeLessThanOrEqual(13)
  await page.screenshot({ path: info.outputPath('batch-spacing.png') })
})
