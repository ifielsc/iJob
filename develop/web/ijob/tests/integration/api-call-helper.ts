import { expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
export async function verifyApiCallTasks(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
) {
  const seen: {
    url: string
    method: string
    body: string
    auth?: string
    task?: string
    service?: string
  }[] = []
  const server = createServer(async (req, res) => {
    let body = ''
    for await (const chunk of req) body += chunk.toString()
    seen.push({
      url: req.url!,
      method: req.method!,
      body,
      auth: req.headers.authorization,
      task: req.headers['x-task'] as string,
      service: req.headers['x-service'] as string,
    })
    res.setHeader('Content-Type', 'application/json')
    if (req.url === '/token')
      return res.end(
        JSON.stringify({ access_token: 'fixture-token', token_type: 'Bearer', expires_in: 3600 }),
      )
    if (req.url === '/fail') res.statusCode = 503
    if (req.url === '/unauth') res.statusCode = 401
    if (req.url === '/slow') return
    if (req.url === '/large') return res.end('x'.repeat(4096))
    res.end(JSON.stringify({ ok: true, id: 'result-id' }))
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  const base = 'http://127.0.0.1:' + (server.address() as AddressInfo).port
  const service = async (name: string, config: Record<string, unknown>) => {
    const r = await request.post('/api/resources', {
      headers,
      data: { name, code: name, type: 'SERVICE', enabled: true, serviceConfig: config },
    })
    expect(r.status(), await r.text()).toBe(201)
    return (await r.json()).data as string
  }
  const create = async (name: string, config: Record<string, unknown>) => {
    const r = await request.post('/api/tasks', {
      headers,
      data: {
        name,
        pluginType: 'API_CALL',
        pluginConfig: JSON.stringify(config),
        enabled: true,
        retryTimes: 0,
        retryInterval: 0,
      },
    })
    expect(r.status(), await r.text()).toBe(201)
    return (await r.json()).data
  }
  const run = async (id: string, status: string) => {
    const r = await request.post('/api/tasks/' + id + '/manual-trigger', {
      headers,
      data: { requestId: crypto.randomUUID(), variables: {} },
    })
    expect(r.status()).toBe(202)
    const tid = (await r.json()).data
    await expect
      .poll(
        async () =>
          (await (await request.get('/api/triggers/' + tid, { headers })).json()).data.status,
        { timeout: 30000 },
      )
      .toBe(status)
    return tid as string
  }
  try {
    const id = await service('api-http-fixture', {
      protocol: 'HTTP',
      endpoint: base,
      headers: { 'X-Service': 'service-secret' },
      authType: 'OAUTH2_CLIENT_CREDENTIALS',
      oauth2: {
        tokenEndpoint: base + '/token',
        clientId: 'fixture-client',
        clientSecret: 'fixture-secret',
      },
      callTimeout: 2,
    })
    const detail = (await (await request.get('/api/resources/' + id, { headers })).json()).data
    expect(JSON.stringify(detail)).not.toContain('fixture-secret')
    expect(detail.config).toMatchObject({
      endpoint: base,
      oauth2TokenEndpoint: base + '/token',
      oauth2ClientId: 'fixture-client',
      endpointConfigured: true,
      oauth2TokenEndpointConfigured: true,
      headerNames: ['X-Service'],
    })
    const saved = await request.put('/api/resources/' + id, {
      headers,
      data: {
        name: 'api-http-fixture',
        code: 'api-http-fixture',
        type: 'SERVICE',
        enabled: true,
        serviceConfig: {
          preserveExisting: true,
          protocol: 'HTTP',
          authType: 'OAUTH2_CLIENT_CREDENTIALS',
          preservedHeaderNames: ['X-Service'],
          headers: {},
        },
      },
    })
    expect(saved.status(), await saved.text()).toBe(200)
    await page.getByRole('link', { name: '服务资源', exact: true }).click()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await page.getByRole('button', { name: '编辑 api-http-fixture', exact: true }).click()
    await expect(page.locator('#service-endpoint')).toHaveValue(base)
    await expect(page.locator('#service-clientId')).toHaveValue('fixture-client')
    await expect(page.locator('#service-clientSecret')).toHaveCount(0)
    await page.locator('#service-endpoint').fill(base + '/')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.service-drawer')).toHaveCount(0)
    const editedHttp = (await (await request.get('/api/resources/' + id, { headers })).json()).data
    expect(editedHttp.config.endpoint).toBe(base + '/')

    const first = await create('API lifecycle', {
      resourceId: id,
      method: 'POST',
      path: '/ok',
      body: { active: false, id: '9223372036854775807' },
      headers: { 'X-Task': 'task-secret' },
      successCondition: { expression: '$.ok', operator: 'EQ', expected: 'true' },
      extractors: [{ variable: 'resultId', expression: '$.id' }],
    })
    expect(seen).toHaveLength(0)
    const task = (await (await request.get('/api/tasks/' + first.taskId, { headers })).json()).data
    expect(task.headerNames).toEqual(['X-Task'])
    expect(task.pluginConfig).not.toContain('task-secret')
    const update = {
      name: 'API lifecycle',
      pluginType: 'API_CALL',
      enabled: true,
      pluginConfig: task.pluginConfig,
      preservedHeaderNames: ['X-Task'],
      headerSourceDefinitionId: first.definitionId,
    }
    const publication = await request.put('/api/tasks/' + first.taskId + '?expectedRevision=1', {
      headers,
      data: update,
    })
    expect(publication.status(), await publication.text()).toBe(200)
    expect(
      (
        await request.put('/api/tasks/' + first.taskId + '?expectedRevision=1', {
          headers,
          data: update,
        })
      ).status(),
    ).toBe(409)
    expect(seen).toHaveLength(0)
    await run(first.taskId, 'SUCCESS')
    expect(seen.find((r) => r.url === '/ok')).toMatchObject({
      auth: 'Bearer fixture-token',
      service: 'service-secret',
      task: 'task-secret',
      method: 'POST',
    })
    expect(JSON.parse(seen.find((r) => r.url === '/ok')!.body).active).toBe(false)
    for (const [path, status, extra] of [
      ['/fail', 'FAILED', {}],
      ['/unauth', 'FAILED', {}],
      [
        '/ok',
        'FAILED',
        { successCondition: { expression: '$.ok', operator: 'EQ', expected: 'false' } },
      ],
      ['/large', 'FAILED', { maxResponseBytes: 1024 }],
      ['/slow', 'TIMED_OUT', {}],
    ] as const) {
      const t = await create('API ' + path, { resourceId: id, method: 'GET', path, ...extra })
      await run(t.taskId, status)
    }
    expect(seen.filter((r) => r.url === '/unauth')).toHaveLength(1)
    const cancelResource = await service('api-cancel-fixture', {
      protocol: 'HTTP',
      endpoint: base,
      callTimeout: 60,
    })
    const cancelTask = await create('API cancellation', {
      resourceId: cancelResource,
      method: 'GET',
      path: '/slow',
    })
    const accepted = await request.post('/api/tasks/' + cancelTask.taskId + '/manual-trigger', {
      headers,
      data: { requestId: crypto.randomUUID(), variables: {} },
    })
    expect(accepted.status()).toBe(202)
    const cancelId = (await accepted.json()).data
    await expect
      .poll(() => seen.filter((r) => r.url === '/slow').length, { timeout: 30000 })
      .toBe(2)
    const cancelTrigger = (
      await (await request.get('/api/triggers/' + cancelId, { headers })).json()
    ).data
    const stop = await request.post('/api/instances/' + cancelTrigger.rootInstanceId + '/stop', {
      headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() },
      data: {},
    })
    expect(stop.status(), await stop.text()).toBe(200)
    await expect
      .poll(
        async () =>
          (await (await request.get('/api/triggers/' + cancelId, { headers })).json()).data.status,
        { timeout: 30000 },
      )
      .toBe('STOPPED')
    const soapId = await service('api-soap-fixture', {
      protocol: 'WEB_SERVICE',
      endpoint: base + '/soap',
      webService: {
        wsdl: base + '/wsdl',
        namespace: 'urn:fixture',
        serviceName: 'Fixture',
        portName: 'FixturePort',
      },
    })
    const soap = await create('API SOAP', {
      resourceId: soapId,
      method: 'POST',
      webService: { operation: 'Check', action: 'urn:Check', parameters: { value: 'a<&' } },
    })
    await run(soap.taskId, 'SUCCESS')
    expect(seen.find((r) => r.url === '/soap')?.body).toContain('a&lt;&amp;')
    await page.getByRole('link', { name: '服务资源', exact: true }).click()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await page.getByRole('button', { name: '编辑 api-soap-fixture', exact: true }).click()
    await expect(page.locator('#service-wsdl')).toHaveValue(base + '/wsdl')
    await expect(page.locator('#service-serviceName')).toHaveValue('Fixture')
    await page.locator('#service-endpoint').fill(base + '/soap-updated')
    await page.locator('#service-namespace').fill('urn:fixture:updated')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.service-drawer')).toHaveCount(0)
    const editedSoap = (await (await request.get('/api/resources/' + soapId, { headers })).json())
      .data
    expect(editedSoap.config).toMatchObject({
      endpoint: base + '/soap-updated',
      namespace: 'urn:fixture:updated',
      portName: 'FixturePort',
    })
    await run(soap.taskId, 'SUCCESS')
    expect(seen.find((r) => r.url === '/soap-updated')?.body).toContain('urn:fixture:updated')

    const before = seen.length
    await page.getByRole('link', { name: '任务管理', exact: true }).click()
    await page.getByRole('button', { name: '新增任务', exact: true }).click()
    await page.locator('#task-name').fill('Browser API')
    await page.locator('#task-pluginType').click()
    await page.getByRole('option', { name: /^API_CALL ·/ }).click()
    await page.locator('#task-resourceId').click()
    await page.locator('.n-base-select-option').filter({ hasText: 'api-http-fixture' }).click()
    await page.locator('#task-path').fill('/browser')
    const bodyEditor = page.locator('[data-json-context="API_CALL_HTTP"]')
    await bodyEditor.getByRole('button', { name: '高级 JSON', exact: true }).click()
    await bodyEditor.locator('.cm-content').fill('false')
    const receipt = page.waitForResponse(
      (r) => new URL(r.url()).pathname === '/api/tasks' && r.request().method() === 'POST',
    )
    await page.getByRole('button', { name: '保存', exact: true }).click()
    const createdBrowser = await receipt
    expect(createdBrowser.status()).toBe(201)
    const browserId = (await createdBrowser.json()).data.taskId
    await expect(page.locator('.task-drawer')).toHaveCount(0)
    expect(seen).toHaveLength(before)
    await run(browserId, 'SUCCESS')
    expect(seen.find((r) => r.url === '/browser')?.body).toBe('false')
  } finally {
    server.closeAllConnections()
    await new Promise<void>((resolve, reject) => server.close((e) => (e ? reject(e) : resolve())))
  }
}
