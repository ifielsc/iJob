import { test, expect } from '@playwright/test'
import { setupPush } from '../push-fixture'

test.use({ viewport: { width: 1440, height: 1000 } })
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    for (const type of ['KAFKA', 'RABBITMQ']) {
      test(`${type} bounded form ${theme} ${locale}`, async ({ page }, info) => {
        const state = await setupPush(page),
          en = locale === 'en-US',
          errors: string[] = []
        page.on('pageerror', (e) => errors.push(e.message))
        state.tasks[0]!.pluginType = 'ETL'
        state.tasks[0]!.name = 'Message ETL'
        state.tasks[0]!.pluginConfig = JSON.stringify({
          version: 2,
          source: {
            type,
            resourceId: type === 'KAFKA' ? '9223372036854775807' : '23',
            topic: 'orders',
            groupId: 'etl-orders',
            queue: 'orders',
            initialPosition: 'EARLIEST',
            prefetch: 100,
          },
          target: {
            type: 'DATABASE',
            resourceId: '9007199254740993',
            table: 'orders',
            mode: 'UPSERT',
            mappingMode: 'AUTO',
            logicalKeys: ['id'],
            transactionScope: 'BATCH',
          },
          message: {
            maxMessages: 10000,
            intakeSeconds: 60,
            idleSeconds: 5,
            batchSize: 100,
            flushIntervalMillis: 1000,
          },
        }).replace(/"(9223372036854775807|9007199254740993|23)"/g, '$1')
        await page.addInitScript(
          (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
          { theme, locale },
        )
        await page.goto('/tasks')
        await page
          .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' Message ETL', exact: true })
          .click()
        await expect(page.locator('.task-drawer')).toHaveCSS('width', '640px')
        await expect(
          page.getByRole('textbox', { name: en ? 'Message format' : '消息格式', exact: true }),
        ).toHaveValue('JSON')
        if (type === 'RABBITMQ') await expect(page.locator('.message-vhost input')).toHaveValue('/')
        await page.screenshot({ path: info.outputPath('source.png'), fullPage: true })
        await page
          .getByRole('tab', { name: en ? 'Consumption & limits' : '消费与限制', exact: true })
          .click()
        await expect(page.locator('[id="task-etl.message.maxMessages"]')).toHaveValue('10000')
        await page.locator('[id="task-etl.message.maxMessages"]').fill('0')
        await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
        expect(state.writes).toHaveLength(0)
        await page.locator('[id="task-etl.message.maxMessages"]').fill('250')
        await page.screenshot({ path: info.outputPath('limits.png'), fullPage: true })
        await page.getByRole('tab', { name: en ? 'Target' : '目标', exact: true }).click()
        await expect(page.locator('[id="task-etl.target.type"]')).toBeVisible()
        await page.screenshot({ path: info.outputPath('target.png'), fullPage: true })
        await page.setViewportSize({ width: 320, height: 844 })
        expect(
          await page.locator('.etl-fields').evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
        ).toBe(true)
        await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
        await expect.poll(() => state.writes.length).toBe(1)
        const saved = JSON.parse(String(state.writes[0]!.body.pluginConfig))
        expect(saved.version).toBe(2)
        expect(saved.source.type).toBe(type)
        expect(saved.message.maxMessages).toBe(250)
        expect(saved.target.transactionScope).toBe('BATCH')
        expect(errors).toEqual([])
      })
    }
