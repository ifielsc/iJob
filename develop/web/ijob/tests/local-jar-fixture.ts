// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
import { setupPush } from './push-fixture'
export const localJarConfig =
  '{"formatVersion":1,"jarFile":"orders.jar","sha256":"' +
  'a'.repeat(64) +
  '","entryClass":"sample.OrderTask","jarSize":4096,"implementationVersion":"1.0","spiVersion":"1","userConfig":{"batchSize":9007199254740993,"regions":["cn","us"],"ratio":0.1234567890123456789,"enabled":true,"optional":null}}'
export async function setupLocalJar(page: Page, permission = true, failure = '') {
  const s = await setupPush(page, permission, failure)
  s.tasks.forEach((r, i) => {
    r.name = 'Local JAR ' + i
    r.pluginType = 'LOCAL_JAR'
    r.pluginConfig = localJarConfig
  })
  return s
}
