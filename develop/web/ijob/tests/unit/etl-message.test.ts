// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect } from 'vitest'
import {
  etlDraft,
  etlConfig,
  etlIssues,
  sourceKeys,
  targetKeys,
} from '../../src/modules/tasks/etl-model'
function message(type = 'KAFKA') {
  return etlDraft(
    JSON.stringify({
      version: 2,
      source: {
        type,
        resourceId: 1,
        topic: 'orders',
        groupId: 'etl-orders',
        queue: 'orders',
        initialPosition: 'EARLIEST',
        prefetch: 100,
      },
      target: {
        type: 'DATABASE',
        resourceId: 2,
        table: 'orders',
        mode: 'UPSERT',
        transactionScope: 'BATCH',
        mappingMode: 'AUTO',
        logicalKeys: ['id'],
      },
      message: {
        maxMessages: 10000,
        intakeSeconds: 60,
        idleSeconds: 5,
        batchSize: 100,
        flushIntervalMillis: 1000,
        metadataColumns: {},
      },
    }),
  )
}
describe('bounded message ETL forms', () => {
  it.each(['KAFKA', 'RABBITMQ'])('round trips %s as v2 with limits and database target', (type) => {
    const d = message(type)
    expect(etlIssues(d)).toEqual({})
    const c = JSON.parse(etlConfig(d))
    expect(c.version).toBe(2)
    expect(c.source.type).toBe(type)
    expect(c.message.maxMessages).toBe(10000)
    expect(c.target.transactionScope).toBe('BATCH')
    expect(targetKeys(d)).not.toContain('beforeSql')
    expect(sourceKeys(d)).not.toContain('path')
  })
  it('rejects invalid quotas without throwing and shows metadata collisions', () => {
    const d = message()
    d.message.maxMessageBytes = 'invalid'
    d.message.maxMessages = '0'
    d.metadataColumns = [
      { key: 'topic', field: 'meta' },
      { key: 'offset', field: 'meta' },
    ]
    expect(etlIssues(d)['etl.message.maxMessageBytes']).toBeTruthy()
    expect(etlIssues(d)['etl.message.maxMessages']).toBeTruthy()
    expect(etlIssues(d)['etl.message.metadataColumns']).toBeTruthy()
  })
  it('prevents non-idempotent targets and malformed subscription identifiers', () => {
    const d = message()
    d.target.mode = 'APPEND'
    d.source.groupId = 'bad group'
    expect(etlIssues(d)['etl.target.mode']).toBeTruthy()
    expect(etlIssues(d)['etl.source.groupId']).toBeTruthy()
  })
  it('keeps legacy database configs on v1 without a message section', () => {
    const d = etlDraft()
    d.source.type = 'DATABASE'
    const c = JSON.parse(etlConfig(d))
    expect(c.version).toBe(1)
    expect(c.message).toBeUndefined()
  })
})
