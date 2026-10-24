// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { test, expect, type Page } from '@playwright/test'
import type { Entry } from '../../src/modules/packages/model'
const resource = {
  key: '00000000-0000-4000-8000-000000000001',
  kind: 'RESOURCE' as const,
  name: 'Source database',
  referenceOnly: false,
  requiredSecrets: ['/dbConfig/password'],
  references: {},
  body: {
    name: 'Source database',
    code: 'source-db',
    type: 'DB',
    enabled: true,
    dbConfig: {
      dbType: 'MYSQL',
      jdbcUrl: 'jdbc:mysql://localhost:1/fixture',
      username: 'fixture',
      password: null,
    },
  },
}
const task = {
  key: '00000000-0000-4000-8000-000000000002',
  kind: 'TASK' as const,
  name: 'Package SQL',
  referenceOnly: false,
  requiredSecrets: [],
  references: { '/pluginConfig/resourceId': resource.key },
  body: {
    name: 'Package SQL',
    pluginType: 'SQL',
    enabled: false,
    pluginConfig: { resourceId: null, script: 'SELECT 1' },
  },
}
const file = {
  formatVersion: 1,
  productVersion: '1.0.0',
  businessTimezone: 'Asia/Shanghai',
  roots: [task.key],
  objects: [resource, task],
}
const id = '00000000-0000-4000-8000-000000000003'
async function setup(
  page: Page,
  {
    locale = 'zh-CN',
    theme = 'light',
    writer = true,
    partial = false,
    many = false,
    concurrency = false,
    jobs = false,
  } = {},
) {
  const writes: { path: string; body: Record<string, unknown> }[] = []
  const packageFile: Omit<typeof file, 'objects'> & { objects: Entry[] } = many
    ? {
        ...file,
        objects: [
          ...file.objects,
          ...Array.from({ length: 38 }, (_, i) => ({
            ...task,
            key: 'many-' + i,
            name: 'Extra task ' + i,
          })),
        ],
      }
    : { ...file, objects: [...file.objects] }
  if (concurrency) {
    packageFile.objects = [
      {
        ...resource,
        key: 'concurrency-resource',
        kind: 'CONCURRENCY_RESOURCE',
        name: 'Shared capacity',
        requiredSecrets: [],
        body: { code: 'shared', name: 'Shared capacity', maxConcurrency: 7, enabled: true },
      },
      {
        ...task,
        body: { ...task.body, concurrencyPolicy: { taskMaxConcurrency: 3, resourceIds: [null] } },
        references: {
          ...task.references,
          '/concurrencyPolicy/resourceIds/0': 'concurrency-resource',
        },
      },
    ]
  }
  if (jobs) {
    const job: Entry = {
      key: 'package-job',
      kind: 'JOB',
      name: 'Package workflow',
      referenceOnly: false,
      requiredSecrets: [],
      references: { '/nodes/0/taskId': task.key },
      body: { name: 'Package workflow', enabled: false, nodes: [{ taskId: null }] },
    }
    packageFile.objects.push(job)
    packageFile.roots = [job.key]
  }
  let applies = 0
  await page.addInitScript(
    ({ locale, theme }) =>
      localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ locale, theme })),
    { locale, theme },
  )
  function result(status: string) {
    return {
      id,
      status,
      createdAt: '2026-10-23T10:00:00Z',
      actor: 'package-admin',
      startedAt: status === 'COMPLETED' || status === 'PARTIAL' ? '2026-10-23T10:01:00Z' : null,
      completedAt: status === 'COMPLETED' ? '2026-10-23T10:02:00Z' : null,
      sourceTimezone: 'Asia/Shanghai',
      targetTimezone: 'Asia/Shanghai',
      steps: packageFile.objects.map((e, i) => ({
        key: e.key,
        kind: e.kind,
        name: e.name,
        action: 'CREATE',
        status:
          status === 'COMPLETED'
            ? 'DONE'
            : status === 'PARTIAL' && i === 1
              ? 'FAILED'
              : status === 'PARTIAL'
                ? 'DONE'
                : status === 'BLOCKED' && i === 0
                  ? 'BLOCKED'
                  : 'CREATE',
        targetId:
          status === 'COMPLETED' || (i === 0 && status === 'PARTIAL') ? String(i + 10) : undefined,
        issues:
          status === 'BLOCKED' && i === 0
            ? ['MISSING_INPUT:/dbConfig/password']
            : status === 'PARTIAL' && i === 1
              ? ['DEPENDENCY_CHANGED']
              : [],
        after: e.body,
      })),
    }
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        path = new URL(req.url()).pathname,
        body = req.method() === 'GET' ? {} : (req.postDataJSON() ?? {})
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (req.method() !== 'GET') writes.push({ path, body })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'package-admin',
          username: 'package-admin',
          displayName: 'Package admin',
          roleCode: writer ? 'ADMIN' : 'VIEWER',
          mustChangePassword: false,
          permissions: [
            'DEFINITION_READ',
            'SUMMARY_READ',
            ...(writer ? ['CONFIGURATION_WRITE', 'SENSITIVE_READ'] : []),
          ],
        })
      if (path === '/api/tasks' || path === '/api/jobs')
        return ok({
          list: Array.from({ length: 32 }, (_, i) => ({
            id: String(i + 100),
            name: 'Package ' + (i + 1),
            pluginType: 'SQL',
            enabled: false,
            groups: [],
            currentRevision: '1',
            currentTaskDefinitionId: '1',
            currentDefinitionId: '1',
            updateTime: '2026-10-23T10:00:00',
            nodeCount: 1,
          })),
          total: 32,
        })
      if (path === '/api/job-packages/export')
        return ok({
          filename: 'jobs.ijob.zip',
          content: Buffer.from('fixture archive').toString('base64'),
          sha256: 'fixture',
        })
      if (path === '/api/job-packages/inspect') return ok(packageFile)
      if (path === '/api/job-packages/plans')
        return ok(result(body.reviewedEnvironment ? 'READY' : 'BLOCKED'))
      if (path.endsWith('/apply')) {
        applies++
        return ok(result(partial && applies === 1 ? 'PARTIAL' : 'COMPLETED'))
      }
      if (path === '/api/job-packages/plans/' + id)
        return ok(result(applies ? 'COMPLETED' : 'READY'))
      if (path === '/api/job-packages/records')
        return ok([
          {
            id,
            status: 'COMPLETED',
            actor: 'package-admin',
            createdAt: '2026-10-23T10:00:00Z',
            startedAt: '2026-10-23T10:01:00Z',
            completedAt: '2026-10-23T10:02:00Z',
            total: 2,
            completed: 2,
          },
        ])
      if (path === '/api/job-packages/candidates')
        return ok([{ id: '99', name: 'Target database', type: 'DB' }])
      return ok({ list: [], total: 0 })
    },
  )
  return writes
}
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test('large selection export ' + theme + ' ' + locale, async ({ page }, info) => {
      const writes = await setup(page, { theme, locale })
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/jobs')
      await page.locator('thead [role=checkbox]').click()
      await page
        .getByRole('button', {
          name: locale === 'zh-CN' ? '导出所选' : 'Export selected',
          exact: true,
        })
        .click()
      const modal = page.locator('.package-export')
      await expect(modal).toBeVisible()
      await expect
        .poll(() => writes.filter((w) => w.path.endsWith('/export')).length)
        .toBeGreaterThan(0)
      await page
        .getByRole('button', {
          name: locale === 'zh-CN' ? '查看清单' : 'View selection',
          exact: true,
        })
        .click()
      await expect(
        modal
          .getByRole('button', { name: locale === 'zh-CN' ? '移除' : 'Remove', exact: true })
          .first(),
      ).toHaveClass(/n-button--error-type/)
      const table = modal.locator('.package-selection-table').first()
      expect(await table.evaluate((e) => e.scrollHeight > e.clientHeight)).toBeTruthy()
      expect(await table.evaluate((e) => e.clientHeight)).toBeGreaterThan(250)
      expect(await modal.evaluate((e) => e.scrollHeight <= e.clientHeight + 1)).toBeTruthy()
      expect(
        await modal
          .locator('.package-radio .n-radio')
          .evaluateAll((els) =>
            Math.abs(els[0]!.getBoundingClientRect().top - els[1]!.getBoundingClientRect().top),
          ),
      ).toBeLessThan(3)
      expect(
        await modal
          .locator('.package-options .n-checkbox')
          .evaluateAll((els) =>
            Math.abs(els[0]!.getBoundingClientRect().top - els[1]!.getBoundingClientRect().top),
          ),
      ).toBeLessThan(3)
      await page.screenshot({ path: info.outputPath('bulk-' + theme + '-' + locale + '.png') })
      await modal
        .getByRole('textbox', {
          name: locale === 'zh-CN' ? '搜索已选对象' : 'Search selected objects',
          exact: true,
        })
        .fill('Package 32')
      await modal
        .getByRole('button', { name: locale === 'zh-CN' ? '移除' : 'Remove', exact: true })
        .click()
      await expect
        .poll(
          () =>
            (
              (writes.filter((w) => w.path.endsWith('/export')).at(-1)?.body.roots ??
                []) as unknown[]
            ).length,
        )
        .toBe(31)
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 800 })
        expect(await modal.evaluate((e) => e.scrollWidth <= e.clientWidth + 1)).toBeTruthy()
        await expect(modal.locator('.package-footer button').last()).toBeInViewport()
      }
      await page.setViewportSize({ width: 1440, height: 1000 })
      const pending = page.waitForEvent('download')
      await modal.locator('.package-footer button').last().click()
      expect((await pending).suggestedFilename()).toBe('selected-jobs.ijob.zip')
    })
  }
