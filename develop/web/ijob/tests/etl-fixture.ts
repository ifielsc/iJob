// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
import { setupPush } from './push-fixture'
export const fileConfig =
  '{"version":1,"extra":0.1234567890123456789,"source":{"type":"FILE","resourceId":9007199254740993,"path":"/incoming/orders","format":"CSV","header":true,"vendor":9007199254740995},"target":{"type":"DATABASE","resourceId":9223372036854775807,"table":"orders","columns":{"id":"order_id","amount":"amount"},"logicalKeys":["id"],"mode":"UPSERT","transactionScope":"RUN"},"incremental":{"enabled":true,"checkpointKey":"orders-v1","watermarkType":"LONG","initialWatermark":"9007199254740993","allowCheckpointPastInvalidRecords":false,"file":{"expression":"orders_{mark}_*.csv","selectionMode":"LOOKBACK","lookback":"2"}},"transforms":[{"type":"NUMBER_SCALE","properties":{"field":"amount","multiply":0.1234567890123456789}}]}'
export async function setupEtl(page: Page, permission = true, failure = '') {
  const state = await setupPush(page, permission, failure)
  state.resources[0]!.type = 'FILE'
  state.resources[0]!.name = 'Orders files'
  state.resources[1]!.type = 'DB'
  state.resources[1]!.name = 'Analytics DB'
  for (const task of state.tasks) task.pluginType = 'ETL'
  state.tasks[0]!.name = 'File ETL'
  state.tasks[0]!.pluginConfig = fileConfig
  state.tasks[1]!.name = 'Database ETL'
  state.tasks[1]!.pluginConfig =
    '{"source":{"type":"DATABASE","resourceId":9223372036854775807,"sql":"SELECT id FROM orders WHERE id > :etlLastWatermark AND id <= :etlUpperWatermark ORDER BY id"},"target":{"type":"DATABASE","resourceId":9223372036854775807,"table":"orders_copy","columns":{"id":"id"},"mode":"UPSERT","logicalKeys":["id"]},"incremental":{"enabled":true,"checkpointKey":"db-v1","watermarkType":"LONG","initialWatermark":"0","watermarkField":"id","upperBoundSql":"SELECT MAX(id) FROM orders"}}'
  return state
}
