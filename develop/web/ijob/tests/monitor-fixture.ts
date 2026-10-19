// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
import { setupPush } from './push-fixture'
export const monitorConfig =
  '{"resourceId":"9007199254740993","collect":{"items":["CPU","MEMORY","DISK","NETWORK","APPLICATION"],"applications":[{"name":"Orders","matchType":"COMMAND","matchValue":"orders.jar","required":true,"allowMultiple":false}]},"rules":{"cpu":{"usage":{"warning":80,"critical":90}},"memory":{"usage":{"warning":80,"critical":90}},"disk":{"usage":{"warning":85,"critical":95},"paths":{"/data":{"warning":80,"critical":90}}},"network":{"enabled":false},"applications":[{"name":"Orders","memory":{"warning":2048,"critical":3072}}]},"notify":{"enabled":false},"future":0.1234567890123456789}'
export async function setupMonitor(page: Page, permission = true, failure = '') {
  const state = await setupPush(page, permission, failure)
  state.tasks.forEach((r, i) => {
    r.pluginType = 'SERVER_MONITOR'
    r.name = 'Monitor ' + i
    r.pluginConfig = monitorConfig
  })
  state.resources.splice(1)
  Object.assign(state.resources[0]!, {
    name: 'Monitor Agent',
    type: 'MONITOR_SERVER',
    config: {
      protocol: 'HTTP',
      endpoint: 'http://agent.invalid/base',
      connectTimeout: 5000,
      readTimeout: 10000,
      osType: 'LINUX',
      agentVersion: '1.0',
      tokenConfigured: true,
      headerNames: ['X-Key'],
    },
  })
  return state
}
