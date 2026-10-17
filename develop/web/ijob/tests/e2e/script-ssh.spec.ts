import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
const task = {
  id: '71',
  name: 'Demo script',
  pluginType: 'SCRIPT',
  enabled: true,
  currentRevision: '3',
  currentTaskDefinitionId: '73',
  groups: [],
  retryTimes: 0,
  retryInterval: 0,
  timeout: null,
  description: '',
  pluginConfig: JSON.stringify({ engine: 'SHELL', script: 'echo local', executionMode: 'LOCAL' }),
}
const resource = {
  id: '81',
  name: 'Demo SSH',
  code: 'demo-ssh',
  type: 'SSH',
  enabled: true,
  description: '',
  config: {
    host: 'localhost',
    port: 22,
    username: 'runner',
    knownHosts: '/keys/known_hosts',
    connectTimeout: 30000,
    hostConfigured: true,
    usernameConfigured: true,
    knownHostsConfigured: true,
    passwordConfigured: true,
    privateKeyConfigured: true,
    passphraseConfigured: true,
  },
}
async function setup(page: Page, operator = false) {
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
          loginId: 'script-demo',
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
        if (path === '/api/resources/ssh/host-key')
          return ok({
            host: req.postDataJSON().host,
            port: req.postDataJSON().port,
            keyType: 'ssh-ed25519',
            fingerprint: 'SHA256:' + 'A'.repeat(43),
            publicKey: 'mock-public-key',
          })
        if (path.endsWith('manual-trigger')) return ok('91', 202)
        if (path.startsWith('/api/tasks'))
          return ok(
            { taskId: '71', definitionId: '74', revision: '4' },
            req.method() === 'POST' ? 201 : 200,
          )
        return ok('81', req.method() === 'POST' ? 201 : 200)
      }
      if (path === '/api/parameter-templates') return ok({ list: [], total: 0 })
      if (path.startsWith('/api/parameter-templates/bindings/')) return ok(null)
      if (path === '/api/tasks') return ok({ list: [task], total: 1 })
      if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
      if (path === '/api/tasks/71') return ok(task)
      if (path.endsWith('/execution-context'))
        return ok({
          pluginType: 'SCRIPT',
          executionMode: 'SSH',
          engine: 'PYTHON',
          sshResourceId: '81',
          resourceName: 'Demo SSH',
          revision: '3',
        })
      if (path === '/api/resources')
        return ok({ list: [resource], total: 1, pageNum: 1, pageSize: 20, pages: 1 })
      if (path === '/api/resources/81') return ok(resource)
      if (path === '/api/triggers') return ok({ list: [], total: 0 })
      return ok({ id: '91', status: 'FAILED', displayMessage: 'Test fixture' })
    },
  )
  return writes
}
async function select(page: Page, id: string, label: string) {
  await page.locator('#' + id).click()
  await page.locator('.n-base-select-option').filter({ hasText: label }).click()
}

test('local and SSH drafts retain content and undo, publish active mode without executing', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/tasks')
  await page.getByRole('button', { name: '编辑 Demo script', exact: true }).click()
  const code = page.locator('#task-script')
  await code.press('Control+End')
  await code.press('Enter')
  await code.pressSequentially('echo second')
  await select(page, 'task-executionMode', 'SSH 远程')
  await expect(code).toHaveText('')
  await select(page, 'task-engine', 'PYTHON')
  await select(page, 'task-resourceId', 'Demo SSH')
  await page.locator('#task-interpreter').fill('python3')
  await code.fill('print("remote")')
  await select(page, 'task-executionMode', '本地 Worker')
  await expect(code).toContainText('echo second')
  await code.press('Control+z')
  await expect(code).not.toContainText('echo second')
  await code.press('Control+y')
  await expect(code).toContainText('echo second')
  await select(page, 'task-executionMode', 'SSH 远程')
  await expect(code).toContainText('print("remote")')
  await page.getByRole('button', { name: '放大编辑', exact: true }).click()
  await expect(page.locator('dialog')).toContainText('编辑脚本内容')
  await page.getByRole('button', { name: '完成编辑', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(1)
  expect(JSON.parse(writes[0]!.body.pluginConfig as string)).toMatchObject({
    executionMode: 'SSH',
    engine: 'PYTHON',
    sshResourceId: '81',
    script: 'print("remote")',
  })
})

test('operator confirms engine and SSH target without sensitive detail or premature execution', async ({
  page,
}) => {
  const writes = await setup(page, true)
  const reads: string[] = []
  page.on('request', (r) => {
    if (r.method() === 'GET') reads.push(new URL(r.url()).pathname)
  })
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '编辑 Demo script', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '执行 Demo script', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('PYTHON')
  await expect(page.locator('.n-dialog')).toContainText('Demo SSH (#81)')
  expect(reads).not.toContain('/api/tasks/71')
  expect(writes).toHaveLength(0)
  await page.locator('.n-dialog').getByRole('button', { name: '开始执行', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]!.path).toBe('/api/tasks/71/manual-trigger')
})

test('SSH editor retains all saved credentials and validates dependent explicit clears', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources/ssh')
  await page.getByRole('button', { name: '编辑 Demo SSH', exact: true }).click()
  await expect(page.locator('#ssh-host')).toHaveValue('localhost')
  await expect(page.locator('#ssh-knownHosts')).toHaveValue('/keys/known_hosts')
  await page.locator('#ssh-name').fill('Renamed SSH')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.ssh-drawer')).toHaveCount(0)
  expect(writes[0]!.body.sshConfig).toMatchObject({
    preserveExisting: true,
    clearPassword: false,
    clearPrivateKey: false,
    clearPassphrase: false,
  })
  expect(writes[0]!.body.sshConfig).not.toHaveProperty('password')
  await page.getByRole('button', { name: '编辑 Demo SSH', exact: true }).click()
  await page
    .getByRole('group', { name: '私钥文件路径', exact: true })
    .getByRole('button', { name: '清除', exact: true })
    .click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('配置私钥口令时必须保留私钥路径。', { exact: true })).toBeVisible()
  expect(writes).toHaveLength(1)
})

