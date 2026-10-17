import { test, expect, type Page } from '@playwright/test'
import { setup } from '../data-check-fixture'
async function open(page: Page, en = false) {
  await page.goto('/tasks')
  await page
    .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Quality check', exact: true })
    .click()
  await expect(page.locator('.data-check-fields')).toBeVisible()
}
test('new task, enabled alignment, SQL expansion and condition clearing', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-pluginType').click()
  await page.locator('.n-base-select-option').filter({ hasText: 'DATA_CHECK' }).click()
  await expect(page.locator('.data-check-fields')).toBeVisible()
  await expect(page.locator('[id="task-check.0.name"]')).toHaveValue('')
  const label = await page.locator('label[for="task-enabled"]').boundingBox(),
    toggle = await page.locator('#task-enabled').boundingBox()
  expect(Math.abs(label!.y + label!.height / 2 - toggle!.y - toggle!.height / 2)).toBeLessThan(3)
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  const dialog = page.locator('dialog[open]')
  await expect(dialog).toBeVisible()
  await dialog.locator('.cm-content').fill('select 1')
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(page.locator('.data-check-fields .cm-content')).toContainText('select 1')
  await page.locator('[id="task-check.0.expected"]').fill('value')
  await page.locator('[id="task-check.0.type"]').click()
  await page.locator('.n-base-select-option').filter({ hasText: '空结果' }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('[id="task-check.0.expected"]')).toHaveCount(0)
  expect(state.writes).toHaveLength(0)
})
test('read-only user has no create or edit action', async ({ page }) => {
  await setup(page, '', false)
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '新增任务', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '编辑 Quality check', exact: true })).toHaveCount(0)
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`editor ${theme} ${locale}`, async ({ page }, info) => {
      const state = await setup(page),
        en = locale === 'en-US'
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await open(page, en)
      await expect(page.locator('[id="task-check.0.expected"]')).toHaveValue('0')
      await expect(page.locator('.data-check-fields')).toContainText(
        en ? 'Counts returned rows' : '统计查询返回的行数',
      )
      await page.locator('[id="task-check.0.expected"]').fill('9007199254740993')
      await page.getByRole('button', { name: 'No anomalies', exact: true }).click()
      await expect(page.locator('[id="task-check.1.expected"]')).toHaveCount(0)
      await page.getByRole('button', { name: 'Rows', exact: true }).click()
      await expect(page.locator('[id="task-check.0.expected"]')).toHaveValue('9007199254740993')
      const drawer = page.locator('.task-drawer')
      await page.screenshot({ path: info.outputPath('desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 320, height: 844 })
      await page.screenshot({ path: info.outputPath('mobile.png'), fullPage: true })
      const bounds = await drawer.boundingBox()
      expect(bounds!.width).toBeLessThanOrEqual(320)
      await drawer.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      expect(state.writes[0]!.body.pluginConfig).toContain('"expected":9007199254740993')
      expect(state.writes[0]!.body.pluginConfig).toContain('"extra":0.1234567890123456789')
    })
test('inline addition preserves other rows, selects and focuses first invalid field', async ({
  page,
}) => {
  const state = await setup(page)
  await open(page)
  await page.getByRole('button', { name: '添加检查项', exact: true }).click()
  await expect(page.locator('[id="task-check.2.name"]')).toBeFocused()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('[id="task-check.2.name"]')).toBeFocused()
  expect(state.writes).toHaveLength(0)
  await page.locator('[id="task-check.2.name"]').fill('Third')
  await page.getByRole('button', { name: 'Rows', exact: true }).click()
  await page.getByRole('button', { name: 'Third', exact: true }).click()
  await expect(page.locator('[id="task-check.2.name"]')).toHaveValue('Third')
})
test('notification picker writes an exact long ID and enabled handler', async ({ page }) => {
  const state = await setup(page)
  await open(page)
  await page.getByText('结果处理（可选）', { exact: true }).click()
  await page.locator('#check-notify').click()
  await page.locator('[id="task-check.notifyConfigId"]').click()
  await page.locator('.n-base-select-option').filter({ hasText: 'Operations' }).click()
  await expect(page.locator('[id="task-check.notifyConfigId"]')).toHaveCount(1)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.pluginConfig).toContain('"notifyConfigId":9223372036854775807')
})
for (const failure of ['network', 'conflict'])
  test(`preserves draft and stops duplicate submission on ${failure}`, async ({ page }) => {
    const state = await setup(page, failure)
    await open(page)
    await page.locator('[id="task-check.0.expected"]').fill('12')
    const save = page.getByRole('button', { name: '保存', exact: true })
    await save.click()
    await expect(save).toBeDisabled()
    await expect(page.locator('[id="task-check.0.expected"]')).toHaveValue('12')
    expect(state.writes).toHaveLength(1)
  })
