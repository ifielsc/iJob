import type { Page } from '@playwright/test'
import { setupWorkbench } from './workbench-fixture'
export function diagnosticData(category = 'LEASE_EXPIRED', limit = 20) {
  return {
    category,
    limit,
    observedAt: '2026-09-28T12:00:00',
    timeZone: 'UTC',
    businessZone: 'Asia/Shanghai',
    queueDelaySeconds: 300,
    hasMore: false,
    node: {
      mode: 'STANDALONE',
      roles: ['API'],
      workerRoleConfigured: false,
      schedulerRoleConfigured: false,
      scheduleEnabled: false,
      clusterWorkerHealth: 'UNKNOWN',
    },
    items: [0, 1].map((i) => ({
      id: String(30471 + i),
      triggerId: String(30471 + i),
      rootInstanceId: '9007199254740993',
      instanceId: '',
      targetType: 'TASK',
      targetId: '800',
      name: i ? '库存校验' : '订单同步',
      status: 'CLAIMED',
      observedDeadline: '2026-09-28T11:48:00',
      createTime: '2026-09-28T11:00:00',
      commandType: '',
    })),
  }
}
export async function setupDiagnostics(page: Page) {
  const base = await setupWorkbench(page, true)
  const state = {
    reads: [] as string[],
    fail: 0,
    delay: 0,
    size: 2,
    root: true,
    unknown: false,
    writes: base.writes,
  }
  await page.route(
    (url) => url.pathname === '/api/diagnostics' || /^\/api\/triggers\/\d+$/.test(url.pathname),
    async (route) => {
      const url = new URL(route.request().url())
      if (route.request().method() !== 'GET') {
        state.writes.push(url.pathname)
        return route.abort()
      }
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (url.pathname.startsWith('/api/triggers/'))
        return ok({
          id: url.pathname.split('/').pop(),
          status: 'WAITING',
          targetType: 'TASK',
          targetId: '800',
          targetName: '订单同步',
          triggerType: 'MANUAL',
          triggerTime: '2026-09-28T11:00:00',
        })
      state.reads.push(url.search)
      if (state.fail)
        return route.fulfill({ status: state.fail, json: { success: false, code: state.fail } })
      const category = url.searchParams.get('category')!,
        limit = Number(url.searchParams.get('limit'))
      const data = diagnosticData(category, limit)
      data.items = Array.from({ length: Math.min(state.size, limit) }, (_, i) => {
        const row = { ...data.items[i % 2]!, id: String(30471 + i), triggerId: String(30471 + i) }
        if (!state.root) row.rootInstanceId = ''
        if (category === 'ROOT_DEADLINE_EXCEEDED') {
          row.id = row.rootInstanceId = row.instanceId = String(9007199254740993n + BigInt(i))
          row.status = 'RUNNING'
        }
        if (category === 'COMMAND_EXPIRED') {
          row.id = 'cmd-' + i
          row.instanceId = row.rootInstanceId
          row.commandType = 'STOP'
          row.status = 'PENDING'
        }
        if (category === 'QUEUE_DELAYED') row.status = 'READY'
        if (state.unknown) row.status = 'FUTURE_STATUS'
        return row
      })
      data.hasMore = state.size > limit
      // Real JSON omits optional nulls; fixtures likewise omit empty optional identities/text.
      const response = JSON.parse(
        JSON.stringify(data, (_key, value) => (value === '' ? undefined : value)),
      )
      if (state.delay) await new Promise((resolve) => setTimeout(resolve, state.delay))
      return ok(response)
    },
  )
  return state
}
