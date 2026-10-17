import { test, expect } from '@playwright/test'
import { setupMonitor } from '../monitor-fixture'
test.use({ viewport: { width: 1440, height: 1100 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    test('monitor forms ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupMonitor(page),
        errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.goto('/tasks')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Monitor 0', exact: true })
        .click()
      await expect(page.locator('.monitor-fields')).toContainText('Monitor Agent')
      await expect(page.locator('.task-drawer')).toHaveCSS('width', '640px')
      await page.screenshot({ path: info.outputPath('collection.png'), fullPage: true })
      await page
        .getByRole('tab', { name: en ? 'Threshold rules' : '阈值规则', exact: true })
        .click()
      await page.locator('[id="task-monitor.metrics.cpu.warning"]').fill('80.1234567890123456789')
      await page.screenshot({ path: info.outputPath('thresholds.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      expect(
        await page
          .locator('.monitor-fields')
          .evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
      ).toBe(true)
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      await page
        .getByRole('tab', { name: en ? 'Alerts & results' : '告警与结果', exact: true })
        .click()
      await expect(page.locator('.monitor-fields')).toContainText('monitor.healthy')
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      expect(String(state.writes[0]!.body.pluginConfig)).toContain('80.1234567890123456789')
      expect(String(state.writes[0]!.body.pluginConfig)).toContain('0.1234567890123456789')
      await page.setViewportSize({ width: 1440, height: 1100 })
      await page.goto('/resources/monitor')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Monitor Agent', exact: true })
        .click()
      await expect(page.locator('#monitor-resource-endpoint')).toHaveValue(
        'http://agent.invalid/base',
      )
      await expect(page.locator('.resource-drawer')).toHaveCSS('width', '520px')
      await page.screenshot({ path: info.outputPath('resource.png'), fullPage: true })
      await page
        .locator('#monitor-resource-name')
        .fill((await page.locator('#monitor-resource-name').inputValue()) + ' updated')
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(2)
      expect(state.writes[1]!.body.monitorConfig).toMatchObject({
        clearToken: false,
        preservedHeaderNames: ['X-Key'],
        headers: {},
      })
      expect(state.writes[1]!.body.monitorConfig).not.toHaveProperty('token')
      expect(state.tests).toEqual([])
      expect(errors).toEqual([])
    })
  }
test('hidden threshold error opens rules and focuses', async ({ page }) => {
  const state = await setupMonitor(page)
  state.tasks[0]!.pluginConfig = state.tasks[0]!.pluginConfig.replace(
    '"warning":80',
    '"warning":101',
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Monitor 0', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('[id="task-monitor.metrics.cpu.warning"]')).toBeFocused()
  expect(state.writes).toEqual([])
})
test('collection toggles retain applications in the draft', async ({ page }) => {
  const state = await setupMonitor(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Monitor 0', exact: true }).click()
  await page.locator('[id="task-monitor.applications.0.matchValue"]').fill('changed.jar')
  await page.getByRole('checkbox', { name: '应用', exact: true }).uncheck()
  await page.getByRole('checkbox', { name: '应用', exact: true }).check()
  await expect(page.locator('[id="task-monitor.applications.0.matchValue"]')).toHaveValue(
    'changed.jar',
  )
  await page.getByRole('checkbox', { name: '应用', exact: true }).uncheck()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(JSON.parse(String(state.writes[0]!.body.pluginConfig)).collect.applications).toEqual([])
})
test('unsupported matcher blocks save', async ({ page }) => {
  await setupMonitor(page).then(
    (s) => (s.tasks[0]!.pluginConfig = s.tasks[0]!.pluginConfig.replace('COMMAND', 'CONTAINS')),
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Monitor 0', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('unknown publication blocks duplicate save', async ({ page }) => {
  const s = await setupMonitor(page, true, 'network')
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Monitor 0', exact: true }).click()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
})
test('unknown resource save locks editing', async ({ page }) => {
  const s = await setupMonitor(page, true, 'network')
  await page.goto('/resources/monitor')
  await page.getByRole('button', { name: '编辑 Monitor Agent', exact: true }).click()
  await page.locator('#monitor-resource-endpoint').fill('http://updated.invalid')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('#monitor-resource-endpoint')).toBeDisabled()
})
test('saved connection test never sends draft and failure allows edit', async ({ page }) => {
  const s = await setupMonitor(page)
  await page.goto('/resources/monitor')
  await page.getByRole('button', { name: '编辑 Monitor Agent', exact: true }).click()
  await page.locator('#monitor-resource-endpoint').fill('http://draft.invalid')
  await page.getByRole('button', { name: '测试已保存配置', exact: true }).click()
  await expect.poll(() => s.tests.length).toBe(1)
  await expect(page.locator('#monitor-resource-endpoint')).toBeEnabled()
  expect(s.writes).toEqual([])
})
test('read-only roles cannot mutate monitor tasks or resources', async ({ page }) => {
  const s = await setupMonitor(page, false)
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '编辑 Monitor 0', exact: true })).toHaveCount(0)
  await page.goto('/resources/monitor')
  await expect(page.getByRole('button', { name: '新增监控服务器', exact: true })).toHaveCount(0)
  expect(s.writes).toEqual([])
})
test('new monitor task uses the dedicated picker and publication', async ({ page }) => {
  const s = await setupMonitor(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('New monitor')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: 'SERVER_MONITOR · 服务器监控', exact: true }).click()
  await page.locator('[id="task-monitor.resourceId"]').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: '#9007199254740993' })
    .click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => s.writes.length).toBe(1)
  expect(s.writes[0]!.body.pluginType).toBe('SERVER_MONITOR')
  expect(s.tests).toEqual([])
})