test('import maps dependencies, keeps credentials out of reports, and resumes partial results', async ({
  page,
}, info) => {
  const writes = await setup(page, { partial: true })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '导入任务包', exact: true }).click()
  await page.locator('input[type=file]').setInputFiles({
    name: 'jobs.ijob.zip',
    mimeType: 'application/zip',
    buffer: Buffer.from('fixture'),
  })
  const resourceRow = page
    .locator('.package-object')
    .filter({ has: page.getByText('Source database', { exact: true }) })
  await resourceRow.getByRole('button', { name: '配置', exact: true }).click()
  await resourceRow.getByLabel('dbConfig / password', { exact: true }).fill('fixture-only-value')
  const taskRow = page
    .locator('.package-object')
    .filter({ has: page.getByText('Package SQL', { exact: true }) })
  await taskRow.getByRole('button', { name: '配置', exact: true }).click()
  await taskRow.getByRole('textbox', { name: '名称', exact: true }).fill('Imported SQL')
  await taskRow.getByRole('button', { name: '收起', exact: true }).click()
  await taskRow.getByRole('button', { name: '配置', exact: true }).click()
  await expect(taskRow.getByRole('textbox', { name: '名称', exact: true })).toHaveValue(
    'Imported SQL',
  )
  await page.getByRole('checkbox', { name: '已检查目标环境参数与资源配置' }).check()
  await page.getByRole('button', { name: '检查差异', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认导入', exact: true })).toBeEnabled()
  const checked = writes.filter((w) => w.path === '/api/job-packages/plans').at(-1)!
  expect(checked.body.choices).toMatchObject({
    [resource.key]: { secrets: { '/dbConfig/password': 'fixture-only-value' } },
    [task.key]: { body: { name: 'Imported SQL' } },
  })
  await page.screenshot({ path: info.outputPath('import-diff.png') })
  await page.getByRole('button', { name: '确认导入', exact: true }).click()
  await expect(page.getByText('部分对象未完成', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '继续导入', exact: true }).click()
  await expect(page.locator('.package-notice').filter({ hasText: '导入完成' })).toBeVisible()
  const pending = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载报告', exact: true }).click()
  const download = await pending
  const stream = await download.createReadStream()
  let text = ''
  for await (const data of stream!) text += data.toString()
  expect(text).not.toContain('fixture-only-value')
  expect(download.suggestedFilename()).toMatch(/\.html$/)
  expect(text).toContain('<!doctype html>')
  expect(text).toContain('package-admin')
  await expect(page.locator('.package-result-counts')).toContainText('新增2')
  await expect(page.getByText('已完成明细', { exact: true })).toHaveCount(0)
  await expect(page.locator('.package-kind-summary')).toHaveText('（任务 1、资源 1）')
  await expect(page.locator('.package-object').first()).toContainText('Package SQL')
  expect(
    await page
      .locator('.package-result-counts strong')
      .first()
      .evaluate((e) => parseFloat(getComputedStyle(e).fontSize)),
  ).toBeLessThanOrEqual(14)
  expect(writes.filter((w) => w.path.endsWith('/apply'))).toHaveLength(2)
})
test('mapping to an existing resource, history and narrow import layout', async ({
  page,
}, info) => {
  await setup(page, { theme: 'dark', locale: 'en-US' })
  await page.setViewportSize({ width: 320, height: 800 })
  await page.goto('/tasks')
  await page.getByRole('button', { name: 'Import task package', exact: true }).click()
  await page.locator('input[type=file]').setInputFiles({
    name: 'jobs.ijob.zip',
    mimeType: 'application/zip',
    buffer: Buffer.from('fixture'),
  })
  const row = page
    .locator('.package-object')
    .filter({ has: page.getByText('Source database', { exact: true }) })
  await row.getByRole('button', { name: 'Configure', exact: true }).click()
  await row.locator('.n-select').first().click()
  await page.getByText('Reuse existing', { exact: true }).click()
  await row.locator('.n-select').nth(1).click()
  await page.getByText('Target database · #99', { exact: true }).click()
  await expect(row.locator('input[type=password]')).toHaveCount(0)
  const modal = page.locator('.package-modal')
  expect(await modal.evaluate((e) => e.scrollWidth <= e.clientWidth + 1)).toBeTruthy()
  await expect(modal.locator('.package-footer button').last()).toBeInViewport()
  await page.screenshot({ path: info.outputPath('import-mobile-dark-en.png') })
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.getByRole('button', { name: 'Leave', exact: true }).click()
  await page.getByRole('button', { name: 'Import history', exact: true }).click()
  await expect(page.locator('.package-history-table')).toContainText('package-admin')
  await expect(page.locator('.package-history-table')).toContainText('2026-10-23 10:01:00')
  await page.getByRole('button', { name: 'View', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Refresh result', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Back to import history', exact: true }).click()
  await expect(page.locator('.package-history-table')).toBeVisible()
  await expect(page.getByRole('button', { name: 'View', exact: true })).toBeVisible()
})
test('viewer has no import or export controls', async ({ page }) => {
  await setup(page, { writer: false })
  await page.goto('/jobs')
  await expect(page.getByRole('button', { name: '导入作业包', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '导出所选', exact: true })).toHaveCount(0)
})

