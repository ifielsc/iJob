import { test, expect } from '@playwright/test'
import { createServer } from 'node:http'
import { monitorConfig } from '../monitor-fixture'
test('isolated monitor: resource secrets, saved ping, publication, references and conflict', async ({
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

  const calls: { url: string; authorization?: string; key?: string }[] = []
  const agent = createServer((req, res) => {
    calls.push({
      url: req.url ?? '',
      authorization: req.headers.authorization,
      key: req.headers['x-key'] as string | undefined,
    })
    res.setHeader('Content-Type', 'application/json')
    res.end(JSON.stringify({ success: true }))
  })
  await new Promise<void>((resolve) => agent.listen(0, '127.0.0.1', resolve))
  try {
    const address = agent.address()
    if (!address || typeof address === 'string') throw Error('fixture bind')
    const endpoint = 'http://127.0.0.1:' + address.port + '/base'
    const resource = async (data: unknown) => {
      const response = await request.post('/api/resources', { headers, data })
      expect(response.status()).toBe(201)
      return String((await response.json()).data)
    }
    const resourceBody = {
      name: 'Monitor fixture',
      code: 'monitor-fixture',
      enabled: true,
      type: 'MONITOR_SERVER',
      monitorConfig: {
        protocol: 'HTTP',
        endpoint,
        connectTimeout: 5000,
        readTimeout: 10000,
        token: 'fixture-token',
        headers: { 'X-Key': 'fixture-header' },
        osType: 'LINUX',
        agentVersion: '1.0',
      },
    }
    const id = await resource(resourceBody)
    const disabled = await resource({
      ...resourceBody,
      name: 'Disabled monitor',
      code: 'disabled-monitor',
      enabled: false,
    })
    const wrong = await resource({
      name: 'Wrong type',
      code: 'wrong-type',
      type: 'DB',
      enabled: true,
      dbConfig: {
        dbType: 'MYSQL',
        jdbcUrl: 'jdbc:mysql://127.0.0.1:1/fixture',
        username: 'fixture',
      },
    })
    const notification = {
      name: 'Monitor notice',
      type: 'DINGTALK',
      enabled: true,
      dingTalkConfig: { webhook: 'https://example.test/never-send', atAll: false, atMobiles: [] },
    }
    const createdNotice = await request.post('/api/notify-configs', { headers, data: notification })
    expect(createdNotice.status()).toBe(201)
    const notifyId = (await createdNotice.json()).data
    const get = async (path: string) => (await (await request.get(path, { headers })).json()).data
    let detail = await get('/api/resources/' + id)
    expect(detail.config.endpoint).toBe(endpoint)
    expect(JSON.stringify(detail)).not.toContain('fixture-token')
    expect(JSON.stringify(detail)).not.toContain('fixture-header')
    expect(detail.config.headerNames).toEqual(['X-Key'])
    await page
      .getByRole('navigation', { name: '主导航' })
      .getByRole('link', { name: '监控服务器', exact: true })
      .click()
    await page.getByRole('button', { name: '编辑 Monitor fixture', exact: true }).click()
    await page.locator('#monitor-resource-agentVersion').fill('2.0')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.resource-drawer')).toHaveCount(0)
    detail = await get('/api/resources/' + id)
    expect(detail.config.agentVersion).toBe('2.0')
    expect(detail.config.tokenConfigured).toBe(true)
    await page.getByRole('button', { name: '编辑 Monitor fixture', exact: true }).click()
    await page.locator('#monitor-resource-endpoint').fill('http://127.0.0.1:1/unsaved')
    await page.getByRole('button', { name: '测试已保存配置', exact: true }).click()
    await expect.poll(() => calls.length).toBe(1)
    expect(calls[0]).toEqual({
      url: '/base/api/monitor/ping',
      authorization: 'Bearer fixture-token',
      key: 'fixture-header',
    })
    await expect(page.getByRole('button', { name: '测试已保存配置', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
    const config = monitorConfig
      .replace('"9007199254740993"', '"' + id + '"')
      .replace(
        '"notify":{"enabled":false}',
        '"notify":{"enabled":true,"notifyConfigId":"' + notifyId + '"}',
      )
    const body = {
      name: 'Monitor browser fixture',
      pluginType: 'SERVER_MONITOR',
      pluginConfig: config,
      enabled: true,
      retryTimes: 0,
      retryInterval: 0,
    }
    for (const invalidId of [disabled, wrong]) {
      const response = await request.post('/api/tasks', {
        headers,
        data: {
          ...body,
          pluginConfig: config.replace(
            '"resourceId":"' + id + '"',
            '"resourceId":"' + invalidId + '"',
          ),
        },
      })
      expect(response.status()).toBe(400)
    }
    const created = await request.post('/api/tasks', { headers, data: body })
    expect(created.status(), await created.text()).toBe(201)
    const task = (await created.json()).data
    await page
      .getByRole('navigation', { name: '主导航' })
      .getByRole('link', { name: '任务管理', exact: true })
      .click()
    await page.getByRole('button', { name: '编辑 Monitor browser fixture', exact: true }).click()
    await page.getByRole('tab', { name: '告警与结果', exact: true }).click()
    await expect(page.locator('.monitor-fields')).toContainText('Monitor notice')
    await page.getByRole('tab', { name: '阈值规则', exact: true }).click()
    await page.locator('[id="task-monitor.metrics.cpu.warning"]').fill('81.1234567890123456789')
    const published = page.waitForResponse(
      (r) => r.url().includes('/api/tasks/' + task.taskId) && r.request().method() === 'PUT',
    )
    await page.getByRole('button', { name: '保存', exact: true }).click()
    expect((await published).status()).toBe(200)
    await expect(page.locator('.task-drawer')).toHaveCount(0)
    const taskDetail = await get('/api/tasks/' + task.taskId)
    expect(taskDetail.pluginConfig).toContain('81.1234567890123456789')
    expect(taskDetail.pluginConfig).toContain('0.1234567890123456789')
    expect(taskDetail.currentRevision).toBe('2')
    expect((await request.delete('/api/notify-configs/' + notifyId, { headers })).status()).toBe(
      409,
    )
    expect((await request.delete('/api/resources/' + id, { headers })).status()).toBe(409)
    await page.getByRole('button', { name: '编辑 Monitor browser fixture', exact: true }).click()
    await page.getByRole('tab', { name: '阈值规则', exact: true }).click()
    await expect(page.locator('[id="task-monitor.metrics.cpu.warning"]')).toHaveValue(
      '81.1234567890123456789',
    )
    expect(
      (
        await request.put('/api/tasks/' + task.taskId + '?expectedRevision=2', {
          headers,
          data: { ...body, pluginConfig: taskDetail.pluginConfig, expectedRevision: '2' },
        })
      ).status(),
    ).toBe(200)
    await page.locator('#task-name').fill('Browser conflict draft')
    await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
    const conflict = page.waitForResponse(
      (r) => r.url().includes('/api/tasks/' + task.taskId) && r.request().method() === 'PUT',
    )
    await page.getByRole('button', { name: '保存', exact: true }).click()
    expect((await conflict).status()).toBe(409)
    expect(
      (
        await request.put('/api/notify-configs/' + notifyId, {
          headers,
          data: { ...notification, enabled: false },
        })
      ).status(),
    ).toBe(200)
    expect((await request.post('/api/tasks', { headers, data: body })).status()).toBe(400)
    const clear = await request.put('/api/resources/' + id, {
      headers,
      data: {
        ...resourceBody,
        monitorConfig: {
          protocol: 'HTTP',
          endpoint,
          clearToken: true,
          preservedHeaderNames: [],
          headers: {},
          connectTimeout: 5000,
          readTimeout: 10000,
          osType: null,
          agentVersion: null,
        },
      },
    })
    expect(clear.status()).toBe(200)
    detail = await get('/api/resources/' + id)
    expect(detail.config.tokenConfigured).toBe(false)
    expect(detail.config.headerNames).toEqual([])
    expect(
      (await request.post('/api/resources/' + id + '/connection-test', { headers })).status(),
    ).toBe(200)
    expect(calls[1]).toEqual({
      url: '/base/api/monitor/ping',
      authorization: undefined,
      key: undefined,
    })
    for (const name of ['triggers', 'instances'])
      expect((await get('/api/' + name + '?pageSize=1')).total).toBe(0)
    expect(calls).toHaveLength(2)
  } finally {
    await new Promise<void>((resolve, reject) => agent.close((e) => (e ? reject(e) : resolve())))
  }
})
