import { expect, test, type Page } from '@playwright/test'

test('trigger detail retains recorded business zone and offset without display-zone conversion', async ({
  page,
}) => {
  await setup(page)
  await page.route('**/api/triggers/' + triggerId, (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          id: triggerId,
          targetType: 'TASK',
          targetId: '71',
          status: 'SUCCESS',
          triggerType: 'MANUAL',
          triggerTime: '2026-10-10T16:30:00',
          businessDate: '2026-10-11',
          businessTime: '2026-10-11T00:30:00+08:00',
          businessZoneId: 'Asia/Shanghai',
        },
      },
    }),
  )
  await page.goto('/runs')
  await page.getByRole('button', { name: '详情 ' + triggerId, exact: true }).click()
  await expect(page.getByText('2026-10-11T00:30:00+08:00', { exact: true })).toBeVisible()
  await expect(page.getByText('Asia/Shanghai', { exact: true })).toBeVisible()
})

for (const scope of ['self', 'run']) {
  test(`log refresh includes a late smaller ID in ${scope} scope even after completion`, async ({
    page,
  }) => {
    const state = await setup(page)
    state.status = 'SUCCESS'
    let late = false
    const rows = [
      {
        id: '9007199254741993',
        logTime: '2026-10-08T10:00:00',
        level: 'INFO',
        content: 'first large ID',
      },
      { id: '20', logTime: '2026-10-08T10:00:01', level: 'INFO', content: 'late small ID' },
    ]
    await page.route(/\/api\/instances\/[^/]+\/logs(?:\/snapshot)?(?:\?|$)/, (route) => {
      const url = new URL(route.request().url())
      const visible = rows.slice(0, late ? 2 : 1)
      const data = url.pathname.endsWith('/snapshot')
        ? { rows: visible, truncated: false }
        : visible.filter((r) => BigInt(r.id) > BigInt(url.searchParams.get('afterId') ?? '0'))
      return route.fulfill({ json: { success: true, code: 200, data } })
    })
    await page.goto('/runs/instances/' + rootId)
    await page.getByRole('checkbox', { name: '自动刷新' }).uncheck()
    await page.getByRole('tab', { name: '日志', exact: true }).click()
    if (scope === 'run') {
      await page.locator('#log-scope').click()
      await page.getByRole('option', { name: '整个作业', exact: true }).click()
    }
    await expect(page.locator('.run-code')).toContainText('first large ID')
    late = true
    await page.getByRole('button', { name: /读取后续日志|刷新日志|加载更多日志/ }).click()
    await expect(page.locator('.run-code')).toContainText('late small ID')
    await expect(page.locator('.run-log-line')).toHaveCount(2)
  })
}

test('send records show notification name above its exact ID and retain deleted IDs', async ({
  page,
}) => {
  await setup(page)
  await page.route('**/api/reference-summaries', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: [{ kind: 'NOTIFICATION', id: childId, name: '163邮件完成通知', type: 'EMAIL' }],
      },
    }),
  )
  await page.goto('/notifications?tab=records&view=attempts')
  await expect(page.getByRole('columnheader', { name: '通知配置', exact: true })).toBeVisible()
  const cell = page.locator('tbody tr').first().locator('td').nth(2)
  await expect(cell).toContainText('163邮件完成通知')
  await expect(cell).toContainText(childId)
  await page.route('**/api/reference-summaries', (route) =>
    route.fulfill({ json: { success: true, code: 200, data: [] } }),
  )
  await page.reload()
  await expect(cell).toContainText(childId)
  await expect(cell).not.toContainText('163邮件完成通知')
})

