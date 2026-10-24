// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { test, expect } from '@playwright/test'
import { setupPush } from '../push-fixture'
import { setupFiles } from '../files-fixture'
test.use({ viewport: { width: 1440, height: 1000 } })

for (const theme of ['light', 'dark']) {
  test('SQL preview selection, parameters and cancellation ' + theme, async ({ page }, info) => {
    const state = await setupPush(page)
    await page.addInitScript(
      (theme) =>
        localStorage.setItem(
          'ijob:preferences:v1:guest',
          JSON.stringify({ theme, locale: 'zh-CN' }),
        ),
      theme,
    )
    const queries: Record<string, unknown>[] = []
    let cancelled = 0
    await page.route('**/api/parameter-templates/bindings/TASK/*', (route) =>
      route.fulfill({
        json: {
          success: true,
          code: 200,
          data: { fields: [{ name: 'id', type: 'INTEGER', required: true, minimum: 1 }] },
        },
      }),
    )
    await page.route('**/api/sql-preview**', async (route) => {
      if (route.request().url().endsWith('/cancel')) {
        cancelled++
        return route.fulfill({ json: { success: true, code: 200, data: null } })
      }
      queries.push(route.request().postDataJSON())
      const stop = queries.length === 3
      if (stop) await expect.poll(() => cancelled).toBe(1)
      return route.fulfill({
        json: {
          success: true,
          code: 200,
          data: {
            status: stop ? 'CANCELLED' : queries.length > 3 ? 'SQL_SYNTAX' : 'SUCCESS',
            columns: stop
              ? []
              : [
                  { name: 'id', type: 'BIGINT' },
                  { name: 'missing', type: 'TEXT' },
                ],
            rows: stop
              ? []
              : [
                  ['9007199254740993', null],
                  ['2', ''],
                ],
            elapsedMillis: 12,
            truncated: '',
            sqlState: queries.length === 4 ? '42S22' : queries.length === 5 ? '42S02' : null,
            position: null,
            traceId: 'preview-test',
            vendorCode: queries.length > 3 ? 1054 : null,
            databaseMessage:
              queries.length > 3
                ? "Unknown column 'missing' in [REDACTED] <script>neverExecuted()</script>"
                : null,
          },
        },
      })
    })
    await page.goto('/tasks')
    await page.getByRole('button', { name: '编辑 KAFKA push', exact: true }).click()
    await expect(page.getByRole('button', { name: '查询预览', exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '放大编辑', exact: true }).click()
    const panel = page.locator('dialog[open] .query-panel')
    const editor = panel.locator('.cm-content')
    const tools = await panel.locator('.sql-large-toolbar').boundingBox()
    const actions = await panel.locator('.query-actions').boundingBox()
    expect(Math.abs(tools!.y - actions!.y)).toBeLessThan(3)
    expect(tools!.x).toBeGreaterThan(actions!.x + actions!.width)
    expect(
      (await page.locator('dialog.sql-large-editor > header').boundingBox())!.height,
    ).toBeLessThan(58)
    await panel.getByRole('button', { name: '查找／替换', exact: true }).click()
    expect(
      (await panel.locator('.cm-search input[name="search"]').boundingBox())!.height,
    ).toBeLessThanOrEqual(28)
    await page.keyboard.press('Escape')
    await expect(panel.getByRole('button', { name: '执行选中 SQL', exact: true })).toBeDisabled()
    await editor.fill('SELECT 1;\nDELETE FROM orders;')
    await editor.press('Control+Home')
    await editor.press('Shift+End')
    await panel.getByRole('button', { name: '执行选中 SQL', exact: true }).click()
    await expect(panel.locator('.query-table tbody tr')).toHaveCount(2)
    expect(queries[0]!.sql).toBe('SELECT 1;')
    expect(queries[0]!.mode).toBe('PUSH')
    await expect(panel.locator('.query-table')).toContainText('9007199254740993')
    await expect(panel.locator('.query-null')).toHaveText('NULL')
    const splitter = panel.getByRole('separator')
    await splitter.focus()
    await splitter.press('ArrowUp')
    await expect(panel.locator('.query-area')).toHaveAttribute('style', /55%/)
    await editor.fill('SELECT ${id!}')
    await expect(panel.locator('.query-stale')).toBeVisible()
    await panel.getByRole('button', { name: '查询全文', exact: true }).click()
    const parameters = page.locator('dialog.query-parameters[open]')
    await expect(parameters).toBeVisible()
    const cancel = await parameters.getByRole('button', { name: '取消', exact: true }).boundingBox()
    const start = await parameters
      .getByRole('button', { name: '开始查询', exact: true })
      .boundingBox()
    expect(Math.abs(cancel!.y - start!.y)).toBeLessThan(2)
    expect(start!.x - cancel!.x - cancel!.width).toBeLessThan(12)
    await parameters.getByRole('textbox', { name: 'id', exact: true }).fill('9007199254740993')
    await parameters.getByRole('button', { name: '开始查询', exact: true }).click()
    await expect.poll(() => queries.length).toBe(2)
    await expect(panel.locator('.query-table')).toBeVisible()
    await page.screenshot({ path: info.outputPath('sql-preview-' + theme + '.png') })
    await panel.getByRole('button', { name: '查询全文', exact: true }).click()
    await parameters.getByRole('textbox', { name: 'id', exact: true }).fill('1')
    await parameters.getByRole('button', { name: '开始查询', exact: true }).click()
    await panel.getByRole('button', { name: '停止', exact: true }).click()
    await expect(panel).toContainText('查询已停止')
    await editor.fill('SELECT missing FROM missing_table')
    await panel.getByRole('button', { name: '查询全文', exact: true }).click()
    await expect(panel).toContainText('字段不存在')
    await expect(panel).toContainText('SQLSTATE 42S22')
    await expect(panel.locator('.query-database-error')).toContainText("Unknown column 'missing'")
    await expect(panel.locator('.query-database-error script')).toHaveCount(0)
    await expect(panel).toContainText('1054')
    await panel.getByRole('button', { name: '查询全文', exact: true }).click()
    await expect(panel).toContainText('表或视图不存在')
    await editor.fill(
      'SELECT ' + Array.from({ length: 40 }, (_, i) => '${param' + i + '}').join(', '),
    )
    await panel.getByRole('button', { name: '查询全文', exact: true }).click()
    await expect(parameters.locator('.query-parameter-row')).toHaveCount(40)
    for (const [width, height] of [
      [1440, 1000],
      [390, 844],
      [320, 480],
    ]) {
      await page.setViewportSize({ width: width!, height: height! })
      const layout = await parameters.evaluate((el) => {
        const inner = el.querySelector('.query-parameter-scroll')!
        const footer = el.querySelector('footer')!.getBoundingClientRect()
        return {
          outer: el.scrollHeight - el.clientHeight,
          wide: el.scrollWidth - el.clientWidth,
          inner: inner.scrollHeight - inner.clientHeight,
          bottom: footer.bottom,
          edge: el.getBoundingClientRect().bottom,
        }
      })
      expect(layout.outer).toBeLessThanOrEqual(1)
      expect(layout.wide).toBeLessThanOrEqual(1)
      expect(layout.inner).toBeGreaterThan(0)
      expect(layout.bottom).toBeLessThanOrEqual(layout.edge)
      await parameters
        .getByRole('textbox', { name: 'param39', exact: true })
        .fill('visible last value')
    }
    await page.screenshot({ path: info.outputPath('many-parameters-' + theme + '.png') })
    await parameters.getByRole('button', { name: '取消', exact: true }).click()
    expect(queries).toHaveLength(5)
    expect(state.writes).toHaveLength(0)
    expect(state.tests).toHaveLength(0)
    await page.setViewportSize({ width: 360, height: 800 })
    await page.screenshot({ path: info.outputPath('sql-preview-mobile-' + theme + '.png') })
  })

  test(
    'file selection maps to transfer directory and exact glob ' + theme,
    async ({ page }, info) => {
      const state = await setupFiles(page)
      await page.addInitScript(
        (theme) =>
          localStorage.setItem(
            'ijob:preferences:v1:guest',
            JSON.stringify({ theme, locale: 'zh-CN' }),
          ),
        theme,
      )
      await page.route('**/api/schedules/metadata', (route) =>
        route.fulfill({ json: { success: true, code: 200, data: { zoneId: 'Asia/Shanghai' } } }),
      )
      const visits: string[] = []
      let closed = 0
      await page.route('**/api/file-browser/**', async (route) => {
        const request = route.request(),
          url = new URL(request.url())
        let data: unknown = null
        if (request.method() === 'POST')
          data = {
            id: '11111111-1111-4111-8111-111111111111',
            protocol: 'SFTP',
            name: 'Files',
            root: '/',
            node: 'node-a',
            expiresIn: 120,
          }
        else if (request.method() === 'DELETE') closed++
        else {
          visits.push(url.searchParams.get('path') || '')
          data = {
            path: visits.at(-1),
            total: 50,
            entries: [
              {
                name: visits.at(-1) ? 'orders[1].csv' : 'inbox',
                directory: !visits.at(-1),
                size: 42,
                modified: '2026-10-22T00:00:00Z',
              },
              ...Array.from({ length: 49 }, (_, i) => ({
                name: 'entry-' + i + '.csv',
                directory: false,
                size: 42,
                modified: '2026-10-22T00:00:00Z',
              })),
            ],
          }
        }
        return route.fulfill({ json: { success: true, code: 200, data } })
      })
      await page.goto('/tasks')
      await page.getByRole('button', { name: '编辑 Transfer fixture', exact: true }).click()
      await page
        .locator('[id="task-transfer.sourcePath"]')
        .locator('xpath=ancestor::div[contains(@class,"file-path-input")]')
        .getByRole('button')
        .click()
      const browser = page.locator('.file-browser')
      await browser.getByRole('button', { name: 'inbox', exact: true }).click()
      await browser.getByRole('radio', { name: 'orders[1].csv', exact: true }).check()
      await expect(browser.locator('thead')).toContainText('Asia/Shanghai')
      await expect(browser.locator('tbody tr').first()).toContainText('2026-10-22 08:00:00')
      await page.screenshot({ path: info.outputPath('file-browser-' + theme + '.png') })
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 })
        const layout = await browser.evaluate((el) => {
          const table = el.querySelector('.file-browser-table')!
          const content = el.querySelector('.n-card-content')!
          return {
            outer: el.scrollHeight - el.clientHeight,
            content: content.scrollHeight - content.clientHeight,
            wide: el.scrollWidth - el.clientWidth,
            table: table.scrollHeight - table.clientHeight,
            height: table.clientHeight,
            footerPadding: parseFloat(
              getComputedStyle(el.querySelector('.n-card__footer')!).paddingTop,
            ),
            footerGap:
              el.querySelector('.file-browser-footer')!.getBoundingClientRect().top -
              el.querySelector('.n-pagination')!.getBoundingClientRect().bottom,
          }
        })
        expect(layout.outer).toBeLessThanOrEqual(1)
        expect(layout.content).toBeLessThanOrEqual(1)
        expect(layout.wide).toBeLessThanOrEqual(1)
        expect(layout.table).toBeGreaterThan(0)
        expect(layout.footerPadding).toBeGreaterThanOrEqual(16)
        expect(layout.footerGap).toBeGreaterThanOrEqual(16)
        expect(layout.height).toBeGreaterThan(width === 1440 ? 500 : 300)
      }
      await page.screenshot({ path: info.outputPath('file-browser-mobile-' + theme + '.png') })
      await browser.getByRole('button', { name: '选择文件', exact: true }).last().click()
      await expect(browser).toHaveCount(0)
      await expect(page.locator('[id="task-transfer.sourcePath"]')).toHaveValue('inbox')
      await expect(page.locator('[id="task-transfer.filePattern"]')).toHaveValue(
        'orders\\[1\\].csv',
      )
      await expect.poll(() => closed).toBe(1)
      expect(visits).toEqual(['', 'inbox'])
      expect(state.writes).toHaveLength(0)
      await page.getByRole('button', { name: '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      const saved = JSON.parse(String(state.writes[0]!.body.pluginConfig))
      expect(saved.sourcePath).toBe('inbox')
      expect(saved.filePattern).toBe('orders\\[1\\].csv')
      expect(saved.recursive).toBe(false)
    },
  )
}
