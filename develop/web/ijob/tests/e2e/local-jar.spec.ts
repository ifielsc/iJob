// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { setupLocalJar } from '../local-jar-fixture'
test.use({ viewport: { width: 1440, height: 1100 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    test('Local JAR typed editor ' + theme + ' ' + locale, async ({ page }, info) => {
      const s = await setupLocalJar(page),
        errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.goto('/tasks')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Local JAR 0', exact: true })
        .click()
      await expect(page.locator('.task-drawer')).toHaveCSS('width', '640px')
      await expect(page.locator('.local-jar-snapshot')).toContainText('4096 bytes')
      await page.locator('[id="task-localJar.jarFile"]').fill('orders-v2.jar')
      await expect(page.locator('.local-jar-snapshot')).toContainText(
        en ? 'Currently published version' : '当前已发布版本',
      )
      await page.screenshot({ path: info.outputPath('artifact.png'), fullPage: true })
      await page
        .getByRole('tab', { name: en ? 'Business parameters' : '业务参数', exact: true })
        .click()
      await page
        .getByRole('button', { name: en ? 'Row 2 Value' : '第 2 行值', exact: true })
        .click()
      await page
        .getByRole('textbox', { name: en ? 'Row 1 Value' : '第 1 行值', exact: true })
        .last()
        .fill('eu')
      await page
        .getByRole('button', { name: en ? 'Row 2 Move up' : '第 2 行上移', exact: true })
        .click()
      await expect(page.locator('.local-jar-breadcrumb')).toContainText('regions')
      await page.screenshot({ path: info.outputPath('parameters.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page
          .locator('.local-jar-fields')
          .evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
      ).toBe(true)
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => s.writes.length).toBe(1)
      const json = String(s.writes[0]!.body.pluginConfig)
      expect(json).toContain('9007199254740993')
      expect(json).toContain('0.1234567890123456789')
      expect(json).toContain('"regions":["us","eu"]')
      expect(json).not.toContain('jarSize')
      expect(s.tests).toEqual([])
      expect(errors).toEqual([])
    })
  }
test('delete and type change require confirmation; cancel preserves nested values', async ({
  page,
}) => {
  const s = await setupLocalJar(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Local JAR 0', exact: true }).click()
  await page.getByRole('tab', { name: '业务参数', exact: true }).click()
  await page.getByRole('button', { name: '第 2 行删除', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).last().click()
  await expect(page.getByRole('button', { name: '第 2 行值', exact: true })).toBeVisible()
  await page.locator('#localJar-root-kind').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^空值$/ })
    .click()
  await page.getByRole('button', { name: '取消', exact: true }).last().click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  expect(String(s.writes[0]!.body.pluginConfig)).toContain('"regions":["cn","us"]')
})
test('deep duplicate error opens correct parent and page', async ({ page }) => {
  const s = await setupLocalJar(page)
  const c = JSON.parse(s.tasks[0]!.pluginConfig)
  c.userConfig = { regions: Object.fromEntries(Array.from({ length: 22 }, (_, i) => ['k' + i, i])) }
  s.tasks[0]!.pluginConfig = JSON.stringify(c)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Local JAR 0', exact: true }).click()
  await page.getByRole('tab', { name: '业务参数', exact: true }).click()
  await page.getByRole('button', { name: '第 1 行值', exact: true }).click()
  await page
    .locator('.local-jar-fields')
    .getByRole('button', { name: '下一页', exact: true })
    .click()
  await page.getByRole('textbox', { name: '第 22 行参数名', exact: true }).fill('k0')
  await page.getByRole('button', { name: '返回上一级', exact: true }).click()
  await page.getByRole('tab', { name: '制品与入口', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '第 22 行参数名', exact: true })).toBeFocused()
  expect(s.writes).toEqual([])
})
test('unsupported configuration cannot be overwritten', async ({ page }) => {
  const s = await setupLocalJar(page)
  s.tasks[0]!.pluginConfig = s.tasks[0]!.pluginConfig.replace(
    '"formatVersion":1',
    '"formatVersion":2',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Local JAR 0', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(s.writes).toEqual([])
})
test('uncertain publication locks the dedicated form', async ({ page }) => {
  const s = await setupLocalJar(page, true, 'network')
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Local JAR 0', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('[id="task-localJar.jarFile"]')).toBeDisabled()
})
test('viewer has no Local JAR write action', async ({ page }) => {
  const s = await setupLocalJar(page, false)
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '编辑 Local JAR 0', exact: true })).toHaveCount(0)
  expect(s.writes).toEqual([])
})
test('multiline string root remains a string when edited and saved', async ({ page }) => {
  const s = await setupLocalJar(page)
  const c = JSON.parse(s.tasks[0]!.pluginConfig)
  c.userConfig = 'first\nsecond'
  s.tasks[0]!.pluginConfig = JSON.stringify(c)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Local JAR 0', exact: true }).click()
  await page.getByRole('tab', { name: '业务参数', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '根值', exact: true })).toHaveValue(
    'first\nsecond',
  )
  await page.getByRole('textbox', { name: '根值', exact: true }).fill('first\n${literal}\nthird')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  expect(JSON.parse(String(s.writes[0]!.body.pluginConfig)).userConfig).toBe(
    'first\n${literal}\nthird',
  )
})
test('new task preserves Local JAR draft across plugin switches', async ({ page }) => {
  const s = await setupLocalJar(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('New jar')
  const select = async (name: string) => {
    await page.locator('#task-pluginType').click()
    await page.getByRole('option', { name, exact: true }).click()
  }
  await select('LOCAL_JAR · 本地 JAR')
  await page.locator('[id="task-localJar.jarFile"]').fill('new.jar')
  await page.locator('[id="task-localJar.sha256"]').fill('a'.repeat(64))
  await page.locator('[id="task-localJar.entryClass"]').fill('sample.Task')
  await select('SERVER_MONITOR · 服务器监控')
  await select('LOCAL_JAR · 本地 JAR')
  await expect(page.locator('[id="task-localJar.jarFile"]')).toHaveValue('new.jar')
  await page.getByRole('tab', { name: '业务参数', exact: true }).click()
  await page.locator('#localJar-root-kind').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^空值$/ })
    .click()
  await page.getByRole('button', { name: '确认丢弃', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  expect(JSON.parse(String(s.writes[0]!.body.pluginConfig)).userConfig).toBe(null)
})
