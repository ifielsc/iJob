import { test, expect } from '@playwright/test'
import { setup } from '../data-check-fixture'
test('production DATA_CHECK editor loads SQL chunk and publishes precise configuration', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const state = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Quality check', exact: true }).click()
  await expect(page.locator('.data-check-fields .cm-editor')).toBeVisible()
  await page.locator('[id="task-check.0.expected"]').fill('9223372036854775807')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.pluginConfig).toContain('"expected":9223372036854775807')
  expect(errors).toEqual([])
})
