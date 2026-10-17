import { expect, test } from '@playwright/test'
test('production job canvas loads Vue Flow, lays out nodes and preserves compact styling', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname
    const data =
      path === '/api/auth/me'
        ? {
            loginId: 'production-job',
            username: 'test',
            displayName: 'Test',
            roleCode: 'ADMIN',
            mustChangePassword: false,
            permissions: ['DEFINITION_READ', 'CONFIGURATION_WRITE', 'SENSITIVE_READ'],
          }
        : path === '/api/jobs/metadata'
          ? { maxNodes: 500, maxEdges: 5000 }
          : { list: [], total: 0 }
    return route.fulfill({ json: { success: true, code: 200, data } })
  })
  await page.goto('/jobs')
  await page.getByRole('button', { name: '新增作业', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '添加任务', exact: false }).click()
  await page.getByRole('button', { name: '添加网关', exact: false }).click()
  await expect(page.locator('.vue-flow__node')).toHaveCount(2)
  await page.getByRole('button', { name: '自动布局', exact: true }).click()
  await expect(page.locator('.application')).toHaveClass(/compact-shell/)
  await expect(page.locator('.job-node strong').first()).toHaveCSS('font-size', '13px')
  await page.waitForTimeout(250)
  const source = (await page
    .locator('.vue-flow__node')
    .first()
    .locator('[data-handleid="top"]')
    .boundingBox())!
  const target = (await page
    .locator('.vue-flow__node')
    .last()
    .locator('[data-handleid="bottom"]')
    .boundingBox())!
  await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2)
  await page.mouse.down()
  await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 8 })
  await page.mouse.up()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(1)
  await expect(page.locator('.vue-flow__edge [data-routing]')).toHaveAttribute(
    'data-routing',
    /^(direct|curve|detour)$/,
  )
  expect(errors).toEqual([])
})
