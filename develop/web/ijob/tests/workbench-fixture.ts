// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
import { workbenchData } from './workbench-data'
export async function setupWorkbench(page: Page, viewer = false) {
  const state = {
    data: workbenchData(),
    reads: [] as string[],
    writes: [] as string[],
    fail: 0,
    delay: 0,
  }
  if (viewer) {
    state.data.approvalsVisible = false
    state.data.pendingApprovals = null
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      if (req.method() !== 'GET') {
        state.writes.push(path)
        return route.abort('failed')
      }
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'workbench-user',
          username: 'reviewer',
          displayName: 'Reviewer',
          roleCode: viewer ? 'VIEWER' : 'OPERATOR',
          permissions: [
            'SUMMARY_READ',
            'DEFINITION_READ',
            'SELF_PASSWORD_CHANGE',
            ...(viewer ? [] : ['APPROVAL_DECIDE']),
          ],
          mustChangePassword: false,
        })
      state.reads.push(url.pathname + url.search)
      if (path === '/api/workbench/runtime')
        return ok({
          queriedAt: '2026-09-28T10:00:01',
          staleAfterSeconds: 30,
          mode: 'CLUSTER',
          online: '1',
          workers: '1',
          stale: '1',
          starting: '0',
          queued: '3',
          claimed: '2',
          expired: '0',
          oldestQueuedAt: '2026-09-28T09:59:55',
          truncated: false,
          nodes: [
            {
              runId: 'run-a',
              name: 'node-a',
              roles: 'API,WORKER',
              groups: 'demo',
              status: 'ONLINE',
              startedAt: '2026-09-28T09:00:00',
              heartbeatAt: '2026-09-28T10:00:00',
              health: 'BUSY',
              active: '2',
              capacity: '8',
              renewalFailures: '0',
              lastScanAt: '2026-09-28T10:00:00',
              lastRenewAt: '2026-09-28T09:59:59',
              scheduleEnabled: false,
            },
            {
              runId: 'run-b',
              name: 'node-b',
              roles: 'WORKER',
              groups: 'demo',
              status: 'STALE',
              heartbeatAt: '2026-09-28T09:59:00',
              health: 'UNAVAILABLE',
            },
          ],
          history: [],
        })
      if (path === '/api/workbench') {
        if (state.fail)
          return route.fulfill({
            status: state.fail,
            json: { success: false, code: state.fail, message: 'Unavailable' },
          })
        const response = structuredClone(state.data)
        if (url.searchParams.has('from')) response.from = url.searchParams.get('from')!
        if (url.searchParams.has('to')) response.to = url.searchParams.get('to')!
        if (state.delay) await new Promise((resolve) => setTimeout(resolve, state.delay))
        return ok(response)
      }
      if (path === '/api/instances' || path === '/api/triggers' || path === '/api/event-waits')
        return ok({ list: [], total: 0, pageNum: 1, pageSize: 20 })
      const root = {
        id: '9007199254740993',
        rootInstanceId: '9007199254740993',
        targetId: '800',
        targetType: 'TASK',
        status: 'FAILED',
        createTime: state.data.from,
      }
      if (path.endsWith('/tree'))
        return ok({
          rootInstanceId: root.id,
          nodes: [root],
          gatewayDecisions: [],
          truncated: false,
          maxNodes: 2000,
        })
      if (path === '/api/instances/' + root.id) return ok(root)
      return ok([])
    },
  )
  return state
}
