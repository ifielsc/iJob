// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
export async function setup(page: Page, failure = '', permission = true) {
  const task = {
    id: '9007199254740993',
    name: 'Quality check',
    pluginType: 'DATA_CHECK',
    enabled: true,
    currentRevision: '2',
    currentTaskDefinitionId: '9007199254740994',
    groups: [],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
    pluginConfig:
      '{"resourceId":9007199254740993,"checks":[{"name":"Rows","type":"COUNT","sql":"select id from orders","compareType":"GT","expected":0,"level":"ERROR"},{"name":"No anomalies","type":"EMPTY","sql":"select id from errors","level":"WARN"}],"handlers":[],"extra":0.1234567890123456789}',
  }
  const writes: { body: Record<string, unknown>; path: string }[] = []
  await page.route(/\/api\/(auth|tasks|resources|notify-configs)([/?]|$)/, async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
    if (path === '/api/auth/me')
      return ok({
        loginId: 'check-admin',
        username: 'admin',
        displayName: 'Admin',
        roleCode: permission ? 'ADMIN' : 'VIEWER',
        permissions: permission
          ? [
              'SUMMARY_READ',
              'DEFINITION_READ',
              'SENSITIVE_READ',
              'CONFIGURATION_WRITE',
              'EXECUTION_CONTROL',
            ]
          : ['SUMMARY_READ', 'DEFINITION_READ', 'SENSITIVE_READ'],
        mustChangePassword: false,
      })
    if (request.method() !== 'GET') {
      writes.push({ body: request.postDataJSON(), path })
      if (failure === 'network') return route.abort()
      if (failure === 'conflict')
        return route.fulfill({ status: 409, json: { success: false, code: 409 } })
      return ok({ taskId: task.id, definitionId: '9007199254740995', revision: '3' })
    }
    if (path === '/api/tasks') return ok({ list: [task], total: 1 })
    if (path === '/api/tasks/' + task.id) return ok(task)
    if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
    if (path === '/api/resources')
      return ok({
        list: [{ id: '9007199254740993', name: 'Orders DB', type: 'DB', enabled: true }],
        total: 1,
      })
    if (path.startsWith('/api/resources/'))
      return ok({ id: '9007199254740993', name: 'Orders DB', type: 'DB', enabled: true })
    if (path === '/api/notify-configs')
      return ok({
        list: [{ id: '9223372036854775807', name: 'Operations', type: 'EMAIL', enabled: true }],
        total: 1,
      })
    if (path.startsWith('/api/notify-configs/'))
      return ok({ id: '9223372036854775807', name: 'Operations', type: 'EMAIL', enabled: true })
    return route.fulfill({ status: 404, json: { success: false, code: 404 } })
  })
  return { task, writes }
}