test('QA14 target types are localized and log scope/search preserve exact cursors', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/runs')
  await page.getByRole('combobox', { name: '目标类型', exact: true }).click()
  await expect(page.getByRole('option', { name: '任务', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '作业', exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await page.goto('/runs/instances/' + rootId)
  await page.getByRole('checkbox', { name: '自动刷新' }).uncheck()
  await page.getByRole('tab', { name: '日志', exact: true }).click()
  await page.locator('#log-scope').click()
  await page.getByRole('option', { name: '整个作业', exact: true }).click()
  await expect
    .poll(() => state.reads.some((p) => p.includes('/logs/snapshot?scope=run&limit=200')))
    .toBe(true)
  await page.getByRole('textbox', { name: '搜索已加载日志' }).fill('not-present')
  await expect(page.locator('.run-log-line')).toHaveCount(0)
  await page.getByRole('textbox', { name: '搜索已加载日志' }).fill('plain log')
  await expect(page.locator('.run-log-line')).toHaveCount(1)
  expect(state.posts).toHaveLength(0)
})
const rootId = '9007199254740993',
  childId = '9007199254740994',
  triggerId = '9223372036854775807'

test('completed run keeps refreshing its bounded log snapshot automatically', async ({ page }) => {
  await page.clock.install()
  const state = await setup(page)
  state.status = 'SUCCESS'
  let late = false
  await page.route('**/api/instances/*/logs/snapshot?*', (route) =>
    route.fulfill({
      json: {
        success: true,
        code: 200,
        data: {
          rows: [
            {
              id: '1001',
              logTime: '2026-10-08T10:00:00',
              level: 'INFO',
              content: 'completed initial log',
            },
            ...(late
              ? [
                  {
                    id: '20',
                    logTime: '2026-10-08T10:00:01',
                    level: 'INFO',
                    content: 'completed late log',
                  },
                ]
              : []),
          ],
          truncated: false,
        },
      },
    }),
  )
  await page.goto('/runs/instances/' + rootId)
  await page.getByRole('tab', { name: '日志', exact: true }).click()
  await expect(page.locator('.run-code')).toContainText('completed initial log')
  late = true
  await page.clock.fastForward(10001)
  await expect(page.locator('.run-code')).toContainText('completed late log')
  await page.getByRole('checkbox', { name: '自动刷新' }).uncheck()
})
const root = {
  id: rootId,
  rootInstanceId: rootId,
  parentInstanceId: null,
  targetType: 'JOB',
  targetId: '3101',
  status: 'RUNNING',
  triggerId,
  createTime: '2026-09-25T10:00:00',
  startTime: '2026-09-25T10:00:01',
  jobDefinitionId: '7101',
}
const child = {
  ...root,
  id: childId,
  parentInstanceId: rootId,
  targetType: 'TASK',
  targetId: '4102',
  taskDefinitionId: '7102',
}
async function setup(
  page: Page,
  permissions = ['SUMMARY_READ', 'SENSITIVE_READ', 'EXECUTION_CONTROL'],
) {
  const state = {
    reads: [] as string[],
    posts: [] as { path: string; key: string }[],
    commandStatus: 'PENDING',
    status: 'RUNNING',
    network: false,
    fail: false,
    variablesMissing: false,
    cursor: '',
    logNode: rootId,
    snapshotReads: 0,
  }
  await page.route(
    /\/api\/(auth|instances|triggers|runtime-commands|notify-send-logs)(?:[/?]|$)/,
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'run-admin',
          username: 'admin',
          displayName: 'Run admin',
          roleCode: 'ADMIN',
          permissions,
          mustChangePassword: false,
        })
      if (req.method() === 'POST') {
        state.posts.push({ path, key: req.headers()['idempotency-key'] ?? '' })
        if (state.network) return route.abort('failed')
        return ok({
          requestId: state.posts.at(-1)!.key,
          instanceId: rootId,
          rootInstanceId: rootId,
          status: state.commandStatus,
          commandType: 'PAUSE',
        })
      }
      state.reads.push(path + url.search)
      if (state.fail) return route.fulfill({ status: 500, json: { success: false, code: 500 } })
      if (path.startsWith('/api/runtime-commands/'))
        return ok({
          requestId: path.split('/').at(-1),
          instanceId: rootId,
          rootInstanceId: rootId,
          status: state.commandStatus,
          commandType: 'PAUSE',
          resultMessage: 'Worker checkpoint',
        })
      if (path.endsWith('/tree'))
        return ok({
          rootInstanceId: rootId,
          nodes: url.searchParams.has('includeVariables')
            ? state.variablesMissing
              ? [{ ...root, status: state.status }]
              : [
                  { ...root, status: state.status },
                  {
                    ...child,
                    inputVariables: '{"apiToken":"[REDACTED]"}',
                    variablesRedacted: true,
                    variablesTruncated: true,
                  },
                ]
            : [{ ...root, status: state.status }, child],
          gatewayDecisions: [
            {
              id: '21',
              jobInstanceId: rootId,
              definitionNodeId: '22',
              evaluationNo: 1,
              status: 'SUCCESS',
              selectedEdgeIds: '[9223372036854775807]',
            },
          ],
          truncated: url.searchParams.has('includeVariables'),
          gatewayDecisionsTruncated: false,
          maxNodes: 250,
        })
      if (path.endsWith('/graph'))
        return ok({
          instanceId: rootId,
          rootInstanceId: rootId,
          definitionId: '7101',
          name: 'Runtime fixture',
          status: state.status,
          nodes: [
            {
              id: '12',
              nodeKey: 'child',
              name: 'Child task',
              kind: 'EXECUTION',
              nodeType: 'TASK',
              refId: child.targetId,
              instanceId: childId,
              status: child.status,
            },
          ],
          edges: [],
        })
      if (path.endsWith('/logs/snapshot')) {
        state.snapshotReads++
        state.cursor = url.searchParams.get('limit')!
        state.logNode = path.split('/')[3]!
        return ok({
          rows: [
            {
              id: '9223372036854775806',
              logTime: '2026-09-25T10:00:02',
              level: 'INFO',
              content: '<script>plain log</script> ' + state.logNode,
            },
            ...(state.snapshotReads > 1
              ? [
                  {
                    id: '9223372036854775807',
                    logTime: '2026-09-25T10:00:03',
                    level: 'WARN',
                    content: 'Next checkpoint',
                  },
                ]
              : []),
          ],
          truncated: false,
        })
      }
      if (path.endsWith('/logs')) {
        state.cursor = url.searchParams.get('afterId')!
        state.logNode = path.split('/')[3]!
        return ok(
          state.cursor === '0'
            ? [
                {
                  id: '9223372036854775806',
                  logTime: '2026-09-25T10:00:02',
                  level: 'INFO',
                  content: '<script>plain log</script> ' + state.logNode,
                },
              ]
            : [
                {
                  id: '9223372036854775807',
                  logTime: '2026-09-25T10:00:03',
                  level: 'WARN',
                  content: 'Next checkpoint',
                },
              ],
        )
      }
      if (path === '/api/triggers')
        return ok({
          list: [
            {
              ...root,
              id: triggerId,
              targetName: 'Nightly snapshot',
              triggerTime: '2026-09-25T10:00:00',
              triggerType: 'MANUAL',
            },
          ],
          total: 21,
        })
      if (path === '/api/triggers/' + triggerId)
        return ok({
          ...root,
          id: triggerId,
          triggerType: 'MANUAL',
          triggerTime: '2026-09-25T10:00:00',
        })
      if (path === '/api/instances')
        return ok({ list: [{ ...root, status: state.status }], total: 1 })
      if (path === '/api/instances/' + rootId) return ok({ ...root, status: state.status })
      if (path === '/api/instances/' + childId) return ok(child)
      if (path === '/api/notify-send-logs')
        return ok({
          list: [
            {
              id: triggerId,
              notifyConfigId: childId,
              notifyType: 'DINGTALK',
              sourceType: 'INSTANCE',
              targetType: 'TASK',
              targetId: '4102',
              instanceId: childId,
              result: 'DELIVERY_FAILED',
              durationMs: 308,
              failureCode: 'CHANNEL_EXCEPTION',
              failureSummary: 'Channel delivery failed',
              createTime: '2026-09-25T10:00:00',
            },
          ],
          total: 1,
        })
      return route.fulfill({ status: 404, json: { success: false, code: 404 } })
    },
  )
  return state
}
test('filters preserve exact IDs and return context; notification records are read only', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/runs')
  await expect(page.getByText('Nightly snapshot')).toBeVisible()
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.locator('#run-targetId').fill(childId)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => state.reads.at(-1)).toContain('targetId=' + childId)
  await page.getByRole('button', { name: '详情 ' + triggerId, exact: true }).click()
  await page.getByRole('link', { name: '打开运行详情' }).click()
  await expect(page.locator('.run-tree')).toContainText(childId)
  await page.getByRole('link', { name: '返回运行中心' }).click()
  await expect(page.locator('#run-targetId')).toHaveValue(childId)
  await page.goto('/notifications?tab=records&view=attempts')
  await page.getByRole('button', { name: '详情 ' + triggerId }).click()
  await expect(page.locator('.run-drawer')).toContainText('CHANNEL_EXCEPTION')
  expect(state.posts).toHaveLength(0)
  await expect(page.locator('.run-drawer')).toContainText(childId)
  expect(state.reads.filter((p) => p.startsWith('/api/notify-send-logs/'))).toHaveLength(0)
  await page
    .locator('.run-drawer')
    .getByRole('button', { name: '关闭', exact: true })
    .last()
    .click()
  await expect(page.getByRole('button', { name: '详情 ' + triggerId })).toBeFocused()
})
test('pending control is distinct from instance status and network uncertainty never reposts', async ({
  page,
}) => {
  const state = await setup(page)
  state.network = true
  await page.goto('/runs/instances/' + rootId)
  await page.getByRole('button', { name: '暂停', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认暂停', exact: true }).click()
  await expect(page.locator('.run-command')).toContainText('待处理')
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeDisabled()
  expect(state.posts).toHaveLength(1)
  expect(state.posts[0]!.key).toMatch(/^[\da-f-]{36}$/)
  expect(state.posts[0]!.path).toBe('/api/instances/' + rootId + '/pause')
  await expect(page.locator('.run-status').first()).toContainText('执行中')
  state.commandStatus = 'REJECTED'
  await page.locator('.run-command').getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.locator('.run-command')).toContainText('已拒绝')
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeEnabled()
  expect(state.posts).toHaveLength(1)
})
test('logs keep cursor precision; child selection and bounded variable preview are independent', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/runs/instances/' + rootId)
  await page.getByRole('checkbox', { name: '自动刷新' }).uncheck()
  await page.locator('.run-node').filter({ hasText: childId }).click()
  await page.getByRole('tab', { name: '日志', exact: true }).click()
  await expect(page.locator('.run-code')).toContainText('<script>plain log</script> ' + childId)
  await page.getByRole('button', { name: '加载更多日志' }).click()
  await expect.poll(() => state.cursor).toBe('400')
  await expect(page.locator('.run-code')).toContainText('Next checkpoint')
  await page.getByRole('tab', { name: '变量', exact: true }).click()
  await expect(page.getByText('部分变量已脱敏。')).toBeVisible()
  await expect(page.getByText(/当前不是完整数据/)).toBeVisible()
  state.variablesMissing = true
  await page
    .locator('.run-layout>section')
    .getByRole('button', { name: '刷新', exact: true })
    .click()
  await expect(page.getByText('所选节点超出本次变量预览范围。')).toBeVisible()
  await expect(page.locator('.run-node')).toHaveCount(2)
})
test('summary reader cannot fetch logs or execute controls', async ({ page }) => {
  const state = await setup(page, ['SUMMARY_READ'])
  await page.goto('/runs/instances/' + rootId)
  await expect(page.locator('.run-node')).toHaveCount(2)
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toHaveCount(0)
  await page.getByRole('tab', { name: '日志', exact: true }).click()
  await expect(page.getByText('没有读取日志的权限。')).toBeVisible()
  expect(state.reads.some((p) => p.includes('/logs'))).toBe(false)
  expect(state.posts).toHaveLength(0)
})
test('retry is root only and confirms original version; validation blocks invalid send filters', async ({
  page,
}) => {
  const state = await setup(page)
  state.status = 'FAILED'
  await page.goto('/runs/instances/' + childId)
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.n-dialog')).toContainText('同一根实例')
  await expect(page.locator('.n-dialog')).toContainText(rootId)
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  expect(state.posts).toHaveLength(0)
  await page.goto('/notifications?tab=records&view=attempts')
  await expect(page.locator('.run-table tbody tr')).toHaveCount(1)
  await page.locator('#run-from').fill('2026-01-01 00:00:00')
  const before = state.reads.length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('#run-from-error')).toBeVisible()
  expect(state.reads.length).toBe(before)
})
test('invalid long instance URL shows an error without sending a rounded ID', async ({ page }) => {
  const state = await setup(page)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/runs/instances/9223372036854775808')
  await expect(page.locator('.request-error')).toBeVisible()
  expect(state.reads.filter((p) => p.includes('/instances/'))).toHaveLength(0)
  expect(errors).toEqual([])
})
test('server error is never rendered as empty records', async ({ page }) => {
  const state = await setup(page)
  state.fail = true
  await page.goto('/notifications?tab=records&view=attempts')
  await expect(page.locator('.request-error')).toBeVisible()
  await expect(page.getByText('没有符合条件的记录。')).toHaveCount(0)
})
test('tree folding and latest command survive returning from the run list', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/runs/instances/' + rootId)
  await expect(page.locator('.run-node')).toHaveCount(2)
  await page.locator('.run-disclosure').click()
  await expect(page.locator('.run-node')).toHaveCount(1)
  await page.locator('.run-disclosure').click()
  await expect(page.locator('.run-node')).toHaveCount(2)
  await page.getByRole('button', { name: '暂停', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认暂停', exact: true }).click()
  await expect(page.locator('.run-command')).toContainText('待处理')
  await page.getByRole('link', { name: '返回运行中心' }).click()
  await page.getByRole('tab', { name: '执行实例', exact: true }).click()
  await page.getByRole('button', { name: '详情 ' + rootId }).click()
  await expect(page.locator('.run-command')).toContainText(state.posts[0]!.key)
  expect(state.posts).toHaveLength(1)
  state.commandStatus = 'EXPIRED'
  await page.locator('.run-command').getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.locator('.run-command')).toContainText('已过期')
})
test('successful pause does not invent execution success; resume targets the root', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/runs/instances/' + rootId)
  await page.getByRole('button', { name: '暂停', exact: true }).click()
  state.commandStatus = 'SUCCEEDED'
  state.status = 'PAUSED'
  await page.locator('.n-dialog').getByRole('button', { name: '确认暂停', exact: true }).click()
  await expect(page.locator('.run-command')).toContainText('处理成功')
  await expect(page.getByRole('button', { name: '恢复', exact: true })).toBeEnabled()
  await page.locator('.run-node').filter({ hasText: childId }).click()
  await page.getByRole('button', { name: '恢复', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认恢复', exact: true }).click()
  await expect.poll(() => state.posts.length).toBe(2)
  expect(state.posts[1]!.path).toBe('/api/instances/' + rootId + '/resume')
  expect(state.posts[1]!.key).not.toBe(state.posts[0]!.key)
})
test('switching nodes discards a delayed log response', async ({ page }) => {
  await setup(page)
  let release: () => void = () => {},
    started = false
  const held = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route(new RegExp('/api/instances/' + rootId + '/logs'), async (route) => {
    started = true
    await held
    await route
      .fulfill({
        json: {
          success: true,
          code: 200,
          data: [
            {
              id: '1',
              logTime: '2026-09-25T10:00:00',
              level: 'INFO',
              content: 'OLD_NODE_RESPONSE',
            },
          ],
        },
      })
      .catch(() => {})
  })
  await page.goto('/runs/instances/' + rootId)
  await page.getByRole('checkbox', { name: '自动刷新' }).uncheck()
  await page.getByRole('tab', { name: '日志', exact: true }).click()
  await expect.poll(() => started).toBe(true)
  await page.locator('.run-node').filter({ hasText: childId }).click()
  await expect(page.locator('.run-code')).toContainText(childId)
  release()
  await expect(page.locator('.run-code')).not.toContainText('OLD_NODE_RESPONSE')
})
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US'])
    test(`runtime layout ${theme} ${locale}`, async ({ page }, info) => {
      await page.addInitScript(
        ({ theme, locale }) => {
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale }))
          localStorage.setItem(
            'ijob:preferences:v1:user:run-admin',
            JSON.stringify({ theme, locale }),
          )
        },
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/runs/instances/' + rootId)
      await expect(page.locator('.run-node')).toHaveCount(2)
      await page
        .getByRole('tab', { name: locale === 'zh-CN' ? '日志' : 'Logs', exact: true })
        .click()
      await expect(page.locator('.run-code')).toContainText('plain log')
      await page.screenshot({ path: info.outputPath('runtime-desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({ path: info.outputPath('runtime-mobile.png'), fullPage: true })
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      ).toBe(true)
      await page.goto('/notifications?tab=records&view=attempts')
      await page
        .getByRole('button', { name: (locale === 'zh-CN' ? '详情 ' : 'Details ') + triggerId })
        .click()
      await expect(page.locator('.run-drawer')).toContainText('CHANNEL_EXCEPTION')
      await expect
        .poll(() =>
          page.locator('.run-drawer').evaluate((e) => Math.round(e.getBoundingClientRect().right)),
        )
        .toBe(390)
      await page.screenshot({ path: info.outputPath('send-mobile.png'), fullPage: true })
      expect(
        await page
          .locator('.run-drawer')
          .evaluate((e) => e.getBoundingClientRect().width <= innerWidth + 1),
      ).toBe(true)
    })

test('query always fetches once, retains page, and revisits recent conditions without cache-only results', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/runs')
  const query = page.getByRole('button', { name: '查询', exact: true })
  const reads = () => state.reads.filter((path) => path.startsWith('/api/triggers?'))
  await expect(page.locator('.run-table tbody tr')).toHaveCount(1)
  const from = await page.locator('#run-from').inputValue(),
    to = await page.locator('#run-to').inputValue()
  expect(Date.parse(to.replace(' ', 'T') + 'Z') - Date.parse(from.replace(' ', 'T') + 'Z')).toBe(
    86400000,
  )
  await expect(
    page.locator('.runs-page').getByRole('button', { name: '刷新', exact: true }),
  ).toHaveCount(0)
  let before = reads().length
  await query.click()
  await expect(query).not.toHaveClass(/loading/)
  expect(reads()).toHaveLength(before + 1)
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect
    .poll(() => new URL('http://local' + reads().at(-1)).searchParams.get('pageNum'))
    .toBe('2')
  before = reads().length
  await query.click()
  await expect(query).not.toHaveClass(/loading/)
  expect(reads()).toHaveLength(before + 1)
  expect(new URL('http://local' + reads().at(-1)).searchParams.get('pageNum')).toBe('2')
  for (const name of ['A', 'B', 'A']) {
    await page.locator('#run-name').fill(name)
    before = reads().length
    await query.click()
    await expect(query).not.toHaveClass(/loading/)
    expect(reads()).toHaveLength(before + 1)
    expect(new URL('http://local' + reads().at(-1)).searchParams.get('name')).toBe(name)
    expect(new URL('http://local' + reads().at(-1)).searchParams.get('pageNum')).toBe('1')
  }
  expect(await page.locator('#run-from').inputValue()).toBe(from)
  expect(await page.locator('#run-to').inputValue()).toBe(to)
})