test('large mapping and difference lists scroll inside the modal and keep actions visible', async ({
  page,
}) => {
  await setup(page, { many: true })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/tasks')
  const toolbar = page.locator('.list-toolbar')
  const left = await toolbar.locator('.list-batch-action').boundingBox()
  const right = await toolbar.locator('.package-actions').boundingBox()
  expect(right!.x).toBeGreaterThan(left!.x + left!.width + 100)
  await page.getByRole('button', { name: '导入任务包', exact: true }).click()
  await expect(page.locator('.package-modal .n-card-header')).toContainText('导入任务包')
  await expect(page.getByRole('button', { name: '选择 .ijob.zip 文件', exact: true })).toHaveClass(
    /n-button--primary-type/,
  )
  await page.locator('input[type=file]').setInputFiles({
    name: 'tasks.ijob.zip',
    mimeType: 'application/zip',
    buffer: Buffer.from('fixture'),
  })
  const modal = page.locator('.package-modal'),
    content = modal.locator('.package-scroll')
  await expect(modal.locator('.package-object')).toHaveCount(40)
  expect(await content.evaluate((e) => e.scrollHeight > e.clientHeight)).toBeTruthy()
  expect(await modal.evaluate((e) => e.scrollHeight <= e.clientHeight + 1)).toBeTruthy()
  await expect(modal.getByRole('button', { name: '检查差异', exact: true })).toBeInViewport()
  await page.getByRole('checkbox', { name: '已检查目标环境参数与资源配置' }).check()
  await page.getByRole('button', { name: '检查差异', exact: true }).click()
  expect(await content.evaluate((e) => e.scrollHeight > e.clientHeight)).toBeTruthy()
  await expect(modal.getByRole('button', { name: '确认导入', exact: true })).toBeInViewport()
  expect(await modal.evaluate((e) => e.scrollHeight <= e.clientHeight + 1)).toBeTruthy()
})

