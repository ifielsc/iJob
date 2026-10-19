// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect } from 'vitest'
import { parse, LosslessNumber } from 'lossless-json'
import {
  batchEtlDraft,
  batchEtlConfig,
  batchEtlIssues,
} from '../../src/modules/tasks/batch-etl-model'
import { draftFor, taskBody, type TaskRow } from '../../src/modules/tasks/model'
const json =
  '{"sourceResourceId":9007199254740993,"targetResourceId":"9223372036854775807","tables":["orders","customers"],"logicalKeys":["tenant_id","id"],"extractMode":"INCREMENTAL","incremental":{"watermarkColumn":"mark","watermarkType":"DECIMAL","initialWatermark":9007199254740993.123456789,"initialLogicalKeyValues":[9007199254740993,0.1234567890123456789],"future":12345678901234567890},"targetNaming":{"prefix":"ods_","future":true},"execution":{"maxTotalRows":9223372036854775807,"future":1.234567890123456789},"future":0.9876543210987654321}'
describe('batch ETL', () => {
  it('keeps exact numbers and unknown fields at each object level', () => {
    const d = batchEtlDraft(json)
    expect(d.blocked).toBe(false)
    expect(batchEtlIssues(d)).toEqual({})
    const saved = batchEtlConfig(d)
    for (const n of [
      '9007199254740993.123456789',
      '0.1234567890123456789',
      '12345678901234567890',
      '9223372036854775807',
      '1.234567890123456789',
      '0.9876543210987654321',
    ])
      expect(saved).toContain(n)
    expect(
      (parse(saved) as { incremental: { initialWatermark: unknown } }).incremental.initialWatermark,
    ).toBeInstanceOf(LosslessNumber)
  })
  it('keeps original string scalar kinds', () => {
    const d = batchEtlDraft(
      json.replace('9007199254740993.123456789', '"9007199254740993.123456789"'),
    )
    expect(d.initial.kind).toBe('string')
    expect(batchEtlConfig(d)).toContain('"initialWatermark":"9007199254740993.123456789"')
  })
  it.each([
    '{"version":2}',
    '{"incremental":null}',
    '{"loadMode":"UNKNOWN"}',
    '{"tables":null}',
    '{"execution":{"maxTotalRows":1.5}}',
  ])('blocks unsupported or damaged structures %s', (override) => {
    const c = { ...JSON.parse(json), ...JSON.parse(override) }
    const d = batchEtlDraft(JSON.stringify(c))
    expect(d.blocked).toBe(true)
    expect(() => batchEtlConfig(d)).toThrow()
  })
  it('validates duplicate table and key names and exact 64 bit limits', () => {
    const d = batchEtlDraft(json)
    d.tables.push({ name: 'ORDERS' })
    d.keys[1]!.name = 'TENANT_ID'
    d.limits.maxTotalRows = '9223372036854775808'
    expect(Object.keys(batchEtlIssues(d))).toEqual(
      expect.arrayContaining([
        'batchEtl.tables.2',
        'batchEtl.keys.1.name',
        'batchEtl.limits.maxTotalRows',
      ]),
    )
  })
  it('retains inactive draft while omitting incompatible clear options', () => {
    const d = batchEtlDraft(json)
    d.clearBeforeImport = true
    d.clearMode = 'TRUNCATE'
    d.acknowledgeNonTransactionalTruncate = true
    const c = JSON.parse(batchEtlConfig(d))
    expect(c.clearBeforeImport).toBe(false)
    expect(c.clearMode).toBe('DELETE')
    expect(d.clearBeforeImport).toBe(true)
    d.extractMode = 'FULL'
    expect(batchEtlConfig(d)).toContain('"future":12345678901234567890')
    expect(d.initial.text).toBe('9007199254740993.123456789')
  })
  it('requires explicit clear and append retry acknowledgements', () => {
    const d = batchEtlDraft(json)
    d.extractMode = 'FULL'
    d.clearBeforeImport = true
    expect(batchEtlIssues(d)).toHaveProperty(['batchEtl.acknowledgePartialCommit'])
    d.acknowledgePartialCommit = true
    d.loadMode = 'APPEND'
    expect(batchEtlIssues(d, 1)).toHaveProperty(['batchEtl.allowNonIdempotentRetry'])
    d.allowNonIdempotentRetry = true
    expect(batchEtlIssues(d, 1)).toEqual({})
    d.extractMode = 'INCREMENTAL'
    expect(batchEtlIssues(d, 1)).toHaveProperty(['batchEtl.loadMode'])
  })
  it('moves initial values with key rows and rejects incomplete numeric values', () => {
    const d = batchEtlDraft(json)
    d.keys.reverse()
    expect(JSON.parse(batchEtlConfig(d)).logicalKeys).toEqual(['id', 'tenant_id'])
    d.keys[0]!.initial.text = '-'
    expect(batchEtlIssues(d)).toHaveProperty(['batchEtl.keys.0.initial'])
  })
  it('integrates with existing versioned task publication', () => {
    const row = {
      id: '1',
      name: 'Batch',
      pluginType: 'BATCH_ETL',
      enabled: true,
      currentRevision: '9',
      pluginConfig: json,
      groups: [],
      retryTimes: 0,
      retryInterval: 0,
      timeout: null,
      description: '',
    } as unknown as TaskRow
    const draft = draftFor(row)
    expect(draft.pluginType).toBe('BATCH_ETL')
    expect(taskBody(draft, row).pluginConfig).toContain('9007199254740993.123456789')
  })
})