test('tab keyboard navigation preserves applied filters and explicitly cleared dates survive detail return', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/runs')
  await expect(page.locator('.run-table tbody tr')).toHaveCount(1)
  await page.locator('#run-name').fill('remembered')
  await page.locator('#run-from').fill('')
  await page.locator('#run-to').fill('')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page).toHaveURL(/name=remembered/)
  const triggers = page.getByRole('tab', { name: '触发记录', exact: true })
  const instances = page.getByRole('tab', { name: '执行实例', exact: true })
  await triggers.focus()
  await triggers.press('ArrowRight')
  await expect(instances).toBeFocused()
  await expect(triggers).toHaveAttribute('aria-selected', 'true')
  await instances.press('Enter')
  await expect(page.locator('#run-name')).toHaveValue('')
  await expect(page.getByRole('checkbox', { name: '仅根实例' })).toBeChecked()
  await triggers.click()
  await expect(page.locator('#run-name')).toHaveValue('remembered')
  await expect(page.locator('#run-from')).toHaveValue('')
  await expect(page.locator('#run-to')).toHaveValue('')
  await page.getByRole('button', { name: '详情 ' + triggerId, exact: true }).click()
  await page.getByRole('link', { name: '打开运行详情' }).click()
  await page.getByRole('link', { name: '返回运行中心' }).click()
  await expect(page.locator('#run-name')).toHaveValue('remembered')
  await expect(page.locator('#run-from')).toHaveValue('')
})

