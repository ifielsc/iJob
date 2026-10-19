// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, type Page, type APIRequestContext } from '@playwright/test'
export async function verifySchedules(
  page: Page,
  request: APIRequestContext,
  headers: Record<string, string>,
  taskId: string,
) {
  const meta = await request.get('/api/schedules/metadata', { headers })
  expect(meta.ok()).toBe(true)
  const config = (await meta.json()).data
  expect(config.zoneId).toBe('Asia/Shanghai')
  const created = await request.post('/api/schedules', {
    headers: { ...headers, 'Content-Type': 'application/json' },
    data: `{"targetType":"TASK","targetId":"${taskId}","scheduleType":"INTERVAL","intervalSec":300,"enabled":false,"variables":{"large":9223372036854775807,"region":"local"}}`,
  })
  expect(created.status()).toBe(201)
  const id = (await created.json()).data
  expect(typeof id).toBe('string')
  await page.getByRole('link', { name: '调度管理', exact: true }).click()
  await expect(page.locator('.schedule-table')).toContainText(id)
  await expect(page.locator('.schedule-target-name')).toHaveText('Isolated SQL task')
  await page.locator('#schedule-filter-targetName').fill('SQL ta')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.schedule-table tbody tr')).toHaveCount(1)
  await page.getByRole('button', { name: '详情 ' + id, exact: true }).click()
  await expect(page.locator('.schedule-meta')).toContainText('Isolated SQL task')
  await page.locator('.n-drawer-footer').getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('button', { name: '编辑 ' + id, exact: true }).click()
  await expect(page.locator('#schedule-intervalSec')).toHaveValue('300')
  await expect(page.locator('.schedule-drawer')).toContainText('Isolated SQL task')
  await page.getByRole('button', { name: '高级 JSON', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '高级 JSON', exact: true })).toHaveValue(
    /9223372036854775807/,
  )
  await page.locator('#schedule-intervalSec').fill('120')
  await page.locator('#schedule-intervalSec').press('Tab')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.schedule-drawer')).toHaveCount(0)
  const result = await request.get('/api/schedules/' + id, { headers })
  const raw = await result.text()
  expect(raw).toContain('"large":9223372036854775807')
  expect(JSON.parse(raw).data.intervalSec).toBe(120)
  expect(JSON.parse(raw).data.targetName).toBe('Isolated SQL task')
  for (const term of ['not-present', '%', '_', "' OR 1=1 --"]) {
    const filtered = await request.get('/api/schedules?targetName=' + encodeURIComponent(term), {
      headers,
    })
    expect(filtered.ok()).toBe(true)
    expect((await filtered.json()).data.total).toBe(0)
  }
  const matched = await request.get('/api/schedules?targetName=SQL%20ta&pageSize=1', { headers })
  expect((await matched.json()).data.total).toBe(1)
  const preview = await request.post('/api/schedules/preview', {
    headers,
    data: { scheduleType: 'INTERVAL', intervalSec: 120, fromTime: '2031-09-28T10:00:00', count: 2 },
  })
  expect((await preview.json()).data.items.map((v: { fireTime: string }) => v.fireTime)).toEqual([
    '2031-09-28T10:02:00',
    '2031-09-28T10:04:00',
  ])
  expect(
    (
      await request.post('/api/schedules/preview', {
        headers,
        data: { scheduleType: 'CRON', cronExpr: 'bad', count: 10 },
      })
    ).status(),
  ).toBe(400)
  expect(
    (
      await request.patch('/api/schedules/' + id + '/enabled', { headers, data: { enabled: true } })
    ).ok(),
  ).toBe(true)
  expect(
    (await (await request.get('/api/schedules/' + id, { headers })).json()).data.nextFireTime,
  ).toBeTruthy()
  expect(
    (
      await request.patch('/api/schedules/' + id + '/enabled', {
        headers,
        data: { enabled: false },
      })
    ).ok(),
  ).toBe(true)
  expect((await request.delete('/api/schedules/' + id, { headers })).ok()).toBe(true)
  const jobBody = {
    name: 'Nightly accounting job',
    enabled: true,
    timeout: 60,
    nodes: [
      {
        nodeId: 'one',
        name: 'SQL step',
        kind: 'EXECUTION',
        nodeType: 'TASK',
        refId: taskId,
        policy: { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' },
      },
    ],
    edges: [],
  }
  const jobResponse = await request.post('/api/jobs', { headers, data: jobBody })
  expect(jobResponse.status()).toBe(201)
  const job = (await jobResponse.json()).data
  const jobScheduleResponse = await request.post('/api/schedules', {
    headers,
    data: {
      targetType: 'JOB',
      targetId: job.jobId,
      scheduleType: 'INTERVAL',
      intervalSec: 60,
      enabled: false,
    },
  })
  expect(jobScheduleResponse.status()).toBe(201)
  const jobScheduleId = (await jobScheduleResponse.json()).data
  const jobMatch = await request.get(
    '/api/schedules?targetName=accounting&targetType=JOB&pageSize=1',
    { headers },
  )
  const jobPage = (await jobMatch.json()).data
  expect(jobPage.total).toBe(1)
  expect(jobPage.list[0].targetName).toBe(jobBody.name)
  expect(
    (
      await request.put('/api/jobs/' + job.jobId + '?expectedRevision=' + job.revision, {
        headers,
        data: { ...jobBody, name: 'Renamed accounting job' },
      })
    ).ok(),
  ).toBe(true)
  expect(
    (await (await request.get('/api/schedules/' + jobScheduleId, { headers })).json()).data
      .targetName,
  ).toBe('Renamed accounting job')
  expect(
    (await (await request.get('/api/schedules?targetName=Nightly', { headers })).json()).data.total,
  ).toBe(0)
  expect((await request.delete('/api/schedules/' + jobScheduleId, { headers })).ok()).toBe(true)
  expect((await request.delete('/api/jobs/' + job.jobId, { headers })).ok()).toBe(true)
  expect((await request.get('/api/schedules/metadata')).status()).toBe(401)
  await page.getByRole('link', { name: '任务管理', exact: true }).click()
}
