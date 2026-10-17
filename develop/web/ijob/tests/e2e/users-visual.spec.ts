import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const identity = {
  loginId: 'user:admin',
  username: 'preview-admin',
  displayName: 'Admin',
  roleCode: 'ADMIN',
  permissions: ['SUMMARY_READ', 'USER_MANAGE', 'AUDIT_READ', 'SELF_PASSWORD_CHANGE'],
  mustChangePassword: false,
}
const accounts = [
  {
    id: '9007199254740993',
    loginId: 'user:admin',
    username: 'preview-admin',
    displayName: '平台管理员',
    roleCode: 'ADMIN',
    enabled: true,
    mustChangePassword: false,
    revision: '9007199254741001',
  },
  {
    id: '9007199254740994',
    loginId: 'user:operator',
    username: 'preview-operator',
    displayName: 'Operations',
    roleCode: 'OPERATOR',
    enabled: true,
    mustChangePassword: true,
    revision: '9007199254741002',
  },
  {
    id: '9007199254740995',
    loginId: 'user:reader',
    username: 'preview-reader',
    displayName: 'Read only',
    roleCode: 'VIEWER',
    enabled: false,
    mustChangePassword: false,
    revision: '9007199254741003',
  },
]
async function setup(page: Page) {
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({ json: { success: true, code: 200, data: identity } }),
  )
  await page.route('**/api/users?*', (r) =>
    r.fulfill({
      json: {
        success: true,
        code: 200,
        data: { list: accounts, total: 3, pageNum: 1, pageSize: 20, pages: 1 },
      },
    }),
  )
  await page.route('**/api/roles', (r) =>
    r.fulfill({
      json: {
        success: true,
        code: 200,
        data: [
          { roleCode: 'ADMIN', permissions: identity.permissions },
          {
            roleCode: 'OPERATOR',
            permissions: ['SUMMARY_READ', 'EXECUTION_CONTROL', 'APPROVAL_DECIDE'],
          },
          { roleCode: 'VIEWER', permissions: ['SUMMARY_READ'] },
        ],
      },
    }),
  )
}

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`compact user surfaces ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto('/users')
      await expect(page.getByRole('cell', { name: 'preview-reader', exact: true })).toBeVisible()
      await expect(page.locator('.sidebar')).toHaveCSS('width', '208px')
      await expect(
        page.getByRole('button', { name: `${en ? 'Edit' : '编辑'} preview-reader`, exact: true }),
      ).toHaveCSS('color', theme === 'light' ? 'rgb(48, 100, 218)' : 'rgb(141, 179, 255)')
      const editBox = await page
        .getByRole('button', { name: `${en ? 'Edit' : '编辑'} preview-reader`, exact: true })
        .boundingBox()
      const deleteBox = await page
        .getByRole('button', { name: `${en ? 'Delete' : '删除'} preview-reader`, exact: true })
        .boundingBox()
      expect(Math.abs(editBox!.y - deleteBox!.y)).toBeLessThan(1)
      expect(deleteBox!.x).toBeGreaterThan(editBox!.x)
      await page.screenshot({ path: info.outputPath('users-desktop.png'), fullPage: true })
      const create = page.getByRole('button', { name: en ? 'New user' : '新增用户', exact: true })
      await create.click()
      const drawer = page.locator('.users-drawer')
      await expect(drawer).toBeVisible()
      await expect(drawer).toHaveCSS('width', '520px')
      await expect(drawer.locator('.n-form-item-label__asterisk')).toHaveCount(2)
      await expect(drawer.locator('.auth-required')).toHaveCount(0)
      for (const id of ['user-username', 'user-password'])
        await expect(
          drawer.locator(`label[for="${id}"] .n-form-item-label__asterisk`),
        ).toBeVisible()
      await expect(
        drawer.locator('label[for="user-display-name"] .n-form-item-label__asterisk'),
      ).toHaveCount(0)
      await page.screenshot({ path: info.outputPath('create-initial.png'), animations: 'disabled' })
      const save = drawer.getByRole('button', {
        name: en ? 'Save' : '保存',
        exact: true,
      })
      await save.click()
      await expect(drawer.locator('.auth-required')).toHaveCount(2)
      await expect(drawer.locator('#user-username')).toBeFocused()
      expect(
        await drawer.locator('form').evaluate((form: HTMLFormElement) => form.noValidate),
      ).toBe(true)
      for (const viewport of [
        { width: 1366, height: 650 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await expect(save).toBeInViewport()
        const rect = await drawer.boundingBox()
        expect(rect!.width).toBeLessThanOrEqual(viewport.width)
        expect(rect!.x).toBeGreaterThanOrEqual(0)
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(viewport.width)
        await page.screenshot({
          path: info.outputPath(`create-${viewport.width}.png`),
          animations: 'disabled',
        })
      }
      await drawer.getByRole('button', { name: en ? 'Cancel' : '取消', exact: true }).click()
      await expect(drawer).toHaveCount(0)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page
        .getByRole('button', { name: `${en ? 'Edit' : '编辑'} preview-reader`, exact: true })
        .click()
      await drawer.locator('#user-display-name').fill('Unsaved visual draft')
      await expect(drawer.locator('.n-form-item-label__asterisk')).toHaveCount(0)
      await page.screenshot({ path: info.outputPath('edit-profile.png'), animations: 'disabled' })
      await drawer
        .getByRole('tab', { name: en ? 'Role and status' : '角色与状态', exact: true })
        .click()
      const dialog = page.locator('.n-dialog')
      await expect(dialog).toBeVisible()
      await dialog.getByRole('button', { name: en ? 'Cancel' : '取消', exact: true }).click()
      await expect(dialog).toHaveCount(0)
      await expect(drawer.locator('#user-display-name')).toHaveValue('Unsaved visual draft')
      await drawer
        .getByRole('tab', { name: en ? 'Role and status' : '角色与状态', exact: true })
        .click()
      await dialog.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }).click()
      await expect(dialog).toHaveCount(0)
      const role = drawer.getByRole('combobox', { name: en ? 'Role' : '角色', exact: true })
      await role.click()
      await page.getByRole('option', { name: en ? 'Operator' : '操作员', exact: true }).click()
      await expect(role).toHaveAttribute('aria-expanded', 'false')
      await expect(
        page.getByRole('listbox', { name: en ? 'Role' : '角色', exact: true }),
      ).not.toBeVisible()
      await expect(
        drawer.locator('#user-editor').getByText(en ? 'Operator' : '操作员', { exact: true }),
      ).toBeVisible()
      const enabled = drawer.getByRole('switch', { name: en ? 'Enable' : '启用', exact: true })
      await enabled.focus()
      await page.keyboard.press('Space')
      await expect(enabled).toBeChecked()
      await page.screenshot({ path: info.outputPath('edit-access.png'), animations: 'disabled' })
      await drawer
        .getByRole('tab', { name: en ? 'Reset password' : '重置密码', exact: true })
        .click()
      await page.screenshot({
        path: info.outputPath('discard-confirm.png'),
        animations: 'disabled',
      })
      await dialog.getByRole('button', { name: en ? 'Confirm' : '确认', exact: true }).click()
      await expect(dialog).toHaveCount(0)
      await expect(drawer.locator('#user-password')).toHaveValue('')
      await expect(drawer.locator('.n-form-item-label__asterisk')).toHaveCount(1)
      await page.screenshot({ path: info.outputPath('edit-reset.png'), animations: 'disabled' })
      await drawer.getByRole('button', { name: en ? 'Cancel' : '取消', exact: true }).click()
      await expect(drawer).toHaveCount(0)
      await page.setViewportSize({ width: 390, height: 844 })
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
      await page.screenshot({ path: info.outputPath('users-mobile.png'), fullPage: true })
    })
  }

test('Naive page-size and role selections retain server pagination and create defaults', async ({
  page,
}) => {
  await setup(page)
  const requests: string[] = []
  let created: Record<string, unknown> | undefined
  page.on('request', (r) => {
    if (r.url().includes('/api/users?')) requests.push(r.url())
  })
  await page.route('**/api/users', (r) => {
    created = r.request().postDataJSON()
    return r.fulfill({ json: { success: true, code: 200, data: accounts[0] } })
  })
  await page.goto('/users')
  await page.getByRole('combobox', { name: '每页条数', exact: true }).click()
  await page.getByRole('option', { name: '50', exact: true }).click()
  await expect
    .poll(() => requests.some((url) => new URL(url).searchParams.get('pageSize') === '50'))
    .toBe(true)
  expect(new URL(requests.at(-1)!).searchParams.get('pageNum')).toBe('1')
  await page.getByRole('button', { name: '新增用户', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect(created).toBeUndefined()
  await page.getByLabel('用户名', { exact: true }).fill('new-operator')
  await page.getByLabel('初始密码', { exact: true }).fill('test-only-initial-password')
  await page.locator('.users-drawer').getByRole('combobox', { name: '角色', exact: true }).click()
  await page.getByRole('option', { name: '操作员', exact: true }).click()
  await page.getByRole('switch', { name: '启用', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.users-drawer')).toHaveCount(0)
  expect(created).toEqual({
    username: 'new-operator',
    password: 'test-only-initial-password',
    displayName: '',
    roleCode: 'OPERATOR',
    enabled: false,
  })
})
