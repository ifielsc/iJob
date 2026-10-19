// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect } from '@playwright/test'
const samples: Record<string, object> = {
  SQL: { resourceId: '81', script: 'SELECT 1;\nSELECT 2;', transaction: false, minRows: 0 },
  SCRIPT: {
    engine: 'SHELL',
    script: 'echo first\necho second',
    env: { type: 'custom', PATH: '/opt/bin' },
  },
  API_CALL: {
    resourceId: '81',
    method: 'POST',
    path: '/start',
    body: { id: '9007199254740993', empty: [], active: false },
    webService: { operation: 'start', parameters: { type: 'job', id: '42' } },
    remoteStop: { path: '/cancel', method: 'POST', timeoutSeconds: 5 },
    extractors: [
      {
        variable: 'operationId',
        path: '$.id',
        required: true,
        type: 'STRING',
        fallback: 'later-field',
      },
    ],
  },
  EVENT: {
    action: 'PUBLISH',
    eventCode: 'ready',
    payload: { batchSize: '9007199254740993', enabled: false },
  },
  ETL: {
    source: { type: 'DATABASE', resourceId: '81', sql: 'SELECT id FROM source' },
    transforms: [{ type: 'TRIM', properties: { field: 'id' } }],
    validations: [
      { type: 'NOT_NULL', ruleId: 'required-id', onFailure: 'FILTER', properties: { field: 'id' } },
    ],
    target: { type: 'DATABASE', resourceId: '81', columns: { source: 'target', type: 'custom' } },
  },
  BATCH_ETL: {
    sourceResourceId: '81',
    targetResourceId: '81',
    tables: ['orders', 'items'],
    logicalKeys: ['id'],
    execution: {
      maxTables: 500,
      fetchSize: 2000,
      writeBatchSize: 1000,
      queryTimeoutSeconds: 300,
      maxTotalRows: 50000000,
    },
  },
  SERVER_MONITOR: {
    resourceId: '81',
    collect: {
      items: ['CPU', 'MEMORY'],
      applications: [{ name: 'java', matchType: 'CONTAINS', pattern: 'java' }],
    },
    rules: { applications: [{ name: 'java', cpu: 80, memory: 1024, enabled: false }] },
  },
  LOCAL_JAR: {
    formatVersion: 1,
    jarFile: '/jobs/demo.jar',
    entryClass: 'Demo',
    userConfig: { batchSize: '9007199254740993', type: 'custom', enabled: false },
  },
  FILE_TRANSFER: {
    mode: 'TRANSFER',
    sourcePath: '/in',
    targetPath: '/out',
    overwrite: false,
    remotePublishMode: 'DIRECT',
    maxScanDepth: 0,
  },
  PUSH: {
    sourceResourceId: '81',
    sql: 'SELECT * FROM messages',
    target: { resourceId: '81', type: 'KAFKA', properties: { topic: 'orders' } },
    serializer: 'json',
    batchSize: 500,
  },
  DATA_CHECK: {
    resourceId: '81',
    checks: [
      {
        name: 'row count',
        type: 'COUNT',
        compareType: 'EQUAL',
        expected: 0,
        sql: 'SELECT COUNT(*) FROM orders',
        variable: 'count',
        level: 'ERROR',
        vendor: 'future-column',
      },
    ],
    handlers: [{ type: 'NOTIFY', params: { notifyConfigId: '82', notifyOnSuccess: false } }],
  },
}
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`all plugin details remain complete and readable ${theme} ${locale}`, async ({
      page,
    }, info) => {
      const en = locale === 'en-US',
        writes: string[] = [],
        errors: string[] = []
      page.on('pageerror', (e) => errors.push(e.message))
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      const tasks = Object.entries(samples).map(([pluginType, config], i) => ({
        id: String(i + 101),
        name: 'Sample ' + pluginType,
        pluginType,
        enabled: true,
        currentRevision: '2',
        currentTaskDefinitionId: String(i + 201),
        groups: [],
        retryTimes: 0,
        retryInterval: 0,
        timeout: null,
        description: '',
        headerNames: ['X-Start-Token'],
        remoteStopHeaderNames: ['X-Stop-Token'],
        pluginConfig: JSON.stringify({ ...config, futureExtension: 'preserved-' + pluginType }),
      }))
      await page.route('**/api/**', (route) => {
        const path = new URL(route.request().url()).pathname
        if (!path.startsWith('/api/')) return route.continue()
        const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
        if (path === '/api/reference-summaries')
          return ok([
            { kind: 'RESOURCE', id: '81', name: 'Named reference', type: 'DB', enabled: true },
          ])
        if (route.request().method() !== 'GET') {
          writes.push(path)
          return route.abort()
        }
        if (path === '/api/auth/me')
          return ok({
            loginId: 'detail-reader',
            username: 'reader',
            displayName: 'Reader',
            roleCode: 'ADMIN',
            mustChangePassword: false,
            permissions: ['DEFINITION_READ', 'SUMMARY_READ', 'SENSITIVE_READ'],
          })
        if (path === '/api/tasks') return ok({ list: tasks, total: tasks.length })
        if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
        const row = tasks.find((t) => path === '/api/tasks/' + t.id)
        if (row) return ok(row)
        return ok({ list: [], total: 0 })
      })
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/tasks')
      for (const width of [1440, 1280, 1100]) {
        await page.setViewportSize({ width, height: 1000 })
        const box = await page.locator('.task-type-filter .n-select').boundingBox()
        expect(box!.width).toBeGreaterThanOrEqual(180)
        expect(box!.width).toBeLessThanOrEqual(220)
        const controls = await page.locator('.list-query-actions').boundingBox()
        const name = await page.locator('.task-name-filter').boundingBox()
        expect(Math.abs(controls!.y - name!.y), 'viewport ' + width).toBeLessThan(4)
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
        ).toBe(true)
      }
      await page.setViewportSize({ width: 1440, height: 1000 })
      for (const task of tasks) {
        await page
          .getByRole('button', { name: (en ? 'Details' : '详情') + ' ' + task.name, exact: true })
          .click()
        const drawer = page.locator('.task-drawer')
        await expect(
          drawer.getByText('preserved-' + task.pluginType, { exact: true }),
        ).not.toBeVisible()
        await expect(drawer.locator('.plugin-detail-sections')).not.toContainText('futureExtension')
        await expect(drawer.locator('.unified-technical')).not.toHaveAttribute('open', '')
        await drawer.locator('.unified-technical > summary').click()
        await drawer.locator('.plugin-extensions > summary').click()
        await expect(
          drawer.getByText('preserved-' + task.pluginType, { exact: true }),
        ).toBeVisible()
        await drawer.locator('.unified-technical > summary').click()
        if (task.pluginType === 'API_CALL') {
          await expect(drawer).toContainText('X-Start-Token')
          await expect(drawer).toContainText('X-Stop-Token')
          await expect(drawer.locator('.plugin-facts-table')).toHaveCount(1)
        }
        if (['API_CALL', 'EVENT', 'LOCAL_JAR'].includes(task.pluginType)) {
          const viewer = drawer.locator('.json-viewer').first()
          await expect(viewer.locator('.cm-content')).toBeVisible()
          await expect(viewer.locator('.cm-content')).toHaveAttribute('contenteditable', 'false')
          await expect(viewer.locator('table,dl')).toHaveCount(0)
          await expect(drawer.locator('.plugin-json')).toHaveCount(0)
          const before = await viewer.locator('.cm-content').innerText()
          await viewer
            .getByRole('button', { name: en ? 'Expand viewer' : '放大查看', exact: true })
            .click()
          const modal = viewer.locator('dialog')
          await expect(modal).toBeVisible()
          const footer = await modal.locator('footer').boundingBox()
          const closeButton = await modal.locator('footer button').boundingBox()
          expect(
            footer!.x + footer!.width - closeButton!.x - closeButton!.width,
          ).toBeLessThanOrEqual(25)
          await expect(modal.locator('.cm-content')).toHaveAttribute('contenteditable', 'false')
          await expect(modal.locator('.cm-content')).toHaveText(before, { useInnerText: true })
          await modal
            .getByRole('button', { name: en ? 'Close' : '关闭', exact: true })
            .last()
            .click()
        }
        if (task.pluginType === 'BATCH_ETL')
          await expect(
            drawer.getByRole('region', {
              name: en ? 'Incremental & limits' : '增量与限制',
              exact: true,
            }),
          ).toContainText('50000000')
        if (task.pluginType === 'DATA_CHECK') {
          await drawer.locator('.plugin-records .plugin-row-toggle').click()
          await expect(drawer.getByText('future-column', { exact: true })).toBeVisible()
          await drawer.locator('.plugin-records').first().scrollIntoViewIfNeeded()
          await page.waitForFunction(
            () =>
              !document
                .getAnimations()
                .some(
                  (a) => a.playState === 'running' && a.effect?.getTiming().iterations !== Infinity,
                ),
          )
          await page.screenshot({ path: info.outputPath('tables.png'), animations: 'disabled' })
          await page.setViewportSize({ width: 390, height: 844 })
          expect(await drawer.evaluate((el) => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
          await page.screenshot({
            path: info.outputPath('tables-mobile.png'),
            animations: 'disabled',
          })
          await page.setViewportSize({ width: 1440, height: 1000 })
        }
        await drawer
          .getByRole('button', { name: en ? 'Close' : '关闭', exact: true })
          .last()
          .click()
        await expect(drawer).toHaveCount(0)
      }
      expect(writes).toEqual([])
      expect(errors).toEqual([])
    })
