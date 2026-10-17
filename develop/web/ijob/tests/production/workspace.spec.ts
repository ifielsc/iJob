import { expect, test } from '@playwright/test'

for (const locale of ['zh-CN', 'en-US']) {
  test(`production workspace guides and permitted links ${locale}`, async ({ page }) => {
    await page.addInitScript(
      (locale) =>
        localStorage.setItem(
          'ijob:preferences:v1:guest',
          JSON.stringify({ locale, theme: 'light' }),
        ),
      locale,
    )
    const reads: string[] = []
    await page.route('**/api/**', (route) => {
      const path = new URL(route.request().url()).pathname
      reads.push(path)
      const data =
        path === '/api/auth/me'
          ? {
              loginId: 'workspace-test',
              username: 'test',
              displayName: 'Workspace test',
              roleCode: 'ADMIN',
              mustChangePassword: false,
              permissions: [
                'DEFINITION_READ',
                'SUMMARY_READ',
                'CONFIGURATION_WRITE',
                'EXECUTION_CONTROL',
                'APPROVAL_DECIDE',
                'SENSITIVE_READ',
                'USER_MANAGE',
                'AUDIT_READ',
              ],
            }
          : { zoneId: 'Asia/Shanghai' }
      return route.fulfill({ json: { success: true, code: 200, data } })
    })
    await page.goto('/')
    await expect(page.locator('.guide-modules li')).toHaveCount(14)
    await expect(page.locator('.resource-links a')).toHaveCount(10)
    await page
      .getByRole('tab', { name: locale === 'zh-CN' ? '任务插件用途' : 'Task plugin guide' })
      .click()
    await expect(page.getByRole('tabpanel')).toHaveAttribute('id', 'workspace-panel-plugins')
    await expect(page.locator('.guide-plugins li')).toHaveCount(11)
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('tabpanel')).toHaveAttribute('id', 'workspace-panel-operations')
    await expect(page.locator('.guide-operations details')).toHaveCount(7)
    expect(reads).toEqual(['/api/auth/me', '/api/schedules/metadata'])
  })
}
