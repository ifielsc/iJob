import { expect } from '@playwright/test'
import type { Page, APIRequestContext } from '@playwright/test'
import { spawn } from 'node:child_process'
import { join } from 'node:path'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'

export async function verifyScriptTasks(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
) {
  const create = async (name: string, config: Record<string, unknown>, timeout = 10) => {
    const response = await request.post('/api/tasks', {
      headers,
      data: {
        name,
        pluginType: 'SCRIPT',
        pluginConfig: JSON.stringify(config),
        enabled: true,
        timeout,
        retryTimes: 0,
        retryInterval: 0,
      },
    })
    expect(response.status(), await response.text()).toBe(201)
    return (await response.json()).data.taskId as string
  }
  const run = async (id: string, status: string, output?: string) => {
    const accepted = await request.post('/api/tasks/' + id + '/manual-trigger', {
      headers,
      data: { requestId: crypto.randomUUID(), variables: {} },
    })
    expect(accepted.status()).toBe(202)
    const triggerId = (await accepted.json()).data
    let trigger: { status: string; rootInstanceId: string }
    await expect
      .poll(
        async () => {
          trigger = (await (await request.get('/api/triggers/' + triggerId, { headers })).json())
            .data
          return trigger.status
        },
        { timeout: 30000 },
      )
      .toBe(status)
    if (output) {
      const logs = (
        await (
          await request.get('/api/instances/' + trigger!.rootInstanceId + '/logs', { headers })
        ).json()
      ).data
      expect(JSON.stringify(logs)).toContain(output)
    }
  }
  await page.getByRole('link', { name: '任务管理', exact: true }).click()
  await page.getByRole('button', { name: '新增任务', exact: true }).click()
  await page.locator('#task-name').fill('Local script browser')
  await page.locator('#task-pluginType').click()
  await page.getByRole('option', { name: /^SCRIPT ·/ }).click()
  await page.locator('#task-engine').click()
  await page.locator('.n-base-select-option').filter({ hasText: 'POWERSHELL' }).click()
  await page.locator('#task-script').fill('Write-Output "local-browser-success"')
  const created = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/tasks' && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const localId = (await (await created).json()).data.taskId
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  expect(
    (
      await (
        await request.get('/api/triggers?targetType=TASK&targetId=' + localId, { headers })
      ).json()
    ).data.total,
  ).toBe(0)
  await page.getByRole('button', { name: '执行 Local script browser', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('POWERSHELL')
  await expect(page.locator('.n-dialog')).toContainText('本地 Worker')
  await page.locator('.n-dialog').getByRole('button', { name: '开始执行', exact: true }).click()
  await expect(page.locator('.task-results').getByRole('status')).toHaveText('执行成功', {
    timeout: 30000,
  })
  await expect(page.locator('.task-results')).toContainText('local-browser-success')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '关闭', exact: true }).click()
  await run(
    await create('Local failure', {
      engine: 'BAT',
      script: '@echo off\r\necho local-failure\r\nexit /b 7',
    }),
    'FAILED',
    'local-failure',
  )
  await run(
    await create('Local timeout', { engine: 'POWERSHELL', script: 'Start-Sleep -Seconds 8' }, 1),
    'TIMED_OUT',
  )

  const fixture = spawn(
    process.env.IJOB_TEST_PYTHON || 'python',
    ['tools/ssh-fixture.py', process.env.IJOB_TEST_DIRECTORY!],
    {
      windowsHide: true,
      env: {
        ...process.env,
        IJOB_TEST_SH:
          process.env.IJOB_TEST_SH || (process.platform === 'win32' ? 'sh.exe' : '/bin/sh'),
      },
      stdio: ['ignore', 'pipe', 'ignore'],
    },
  )
  try {
    const address = await new Promise<{ port: number; knownHosts: string }>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('SSH fixture readiness timed out')), 15000)
      let text = ''
      fixture.stdout.on('data', (chunk) => {
        text += String(chunk)
        if (text.includes('\n')) {
          clearTimeout(timer)
          resolve(JSON.parse(text.trim()))
        }
      })
      fixture.once('error', (error) => {
        clearTimeout(timer)
        reject(error)
      })
      fixture.once('exit', () => {
        clearTimeout(timer)
        reject(new Error('SSH fixture exited'))
      })
    })
    const authFile = join(process.env.IJOB_TEST_DIRECTORY!, 'ssh-auth-attempts')
    const authCount = () =>
      existsSync(authFile) ? readFileSync(authFile, 'utf8').trim().split('\n').length : 0
    if (!(await page.getByRole('link', { name: 'SSH 资源', exact: true }).isVisible()))
      await page.getByRole('button', { name: '资源管理', exact: true }).click()
    await page.getByRole('link', { name: 'SSH 资源', exact: true }).click()
    await page.getByRole('button', { name: '新增 SSH 资源', exact: true }).click()
    for (const [key, value] of Object.entries({
      name: 'Isolated SSH',
      host: '127.0.0.1',
      port: String(address.port),
      username: 'fixture',
      password: process.env.IJOB_TEST_PASSWORD!,
    }))
      await page.locator('#ssh-' + key).fill(value)
    const resourceCreated = page.waitForResponse(
      (r) => new URL(r.url()).pathname === '/api/resources' && r.request().method() === 'POST',
    )
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.n-dialog')).toContainText('确认 SSH 主机身份')
    expect(authCount()).toBe(0)
    await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
    await expect(page.locator('.n-dialog')).toHaveCount(0)
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await page.locator('.n-dialog').getByRole('button', { name: '确认信任', exact: true }).click()
    const resourceResponse = await resourceCreated
    expect(resourceResponse.status()).toBe(201)
    const sshResourceId = (await resourceResponse.json()).data
    await expect(page.locator('.ssh-drawer')).toHaveCount(0)
    await page.getByRole('button', { name: '编辑 Isolated SSH', exact: true }).click()
    await expect(page.locator('#ssh-host')).toHaveValue('127.0.0.1')
    await expect(page.locator('#ssh-password')).toHaveCount(0)
    await page.locator('#ssh-description').fill('Preserve saved credentials')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.ssh-drawer')).toHaveCount(0)
    const connection = await request.post('/api/resources/' + sshResourceId + '/connection-test', {
      headers,
    })
    expect(connection.ok(), await connection.text()).toBe(true)
    expect((await connection.json()).data.successful).toBe(true)
    const detail = (
      await (await request.get('/api/resources/' + sshResourceId, { headers })).json()
    ).data
    expect(detail.config).not.toHaveProperty('password')
    expect(detail.config).not.toHaveProperty('hostKey')
    expect(detail.config.hostKeyVerification).toBe('MANAGED')
    expect(detail.config.hostKeyFingerprint).toMatch(/^SHA256:/)
    expect(detail.config.passwordConfigured).toBe(true)
    const shellId = await create('SSH Shell fixture', {
      executionMode: 'SSH',
      sshResourceId,
      engine: 'SHELL',
      script: 'echo "ssh-success:$MODE"',
      env: { MODE: 'isolated' },
    })
    await run(shellId, 'SUCCESS', 'ssh-success:isolated')
    await run(
      await create('SSH Python fixture', {
        executionMode: 'SSH',
        sshResourceId,
        engine: 'PYTHON',
        interpreter: 'python',
        script: 'print("ssh-python-success")',
      }),
      'SUCCESS',
      'ssh-python-success',
    )
    await run(
      await create('SSH failure fixture', {
        executionMode: 'SSH',
        sshResourceId,
        engine: 'SHELL',
        script: 'echo ssh-failure; exit 7',
      }),
      'FAILED',
      'ssh-failure',
    )
    const context = (
      await (await request.get('/api/tasks/' + shellId + '/execution-context', { headers })).json()
    ).data
    expect(context).toMatchObject({
      executionMode: 'SSH',
      engine: 'SHELL',
      resourceName: 'Isolated SSH',
    })
    expect(context).not.toHaveProperty('script')
    // Existing file-based clients retain strict verification without changing their payloads.
    const legacy = await request.post('/api/resources', {
      headers,
      data: {
        name: 'Legacy SSH',
        code: 'legacy-ssh',
        type: 'SSH',
        enabled: true,
        sshConfig: {
          host: '127.0.0.1',
          port: address.port,
          username: 'fixture',
          password: process.env.IJOB_TEST_PASSWORD,
          knownHosts: address.knownHosts,
        },
      },
    })
    expect(legacy.status(), await legacy.text()).toBe(201)
    const legacyId = (await legacy.json()).data
    expect(
      (
        await (
          await request.post('/api/resources/' + legacyId + '/connection-test', { headers })
        ).json()
      ).data.successful,
    ).toBe(true)
    const beforeRotation = authCount()
    writeFileSync(join(process.env.IJOB_TEST_DIRECTORY!, 'ssh-rotate'), 'rotate')
    const changed = await request.post('/api/resources/' + sshResourceId + '/connection-test', {
      headers,
    })
    expect(changed.status(), await changed.text()).toBe(409)
    expect(authCount()).toBe(beforeRotation)
    await run(shellId, 'FAILED')
    expect(authCount()).toBe(beforeRotation)
    await page.getByRole('button', { name: '编辑 Isolated SSH', exact: true }).click()
    await page.getByRole('button', { name: '重新核对指纹', exact: true }).click()
    await expect(page.locator('.n-dialog')).toContainText('SSH 主机身份发生变化')
    await expect(page.locator('.n-dialog')).toContainText(detail.config.hostKeyFingerprint)
    await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
    await expect(page.locator('.n-dialog')).toHaveCount(0)
    expect(
      (await (await request.get('/api/resources/' + sshResourceId, { headers })).json()).data.config
        .hostKeyFingerprint,
    ).toBe(detail.config.hostKeyFingerprint)
    await page.getByRole('button', { name: '重新核对指纹', exact: true }).click()
    await page
      .locator('.n-dialog')
      .getByRole('button', { name: '确认信任新密钥', exact: true })
      .click()
    expect(authCount()).toBe(beforeRotation)
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.ssh-drawer')).toHaveCount(0)
    expect(
      (
        await (
          await request.post('/api/resources/' + sshResourceId + '/connection-test', { headers })
        ).json()
      ).data.successful,
    ).toBe(true)
    await run(shellId, 'SUCCESS', 'ssh-success:isolated')
  } finally {
    fixture.kill()
    if (fixture.exitCode === null)
      await new Promise<void>((resolve) => fixture.once('exit', () => resolve()))
  }
}