for (const [theme, locale] of [
  ['light', 'zh-CN'],
  ['dark', 'en-US'],
] as const) {
  test('script and SSH responsive visual ' + theme + ' ' + locale, async ({ page }, info) => {
    const en = locale === 'en-US'
    await page.addInitScript(
      ({ theme, locale }) =>
        localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
      { theme, locale },
    )
    const errors: string[] = []
    page.on('pageerror', (e) => errors.push(e.message))
    await setup(page)
    await page.goto('/tasks')
    await page
      .getByRole('button', { name: `${en ? 'Edit' : '编辑'} Demo script`, exact: true })
      .click()
    await page.getByText(en ? 'Advanced settings' : '高级配置', { exact: true }).click()
    await page
      .getByRole('button', { name: en ? 'Add variable' : '添加环境变量', exact: true })
      .click()
    await page.locator('#task-env-name-0').fill('MODE')
    await page.locator('#task-env-value-0').fill('production')
    await page.screenshot({ path: info.outputPath('script-advanced.png'), animations: 'disabled' })
    await page.getByRole('button', { name: en ? 'Expand editor' : '放大编辑', exact: true }).click()
    await page.screenshot({ path: info.outputPath('script-expanded.png'), animations: 'disabled' })
    await page.goto('/resources/ssh')
    await page
      .getByRole('button', { name: en ? 'New SSH resource' : '新增 SSH 资源', exact: true })
      .click()
    await page.locator('#ssh-name').fill('Release runner')
    await page.locator('#ssh-host').fill('10.20.0.15')
    await page.locator('#ssh-username').fill('runner')
    await expect(page.locator('#ssh-knownHosts')).toHaveCount(0)
    await page.screenshot({ path: info.outputPath('ssh-editor.png'), animations: 'disabled' })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: info.outputPath('ssh-mobile.png'), animations: 'disabled' })
    await page
      .getByRole('button', {
        name: en ? 'Get and verify fingerprint' : '获取并核对指纹',
        exact: true,
      })
      .click()
    await expect(page.locator('.n-dialog')).toContainText('SHA256:')
    await page.screenshot({
      path: info.outputPath('ssh-confirm-mobile.png'),
      animations: 'disabled',
    })
    expect(
      await page.locator('.ssh-drawer').evaluate((el) => el.scrollWidth <= el.clientWidth),
    ).toBe(true)
    expect(errors).toEqual([])
  })
}

test('managed SSH requires explicit trust before saving and probes without credentials', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources/ssh')
  await page.getByRole('button', { name: '新增 SSH 资源', exact: true }).click()
  for (const [key, value] of Object.entries({
    name: 'Managed SSH',
    host: 'localhost',
    username: 'runner',
    password: 'draft-password',
  }))
    await page.locator('#ssh-' + key).fill(value)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('确认 SSH 主机身份')
  expect(writes).toEqual([
    {
      path: '/api/resources/ssh/host-key',
      body: { host: 'localhost', port: 22, connectTimeout: 30000 },
    },
  ])
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  await expect(page.locator('#ssh-password')).toHaveValue('draft-password')
  expect(writes).toHaveLength(1)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认信任', exact: true }).click()
  await expect(page.locator('.ssh-drawer')).toHaveCount(0)
  expect(writes).toHaveLength(3)
  expect(writes[2]!.body.sshConfig).toMatchObject({
    hostKeyVerification: 'MANAGED',
    hostKey: 'mock-public-key',
  })
})

test('changing SSH address invalidates a confirmed draft and failed probe keeps input', async ({
  page,
}) => {
  const writes = await setup(page)
  await page.goto('/resources/ssh')
  await page.getByRole('button', { name: '新增 SSH 资源', exact: true }).click()
  await page.locator('#ssh-host').fill('localhost')
  await page.getByRole('button', { name: '获取并核对指纹', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认信任', exact: true }).click()
  await expect(page.locator('.ssh-host-trust')).toContainText('SHA256:')
  await page.locator('#ssh-port').fill('2222')
  await page.locator('#ssh-port').press('Tab')
  await expect(page.locator('.ssh-host-trust')).not.toContainText('SHA256:')
  await page.route('**/api/resources/ssh/host-key', (route) =>
    route.fulfill({
      status: 503,
      json: { success: false, code: 503, message: 'Host probe failed' },
    }),
  )
  await page.getByRole('button', { name: '获取并核对指纹', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.locator('#ssh-port')).toHaveValue('2222')
  expect(writes).toHaveLength(1)
})
