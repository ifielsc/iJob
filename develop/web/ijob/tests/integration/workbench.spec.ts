// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
import { spawnSync } from 'node:child_process'

test('real workbench excludes children and keeps current backlog outside the window', async ({
  page,
}) => {
  const password = process.env.IJOB_TEST_PASSWORD!,
    next = password + '-Changed'
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('ui-test-admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '首次登录，请修改密码' })).toBeVisible()
  await page.getByLabel('当前密码', { exact: true }).fill(password)
  await page.getByLabel('新密码', { exact: false }).first().fill(next)
  await page.getByLabel('确认新密码', { exact: true }).fill(next)
  await page.getByRole('button', { name: '更新密码并重新登录' }).click()
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  await page.getByLabel('用户名', { exact: true }).fill('ui-test-admin')
  await page.getByLabel('密码', { exact: true }).fill(next)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('nav a[href="/workbench"]')).toBeVisible()
  // Fixture writes are confined to the disposable API-only SQLite database.
  const seeded = spawnSync(
    process.env.IJOB_TEST_PYTHON || 'python',
    [
      '-c',
      `
import os,sqlite3,tempfile
from pathlib import Path
p=Path(os.environ['IJOB_TEST_DIRECTORY']).resolve()
assert p.parent==Path(tempfile.gettempdir()).resolve() and p.name.startswith('ijob-workbench-isolated-')
with sqlite3.connect(p/'isolated.db') as db:
    for i,status,parent,created in [(1,'SUCCESS',None,'2026-09-27 01:00:00.000'),(2,'SUCCESS',None,'2026-09-27 02:00:00.000'),(9007199254740993,'FAILED',None,'2026-09-27 03:00:00.000'),(4,'TIMED_OUT',None,'2026-09-27 04:00:00.000'),(5,'RUNNING',None,'2026-01-01 00:00:00.000'),(6,'FAILED',1,'2026-09-27 05:00:00.000')]:
        db.execute("INSERT INTO ifj_instance(id,root_instance_id,parent_instance_id,target_type,target_id,status,create_time) VALUES(?,?,?,'TASK',800,?,?)",(i,parent or i,parent,status,created))
`,
    ],
    { windowsHide: true, encoding: 'utf8' },
  )
  expect(seeded.status, seeded.stderr).toBe(0)
  const writes: string[] = []
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    (route) => {
      if (route.request().method() !== 'GET') {
        writes.push(new URL(route.request().url()).pathname)
        return route.abort()
      }
      return route.continue()
    },
  )
  await page.locator('nav a[href="/workbench"]').click()
  await expect(page.getByTestId('total')).toBeVisible()
  await page.locator('#workbench-from').fill('2026-09-27 00:00:00')
  await page.locator('#workbench-to').fill('2026-09-28 00:00:00')
  const response = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/workbench')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const result = await response
  expect(result.status()).toBe(200)
  const data = (await result.json()).data
  expect(data).toMatchObject({
    total: '4',
    successRatePercent: '50.00',
    successRateDenominator: '4',
    currentActiveRoots: { RUNNING: '1' },
    node: { roles: ['API'], workerRoleConfigured: false, scheduleEnabled: false },
  })
  expect(data.recentFailures.map((v: { id: string }) => v.id)).toEqual(['4', '9007199254740993'])
  await expect(page.getByTestId('total')).toHaveText('4')
  await expect(page.getByTestId('rate')).toHaveText('50.00%')
  await expect(page.locator('.workbench-node')).toContainText('Worker 角色未配置')
  await expect(page.locator('.workbench-node')).toContainText('调度未启用')
  await expect(page.locator('.workbench-backlog')).toContainText('1')
  await page
    .locator('.workbench-distribution')
    .getByRole('link', { name: '查看执行失败根运行', exact: true })
    .click()
  await expect(page).toHaveURL(/status=FAILED/)
  await page.goBack()
  await expect(page.getByTestId('total')).toHaveText('4')
  expect(writes).toEqual([])
})
