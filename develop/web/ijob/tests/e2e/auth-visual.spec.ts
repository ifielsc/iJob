// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`compact auth sample ${theme}/${locale}`, async ({ page }, info) => {
      const en = locale === 'en-US'
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
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
              loginId: 'visual:test',
              username: 'visual',
              displayName: 'Visual test',
              roleCode: 'ADMIN',
              permissions: ['SELF_PASSWORD_CHANGE'],
              mustChangePassword: true,
            },
          },
        }),
      )
      const measurements: unknown[] = []
      for (const path of ['/login', '/change-password']) {
        await page.goto(path)
        await expect(page).toHaveURL(new RegExp(`${path}$`))
        await expect(page.locator('.auth-card h1')).toHaveText(
          path === '/login'
            ? en
              ? 'Sign in'
              : '登录'
            : en
              ? 'Change your initial password'
              : '首次登录，请修改密码',
        )
        await expect(page.locator('.auth-card h1')).toBeVisible()
        for (const viewport of [
          { width: 1366, height: 650 },
          { width: 655, height: 620 },
          { width: 1440, height: 900 },
          { width: 1920, height: 1080 },
          { width: 390, height: 844 },
        ]) {
          await page.setViewportSize(viewport)
          await page.evaluate(() => document.fonts.ready)
          const geometry = await page.evaluate(() => {
            const card = document.querySelector('.auth-card')!
            const input = document.querySelector('#main input')!
            return {
              width: innerWidth,
              height: innerHeight,
              scrollHeight: document.documentElement.scrollHeight,
              scrollWidth: document.documentElement.scrollWidth,
              card: card.getBoundingClientRect().toJSON(),
              font: getComputedStyle(input).font,
            }
          })
          measurements.push({ path, ...geometry })
          expect(geometry.scrollHeight).toBeLessThanOrEqual(viewport.height)
          expect(geometry.scrollWidth).toBeLessThanOrEqual(viewport.width)
          await page.screenshot({
            path: info.outputPath(`${path.slice(1)}-${viewport.width}.png`),
            animations: 'disabled',
          })
        }
      }
      // Small content viewport (e.g. browser zoom): scrolling must preserve access, not clip controls.
      await page.setViewportSize({ width: 683, height: 325 })
      const logout = page.getByRole('button', { name: en ? 'Sign out' : '退出登录', exact: true })
      await logout.scrollIntoViewIfNeeded()
      await expect(logout).toBeInViewport()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(683)
      await info.attach('layout-metrics', {
        body: JSON.stringify(measurements, null, 2),
        contentType: 'application/json',
      })
      expect(errors).toEqual([])
    })
  }

test('auth preferences preserve input and password visibility is keyboard accessible', async ({
  page,
}) => {
  let identityCalls = 0
  let loginCalls = 0
  await page.route('**/api/auth/me', (r) => {
    identityCalls++
    return r.fulfill({ status: 401, json: { success: false, code: 401 } })
  })
  await page.route('**/api/auth/login', (r) => {
    loginCalls++
    return r.fulfill({ status: 401, json: { success: false, code: 401 } })
  })
  await page.goto('/login')
  const user = page.getByLabel('用户名', { exact: true })
  await expect(user).toBeVisible()
  expect(identityCalls).toBe(0)
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect(loginCalls).toBe(0)
  await user.fill('visual_draft')
  const password = page.getByLabel('密码', { exact: true })
  await password.fill('test-only-visual-password')
  await expect(password).toHaveAttribute('autocomplete', 'current-password')
  const toggle = page.getByRole('button', { name: '显示密码 · 密码', exact: true })
  await toggle.focus()
  await page.keyboard.press('Space')
  await expect(password).toHaveAttribute('type', 'text')
  await expect(toggle).toHaveAttribute('aria-pressed', 'true')
  await page.keyboard.press('Space')
  await expect(password).toHaveAttribute('type', 'password')
  await password.evaluate((el: HTMLInputElement) => {
    el.setSelectionRange(2, 5)
    el.dataset.retained = 'yes'
  })
  await page.getByRole('button', { name: '主题: 浅色', exact: true }).click()
  await page.getByRole('button', { name: '语言: 中文', exact: true }).click()
  const retained = page.getByLabel('Password', { exact: true })
  await expect(retained).toHaveValue('test-only-visual-password')
  await expect(retained).toHaveAttribute('data-retained', 'yes')
  expect(
    await retained.evaluate((el: HTMLInputElement) => [el.selectionStart, el.selectionEnd]),
  ).toEqual([2, 5])
  await expect(page.getByLabel('Username', { exact: true })).toHaveValue('visual_draft')
})

test('password validation and failure feedback preserve a usable compact layout', async ({
  page,
}, info) => {
  let changes = 0
  await page.setViewportSize({ width: 1366, height: 650 })
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          loginId: 'visual:test',
          username: 'visual',
          displayName: 'Visual',
          roleCode: 'ADMIN',
          permissions: ['SELF_PASSWORD_CHANGE'],
          mustChangePassword: false,
        },
      },
    }),
  )
  await page.route('**/api/auth/change-password', (r) => {
    changes++
    return r.fulfill({
      status: 400,
      json: {
        success: false,
        code: 400,
        messageKey: 'error.security.password',
        traceId: 'visual-validation',
      },
    })
  })
  await page.goto('/change-password')
  await expect(page.getByRole('heading', { name: '修改密码', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '取消', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBe(650)
  const current = page.getByLabel('当前密码', { exact: true })
  const next = page.getByLabel('新密码', { exact: true })
  const confirm = page.getByLabel('确认新密码', { exact: true })
  const save = page.getByRole('button', { name: '更新密码并重新登录', exact: true })
  await current.fill('test-only-current-password')
  await next.fill('test-only-next-password')
  await confirm.fill('test-only-mismatch')
  await save.click()
  await expect(page.getByRole('alert')).toContainText('两次输入的新密码不一致')
  expect(changes).toBe(0)
  await next.fill('short')
  await confirm.fill('short')
  await save.click()
  expect(changes).toBe(0)
  await expect(next).toHaveValue('short')
  await next.fill('test-only-next-password')
  await confirm.fill('test-only-next-password')
  await save.click()
  await expect(page.getByRole('alert')).toContainText('visual-validation')
  await expect(current).toHaveValue('')
  await expect(next).toHaveValue('')
  await expect(confirm).toHaveValue('')
  expect(changes).toBe(1)
  await page.screenshot({ path: info.outputPath('password-error.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 480 })
  await save.scrollIntoViewIfNeeded()
  await expect(save).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
})
