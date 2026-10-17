import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const admin = {
  loginId: 'user:design-test',
  username: 'admin',
  displayName: 'Design test',
  roleCode: 'ADMIN',
  permissions: [
    'SUMMARY_READ',
    'DEFINITION_READ',
    'CONFIGURATION_WRITE',
    'USER_MANAGE',
    'AUDIT_READ',
    'EXECUTION_CONTROL',
    'SENSITIVE_READ',
    'APPROVAL_DECIDE',
    'SELF_PASSWORD_CHANGE',
  ],
  mustChangePassword: false,
}

async function identity(page: Page, data = admin) {
  await page.route('**/api/schedules/metadata', (route) =>
    route.fulfill({ json: { success: true, code: 200, data: { zoneId: 'Asia/Shanghai' } } }),
  )
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ json: { success: true, code: 200, data } }),
  )
}
async function select(page: Page, kind: 'theme' | 'locale', option: string) {
  const control = page.getByRole('button', {
    name: kind === 'theme' ? /^(主题|Theme):/ : /^(语言|Language):/,
  })
  if ((await control.innerText()).trim() !== option) await control.click()
  await expect(control).toHaveText(option)
}

for (const theme of ['light', 'dark'] as const)
  for (const locale of ['zh-CN', 'en-US'] as const) {
    test(`shell, input and overlay: ${theme} / ${locale}`, async ({ page }, info) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(error.message))
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.addInitScript(
        ({ theme, locale }) => {
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale }))
        },
        { theme, locale },
      )
      await identity(page)
      await page.goto('/')
      await expect(
        page.getByRole('heading', {
          name: locale === 'zh-CN' ? '工作空间' : 'Workspace',
          exact: true,
        }),
      ).toBeVisible()
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
      await expect(page.locator('html')).toHaveAttribute('lang', locale)
      await expect(
        page.locator('.sidebar').getByRole('link', {
          name: locale === 'zh-CN' ? '用户管理' : 'Users',
          exact: true,
        }),
      ).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      await page.screenshot({
        path: info.outputPath(`shell-${theme}-${locale}.png`),
        fullPage: true,
      })
      await page
        .getByRole('tab', { name: locale === 'zh-CN' ? '任务插件用途' : 'Task plugin guide' })
        .click()
      await expect(page.locator('.guide-plugins li')).toHaveCount(11)
      await page.screenshot({
        path: info.outputPath(`plugins-${theme}-${locale}.png`),
        fullPage: true,
      })
      await page
        .getByRole('tab', { name: locale === 'zh-CN' ? '常见操作' : 'Common actions' })
        .click()
      await page.locator('.guide-operations summary').first().click()
      await expect(page.locator('.guide-operations details').first()).toHaveAttribute('open', '')
      await page.screenshot({
        path: info.outputPath(`operations-${theme}-${locale}.png`),
        fullPage: true,
      })
      await page.goto('/development/components')
      const field = page.locator('#verification-draft')
      await field.fill('SELECT 用户填写的内容;')
      await field.evaluate((element: HTMLTextAreaElement) => element.setSelectionRange(3, 8))
      await select(
        page,
        'theme',
        theme === 'light'
          ? locale === 'zh-CN'
            ? '深色'
            : 'Dark'
          : locale === 'zh-CN'
            ? '浅色'
            : 'Light',
      )
      await expect(field).toHaveValue('SELECT 用户填写的内容;')
      expect(
        await page
          .locator('.n-input')
          .evaluate((e) => getComputedStyle(e).getPropertyValue('--n-color').trim()),
      ).toBe(theme === 'light' ? '#192C49' : '#FFFFFF')
      await expect(page.locator('.n-input')).toHaveCSS(
        'background-color',
        theme === 'light' ? 'rgb(25, 44, 73)' : 'rgb(255, 255, 255)',
      )
      expect(
        await field.evaluate((element: HTMLTextAreaElement) => [
          element.selectionStart,
          element.selectionEnd,
        ]),
      ).toEqual([3, 8])
      await select(page, 'locale', locale === 'zh-CN' ? 'English' : '中文')
      await expect(field).toHaveValue('SELECT 用户填写的内容;')
      await page
        .getByRole('button', {
          name: locale === 'zh-CN' ? 'Open overlay' : '打开浮层',
          exact: true,
        })
        .click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await expect(page.getByRole('dialog')).toHaveCSS('opacity', '1')
      await expect(page.getByRole('dialog')).toHaveCSS(
        'background-color',
        theme === 'light' ? 'rgb(32, 52, 81)' : 'rgb(255, 255, 255)',
      )
      await expect(page.getByRole('dialog')).toContainText(
        locale === 'zh-CN' ? 'Themed overlay' : '主题浮层',
      )
      await page.screenshot({
        path: info.outputPath(`overlay-${theme}-${locale}.png`),
        animations: 'disabled',
        fullPage: true,
      })
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog')).not.toBeVisible()
      expect(errors).toEqual([])
    })
  }

test('default ignores dark OS and English browser; keyboard skip link works', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await identity(page)
  await page.goto('/')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')
  await expect(page.locator('.identity-name')).toContainText('Design test')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '跳转到主要内容' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#main')).toBeFocused()
})

test('viewer and STATIC-style identity do not gain user administration from role', async ({
  page,
}) => {
  await identity(page, { ...admin, permissions: ['SUMMARY_READ'] })
  await page.goto('/')
  await expect(page.locator('.identity-name')).toContainText('Design test')
  await expect(page.getByRole('link', { name: '用户管理', exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '操作审计', exact: true })).toHaveCount(0)
  await expect(page.locator('.guide-modules a')).toHaveCount(3)
  await expect(page.locator('.guide-modules a[href="/users"]')).toHaveCount(0)
  await expect(page.locator('.guide-steps li')).toHaveCount(2)
  await expect(page.getByRole('tab', { name: '任务插件用途' })).toHaveCount(0)
})

