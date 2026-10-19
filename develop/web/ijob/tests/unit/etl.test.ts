// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect } from 'vitest'
import {
  etlDraft,
  prepareEtlForms,
  etlConfig,
  etlIssues,
  sourceKeys,
  fileKeys,
} from '../../src/modules/tasks/etl-model'
import { draftFor, taskBody, issues } from '../../src/modules/tasks/model'
import { parse } from 'lossless-json'

export const fileConfig =
  '{"version":1,"extra":0.1234567890123456789,"source":{"type":"FILE","resourceId":9007199254740993,"path":"/incoming/orders","format":"CSV","header":true,"vendor":9007199254740995},"target":{"type":"DATABASE","resourceId":9223372036854775807,"table":"orders","columns":{"id":"order_id","amount":"amount"},"logicalKeys":["id"],"mode":"UPSERT","transactionScope":"RUN"},"incremental":{"enabled":true,"checkpointKey":"orders-v1","watermarkType":"LONG","initialWatermark":"9007199254740993","allowCheckpointPastInvalidRecords":false,"file":{"expression":"orders_{mark}_*.csv","selectionMode":"LOOKBACK","lookback":"2"}},"transforms":[{"type":"NUMBER_SCALE","properties":{"field":"amount","multiply":0.1234567890123456789}}]}'
