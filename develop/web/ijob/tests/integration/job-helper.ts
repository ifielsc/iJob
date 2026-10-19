// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, type Page, type APIRequestContext } from '@playwright/test'
export async function verifyJobs(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
  taskId: string,
) {
  const get = async (path: string) => {
    const response = await request.get(path, { headers })
    expect(response.ok()).toBe(true)
    return (await response.json()).data
  }
  const meta = await get('/api/jobs/metadata')
  expect(meta.maxNodes).toBe(500)
  const original = await get('/api/tasks/' + taskId)
  const source = await request.post('/api/tasks', {
    headers,
    data: {
      ...original,
      name: 'Job source v1',
      groups: [],
      pluginConfig: JSON.stringify({ ...JSON.parse(original.pluginConfig), script: 'SELECT 1;' }),
    },
  })
  expect(source.status()).toBe(201)
  const sourceId = (await source.json()).data.taskId
  const policy = { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' }
  const task = (nodeId: string) => ({
    nodeId,
    name: nodeId,
    kind: 'EXECUTION',
    nodeType: 'TASK',
    refId: sourceId,
    policy,
  })
  const body = {
    name: 'Isolated DAG',
    enabled: true,
    groups: ['job_daily'],
    nodes: [
      task('read'),
      { nodeId: 'gate', name: 'gate', kind: 'GATEWAY', gateway: { mode: 'EXCLUSIVE' } },
      task('yes'),
      task('no'),
    ],
    edges: [
      { fromNodeId: 'read', toNodeId: 'gate' },
      {
        fromNodeId: 'gate',
        toNodeId: 'yes',
        routeKey: 'match',
        priority: 10,
        defaultRoute: false,
        condition: { op: 'EQ', left: { var: 'input.value' }, right: { literal: '__large__' } },
      },
      {
        fromNodeId: 'gate',
        toNodeId: 'no',
        routeKey: 'fallback',
        priority: 100,
        defaultRoute: true,
      },
    ],
    layout: {
      version: 1,
      positions: {
        read: { x: 30, y: 80 },
        gate: { x: 320, y: 60 },
        yes: { x: 570, y: 20 },
        no: { x: 570, y: 200 },
      },
    },
  }
  const raw = JSON.stringify(body).replace('"__large__"', '9223372036854775807')
  const jsonHeaders = { ...headers, 'Content-Type': 'application/json' }
  const before = await get('/api/jobs')
  const preflight = await request.post('/api/jobs/validate', {
    headers: jsonHeaders,
    data: '{"definition":' + raw + '}',
  })
  expect((await preflight.json()).data.valid).toBe(true)
  expect((await get('/api/jobs')).total).toBe(before.total)
  const invalid = {
    ...body,
    edges: body.edges.map((e) =>
      e.condition ? { ...e, condition: { op: 'AND', operands: [] } } : e,
    ),
  }
  for (const language of ['zh-CN', 'en-US']) {
    const result = (
      await (
        await request.post('/api/jobs/validate', {
          headers: { ...headers, 'Accept-Language': language },
          data: { definition: invalid },
        })
      ).json()
    ).data
    expect(result.valid).toBe(false)
    expect(result.issues[0].errorCode).toBe('IJOB-JOB-CONDITION-GROUP')
    expect(result.issues[0].message).toBe(
      language === 'zh-CN' ? 'AND/OR至少需要两个子条件' : 'AND/OR require at least two conditions',
    )
  }
  const created = await request.post('/api/jobs', { headers: jsonHeaders, data: raw })
  expect(created.status()).toBe(201)
  const pub = (await created.json()).data
  expect(typeof pub.jobId).toBe('string')
  expect(pub.revision).toBe('1')
  const firstResponse = await request.get('/api/jobs/' + pub.jobId, { headers })
  const firstRaw = await firstResponse.text()
  expect(firstRaw).toContain('"literal":9223372036854775807')
  const first = JSON.parse(firstRaw).data
  expect(first.layout.positions.read).toEqual({ x: 30, y: 80 })
  expect(first.nodes[0].refName).toBe('Job source v1')
  expect(typeof first.nodes[0].id).toBe('string')
  for (const name of ['%', '_', "' OR 1=1 --"])
    expect((await get('/api/jobs?name=' + encodeURIComponent(name))).total).toBe(0)
  expect((await get('/api/jobs?name=Isolated%20DAG')).total).toBe(1)
  expect((await get('/api/jobs/groups?prefix=JOB_')).list).toContain('job_daily')
  // Dedicated safe metadata must survive non-admin projection; graph bodies remain admin-only.
  const viewerPassword = `${process.env.IJOB_TEST_PASSWORD!.slice(0, 30)}-changed-viewer`
  const user = (await get('/api/users')).list.find(
    (v: { username: string }) => v.username === 'ui-test-viewer',
  )
  for (const roleCode of ['VIEWER', 'OPERATOR']) {
    if (roleCode === 'OPERATOR')
      expect(
        (
          await request.put('/api/users/' + user.id + '/access', {
            headers,
            data: { expectedRevision: user.revision, roleCode, enabled: true },
          })
        ).ok(),
      ).toBe(true)
    const auth = (
      await (
        await request.post('/api/auth/login', {
          data: { username: user.username, password: viewerPassword },
        })
      ).json()
    ).data
    const safeHeaders = { [auth.tokenName]: auth.tokenValue }
    for (const path of [
      '/api/jobs/metadata',
      '/api/jobs/groups',
      '/api/jobs/' + pub.jobId + '/versions',
      '/api/jobs/' + pub.jobId + '/execution-context',
    ]) {
      const response = await request.get(path, { headers: safeHeaders })
      expect(response.status()).toBe(200)
      const data = (await response.json()).data
      if (path.endsWith('/versions')) {
        expect(data.list[0].definitionId).toBe(pub.definitionId)
        expect(data.list[0].nodes).toBeUndefined()
      }
      if (path.endsWith('/execution-context'))
        expect(data).toMatchObject({ name: body.name, revision: '1', enabled: true })
    }
    for (const path of [
      '/api/jobs/' + pub.jobId,
      '/api/jobs/' + pub.jobId + '/versions/' + pub.definitionId,
    ])
      expect((await request.get(path, { headers: safeHeaders })).status()).toBe(403)
    expect(
      (
        await request.post('/api/jobs/validate', {
          headers: safeHeaders,
          data: { definition: body },
        })
      ).status(),
    ).toBe(403)
  }
  const restore = (await get('/api/users')).list.find((v: { id: string }) => v.id === user.id)
  expect(
    (
      await request.put('/api/users/' + user.id + '/access', {
        headers,
        data: { expectedRevision: restore.revision, roleCode: 'VIEWER', enabled: true },
      })
    ).ok(),
  ).toBe(true)
  const currentTask = await get('/api/tasks/' + sourceId)
  expect(
    (
      await request.put('/api/tasks/' + sourceId + '?expectedRevision=1', {
        headers,
        data: { ...currentTask, name: 'Job source v2' },
      })
    ).ok(),
  ).toBe(true)
  const fixed = await get('/api/jobs/' + pub.jobId)
  expect(fixed.nodes[0]).toMatchObject({
    refName: 'Job source v1',
    refRevision: '1',
    currentRefName: 'Job source v2',
    currentRefRevision: '2',
  })
  await page.getByRole('link', { name: '作业编排', exact: true }).click()
  const row = page.locator('.job-table tbody tr').filter({ hasText: 'Isolated DAG' })
  const config = await request.post('/api/notify-configs', {
    headers,
    data: {
      name: 'Job binding fixture',
      type: 'DINGTALK',
      enabled: false,
      dingTalkConfig: { webhook: 'https://example.test/never-send', atAll: false, atMobiles: [] },
    },
  })
  expect(config.status()).toBe(201)
  const configId = (await config.json()).data
  await row.getByRole('button', { name: /更多/ }).click()
  await page.getByText('通知绑定', { exact: true }).click()
  const drawer = page.locator('.binding-drawer')
  await drawer.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#binding-config').fill('Job binding')
  await page.locator('.n-base-select-option').filter({ hasText: 'Job binding fixture' }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(
    drawer.getByRole('button', { name: '修改 Job binding fixture', exact: true }),
  ).toBeEnabled()
  const bindings = await get('/api/notify-bindings?targetType=JOB&targetId=' + pub.jobId)
  expect(bindings).toHaveLength(1)
  expect(bindings[0].targetType).toBe('JOB')
  expect((await get('/api/jobs/' + pub.jobId)).currentRevision).toBe('1')
  await drawer.getByRole('button', { name: '移除 Job binding fixture', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(drawer).toContainText('暂无记录')
  await drawer
    .locator('.n-drawer-footer')
    .getByRole('button', { name: '关闭', exact: true })
    .click()
  expect((await request.delete('/api/notify-configs/' + configId, { headers })).ok()).toBe(true)
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeDisabled()
  await page.locator('#job-name').fill('Isolated DAG v2')
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toBeEnabled()
  await page.locator('#job-groups').fill('')
  await page.getByRole('button', { name: '保存并发布', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('Job source v2')
  const saved = page.waitForResponse(
    (r) => r.request().method() === 'PUT' && r.url().includes('/api/jobs/'),
  )
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  expect((await saved).ok()).toBe(true)
  await expect(page).toHaveURL((url) => url.pathname === '/jobs/' + pub.jobId)
  expect(new URL(page.url()).searchParams.get('back')).toBe('/jobs?pageNum=1&pageSize=20')
  const latest = await get('/api/jobs/' + pub.jobId)
  expect(latest.currentRevision).toBe('2')
  expect(latest.nodes[0].refRevision).toBe('2')
  const versions = await get('/api/jobs/' + pub.jobId + '/versions?pageSize=1&pageNum=2')
  expect(versions.total).toBe(2)
  expect(versions.list[0].definitionId).toBe(pub.definitionId)
  const historical = await get('/api/jobs/' + pub.jobId + '/versions/' + pub.definitionId)
  expect(historical.current).toBe(false)
  expect(historical.nodes[0].refName).toBe('Job source v1')
  const conflict = await request.put('/api/jobs/' + pub.jobId + '?expectedRevision=1', {
    headers: jsonHeaders,
    data: raw,
  })
  expect(conflict.status()).toBe(409)
  const other = await request.post('/api/jobs', {
    headers,
    data: {
      name: 'Nested DAG',
      nodes: [
        {
          nodeId: 'nested',
          name: 'nested',
          kind: 'EXECUTION',
          nodeType: 'JOB',
          refId: pub.jobId,
          policy,
        },
      ],
      edges: [],
    },
  })
  expect(other.status()).toBe(201)
  const nested = (await other.json()).data
  expect(
    (
      await request.get('/api/jobs/' + nested.jobId + '/versions/' + pub.definitionId, { headers })
    ).status(),
  ).toBe(404)
  const accepted = await request.post('/api/jobs/' + pub.jobId + '/manual-trigger', {
    headers: jsonHeaders,
    data: '{"requestId":"stage6-gateway","variables":{"value":9223372036854775807}}',
  })
  expect(accepted.status()).toBe(202)
  const triggerId = (await accepted.json()).data
  expect(typeof triggerId).toBe('string')
  await expect
    .poll(async () => (await get('/api/triggers/' + triggerId)).status, { timeout: 30000 })
    .toBe('SUCCESS')
  const trigger = await get('/api/triggers/' + triggerId)
  expect(trigger.jobDefinitionId).toBe(latest.currentDefinitionId)
  await page.getByRole('button', { name: '历史版本', exact: true }).click()
  await expect(page.locator('.job-table tbody tr')).toHaveCount(2)
  await page
    .locator('.job-table tbody tr')
    .last()
    .getByRole('button', { name: '查看拓扑', exact: true })
    .click()
  await expect(page.locator('.vue-flow__node')).toHaveCount(4)
  await expect(page.getByRole('button', { name: '保存并发布', exact: true })).toHaveCount(0)
  expect((await request.delete('/api/jobs/' + nested.jobId, { headers })).ok()).toBe(true)
  expect((await request.delete('/api/jobs/' + pub.jobId, { headers })).ok()).toBe(true)
  expect(
    (await get('/api/jobs/' + pub.jobId + '/versions/' + pub.definitionId)).nodes[0].refRevision,
  ).toBe('1')
  expect((await request.delete('/api/tasks/' + sourceId, { headers })).ok()).toBe(true)
  expect(
    (await get('/api/jobs/' + pub.jobId + '/versions/' + pub.definitionId)).nodes[0]
      .referenceAvailable,
  ).toBe(false)
  await page.getByRole('link', { name: '任务管理', exact: true }).click()
}
