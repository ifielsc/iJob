import { expect, type APIRequestContext, type Page } from '@playwright/test'
export async function verifyRuntime(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
  taskId: string,
) {
  const list = (
    await (
      await request.get('/api/triggers?targetType=TASK&targetId=' + taskId, { headers })
    ).json()
  ).data.list
  const failed = list.find((r: { status: string }) => r.status === 'FAILED')
  expect(failed).toBeTruthy()
  const rootId = failed.rootInstanceId
  const tree = (
    await (
      await request.get('/api/instances/' + rootId + '/tree?includeVariables=true', { headers })
    ).json()
  ).data
  expect(typeof tree.rootInstanceId).toBe('string')
  expect(tree.nodes.some((n: { id: string }) => n.id === rootId)).toBe(true)
  await page.getByRole('link', { name: '运行中心', exact: true }).click()
  await page.getByRole('button', { name: '详情 ' + failed.id, exact: true }).click()
  await page.getByRole('link', { name: '打开运行详情', exact: true }).click()
  await expect(page.locator('.run-node')).toHaveCount(1)
  await page.getByRole('button', { name: '重试', exact: true }).click()
  const posted = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/instances/' + rootId + '/retry' &&
      r.request().method() === 'POST',
  )
  await page.locator('.n-dialog').getByRole('button', { name: '确认重试', exact: true }).click()
  const response = await posted
  expect(response.ok()).toBe(true)
  const cmd = (await response.json()).data
  expect(typeof cmd.instanceId).toBe('string')
  expect(cmd.instanceId).toBe(rootId)
  const queried = await request.get('/api/runtime-commands/' + cmd.requestId, { headers })
  expect(queried.ok()).toBe(true)
  await expect(page.locator('.run-command')).toContainText(cmd.requestId)
  // The original failing SQL version is retried, not the task's newly published version.
  await expect
    .poll(
      async () =>
        (await (await request.get('/api/instances/' + rootId, { headers })).json()).data.status,
    )
    .toBe('FAILED')
  await page.getByRole('tab', { name: '日志', exact: true }).click()
  await expect(page.locator('.run-code')).not.toBeEmpty()
  const replay = await request.post('/api/instances/' + rootId + '/retry', {
    headers: { ...headers, 'Idempotency-Key': cmd.requestId },
  })
  expect(replay.ok()).toBe(true)
  expect((await replay.json()).data.replayed).toBe(true)
  const mismatch = await request.post('/api/instances/' + rootId + '/stop', {
    headers: { ...headers, 'Idempotency-Key': cmd.requestId },
  })
  expect(mismatch.status()).toBe(409)
  expect((await request.get('/api/runtime-commands/' + cmd.requestId)).status()).toBe(401)
  await page.getByRole('link', { name: '通知管理', exact: true }).click()
  await page.getByRole('tab', { name: '发送记录', exact: true }).click()
  const send = await request.get('/api/notify-send-logs', { headers })
  expect(send.ok()).toBe(true)
  for (const row of (await send.json()).data.list) {
    expect(typeof row.id).toBe('string')
    if (row.notifyConfigId) expect(typeof row.notifyConfigId).toBe('string')
  }
  await expect(
    page.getByRole('tabpanel', { name: '发送记录', exact: true }).getByRole('status'),
  ).toHaveText('暂无记录')
  const attempts = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/notify-send-logs' && r.request().method() === 'GET',
  )
  await page.getByRole('button', { name: '逐次发送记录', exact: true }).click()
  expect((await attempts).ok()).toBe(true)
  await expect(page.locator('.runs-page')).toBeVisible()
  await expect(page.locator('.request-error')).toHaveCount(0)
  expect((await request.get('/api/notify-send-logs')).status()).toBe(401)
  expect(
    (
      await request.get(
        '/api/notify-send-logs?fromTime=2026-01-01T00:00:00&toTime=2026-09-01T00:00:00',
        { headers },
      )
    ).status(),
  ).toBe(400)
  await page.getByRole('link', { name: '任务管理', exact: true }).click()
}
