// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import console from 'node:console'
import { chromium } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
const credentials = JSON.parse(readFileSync(0, 'utf8'))
const directory = '../../../test/reports/20260926-stage5'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
try {
  await page.goto('http://127.0.0.1:5173/login')
  await page.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('link', { name: '调度管理', exact: true }).click()
  await page.getByText('没有符合条件的调度', { exact: false }).waitFor({ timeout: 10000 })
  await page.screenshot({ path: directory + '/live-list.png', fullPage: true })
  await page.getByRole('button', { name: '新增调度', exact: true }).click()
  await page.locator('#schedule-cronExpr').fill('0 0 9 * * *')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await page.locator('.schedule-preview-table tbody tr').first().waitFor()
  const rows = await page.locator('.schedule-preview-table tbody tr').count()
  if (rows !== 10 || errors.length) throw new Error('Live preview validation failed')
  await page.screenshot({ path: directory + '/live-preview.png', fullPage: true })
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await page.locator('.schedule-drawer').waitFor({ state: 'detached' })
  writeFileSync(
    directory + '/live-browser.json',
    JSON.stringify(
      { authenticated: true, previewRows: rows, savedSchedules: 0, pageErrors: errors },
      null,
      2,
    ),
  )
  console.log('Live scheduling preview verified; no schedule saved.')
} finally {
  await browser.close()
}