test('concurrency dependencies expose translated capacity and task limits for target review', async ({
  page,
}) => {
  const writes = await setup(page, { concurrency: true })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '导入任务包', exact: true }).click()
  await page.locator('input[type=file]').setInputFiles({
    name: 'concurrency.ijob.zip',
    mimeType: 'application/zip',
    buffer: Buffer.from('fixture'),
  })
  const capacity = page.locator('.package-object').filter({ hasText: 'Shared capacity' })
  await expect(capacity).toContainText('并发资源')
  await capacity.getByRole('button', { name: '配置', exact: true }).click()
  await capacity.getByRole('textbox', { name: '并发资源容量', exact: true }).fill('9')
  const taskRow = page.locator('.package-object').filter({ hasText: 'Package SQL' })
  await taskRow.getByRole('button', { name: '配置', exact: true }).click()
  await taskRow.getByRole('textbox', { name: '任务并发上限（留空继承）', exact: true }).fill('4')
  await page.getByRole('checkbox', { name: '已检查目标环境参数与资源配置' }).check()
  await page.getByRole('button', { name: '检查差异', exact: true }).click()
  expect(writes.filter((w) => w.path.endsWith('/plans')).at(-1)!.body.choices).toMatchObject({
    'concurrency-resource': { body: { maxConcurrency: 9 } },
    [task.key]: { body: { concurrencyPolicy: { taskMaxConcurrency: 4, resourceIds: [null] } } },
  })
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(
      'task entry imports jobs with consistent order and retained drafts ' + theme + ' ' + locale,
      async ({ page }, info) => {
        const en = locale === 'en-US'
        const writes = await setup(page, { jobs: true, theme, locale })
        await page.goto('/tasks')
        await page
          .getByRole('button', { name: en ? 'Import task package' : '导入任务包', exact: true })
          .click()
        await page.locator('input[type=file]').setInputFiles({
          name: 'jobs.ijob.zip',
          mimeType: 'application/zip',
          buffer: Buffer.from('fixture'),
        })
        const modal = page.locator('.package-modal')
        const headings = modal.locator('.package-object-heading > strong')
        const order = ['Package workflow', 'Package SQL', 'Source database']
        await expect(headings).toHaveText(order)
        const jobRow = modal
          .locator('.package-object')
          .filter({ has: page.getByText('Package workflow', { exact: true }) })
        await jobRow.getByRole('button', { name: en ? 'Configure' : '配置', exact: true }).click()
        await jobRow
          .getByRole('textbox', { name: en ? 'Name' : '名称', exact: true })
          .fill('Imported workflow')
        const search = modal.getByRole('textbox', {
          name: en ? 'Search object names' : '搜索对象名称',
          exact: true,
        })
        await search.fill('Source database')
        await expect(jobRow).toBeHidden()
        await search.fill('')
        await expect(
          jobRow.getByRole('textbox', { name: en ? 'Name' : '名称', exact: true }),
        ).toHaveValue('Imported workflow')
        await expect(headings).toHaveText(order)
        for (const width of [390, 320]) {
          await page.setViewportSize({ width, height: 844 })
          expect(
            await modal.evaluate(
              (e) => e.scrollWidth <= e.clientWidth + 1 && e.scrollHeight <= e.clientHeight + 1,
            ),
          ).toBeTruthy()
          await expect(modal.locator('.package-footer button').last()).toBeInViewport()
        }
        await page.setViewportSize({ width: 1440, height: 1000 })
        await page.screenshot({ path: info.outputPath('mapping-job-through-task.png') })
        await modal.getByRole('checkbox').check()
        await modal
          .getByRole('button', { name: en ? 'Check differences' : '检查差异', exact: true })
          .click()
        await expect(headings).toHaveText(order)
        expect(writes.filter((w) => w.path.endsWith('/plans')).at(-1)!.body.choices).toMatchObject({
          'package-job': { body: { name: 'Imported workflow' } },
        })
        await modal
          .getByRole('button', { name: en ? 'Confirm import' : '确认导入', exact: true })
          .click()
        await expect(
          modal.locator('.package-notice').filter({ hasText: en ? 'Import complete' : '导入完成' }),
        ).toBeVisible()
        await expect(headings).toHaveText(order)
        await expect(modal.locator('.package-kind-summary')).toContainText(en ? 'Job 1' : '作业 1')
        expect(writes.filter((w) => w.path.endsWith('/apply'))).toHaveLength(1)
      },
    )
  }
