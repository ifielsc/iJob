import { test, expect } from '@playwright/test'

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`inline required feedback ${theme}/${locale}`, async ({ page }, info) => {
      let commands = 0
      await page.setViewportSize({ width: 1366, height: 650 })
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await page.route('**/api/auth/me', (r) =>
        r.fulfill({
          json: {
            success: true,
            code: 200,
            data: {
              loginId: 'validation:test',
              username: 'validation',
              displayName: 'Validation',
              roleCode: 'ADMIN',
              permissions: ['SELF_PASSWORD_CHANGE'],
              mustChangePassword: true,
            },
          },
        }),
      )
      await page.route(/\/api\/auth\/(login|change-password)$/, (r) => {
        commands++
        return r.fulfill({
          status: 400,
          json: { success: false, code: 400, traceId: 'required-test' },
        })
      })
      for (const path of ['/login', '/change-password']) {
        const en = locale === 'en-US'
        await page.goto(path)
        await expect(page).toHaveURL(new RegExp(`${path}$`))
        const fields =
          path === '/login'
            ? ['auth-username', 'auth-password']
            : ['auth-current-password', 'auth-new-password', 'auth-confirm-password']
        const first = page.locator(`#${fields[0]}`)
        await expect(first).toBeVisible()
        const cardBefore = await page.locator('.auth-card').boundingBox()
        const fieldBefore = await first.boundingBox()
        await expect(page.locator('.auth-required')).toHaveCount(0)
        await expect(page.locator('.n-form-item-label__asterisk')).toHaveCount(fields.length)
        for (const id of fields) {
          await expect(
            page.locator(`label[for="${id}"] .n-form-item-label__asterisk`),
          ).toBeVisible()
          await expect(page.locator(`#${id}`)).toHaveAttribute('required', '')
          await expect(page.locator(`#${id}`)).not.toHaveAttribute('aria-invalid', 'true')
        }
        expect(await page.locator('form').evaluate((el: HTMLFormElement) => el.noValidate)).toBe(
          true,
        )
        const beforeCommands = commands
        await first.press('Enter')
        await expect(first).toBeFocused()
        await expect(page.locator('.auth-required')).toHaveCount(fields.length)
        for (const id of fields) {
          await expect(page.locator(`#${id}`)).toHaveAttribute('aria-invalid', 'true')
          await expect(page.locator(`#${id}`)).toHaveAttribute(
            'aria-describedby',
            new RegExp(`${id}-required`),
          )
          await expect(page.locator(`#${id}-required`)).toHaveText(en ? 'Required' : '必填')
        }
        expect(commands).toBe(beforeCommands)
        expect(await page.locator('.auth-card').boundingBox()).toEqual(cardBefore)
        expect(await first.boundingBox()).toEqual(fieldBefore)
        for (const viewport of [
          { width: 1366, height: 650 },
          { width: 390, height: 844 },
        ]) {
          await page.setViewportSize(viewport)
          expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBe(
            viewport.height,
          )
          expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(
            viewport.width,
          )
          await page.screenshot({
            path: info.outputPath(`${path.slice(1)}-required-${viewport.width}.png`),
            animations: 'disabled',
          })
        }
        await first.fill('test-only-current')
        await expect(page.locator(`#${fields[0]}-required`)).toHaveCount(0)
        await page
          .getByRole('button', { name: en ? 'Language: English' : '语言: 中文', exact: true })
          .click()
        for (const id of fields.slice(1))
          await expect(page.locator(`#${id}-required`)).toHaveText(en ? '必填' : 'Required')
        await expect(first).toHaveValue('test-only-current')
        await first.press('Enter')
        await expect(page.locator(`#${fields[1]}`)).toBeFocused()
        expect(commands).toBe(beforeCommands)
        for (const id of fields.slice(1))
          await page.locator(`#${id}`).fill('test-only-next-password')
        await expect(page.locator('.auth-required')).toHaveCount(0)
        await page.locator(`#${fields.at(-1)}`).press('Enter')
        await expect(page.getByRole('alert')).toContainText('required-test')
        expect(commands).toBe(beforeCommands + 1)
        await expect(page.locator('.auth-required')).toHaveCount(0)
        for (const id of fields.filter((id) => id !== 'auth-username'))
          await expect(page.locator(`#${id}`)).toHaveValue('')
        await page.setViewportSize({ width: 1366, height: 650 })
      }
    })
  }
