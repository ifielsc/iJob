import { test, expect } from '@playwright/test'
import { fileConfig } from '../etl-fixture'
test('real SQLite V30: ETL publish, edit, exact markers, revision conflict and source reference protection', async ({
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
  const resource = async (data: unknown) => {
    const response = await request.post('/api/resources', { headers, data })
    expect(response.status(), await response.text()).toBe(201)
    return String((await response.json()).data)
  }
  const source = await resource({
    name: 'ETL source fixture',
    code: 'etl-source-fixture',
    type: 'FILE',
    enabled: true,
    fileConfig: { protocol: 'LOCAL', basePath: process.env.IJOB_TEST_DIRECTORY },
  })
  const target = await resource({
    name: 'ETL target fixture',
    code: 'etl-target-fixture',
    type: 'DB',
    enabled: true,
    dbConfig: { dbType: 'MYSQL', jdbcUrl: 'jdbc:mysql://127.0.0.1:1/fixture', username: 'fixture' },
  })
  const config = fileConfig
    .replace('"resourceId":9007199254740993', '"resourceId":' + source)
    .replace('"resourceId":9223372036854775807', '"resourceId":' + target)
  const create = await request.post('/api/tasks', {
    headers,
    data: {
      name: 'ETL browser fixture',
      pluginType: 'ETL',
      pluginConfig: config,
      enabled: true,
      retryTimes: 0,
      retryInterval: 0,
    },
  })
  expect(create.status(), await create.text()).toBe(201)
  const task = (await create.json()).data
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '任务管理', exact: true })
    .click()
  await page.getByRole('button', { name: '编辑 ETL browser fixture', exact: true }).click()
  await expect(page.locator('.etl-fields')).toContainText('ETL source fixture')
  await page.getByRole('tab', { name: '转换与校验', exact: true }).click()
  await page.locator('#etl-rule-transforms-0-multiply').fill('0.9876543210987654321')
  await page.getByRole('button', { name: '添加规则', exact: true }).last().click()
  await page.locator('#etl-rule-validations-0-fields-value-0').fill('order_id')
  await page.getByRole('tab', { name: '目标', exact: true }).click()
  await expect(page.getByRole('table', { name: '字段映射', exact: true })).toBeVisible()
  await page.locator('[id="task-etl.target.mappingMode"]').click()
  await page.getByRole('option', { name: '自动同名映射', exact: true }).click()
  const mappings = page.getByRole('table', { name: '自定义映射（可选）', exact: true })
  await mappings.getByRole('button').first().click()
  await mappings.getByRole('button').first().click()
  await page.locator('[id="task-etl.target.excludedFields"]').fill('internal_note')
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await page.locator('[id="task-etl.incremental.initialWatermark"]').fill('9007199254740995')
  const published = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + task.taskId) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const response = await published
  expect(response.status(), await response.text()).toBe(200)
  await expect(page.locator('.task-drawer')).toHaveCount(0)
  const detail = (await (await request.get('/api/tasks/' + task.taskId, { headers })).json()).data
  expect(detail.pluginConfig).toContain('9007199254740995')
  expect(detail.pluginConfig).toContain('0.9876543210987654321')
  expect(JSON.parse(detail.pluginConfig).validations[0].properties.fields).toEqual(['order_id'])
  expect(JSON.parse(detail.pluginConfig).target).toMatchObject({
    mappingMode: 'AUTO',
    columns: {},
    excludedFields: ['internal_note'],
  })
  expect(String(detail.currentRevision)).toBe('2')
  await page.getByRole('button', { name: '编辑 ETL browser fixture', exact: true }).click()
  await page.getByRole('tab', { name: '增量与限制', exact: true }).click()
  await expect(page.locator('[id="task-etl.incremental.initialWatermark"]')).toHaveValue(
    '9007199254740995',
  )
  // Change the same task through the API while the browser retains revision 2.
  const concurrent = await request.put(
    '/api/tasks/' + task.taskId + '?expectedRevision=' + detail.currentRevision,
    {
      headers,
      data: {
        ...detail,
        name: 'ETL concurrent',
        expectedRevision: detail.currentRevision,
        pluginConfig: detail.pluginConfig,
      },
    },
  )
  expect(concurrent.status(), await concurrent.text()).toBe(200)
  await page.locator('#task-name').fill('Browser conflict draft')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  const conflict = page.waitForResponse(
    (r) => r.url().includes('/api/tasks/' + task.taskId) && r.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  expect((await conflict).status()).toBe(409)
  expect((await request.delete('/api/resources/' + source, { headers })).status()).toBe(409)
  expect(
    (await (await request.get('/api/triggers?pageSize=1', { headers })).json()).data.total,
  ).toBe(0)
})
