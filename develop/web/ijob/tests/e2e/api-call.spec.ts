import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
const task = {
  id: '71',
  name: 'Demo API',
  pluginType: 'API_CALL',
  enabled: true,
  currentRevision: '3',
  currentTaskDefinitionId: '73',
  groups: [],
  retryTimes: 0,
  retryInterval: 0,
  timeout: null,
  description: '',
  headerNames: ['X-Token'],
  pluginConfig:
    '{"resourceId":"81","method":"POST","path":"/orders","body":{"id":9223372036854775807,"active":false}}',
}
const resource = {
  id: '81',
  name: 'Demo Service',
  code: 'demo-service',
  type: 'SERVICE',
  enabled: true,
  description: '',
  config: {
    protocol: 'HTTP',
    authType: 'OAUTH2_CLIENT_CREDENTIALS',
    endpoint: 'https://api.example.invalid',
    oauth2TokenEndpoint: 'https://auth.example.invalid/token',
    oauth2ClientId: 'fixture-client',
    oauth2Scopes: 'orders.read',
    endpointConfigured: true,
    headerNames: ['X-Default'],
    webServiceConfigured: false,
    oauth2Configured: true,
    oauth2TokenEndpointConfigured: true,
    oauth2ClientIdConfigured: true,
    oauth2ClientSecretConfigured: true,
    oauth2ScopesConfigured: true,
    oauth2AudienceConfigured: false,
    oauth2ClientAuthenticationMethod: 'CLIENT_SECRET_BASIC',
    oauth2RefreshSkewSeconds: 30,
    connectTimeout: 10,
    readTimeout: 30,
    writeTimeout: 30,
    callTimeout: 60,
  },
}
async function setup(page: Page, operator = false, currentTask = task) {
  const writes: { path: string; body: Record<string, unknown> }[] = []
  await page.route(
    /\/api\/(?:auth|tasks|resources|triggers|instances|parameter-templates)(?:[/?]|$)/,
    async (route) => {
      const req = route.request(),
        path = new URL(req.url()).pathname
      const ok = (data: unknown, status = 200) =>
        route.fulfill({ status, json: { success: true, code: status, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'api-demo',
          username: 'demo-admin',
          displayName: 'Demo',
          roleCode: operator ? 'OPERATOR' : 'ADMIN',
          permissions: operator
            ? ['DEFINITION_READ', 'SUMMARY_READ', 'EXECUTION_CONTROL']
            : [
                'DEFINITION_READ',
                'SUMMARY_READ',
                'SENSITIVE_READ',
                'CONFIGURATION_WRITE',
                'EXECUTION_CONTROL',
              ],
          mustChangePassword: false,
        })
      if (req.method() !== 'GET') {
        writes.push({ path, body: req.postDataJSON() ?? {} })
        if (path.endsWith('manual-trigger')) return ok('91', 202)
        return ok(
          path.startsWith('/api/tasks')
            ? { taskId: '71', definitionId: '74', revision: '4' }
            : '81',
          req.method() === 'POST' ? 201 : 200,
        )
      }
      if (path === '/api/tasks') return ok({ list: [currentTask], total: 1 })
      if (path === '/api/parameter-templates') return ok({ list: [], total: 0 })
      if (path.startsWith('/api/parameter-templates/bindings/')) return ok(null)
      if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
      if (path === '/api/tasks/71') return ok(currentTask)
      if (path.endsWith('/execution-context'))
        return ok({
          pluginType: 'API_CALL',
          serviceResourceId: '81',
          resourceName: 'Demo Service',
          protocol: 'HTTP',
          method: 'POST',
          revision: '3',
        })
      if (path === '/api/resources')
        return ok({
          list: [resource, { ...resource, id: '82', name: 'Demo SOAP' }],
          total: 2,
          pageNum: 1,
          pageSize: 20,
          pages: 1,
        })
      if (path === '/api/resources/81') return ok(resource)
      if (path === '/api/resources/82')
        return ok({
          ...resource,
          id: '82',
          name: 'Demo SOAP',
          config: {
            ...resource.config,
            protocol: 'WEB_SERVICE',
            authType: 'NONE',
            webServiceConfigured: true,
            wsdl: 'https://soap.example.invalid?wsdl',
            namespace: 'urn:orders',
            serviceName: 'Orders',
            portName: 'OrdersPort',
          },
        })
      if (path === '/api/triggers') return ok({ list: [], total: 0 })
      return ok({ id: '91', status: 'FAILED', displayMessage: 'Fixture' })
    },
  )
  return writes
}
async function select(page: Page, id: string, label: string) {
  await page.locator('#' + id).click()
  await page.locator('.n-base-select-option').filter({ hasText: label }).click()
}

test('nested arrays and omitted fields retain draft values through mode switching', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.route('**/api/tasks/71', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          ...task,
          pluginConfig:
            '{"resourceId":"81","method":"POST","path":"/orders","body":{"tags":["nightly",false,0],"optional":null}}',
        },
      },
    }),
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
  const form = page.locator('[data-json-context="API_CALL_HTTP"]')
  await form.getByRole('checkbox', { name: '传入 optional', exact: true }).uncheck()
  const nested = form.locator('[data-json-context^="API_CALL_HTTP-array-"]')
  await nested.getByRole('button', { name: '3', exact: true }).click()
  await nested.getByRole('textbox', { name: '值', exact: true }).fill('9007199254740993')
  await form
    .locator(':scope > .json-toolbar')
    .getByRole('button', { name: '高级 JSON', exact: true })
    .click()
  await expect(form.locator('.cm-content')).toHaveText(
    '{"tags":["nightly",false,9007199254740993]}',
  )
  await form
    .locator(':scope > .json-toolbar')
    .getByRole('button', { name: '表单', exact: true })
    .click()
  await expect(form.getByRole('checkbox', { name: '传入 optional', exact: true })).not.toBeChecked()
  await form.getByRole('checkbox', { name: '传入 optional', exact: true }).check()
  await nested.scrollIntoViewIfNeeded()
  await page.screenshot({ path: info.outputPath('nested-array.png'), animations: 'disabled' })
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body.pluginConfig).toContain(
    '"tags":["nightly",false,9007199254740993],"optional":null',
  )
})

