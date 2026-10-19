// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { LosslessNumber, parse, stringify } from 'lossless-json'
import { pushId } from './push-model'

export const batchLimits = {
  fetchSize: ['2000', '100000'],
  writeBatchSize: ['1000', '100000'],
  queryTimeoutSeconds: ['300', '86400'],
  maxTables: ['500', '500'],
  maxRowsPerTable: ['10000000', '9223372036854775807'],
  maxTotalRows: ['50000000', '9223372036854775807'],
} as const
export type BatchLimit = keyof typeof batchLimits
export type BatchValue = { kind: 'string' | 'number' | 'boolean'; text: string }
const object = (v: unknown): Record<string, unknown> => {
  if (!v || typeof v !== 'object' || Array.isArray(v) || v instanceof LosslessNumber) throw Error()
  return v as Record<string, unknown>
}
function scalar(v: unknown): BatchValue {
  if (typeof v === 'string') return { kind: 'string', text: v }
  if (v instanceof LosslessNumber) return { kind: 'number', text: v.value }
  if (typeof v === 'boolean') return { kind: 'boolean', text: String(v) }
  throw Error()
}
function value(v: BatchValue): unknown {
  if (v.kind === 'number') return new LosslessNumber(v.text)
  if (v.kind === 'boolean') return v.text === 'true'
  return v.text
}
export function batchEtlDraft(text?: string) {
  const d = {
    original: text ?? '{}',
    blocked: false,
    sourceResourceId: '',
    targetResourceId: '',
    sourceSchema: '',
    targetSchema: '',
    prefix: '',
    suffix: '',
    tables: [] as { name: string }[],
    keys: [] as { name: string; initial: BatchValue }[],
    extractMode: 'FULL',
    loadMode: 'UPSERT',
    nullPolicy: 'OVERWRITE',
    watermarkColumn: '',
    watermarkType: 'TIMESTAMP',
    initial: { kind: 'string', text: '' } as BatchValue,
    clearBeforeImport: false,
    clearMode: 'DELETE',
    acknowledgePartialCommit: false,
    acknowledgeNonTransactionalTruncate: false,
    allowNonIdempotentRetry: false,
    limits: Object.fromEntries(Object.entries(batchLimits).map(([k, v]) => [k, v[0]])) as Record<
      BatchLimit,
      string
    >,
  }
  if (!text) return d
  try {
    const c = object(parse(text))
    if (c.version !== undefined && String(c.version) !== '1') throw Error()
    for (const k of ['sourceResourceId', 'targetResourceId'] as const) {
      d[k] = String(c[k])
      if (!pushId(d[k])) throw Error()
    }
    for (const k of ['sourceSchema', 'targetSchema'] as const) {
      if (c[k] !== undefined && c[k] !== null) {
        if (typeof c[k] !== 'string') throw Error()
        d[k] = c[k]
      }
    }
    for (const [key, options] of Object.entries({
      extractMode: ['FULL', 'INCREMENTAL'],
      loadMode: ['UPSERT', 'INSERT_ONLY', 'UPDATE_ONLY', 'APPEND'],
      nullPolicy: ['OVERWRITE', 'KEEP_TARGET'],
      clearMode: ['DELETE', 'TRUNCATE'],
    })) {
      const k = key as 'extractMode' | 'loadMode' | 'nullPolicy' | 'clearMode'
      if (c[k] !== undefined) {
        if (!options.includes(String(c[k]))) throw Error()
        d[k] = String(c[k])
      }
    }
    for (const k of [
      'clearBeforeImport',
      'acknowledgePartialCommit',
      'acknowledgeNonTransactionalTruncate',
      'allowNonIdempotentRetry',
    ] as const) {
      if (c[k] !== undefined) {
        if (typeof c[k] !== 'boolean') throw Error()
        d[k] = c[k]
      }
    }
    if (!Array.isArray(c.tables) || !c.tables.every((v) => typeof v === 'string')) throw Error()
    d.tables = c.tables.map((name) => ({ name: name as string }))
    const keys = c.logicalKeys === undefined ? [] : c.logicalKeys
    if (!Array.isArray(keys) || !keys.every((v) => typeof v === 'string')) throw Error()
    const inc = c.incremental === undefined ? {} : object(c.incremental)
    if (inc.watermarkColumn !== undefined && inc.watermarkColumn !== null) {
      if (typeof inc.watermarkColumn !== 'string') throw Error()
      d.watermarkColumn = inc.watermarkColumn
    }
    if (inc.watermarkType !== undefined) d.watermarkType = String(inc.watermarkType).toUpperCase()
    if (!['LONG', 'DECIMAL', 'DATE', 'TIMESTAMP', 'STRING'].includes(d.watermarkType)) throw Error()
    if (inc.initialWatermark !== undefined && inc.initialWatermark !== null)
      d.initial = scalar(inc.initialWatermark)
    const ties = inc.initialLogicalKeyValues === undefined ? [] : inc.initialLogicalKeyValues
    if (!Array.isArray(ties) || (ties.length !== 0 && ties.length !== keys.length)) throw Error()
    d.keys = keys.map((name, i) => ({
      name,
      initial: i < ties.length ? scalar(ties[i]) : { kind: 'string', text: '' },
    }))
    const naming = c.targetNaming === undefined ? {} : object(c.targetNaming)
    for (const k of ['prefix', 'suffix'] as const)
      if (naming[k] !== undefined) {
        if (typeof naming[k] !== 'string') throw Error()
        d[k] = naming[k]
      }
    const execution = c.execution === undefined ? {} : object(c.execution)
    for (const k of Object.keys(batchLimits) as BatchLimit[])
      if (execution[k] !== undefined) {
        if (!(execution[k] instanceof LosslessNumber) && typeof execution[k] !== 'string')
          throw Error()
        d.limits[k] = String(execution[k])
        if (!/^\d+$/.test(d.limits[k])) throw Error()
      }
  } catch {
    d.blocked = true
  }
  return d
}
export type BatchEtlDraft = ReturnType<typeof batchEtlDraft>
const template = (s: string) => s.includes('${')
const identifier = (s: string) => template(s) || /^[A-Za-z_][A-Za-z0-9_$#]{0,127}$/.test(s)
const validValue = (v: BatchValue) =>
  v.kind === 'number'
    ? /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(v.text)
    : v.kind === 'boolean'
      ? ['true', 'false'].includes(v.text)
      : v.text.length > 0
export function batchEtlIssues(d: BatchEtlDraft, retries = 0) {
  const e: Record<string, string> = {},
    fail = (key: string, code = 'invalid') => {
      e['batchEtl.' + key] = code
    }
  if (d.blocked) {
    fail('sourceResourceId', 'unsupported')
    return e
  }
  for (const k of ['sourceResourceId', 'targetResourceId'] as const) if (!pushId(d[k])) fail(k)
  for (const k of ['sourceSchema', 'targetSchema'] as const) if (d[k] && !identifier(d[k])) fail(k)
  for (const k of ['prefix', 'suffix'] as const)
    if (!template(d[k]) && !/^[A-Za-z0-9_$#]{0,64}$/.test(d[k])) fail(k)
  if (!d.tables.length || d.tables.length > 500) fail('tables', 'tablesRequired')
  const seen = new Set<string>()
  d.tables.forEach((r, i) => {
    if (!identifier(r.name) || seen.has(r.name.toUpperCase())) fail('tables.' + i)
    seen.add(r.name.toUpperCase())
  })
  const incremental = d.extractMode === 'INCREMENTAL'
  if ((d.loadMode !== 'APPEND' || incremental) && !d.keys.length) fail('keys', 'keysRequired')
  const keys = new Set<string>()
  d.keys.forEach((r, i) => {
    if (!identifier(r.name) || keys.has(r.name.toUpperCase())) fail('keys.' + i + '.name')
    keys.add(r.name.toUpperCase())
    if (incremental && !validValue(r.initial)) fail('keys.' + i + '.initial')
  })
  if (incremental) {
    if (d.loadMode === 'APPEND') fail('loadMode', 'incrementalAppend')
    if (!identifier(d.watermarkColumn)) fail('watermarkColumn')
    if (!validValue(d.initial) || d.initial.kind === 'boolean') fail('initial')
  }
  if (!incremental && d.clearBeforeImport) {
    if (d.clearMode === 'DELETE' && !d.acknowledgePartialCommit)
      fail('acknowledgePartialCommit', 'acknowledge')
    if (d.clearMode === 'TRUNCATE' && !d.acknowledgeNonTransactionalTruncate)
      fail('acknowledgeNonTransactionalTruncate', 'acknowledge')
  }
  if (d.loadMode === 'APPEND' && retries > 0 && !d.allowNonIdempotentRetry)
    fail('allowNonIdempotentRetry', 'acknowledge')
  for (const k of Object.keys(batchLimits) as BatchLimit[])
    if (
      !/^\d+$/.test(d.limits[k]) ||
      BigInt(d.limits[k]) < 1n ||
      BigInt(d.limits[k]) > BigInt(batchLimits[k][1])
    )
      fail('limits.' + k)
  if (!e['batchEtl.limits.maxTables'] && BigInt(d.limits.maxTables) < BigInt(d.tables.length))
    fail('limits.maxTables')
  if (
    !e['batchEtl.limits.maxTotalRows'] &&
    !e['batchEtl.limits.maxRowsPerTable'] &&
    BigInt(d.limits.maxTotalRows) < BigInt(d.limits.maxRowsPerTable)
  )
    fail('limits.maxTotalRows')
  if (
    !Object.keys(e).length &&
    new TextEncoder().encode(batchEtlConfig(d)).length > 8 * 1024 * 1024
  )
    fail('tables', 'tooLarge')
  return e
}
export function batchEtlConfig(d: BatchEtlDraft) {
  if (d.blocked) throw Error('Unsupported BATCH_ETL configuration')
  const c = object(parse(d.original)),
    incremental = d.extractMode === 'INCREMENTAL',
    inc = { ...object(c.incremental ?? {}) }
  for (const k of [
    'watermarkColumn',
    'watermarkType',
    'initialWatermark',
    'initialLogicalKeyValues',
  ])
    delete inc[k]
  if (incremental)
    Object.assign(inc, {
      watermarkColumn: d.watermarkColumn,
      watermarkType: d.watermarkType,
      initialWatermark: value(d.initial),
      initialLogicalKeyValues: d.keys.map((r) => value(r.initial)),
    })
  return stringify({
    ...c,
    version: 1,
    sourceResourceId: d.sourceResourceId,
    targetResourceId: d.targetResourceId,
    sourceSchema: d.sourceSchema || null,
    targetSchema: d.targetSchema || null,
    tables: d.tables.map((r) => r.name),
    logicalKeys: d.keys.map((r) => r.name),
    extractMode: d.extractMode,
    loadMode: d.loadMode,
    nullPolicy: d.nullPolicy,
    incremental: inc,
    targetNaming: { ...object(c.targetNaming ?? {}), prefix: d.prefix, suffix: d.suffix },
    clearBeforeImport: !incremental && d.clearBeforeImport,
    clearMode: !incremental && d.clearBeforeImport ? d.clearMode : 'DELETE',
    acknowledgePartialCommit:
      !incremental && d.clearBeforeImport && d.clearMode === 'DELETE' && d.acknowledgePartialCommit,
    acknowledgeNonTransactionalTruncate:
      !incremental &&
      d.clearBeforeImport &&
      d.clearMode === 'TRUNCATE' &&
      d.acknowledgeNonTransactionalTruncate,
    allowNonIdempotentRetry: d.loadMode === 'APPEND' && d.allowNonIdempotentRetry,
    execution: {
      ...object(c.execution ?? {}),
      ...Object.fromEntries(Object.entries(d.limits).map(([k, v]) => [k, new LosslessNumber(v)])),
    },
  })!
}
