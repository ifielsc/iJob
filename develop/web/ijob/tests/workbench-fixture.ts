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