test('typed body retains partial input, prevents accidental submit and preserves exact values', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.setViewportSize({ width: 1280, height: 640 })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
  const form = page.locator('[data-json-context="API_CALL_HTTP"]')
  await form.getByRole('button', { name: 'id', exact: true }).click()
  const input = form.getByRole('textbox', { name: '值', exact: true })
  await input.fill('-')
  await expect(input).toHaveValue('-')
  await expect(form.getByRole('alert')).toBeVisible()
  await input.fill('9007199254740993')
  await form.getByRole('button', { name: 'active', exact: true }).click()
  await expect(form).toContainText('false')
  expect(writes).toHaveLength(0)
  await form.getByRole('button', { name: '高级 JSON', exact: true }).click()
  await expect(form.locator('.cm-content')).toContainText('9007199254740993')
  await form.getByRole('button', { name: '表单', exact: true }).click()
  await form.getByRole('button', { name: 'id', exact: true }).click()
  await expect(input).toHaveValue('9007199254740993')
  await page.screenshot({ path: info.outputPath('json-object-compact.png'), fullPage: true })
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.body.pluginConfig).toContain('9007199254740993')
  expect(writes[0]!.body.pluginConfig).toContain('"active":false')
})
test('API draft keeps JSON and undo across enlarged editor and protocol switches, preserves headers and publishes only', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
  await page
    .locator('[data-json-context="API_CALL_HTTP"]')
    .getByRole('button', { name: '高级 JSON', exact: true })
    .click()
  const code = page.locator('[data-json-context="API_CALL_HTTP"] .cm-content')
  await expect(code).toContainText('9223372036854775807')
  await code.press('Control+End')
  await code.pressSequentially(' ')
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  await expect(page.locator('dialog')).toContainText('请求体（JSON）')
  await page.locator('dialog .cm-content').press('Control+z')
  await page.getByRole('button', { name: '完成编辑', exact: true }).click()
  await select(page, 'task-resourceId', 'Demo SOAP')
  await page.locator('#task-operation').fill('Check')
  await select(page, 'task-resourceId', 'Demo Service')
  await page
    .locator('[data-json-context="API_CALL_HTTP"]')
    .getByRole('button', { name: '高级 JSON', exact: true })
    .click()
  await expect(code).toContainText('9223372036854775807')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
  expect(writes[0]!.body).toMatchObject({
    preservedHeaderNames: ['X-Token'],
    headerSourceDefinitionId: '73',
  })
  expect(writes[0]!.body.pluginConfig).toContain('9223372036854775807')
  expect(JSON.parse(writes[0]!.body.pluginConfig as string).body.active).toBe(false)
})
test('SERVICE edit changes public configuration and retains hidden OAuth secret, explicitly removes header and never tests on save', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources/services')
  await page.getByRole('button', { name: '编辑 Demo Service', exact: true }).click()
  await expect(page.locator('#service-clientSecret')).toHaveCount(0)
  await expect(page.locator('#service-endpoint')).toHaveValue('https://api.example.invalid')
  await page.locator('#service-endpoint').fill('https://api.example.invalid/v2')
  await expect(page.locator('#service-clientId')).toHaveValue('fixture-client')
  await page.locator('#service-name').fill('Renamed service')
  await page.getByRole('button', { name: '移除 1', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.service-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
  expect(writes[0]!.body.serviceConfig).toMatchObject({
    preserveExisting: true,
    preservedHeaderNames: [],
    headers: {},
    oauth2: { clearScopes: false },
  })
  expect(writes[0]!.body.serviceConfig).toHaveProperty('endpoint', 'https://api.example.invalid/v2')
  expect((writes[0]!.body.serviceConfig as { oauth2: object }).oauth2).not.toHaveProperty(
    'clientSecret',
  )
})
test('operator confirms safe API context without detail and only triggers after confirmation', async ({
  page,
}) => {
  const writes = await setup(page, true),
    reads: string[] = []
  page.on('request', (r) => {
    if (r.method() === 'GET') reads.push(new URL(r.url()).pathname)
  })
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '编辑 Demo API', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '执行 Demo API', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('HTTP / POST')
  await expect(page.locator('.n-dialog')).toContainText('Demo Service (#81)')
  expect(reads).not.toContain('/api/tasks/71')
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.path).toBe('/api/tasks/71/manual-trigger')
})
test('metadata failure prevents publishing and recoverable retry retains JSON', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.route('**/api/resources/81', (route) =>
    route.fulfill({ status: 503, json: { success: false, code: 503, message: 'Unavailable' } }),
  )
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
  await expect(page.getByRole('button', { name: '重新读取资源', exact: true })).toBeVisible()
  await page
    .locator('#task-name')
    .fill((await page.locator('#task-name').inputValue()) + ' updated')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect(writes).toHaveLength(0)
  await page.unroute('**/api/resources/81')
  await page.getByRole('button', { name: '重新读取资源', exact: true }).click()
  await expect(page.getByRole('button', { name: '重新读取资源', exact: true })).toHaveCount(0)
  await expect(page.locator('[data-json-context="API_CALL_HTTP"]')).toContainText(
    '9223372036854775807',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
})
for (const theme of ['light', 'dark'] as const)
  for (const locale of ['zh-CN', 'en-US'] as const) {
    test('API and service visual ' + theme + ' ' + locale, async ({ page }, info) => {
      const en = locale === 'en-US',
        errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.goto('/tasks')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo API', exact: true })
        .click()
      await expect(page.locator('[data-json-context="API_CALL_HTTP"]')).toBeVisible()
      expect((await page.locator('.task-drawer').boundingBox())!.width).toBe(640)
      await expect(
        page.getByRole('table', { name: en ? 'Request headers' : '请求头', exact: true }),
      ).toBeVisible()
      await page.getByText(en ? 'Advanced settings' : '高级配置', { exact: true }).click()
      await page
        .getByRole('button', { name: en ? 'Add extraction' : '添加提取', exact: true })
        .click()
      await page.locator('#task-extractor-0').fill('orderId')
      await page.locator('#task-extractor-expression-0').fill('$.id')
      await page.locator('#task-extractor-0').scrollIntoViewIfNeeded()
      await page.screenshot({ animations: 'disabled', path: info.outputPath('api-advanced.png') })
      await page.getByText(en ? 'Advanced settings' : '高级配置', { exact: true }).click()
      await page.locator('#task-name').scrollIntoViewIfNeeded()
      await page.screenshot({ animations: 'disabled', path: info.outputPath('api-desktop.png') })
      await page
        .locator('[data-json-context="API_CALL_HTTP"]')
        .getByRole('button', { name: en ? 'Advanced JSON' : '高级 JSON', exact: true })
        .click()
      await page
        .getByRole('button', { name: en ? 'Expand editor' : '放大编辑', exact: true })
        .click()
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('api-json-editor.png'),
      })
      await page
        .getByRole('button', { name: en ? 'Done editing' : '完成编辑', exact: true })
        .click()
      await select(page, 'task-resourceId', 'Demo SOAP')
      await page.locator('#task-operation').fill('GetOrder')
      await page
        .getByRole('button', { name: en ? 'Add parameter' : '添加参数', exact: true })
        .click()
      await page.locator('#task-parameter-0').fill('orderId')
      await page.locator('#task-parameter-value-0').fill('123')
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('api-soap-mobile.png'),
      })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      await page.goto('/resources/services')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Demo Service', exact: true })
        .click()
      await expect(page.locator('.service-drawer')).toBeVisible()
      await page.screenshot({ animations: 'disabled', path: info.outputPath('service-mobile.png') })
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('service-desktop.png'),
      })
      expect((await page.locator('.service-drawer').boundingBox())!.width).toBe(520)
      await page.getByText(en ? 'Advanced settings' : '高级配置', { exact: true }).click()
      await page.locator('#service-description').scrollIntoViewIfNeeded()
      const widths = await page
        .locator('.service-fields-grid .n-input-number')
        .evaluateAll((els) => els.map((el) => el.getBoundingClientRect().width))
      expect(widths).toHaveLength(4)
      expect(Math.max(...widths) - Math.min(...widths)).toBeLessThan(1)
      await page.screenshot({
        animations: 'disabled',
        path: info.outputPath('service-advanced.png'),
      })
      expect(errors).toEqual([])
    })
  }
