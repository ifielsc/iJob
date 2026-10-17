import { expect, type APIRequestContext, type Page } from '@playwright/test'
export async function verifyNotifyBindings(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
  taskId: string,
) {
  const readTask = async () =>
    (await (await request.get('/api/tasks/' + taskId, { headers })).json()).data
  const before = await readTask()
  const created = await request.post('/api/notify-configs', {
    headers,
    data: {
      name: 'Binding fixture bot',
      type: 'DINGTALK',
      enabled: false,
      dingTalkConfig: { webhook: 'https://example.test/never-send', atAll: false, atMobiles: [] },
    },
  })
  expect(created.status()).toBe(201)
  const configId = (await created.json()).data
  await page.getByRole('button', { name: '更多 Isolated SQL task', exact: true }).click()
  await page.getByText('通知绑定', { exact: true }).click()
  const drawer = page.locator('.binding-drawer')
  await drawer.getByRole('button', { name: '添加绑定', exact: true }).click()
  await page.locator('#binding-config').fill('Binding fixture')
  await page.locator('.n-base-select-option').filter({ hasText: 'Binding fixture bot' }).click()
  const saving = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/notify-bindings' && r.request().method() === 'POST',
  )
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  const saved = await saving
  expect(saved.status()).toBe(201)
  const id = (await saved.json()).data
  expect(typeof id).toBe('string')
  await expect(
    drawer.getByRole('button', { name: '修改 Binding fixture bot', exact: true }),
  ).toBeEnabled()
  const listPath = '/api/notify-bindings?targetType=TASK&targetId=' + taskId
  expect((await request.get(listPath)).status()).toBe(401)
  const bindings = (await (await request.get(listPath, { headers })).json()).data
  expect(bindings).toHaveLength(1)
  expect(bindings[0]).toMatchObject({
    id,
    targetId: taskId,
    notifyConfigId: configId,
    notifyConfigEnabled: false,
    notifyPolicy: 'ON_FAILURE',
  })
  const detail = await readTask()
  expect(detail.currentRevision).toBe(before.currentRevision)
  expect(detail.currentTaskDefinitionId).toBe(before.currentTaskDefinitionId)
  expect(detail.notifyBindings[0].id).toBe(id)
  expect(detail.notifyBindings[0].notifyConfigId).toBe(configId)
  expect(
    (
      await request.post('/api/notify-bindings', {
        headers,
        data: {
          targetType: 'TASK',
          targetId: taskId,
          notifyConfigId: configId,
          notifyPolicy: 'ALWAYS',
        },
      })
    ).status(),
  ).toBe(409)
  await drawer.getByRole('button', { name: '修改 Binding fixture bot', exact: true }).click()
  await page.locator('#binding-policy-' + id).click()
  await page.getByRole('option', { name: '不通知', exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(
    drawer.getByRole('button', { name: '修改 Binding fixture bot', exact: true }),
  ).toBeEnabled()
  expect(
    (await (await request.get('/api/notify-bindings/' + id, { headers })).json()).data.notifyPolicy,
  ).toBe('NEVER')
  await drawer.getByRole('button', { name: '移除 Binding fixture bot', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(drawer).toContainText('暂无记录')
  expect((await (await request.get(listPath, { headers })).json()).data).toEqual([])
  expect((await readTask()).currentRevision).toBe(before.currentRevision)
  expect((await request.get('/api/notify-configs/' + configId, { headers })).status()).toBe(200)
  expect((await request.delete('/api/notify-configs/' + configId, { headers })).ok()).toBe(true)
  await drawer
    .locator('.n-drawer-footer')
    .getByRole('button', { name: '关闭', exact: true })
    .click()
}