for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    test(`run list query layout ${theme} ${locale}`, async ({ page }, info) => {
      await page.addInitScript(
        ({ theme, locale }) =>
          localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify({ theme, locale })),
        { theme, locale },
      )
      await setup(page)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/runs')
      await expect(page.locator('.run-table tbody tr')).toHaveCount(1)
      const from = (await page.locator('#run-from').boundingBox())!,
        to = (await page.locator('#run-to').boundingBox())!
      expect(Math.abs(from.y - to.y)).toBeLessThan(2)
      await expect(page.locator('.subtitle')).toHaveText(
        locale === 'zh-CN' ? '查询触发记录与执行实例' : 'Query triggers and execution instances',
      )
      for (const field of await page.locator('.run-filters').first().locator('.run-field').all()) {
        const label = (await field.locator('label').boundingBox())!
        const control = (await field.locator(':scope > :last-child').boundingBox())!
        expect(control.width).toBeGreaterThanOrEqual(120)
        expect(control.x).toBeGreaterThan(label.x + label.width)
        expect(Math.abs(label.y + label.height / 2 - control.y - control.height / 2)).toBeLessThan(
          3,
        )
      }
      await page.screenshot({ path: info.outputPath('run-list-desktop.png'), fullPage: true })
      for (const width of [768, 390]) {
        await page.setViewportSize({ width, height: 844 })
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
        ).toBe(true)
        await page.screenshot({ path: info.outputPath(`run-list-${width}.png`), fullPage: true })
      }
      await page.goto('/notifications?tab=records&view=attempts')
      await expect(page.locator('.run-table tbody tr')).toHaveCount(1)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.screenshot({ path: info.outputPath('send-list-desktop.png'), fullPage: true })
    })
  }