test('API pending publish blocks double submit and close; conflict keeps draft and safe latest', async ({
  page,
}) => {
  await setup(page)
  let release: () => void = () => {},
    count = 0
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/tasks/71?*', async (route) => {
    count++
    await pending
    await route.fulfill({ status: 409, json: { success: false, code: 409 } })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
  await page
    .locator('[data-json-context="API_CALL_HTTP"]')
    .getByRole('button', { name: '高级 JSON', exact: true })
    .click()
  await page.locator('[data-json-context="API_CALL_HTTP"] .cm-content').fill('[false,0]')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-footer button').last()).toBeDisabled()
  await expect(
    page.locator('.task-drawer').getByRole('button', { name: '关闭', exact: true }),
  ).toBeDisabled()
  expect(count).toBe(1)
  release()
  await expect(page.getByRole('button', { name: '读取最新记录', exact: true })).toBeVisible()
  await expect(page.locator('[data-json-context="API_CALL_HTTP"] .cm-content')).toHaveText(
    '[false,0]',
  )
  await page.getByRole('button', { name: '读取最新记录', exact: true }).click()
  await expect(page.locator('.task-conflict pre')).toContainText('Demo API')
  await expect(page.locator('.task-conflict pre')).not.toContainText('task-secret')
})

test('SOAP resource displays editable fields and persists their changes', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/resources/services')
  await page.getByRole('button', { name: '编辑 Demo SOAP', exact: true }).click()
  await expect(page.locator('#service-wsdl')).toHaveValue('https://soap.example.invalid?wsdl')
  await expect(page.locator('#service-serviceName')).toHaveValue('Orders')
  await expect(page.locator('#service-portName')).toHaveValue('OrdersPort')
  await page.locator('#service-namespace').fill('urn:orders:v2')
  await page.locator('#service-endpoint').fill('https://soap.example.invalid/v2')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.service-drawer')).toHaveCount(0)
  expect(writes[0]!.body.serviceConfig).toMatchObject({
    endpoint: 'https://soap.example.invalid/v2',
    webService: { namespace: 'urn:orders:v2', serviceName: 'Orders', portName: 'OrdersPort' },
  })
})

