import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
test('isolated Local JAR publication, exact parameters, snapshots, validation and conflict', async ({
  page,
  request,
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
  const login = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const token = (await (await login).json()).data,
    headers = { [token.tokenName]: token.tokenValue }

  const fixture = JSON.parse(
    readFileSync(join(process.env.IJOB_TEST_DIRECTORY!, 'local-jar-fixture.json'), 'utf8'),
  )
  const get = async (path: string) => (await (await request.get(path, { headers })).json()).data
  await page.locator('nav a[href="/tasks"]').click()
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('Isolated local JAR')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: 'LOCAL_JAR · 本地 JAR', exact: true }).click()
  for (const key of ['jarFile', 'sha256', 'entryClass'])
    await page.locator('[id="task-localJar.' + key + '"]').fill(fixture[key])
  await page.getByRole('tab', { name: '业务参数', exact: true }).click()
  await page.getByRole('button', { name: '添加参数', exact: true }).click()
  await page.getByRole('textbox', { name: '第 1 行参数名', exact: true }).fill('regions')
  await page.locator('.local-jar-fields .n-select').nth(1).click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^数组$/ })
    .click()
  await page.getByRole('button', { name: '确认丢弃', exact: true }).click()
  await page.getByRole('button', { name: '第 1 行值', exact: true }).click()
  await page
    .locator('.local-jar-nested')
    .getByRole('button', { name: '添加参数', exact: true })
    .click()
  await page
    .locator('.local-jar-nested')
    .getByRole('textbox', { name: '第 1 行值', exact: true })
    .fill('cn')
  const create = page.waitForResponse(
    (r) => r.url().endsWith('/api/tasks') && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const created = await create
  expect(created.status()).toBe(201)
  const id = String((await created.json()).data.taskId)
  const detail = await get('/api/tasks/' + id),
    config = JSON.parse(detail.pluginConfig)
  expect(config).toMatchObject({
    jarSize: fixture.jarSize,
    implementationVersion: 'editor-fixture-1.0',
    spiVersion: '2',
    userConfig: { regions: ['cn'] },
  })
  await page.getByRole('button', { name: '编辑 Isolated local JAR', exact: true }).click()
  await expect(page.locator('.local-jar-snapshot')).toContainText('editor-fixture-1.0')
  await page.locator('[id="task-localJar.sha256"]').fill('0'.repeat(64))
  const fail = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + id) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await fail).status()).toBe(400)
  await expect(page.locator('[id="task-localJar.sha256"]')).toHaveValue('0'.repeat(64))
  await page.locator('[id="task-localJar.sha256"]').fill(fixture.sha256)
  await page.locator('#task-name').fill('Isolated local JAR updated')
  const published = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + id) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await published).status()).toBe(200)
  const body = {
    name: 'Exact numeric values',
    pluginType: 'LOCAL_JAR',
    enabled: true,
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
    groups: [],
    pluginConfig: JSON.stringify({ ...fixture, formatVersion: 1, userConfig: null }).replace(
      '"userConfig":null',
      '"userConfig":{"big":900719925474099312345,"ratio":0.123456789012345678901,"literal":{"__proto__":true,"a.b":false,"":null}}',
    ),
  }
  // Derived metadata is generated by the server, not supplied by the UI.
  body.pluginConfig = body.pluginConfig.replace(/,"jarSize":\d+/, '')
  const precise = await request.post('/api/tasks', { headers, data: body })
  expect(precise.status()).toBe(201)
  const preciseId = String((await precise.json()).data.taskId)
  // Refresh through SPA navigation, preserving the in-memory session.
  await page.locator('nav a[href="/jobs"]').click()
  await page.locator('nav a[href="/tasks"]').click()
  await page.getByRole('button', { name: '编辑 Exact numeric values', exact: true }).click()
  await page.getByRole('tab', { name: '业务参数', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '第 1 行值', exact: true })).toHaveValue(
    '900719925474099312345',
  )
  await expect(page.getByRole('textbox', { name: '第 2 行值', exact: true })).toHaveValue(
    '0.123456789012345678901',
  )
  await page.locator('#task-name').fill('Exact numeric values updated')
  const exactSave = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + preciseId) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await exactSave).status()).toBe(200)
  const exact = await get('/api/tasks/' + preciseId)
  expect(exact.pluginConfig).toContain('900719925474099312345')
  expect(exact.pluginConfig).toContain('0.123456789012345678901')
  expect(exact.pluginConfig).toContain('"__proto__":true')
  for (const invalid of [
    body.pluginConfig.replace(fixture.jarFile, 'missing.jar'),
    body.pluginConfig.replace(fixture.entryClass, 'sample.Missing'),
    body.pluginConfig.replace('"formatVersion":1', '"formatVersion":2'),
    body.pluginConfig.replace('"userConfig":{', '"userConfig":{"duplicate":1,"duplicate":1,'),
  ])
    expect(
      (
        await request.post('/api/tasks', { headers, data: { ...body, pluginConfig: invalid } })
      ).status(),
    ).toBe(400)
  await page.getByRole('button', { name: '编辑 Exact numeric values updated', exact: true }).click()
  expect(
    (
      await request.put('/api/tasks/' + preciseId + '?expectedRevision=2', {
        headers,
        data: { ...body, expectedRevision: '2' },
      })
    ).status(),
  ).toBe(200)
  await page.locator('#task-name').fill('Stale client change')
  const conflict = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + preciseId) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await conflict).status()).toBe(409)
  for (const name of ['triggers', 'instances'])
    expect((await get('/api/' + name + '?pageSize=1')).total).toBe(0)
})
