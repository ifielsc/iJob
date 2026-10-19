// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from '@playwright/test'
test('production runtime graph loads for summary-only users', async ({ page }) => {
  const root = {
    id: '9007199254740993',
    rootInstanceId: '9007199254740993',
    targetId: '10',
    targetType: 'JOB',
    jobDefinitionId: '20',
    status: 'SUCCESS',
  }
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      const data =
        path === '/api/auth/me'
          ? {
              loginId: 'viewer',
              username: 'viewer',
              displayName: 'Viewer',
              roleCode: 'VIEWER',
              permissions: ['SUMMARY_READ'],
              mustChangePassword: false,
            }
          : path.endsWith('/graph')
            ? {
                instanceId: root.id,
                rootInstanceId: root.id,
                name: '执行版本快照',
                definitionId: '20',
                status: 'SUCCESS',
                nodes: [
                  {
                    id: '30',
                    nodeKey: 'step',
                    name: '已完成步骤',
                    kind: 'EXECUTION',
                    nodeType: 'TASK',
                    refId: '40',
                    status: 'SUCCESS',
                  },
                ],
                edges: [],
              }
            : path.endsWith('/tree')
              ? { rootInstanceId: root.id, nodes: [root], gatewayDecisions: [] }
              : root
      await route.fulfill({ json: { success: true, code: 200, data } })
    },
  )
  await page.goto('/runs/instances/' + root.id)
  await expect(page.locator('.run-graph-canvas .job-node')).toContainText('已完成步骤')
  await expect(page.locator('.run-graph-canvas .job-node')).toContainText('执行成功')
  expect(errors).toEqual([])
})