test('remote stop keeps independent draft, validates path and publishes explicit control settings', async ({
  page,
}, info) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
  await page.getByText('高级配置', { exact: true }).click()
  const toggle = page.locator('#task-remoteStop-enabled')
  await expect(toggle).toHaveAttribute('aria-checked', 'false')
  await toggle.click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect(writes).toHaveLength(0)
  await expect(page.locator('#task-remoteStop-path')).toBeFocused()
  await page.locator('#task-remoteStop-path').fill('/cancel')
  await page.locator('#task-remoteStop-timeoutSeconds').fill('7')
  const body = page.locator('[data-json-context="API_CALL_REMOTE_STOP"]')
  await body.getByRole('button', { name: '高级 JSON', exact: true }).click()
  await body
    .locator('.cm-content')
    .fill('{"operationId":"${ijob.apiCall.operationId}","id":9223372036854775807}')
  await toggle.click()
  await toggle.click()
  await expect(page.locator('#task-remoteStop-path')).toHaveValue('/cancel')
  await select(page, 'task-resourceId', 'Demo SOAP')
  await page.locator('#task-operation').fill('Work')
  await page.locator('#task-remoteStop-operation').fill('Cancel')
  await select(page, 'task-resourceId', 'Demo Service')
  await expect(page.locator('#task-remoteStop-path')).toHaveValue('/cancel')
  await page.locator('.remote-stop-fields').screenshot({ path: info.outputPath('remote-stop.png') })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  const raw = writes[0]!.body.pluginConfig as string
  expect(raw).toContain('9223372036854775807')
  expect(JSON.parse(raw).remoteStop).toMatchObject({
    path: '/cancel',
    method: 'POST',
    timeoutSeconds: 7,
    body: { operationId: '${ijob.apiCall.operationId}' },
  })
  expect(writes[0]!.body.preservedRemoteStopHeaderNames).toEqual([])
})
for (const count of [5, 100]) {
  test(`JSON form ${count} rows scrolls and keeps added, moved and copied values visible`, async ({
    page,
  }) => {
    const body = Object.fromEntries(
      Array.from({ length: count }, (_, i) => [`field${i + 1}`, `value${i + 1}`]),
    )
    const writes = await setup(page, false, {
      ...task,
      pluginConfig: JSON.stringify({ resourceId: '81', method: 'POST', path: '/orders', body }),
    })
    await page.goto('/tasks')
    await page.getByRole('button', { name: '编辑 Demo API', exact: true }).click()
    const form = page.locator('[data-json-context="API_CALL_HTTP"]')
    const scroll = form.locator('.json-table-wrap')
    await expect(scroll.locator('tbody tr')).toHaveCount(count)
    expect(await scroll.evaluate((el) => el.scrollHeight > el.clientHeight + 1)).toBe(count > 6)
    if (count > 6) await expect(scroll.locator('th').first()).toHaveCSS('position', 'sticky')
    await form.getByRole('button', { name: '+ 添加字段', exact: true }).click()
    await expect(scroll.locator('tr.selected')).toBeInViewport()
    expect(
      await scroll.evaluate((el) => el.scrollHeight <= el.clientHeight + el.scrollTop + 3),
    ).toBe(true)
    await form.getByRole('textbox', { name: '值', exact: true }).fill('added')
    await form.getByRole('button', { name: '↑ 上移', exact: true }).click()
    await expect(scroll.locator('tr.selected')).toBeInViewport()
    await form.getByRole('button', { name: '复制', exact: true }).click()
    await expect(scroll.locator('tr.selected')).toBeInViewport()
    await form.getByRole('button', { name: '高级 JSON', exact: true }).click()
    await expect(form.locator('.cm-content')).toContainText('added')
    await form.getByRole('button', { name: '表单', exact: true }).click()
    await expect(scroll.locator('tbody tr')).toHaveCount(count + 2)
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect.poll(() => writes.length).toBe(1)
    const saved = JSON.parse(writes[0]!.body.pluginConfig as string).body
    expect(saved.field1).toBe('value1')
    expect(saved[`field${count + 1}`]).toBe('added')
    expect(saved[`field${count + 1}_copy`]).toBe('added')
  })
}