describe('ETL contracts', () => {
  it('defaults new tasks to AUTO while keeping old and explicit MANUAL tasks manual', () => {
    expect(etlDraft().target.mappingMode).toBe('AUTO')
    expect(etlDraft(fileConfig).target.mappingMode).toBe('MANUAL')
    expect(JSON.parse(etlConfig(etlDraft(fileConfig))).target.mappingMode).toBe('MANUAL')
  })
  it('allows automatic mapping without explicit columns but still requires logical keys', () => {
    const d = etlDraft(fileConfig)
    d.target.mappingMode = 'AUTO'
    d.columns = []
    d.target.excludedFields = 'secret, audit_note'
    expect(etlIssues(d)).toEqual({})
    const target = JSON.parse(etlConfig(d)).target
    expect(target.mappingMode).toBe('AUTO')
    expect(target.columns).toEqual({})
    expect(target.excludedFields).toEqual(['secret', 'audit_note'])
    d.target.logicalKeys = ''
    expect(etlIssues(d)['etl.target.logicalKeys']).toBeTruthy()
  })
  it('rejects excluded override sources and preserves optional drafts when switching modes', () => {
    const d = etlDraft(fileConfig)
    d.target.mappingMode = 'AUTO'
    d.target.excludedFields = 'amount'
    expect(etlIssues(d)['etl.target.excludedFields']).toBe('excludedConflict')
    d.target.mappingMode = 'MANUAL'
    expect(etlIssues(d)).toEqual({})
    expect(JSON.parse(etlConfig(d)).target.excludedFields).toBeUndefined()
    d.columns = []
    expect(etlIssues(d)['etl.target.columns']).toBe('required')
    d.target.mappingMode = 'AUTO'
    expect(d.target.excludedFields).toBe('amount')
  })
  it('round trips IDs, exact decimals, extension fields and marker strings without rounding', () => {
    const d = etlDraft(fileConfig)
    expect(etlIssues(d)).toEqual({})
    const text = etlConfig(d)
    expect(text).toContain('9007199254740993')
    expect(text).toContain('9223372036854775807')
    expect(text.match(/0.1234567890123456789/g)).toHaveLength(2)
    expect(text).toContain('"vendor":9007199254740995')
    expect(etlDraft(text).incremental.initialWatermark).toBe('9007199254740993')
  })
  it('integrates ETL publication with existing task body and general retry validation', () => {
    const d = draftFor()
    d.pluginType = 'ETL'
    d.name = 'Orders'
    d.etl = etlDraft(fileConfig)
    expect(issues(d)).toEqual({})
    expect(taskBody(d).pluginConfig).toBe(etlConfig(d.etl))
  })
  it('clears inactive file marker fields when switching to database and removes file parsing fields', () => {
    const d = etlDraft(fileConfig)
    d.source.type = 'DATABASE'
    d.source.sql =
      'SELECT * FROM orders WHERE id > :etlLastWatermark AND id <= :etlUpperWatermark ORDER BY id'
    d.incremental.watermarkField = 'id'
    d.incremental.upperBoundSql = 'SELECT MAX(id) FROM orders'
    expect(etlIssues(d)).toEqual({})
    const text = etlConfig(d)
    expect(text).not.toContain('"file":')
    expect(text).not.toContain('"path":')
    expect(text).not.toContain('"format":')
    expect(text).toContain('"vendor":9007199254740995')
  })
  it('removes disabled incremental fields without deleting the in-memory draft', () => {
    const d = etlDraft(fileConfig)
    d.incremental.enabled = false
    const c = JSON.parse(etlConfig(d))
    expect(c.incremental).toEqual({ enabled: false })
    expect(d.file.expression).toBe('orders_{mark}_*.csv')
  })
  it('preserves exact JSON rule values when reordering or editing rules', () => {
    const d = etlDraft(fileConfig)
    d.transforms.unshift({
      type: 'TRIM',
      ruleId: '',
      onFailure: 'FAIL_TASK',
      properties: '{"fields":["name"]}',
      original: '{}',
    })
    expect(JSON.parse(etlConfig(d)).transforms.map((r: { type: string }) => r.type)).toEqual([
      'TRIM',
      'NUMBER_SCALE',
    ])
    expect(etlConfig(d)).toContain('0.1234567890123456789')
  })
  it('allows SCD2 business keys without requiring separate logical keys', () => {
    const d = etlDraft(fileConfig)
    d.target.mode = 'SCD2'
    d.target.logicalKeys = ''
    d.target.scd2 =
      '{"businessKeys":["id"],"compareFields":["amount"],"effectiveValueField":"effective_date","validFromColumn":"start_at","validToColumn":"end_at"}'
    expect(etlIssues(d)).toEqual({})
    expect(JSON.parse(etlConfig(d)).target.scd2.businessKeys).toEqual(['id'])
  })
  it.each(['APPEND', 'file', 'beforeSql', 'dedupe'])(
    'rejects incompatible file incremental operation %s',
    (kind) => {
      const d = etlDraft(fileConfig)
      if (kind === 'APPEND') d.target.mode = kind
      if (kind === 'file') {
        d.target.type = 'FILE'
        d.target.path = '/out.csv'
      }
      if (kind === 'beforeSql') d.target.beforeSql = '["DELETE FROM orders"]'
      if (kind === 'dedupe')
        d.transforms.push({
          type: 'DEDUPLICATE',
          ruleId: '',
          onFailure: 'FAIL_TASK',
          properties: '{"fields":["id"]}',
          original: '{}',
        })
      expect(Object.keys(etlIssues(d)).length).toBeGreaterThan(0)
    },
  )
  it.each(['9223372036854775808', '1.5', '1e3'])('rejects invalid long marker %s', (v) => {
    const d = etlDraft(fileConfig)
    d.incremental.initialWatermark = v
    expect(etlIssues(d)['etl.incremental.initialWatermark']).toBeTruthy()
  })
  it('retains decimal marker precision and zero-like lexical values', () => {
    const d = etlDraft(fileConfig)
    d.incremental.watermarkType = 'DECIMAL'
    d.incremental.initialWatermark = '0.000000000000000000001'
    expect(etlIssues(d)).toEqual({})
    expect(etlConfig(d)).toContain('0.000000000000000000001')
  })
  it('date directory and timestamp selectors reveal only temporal options', () => {
    const d = etlDraft(fileConfig)
    expect(fileKeys(d)).not.toContain('markerFormat')
    d.incremental.watermarkType = 'DATE'
    d.incremental.initialWatermark = '2026-09-01'
    d.file.markerFormat = 'yyyyMMdd'
    d.file.markerLocation = 'RELATIVE_PATH'
    d.file.expression = '{mark}/orders.csv'
    d.file.lookback = 'P2D'
    expect(fileKeys(d)).toContain('markerFormat')
    expect(etlIssues(d)).toEqual({})
    expect(JSON.parse(etlConfig(d)).incremental.file.markerFormat).toBe('yyyyMMdd')
    d.incremental.watermarkType = 'STRING'
    expect(etlIssues(d)['etl.file.lookback']).toBeTruthy()
  })
  it('validates named regex markers without executing potentially costly Java regex in the browser', () => {
    const d = etlDraft(fileConfig)
    d.file.matchMode = 'REGEX'
    d.file.expression = 'orders_(?<mark>\\d+)_[a-z]+\\.csv'
    expect(etlIssues(d)).toEqual({})
    d.file.expression = '(.*)'
    expect(etlIssues(d)['etl.file.expression']).toBeTruthy()
  })
  it('catches target mapping duplicates and unmapped logical keys', () => {
    const d = etlDraft(fileConfig)
    d.columns.push({ target: 'id', source: 'other' })
    expect(etlIssues(d)['etl.target.columns']).toBeTruthy()
    d.target.logicalKeys = 'missing'
    expect(etlIssues(d)['etl.target.logicalKeys']).toBeTruthy()
  })
  it('requires DB query bounds and one initial value per tie', () => {
    const d = etlDraft(fileConfig)
    d.source.type = 'DATABASE'
    d.source.sql = 'SELECT id FROM orders'
    d.incremental.tieBreakerFields = 'id'
    expect(etlIssues(d)['etl.source.sql']).toBe('sqlMarkers')
    expect(etlIssues(d)['etl.incremental.initialTieBreakerValues']).toBeTruthy()
  })
  it('preserves long execution quotas as JSON integers and rejects fractional values', () => {
    const d = etlDraft(fileConfig)
    d.execution.maxRecords = '9007199254740993'
    expect(etlIssues(d)).toEqual({})
    expect(
      String((parse(etlConfig(d)) as { execution: { maxRecords: unknown } }).execution.maxRecords),
    ).toBe('9007199254740993')
    d.execution.maxRecords = '1.1'
    expect(etlIssues(d)['etl.execution.maxRecords']).toBeTruthy()
  })
  it.each([
    '{"version":2}',
    fileConfig.replace('"FILE"', '"REMOTE"'),
    fileConfig.replace('"header":true', '"header":"true"'),
  ])('blocks unsupported shapes safely', (text) => {
    expect(etlDraft(text).blocked).toBe(true)
    expect(() => etlConfig(etlDraft(text))).toThrow()
  })
  it('keeps selected file SQL parser fields and rejects direct database-file reading', () => {
    const d = etlDraft(fileConfig)
    d.source.format = 'SQLITE'
    expect(etlIssues(d)['etl.source.readMode']).toBeTruthy()
    d.source.readMode = 'SQL'
    d.source.sql = 'SELECT * FROM SOURCE_DATA'
    expect(sourceKeys(d)).toContain('sql')
    expect(etlIssues(d)).toEqual({})
  })
})

it('validates only the active target after switching an unfinished SCD2 draft to FILE', () => {
  const draft = etlDraft()
  prepareEtlForms(draft)
  Object.assign(draft.source, { resourceId: '1', sql: 'select 1' })
  Object.assign(draft.target, { type: 'FILE', resourceId: '2', path: 'out.csv', mode: 'SCD2' })
  expect(etlIssues(draft)).toEqual({})
  expect(JSON.parse(etlConfig(draft)).target).not.toHaveProperty('scd2')
  expect(JSON.parse(etlConfig(draft)).target).not.toHaveProperty('mode')
  draft.target.type = 'DATABASE'
  draft.target.table = 'orders'
  expect(etlIssues(draft)['etl.target.scd2']).toBe('required')
})
