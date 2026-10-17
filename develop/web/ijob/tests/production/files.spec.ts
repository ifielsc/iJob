import { test, expect } from '@playwright/test'
import { setupFiles } from '../files-fixture'
test('production new file task selects exact references and does not run', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const state = await setupFiles(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('New transfer')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: 'FILE_TRANSFER · 文件传输', exact: true }).click()
  await expect(page.locator('.task-drawer .task-identity-id')).toHaveCount(0)
  for (const [field, name] of [
    ['sourceResourceId', 'LOCAL fixture'],
    ['targetResourceId', 'FTP fixture'],
  ]) {
    await page.locator('[id="task-transfer.' + field + '"]').click()
    await page
      .locator('.n-base-select-option:visible')
      .filter({ hasText: new RegExp('^' + name + ' ·') })
      .click()
    await expect(page.locator('.n-base-select-option:visible')).toHaveCount(0)
  }
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.pluginConfig).toContain('"targetResourceId":9223372036854775807')
  expect(errors).toEqual([])
})
test('production file resource and probe chunks load without errors', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const state = await setupFiles(page)
  await page.goto('/resources/files')
  await page.getByRole('button', { name: '编辑 SFTP fixture', exact: true }).click()
  await expect(page.locator('.file-fingerprint')).toContainText('SHA256:')
  await expect(page.locator('#file-knownHosts')).toHaveCount(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Probe fixture', exact: true }).click()
  await expect(page.locator('.file-probe-fields')).toBeVisible()
  await page.locator('#task-name').fill('Probe fixture updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.path).toContain('/api/tasks/')
  expect(errors).toEqual([])
})