test('workspace explains available modules and plugins without loading business data', async ({
  page,
}) => {
  const requests: string[] = []
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path.startsWith('/api/')) requests.push(path)
  })
  await identity(page)
  await page.route('**/api/schedules/metadata', (route) =>
    route.fulfill({ json: { success: true, code: 200, data: { zoneId: 'Asia/Shanghai' } } }),
  )
  await page.goto('/')
  await expect(page.locator('.guide-steps li')).toHaveCount(7)
  await expect(page.locator('.guide-modules a')).toHaveCount(14)
  await expect(page.locator('.resource-links a')).toHaveCount(10)
  await expect(page.locator('.guide-modules a[href="/parameters"]')).toBeVisible()
  await expect(page.locator('a[href="/notifications?tab=records"]')).toBeVisible()
  await expect(page.locator('.guide-modules a[href="/tasks"]')).toBeVisible()
  await expect(page.locator('.guide-modules a[href="/notifications"]')).toHaveCount(1)
  await page.getByRole('tab', { name: '任务插件用途' }).click()
  await expect(page.locator('.guide-plugins li')).toHaveCount(11)
  await expect(page.locator('.guide-plugins dl')).toHaveCount(11)
  await page.getByRole('tab', { name: '常见操作' }).click()
  await expect(page.locator('.guide-operations details')).toHaveCount(7)
  await expect(page.getByText('更多业务模块逐步开放')).toHaveCount(0)
  expect(requests).toEqual(['/api/auth/me', '/api/schedules/metadata'])
})

test('mandatory password change removes business navigation', async ({ page }) => {
  await identity(page, { ...admin, mustChangePassword: true })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '首次登录，请修改密码' })).toBeVisible()
  await expect(page.locator('.nav-pending')).toHaveCount(0)
})

test('401 is a sign-in state and does not retry indefinitely', async ({ page }) => {
  let calls = 0
  await page.route('**/api/auth/me', (route) => {
    calls++
    return route.fulfill({ status: 401, json: { success: false, code: 401, data: null } })
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  expect(calls).toBe(1)
})

test('blocked local storage still allows theme and locale changes', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', {
      get() {
        throw new Error('Blocked storage')
      },
    })
  })
  await identity(page)
  await page.goto('/')
  await select(page, 'theme', '深色')
  await select(page, 'locale', 'English')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.locator('html')).toHaveAttribute('lang', 'en-US')
})

test('English layout fits a narrow viewport', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await identity(page)
  await page.goto('/')
  await select(page, 'locale', 'English')
  await expect(page.getByRole('heading', { name: 'Workspace', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('mobile-english.png'), fullPage: true })
  await page.getByRole('tab', { name: 'Task plugin guide' }).click()
  await expect(page.locator('.guide-plugins li')).toHaveCount(11)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('mobile-plugins.png'), fullPage: true })
})

test('operator sees review steps and approval guidance without configuration actions', async ({
  page,
}) => {
  await identity(page, {
    ...admin,
    roleCode: 'OPERATOR',
    permissions: ['DEFINITION_READ', 'SUMMARY_READ', 'EXECUTION_CONTROL', 'APPROVAL_DECIDE'],
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '查看与处理运行' })).toBeVisible()
  await expect(page.locator('.guide-steps a[href="/events/approvals"]')).toBeVisible()
  await expect(page.getByRole('heading', { name: '开始配置与执行' })).toHaveCount(0)
  await page.getByRole('tab', { name: '常见操作' }).click()
  await expect(page.locator('.guide-operations details')).toHaveCount(5)
  await expect(page.getByText('如何配置通知并查看发送结果？')).toHaveCount(0)
  const summary = page.locator('.guide-operations summary').first()
  await summary.focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('.guide-operations details').first()).toHaveAttribute('open', '')
})

test('tablet tabs fit, preserve selection across language changes and do not execute actions', async ({
  page,
}, info) => {
  const writes: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/') && request.method() !== 'GET')
      writes.push(request.url())
  })
  await page.setViewportSize({ width: 768, height: 1024 })
  await identity(page)
  await page.goto('/')
  await page.getByRole('tab', { name: '功能导航' }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('tab', { name: '任务插件用途' })).toBeFocused()
  await expect(page.getByRole('tab', { name: '任务插件用途' })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await page.keyboard.press('End')
  await expect(page.getByRole('tab', { name: '常见操作' })).toBeFocused()
  await page.getByRole('tab', { name: '常见操作' }).click()
  await select(page, 'locale', 'English')
  await expect(page.getByRole('tab', { name: 'Common actions' })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await expect(page.locator('.guide-operations details')).toHaveCount(7)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('tablet-english.png'), fullPage: true })
  expect(writes).toEqual([])
})

test('empty permissions show a clear empty state and identity refresh failure is not success', async ({
  page,
}) => {
  await identity(page, { ...admin, permissions: [] })
  await page.goto('/')
  await expect(page.getByText('当前账户暂无业务模块访问权限。')).toBeVisible()
  await expect(page.locator('.guide-steps')).toHaveCount(0)
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: 503, json: { success: false, code: 503, data: null } }),
  )
  await page.getByRole('button', { name: '刷新身份' }).click()
  await expect(page.locator('.identity-state')).toContainText('当前身份刷新失败')
  await expect(page.locator('.identity-error')).toBeVisible()
})
