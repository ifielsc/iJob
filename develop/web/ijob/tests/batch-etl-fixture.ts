// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
import { setupPush } from './push-fixture'
export const batchConfig =
  '{"version":1,"sourceResourceId":"9007199254740993","targetResourceId":"9223372036854775807","tables":["orders","customers"],"logicalKeys":["tenant_id","id"],"targetNaming":{"prefix":"ods_"},"extractMode":"INCREMENTAL","incremental":{"watermarkColumn":"batch_no","watermarkType":"DECIMAL","initialWatermark":9007199254740993.123456789,"initialLogicalKeyValues":[0,9007199254740993]},"future":0.1234567890123456789}'
export async function setupBatchEtl(page: Page, permission = true, failure = '') {
  const state = await setupPush(page, permission, failure)
  state.resources.forEach((r) => {
    r.type = 'DB'
  })
  state.tasks.forEach((r, i) => {
    r.pluginType = 'BATCH_ETL'
    r.name = 'Batch ETL ' + i
    r.pluginConfig = batchConfig
  })
  return state
}
