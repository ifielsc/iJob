import { test, expect } from '@playwright/test'
import { spawnSync } from 'node:child_process'

test('real diagnostics reads four categories and safe drilldown', async ({ page }) => {
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
  await expect(page.locator('nav a[href="/diagnostics"]')).toBeVisible()
  const seeded = spawnSync(
    process.env.IJOB_TEST_PYTHON || 'python',
    [
      '-c',
      `
import os,sqlite3,tempfile
from pathlib import Path
p=Path(os.environ['IJOB_TEST_DIRECTORY']).resolve()
assert p.parent==Path(tempfile.gettempdir()).resolve() and p.name.startswith('ijob-diagnostics-isolated-')
with sqlite3.connect(p/'isolated.db') as db:
    db.execute("INSERT INTO ifj_task(id,name,plugin_type,plugin_config) VALUES(800,'Current name','SQL','{}')")
    db.execute("INSERT INTO ifj_task_definition(id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled) VALUES(801,800,1,'Published task','SQL','{}',0,0,1)")
    db.execute("INSERT INTO ifj_instance(id,root_instance_id,target_type,target_id,status,timeout_at,task_definition_id) VALUES(9007199254740993,9007199254740993,'TASK',800,'RUNNING','2020-01-01 00:00:00.000',801)")
    for i,status in [(10,'CLAIMED'),(11,'READY')]:
        db.execute("INSERT INTO ifj_trigger(id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time,task_definition_id) VALUES(?,'TASK',800,'MANUAL',?,'WAITING','2020-01-01 00:00:00.000',801)",(i,'fixture-'+str(i)))
        db.execute("INSERT INTO ifj_trigger_work_item(trigger_id,status,available_at,max_attempts,lease_owner,claim_token,lease_deadline,last_error) VALUES(?,?,'2020-01-01 00:00:00.000',3,?,?,?,'fixture-private-error')",(i,status,'private-owner' if i==10 else None,'private-token' if i==10 else None,'2020-01-01 00:00:00.000' if i==10 else None))
    db.execute("INSERT INTO ifj_runtime_command(request_id,instance_id,root_instance_id,trigger_id,command_type,status,requested_by,available_at,expires_at) VALUES('cmd-fixture',9007199254740993,9007199254740993,10,'STOP','PENDING','fixture-private-user','2020-01-01 00:00:00.000','2020-01-01 00:00:00.000')")
`,
    ],
    { windowsHide: true, encoding: 'utf8' },
  )
  expect(seeded.status, seeded.stderr).toBe(0)
  const writes: string[] = [],
    errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
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
  let response = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/diagnostics')
  await page.locator('nav a[href="/diagnostics"]').click()
  const first = await response
  expect(first.status()).toBe(200)
  const data = (await first.json()).data
  expect(data.items).toHaveLength(1)
  expect(data.items[0].id).toBe('10')
  expect(JSON.stringify(data)).not.toContain('private')
  await expect(page.locator('tbody')).toContainText('Published task')
  await page.getByRole('button', { name: '查看 10 的证据', exact: true }).click()
  const triggerDetail = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/triggers/10' && r.request().method() === 'GET',
  )
  await page.locator('.diagnostics-drawer').getByRole('link', { name: '查看触发记录' }).click()
  const inspected = await triggerDetail
  expect(inspected.status()).toBe(200)
  expect((await inspected.json()).data).toMatchObject({
    id: '10',
    targetId: '800',
    taskDefinitionId: '801',
  })
  await expect(
    page.locator('.run-drawer').getByText('记录 ID', { exact: true }).locator('+ dd'),
  ).toHaveText('10')
  await expect(page.locator('.run-drawer')).toContainText('历史名称不可用')
  await page.keyboard.press('Escape')
  await page.getByRole('link', { name: '返回运行诊断', exact: false }).click()
  await expect(page.getByTestId('diagnostics-count')).toBeVisible()
  for (const [label, category, id] of [
    ['队列等待较久', 'QUEUE_DELAYED', '11'],
    ['根运行超期', 'ROOT_DEADLINE_EXCEEDED', '9007199254740993'],
    ['命令待收尾', 'COMMAND_EXPIRED', 'cmd-fixture'],
  ]) {
    response = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/diagnostics')
    await page.getByRole('tab', { name: label, exact: true }).click()
    const result = await response
    expect(result.status()).toBe(200)
    const facts = (await result.json()).data
    expect(facts.category).toBe(category)
    expect(facts.items).toHaveLength(1)
    expect(facts.items[0].id).toBe(id)
    await expect(page.getByTestId('diagnostics-count')).toHaveText('已显示 1 项')
  }
  await page.getByRole('button', { name: '查看 cmd-fixture 的证据', exact: true }).click()
  await page.locator('.diagnostics-drawer').getByRole('link', { name: '查看关联实例' }).click()
  await expect(page).toHaveURL(/requestId=cmd-fixture/)
  await expect(page.locator('a[href^="/diagnostics?"]')).toBeVisible()
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})
