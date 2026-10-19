// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { parseExact as parse } from '../../shared/ui/json-form'
import { stringify, LosslessNumber } from 'lossless-json'
import {
  formText,
  ruleFormText,
  prepareForm,
  ruleSchemas,
  targetSchemas,
  type FormNode,
} from './etl-form-model'

export type Values = Record<string, string | boolean>
export type Field = {
  key: string
  kind?: 'select' | 'bool' | 'number' | 'json' | 'sql' | 'resource' | 'list'
  options?: string[]
  value?: string | boolean
  required?: boolean
  min?: string
  max?: string
  wide?: boolean
}
const f = (key: string, value = '', extra: Partial<Field> = {}): Field => ({
  key,
  value,
  required: [
    'sql',
    'path',
    'charset',
    'pattern',
    'table',
    'checkpointKey',
    'initialWatermark',
    'watermarkField',
    'upperBoundSql',
    'expression',
    'lookback',
    'lowerBound',
  ].includes(key),
  ...extra,
})
const select = (key: string, value: string, options: string[]) =>
  f(key, value, { kind: 'select', options })
const num = (key: string, value: string, min = '0', max = '2147483647') =>
  f(key, value, { kind: 'number', min, max })
export const messageFields: Field[] = [
  num('maxMessages', '10000', '1', '10000000'),
  num('intakeSeconds', '60', '1', '3600'),
  num('idleSeconds', '5', '1', '300'),
  num('batchSize', '100', '1', '1000'),
  num('flushIntervalMillis', '1000', '100', '10000'),
  num('maxMessageBytes', '1048576', '1024', '16777216'),
  num('maxExpandedBytes', '8388608', '1024', '67108864'),
  num('maxTotalBytes', '1073741824', '1024', '107374182400'),
  num('maxBufferBytes', '33554432', '1024', '268435456'),
  num('maxJsonDepth', '32', '1', '100'),
]
export const isMessageSource = (type: unknown) => ['KAFKA', 'RABBITMQ'].includes(String(type))
export const sourceFields: Field[] = [
  f('topic', '', { required: true, wide: true }),
  f('groupId', '', { required: true, wide: true }),
  f('queue', '', { required: true, wide: true }),
  select('initialPosition', 'EARLIEST', ['EARLIEST', 'LATEST']),
  num('prefetch', '100', '1', '1000'),
  select('type', 'DATABASE', ['DATABASE', 'FILE', 'KAFKA', 'RABBITMQ']),
  f('resourceId', '', { kind: 'resource', required: true }),
  f('sql', '', { kind: 'sql', wide: true }),
  f('path', '', { wide: true }),
  select('format', 'CSV', ['CSV', 'TXT', 'EXCEL', 'REGEX', 'SQLITE', 'MDB']),
  select('readMode', 'DIRECT', ['DIRECT', 'SQL']),
  f('charset', 'UTF-8'),
  f('delimiter', ','),
  f('quote', '"'),
  f('escape', '"'),
  { key: 'header', kind: 'bool', value: true },
  num('skipLines', '0'),
  num('headerRow', '0'),
  num('startRow', ''),
  f('sheetName'),
  num('sheetIndex', ''),
  select('regexMode', 'SINGLE_LINE', ['SINGLE_LINE', 'BLOCK']),
  f('pattern', '', { wide: true }),
  f('blockStart'),
  f('blockEnd'),
  num('maxBlockLines', '10000', '1', '1000000'),
  num('maxBlockChars', '4194304', '1', '67108864'),
  f('regexFields', '[]', { kind: 'json', wide: true }),
  f('stagingTable', 'SOURCE_DATA'),
  f('stagingColumns', '', { kind: 'list' }),
  f('sheets', '[]', { kind: 'json', wide: true }),
  num('fetchSize', '2000', '1', '100000'),
  f('parameters', '{}', { kind: 'json', wide: true }),
  f('beforeSql', '[]', { kind: 'json', wide: true }),
]
export const targetFields: Field[] = [
  select('type', 'DATABASE', ['DATABASE', 'FILE']),
  f('resourceId', '', { kind: 'resource', required: true }),
  f('schema'),
  f('table'),
  select('mode', 'UPSERT', ['UPSERT', 'INSERT_ONLY', 'UPDATE_ONLY', 'APPEND', 'SCD2']),
  f('logicalKeys', '', { kind: 'list' }),
  select('mappingMode', 'AUTO', ['AUTO', 'MANUAL']),
  f('excludedFields', '', { kind: 'list' }),
  select('transactionScope', 'RUN', ['RUN', 'BATCH']),
  select('nullPolicy', 'KEEP_TARGET', ['KEEP_TARGET', 'OVERWRITE']),
  f('columnNullPolicies', '{}', { kind: 'json', wide: true }),
  f('protection', 'null', { kind: 'json', wide: true }),
  f('scd2', 'null', { kind: 'json', wide: true }),
  f('beforeSql', '[]', { kind: 'json', wide: true }),
  f('afterSql', '[]', { kind: 'json', wide: true }),
  f('path', '', { wide: true }),
  f('charset', 'UTF-8'),
  f('delimiter', ','),
  f('quote', '"'),
  { key: 'header', kind: 'bool', value: true },
  select('existingFilePolicy', 'FAIL', ['FAIL', 'OVERWRITE', 'SKIP']),
  select('emptyFilePolicy', 'CREATE_WITH_HEADER', ['CREATE_WITH_HEADER', 'CREATE_EMPTY', 'SKIP']),
]
export const incrementalFields: Field[] = [
  { key: 'enabled', kind: 'bool', value: false },
  f('checkpointKey', 'etl-v1'),
  select('watermarkType', 'LONG', ['LONG', 'DECIMAL', 'DATE', 'TIMESTAMP', 'STRING']),
  f('initialWatermark', '0'),
  f('watermarkField'),
  f('upperBoundSql', '', { kind: 'sql', wide: true }),
  f('tieBreakerFields', '', { kind: 'list' }),
  f('initialTieBreakerValues', '[]', { kind: 'json', wide: true }),
  { key: 'allowCheckpointPastInvalidRecords', kind: 'bool', value: false },
]
export const fileFields: Field[] = [
  select('markerLocation', 'NAME', ['NAME', 'RELATIVE_PATH']),
  select('matchMode', 'TEMPLATE', ['TEMPLATE', 'REGEX']),
  f('expression', 'orders_{mark}_*.csv', { wide: true }),
  f('markerFormat'),
  f('zoneId', 'UTC'),
  select('selectionMode', 'STRICT', ['STRICT', 'LOOKBACK', 'LOWER_BOUND']),
  f('lookback', '2'),
  f('lowerBound'),
  f('upperBound'),
  select('changedFilePolicy', 'FAIL', ['FAIL', 'REPROCESS']),
  { key: 'recursive', kind: 'bool', value: false },
  num('maxDepth', '8', '0', '32'),
  num('maxScanEntries', '10000', '1', '100000'),
  num('maxFiles', '1000', '1', '10000'),
  num('maxTotalBytes', '1073741824', '1', '107374182400'),
  num('stableAgeSeconds', '60', '0', '604800'),
  f('readySuffix'),
]
export const executionFields: Field[] = [
  num('readBatchSize', '2000', '1', '100000'),
  num('writeBatchSize', '1000', '1', '100000'),
  num('queryTimeoutSeconds', '300', '1', '86400'),
  ...[
    ['maxRecords', '10000000'],
    ['maxFileBytes', '1073741824'],
    ['maxExpandedTextChars', '268435456'],
    ['maxExcelCells', '50000000'],
    ['maxStagingCells', '50000000'],
  ].map(([k, v]) => num(k!, v!, '1', '9223372036854775807')),
  num('maxRecordFields', '1000', '1', '10000'),
  num('maxTextFieldChars', '1048576', '1', '16777216'),
  num('maxExcelSheets', '100', '1', '10000'),
]
export const transformExamples: Record<string, unknown> = {
  FIELD_MAPPING: { order_id: 'id' },
  DEFAULT_VALUE: { status: 'NEW' },
  TRIM: { fields: ['name'] },
  REPLACE: { field: 'status', replace: { old: 'new' } },
  DATE_FORMAT: { field: 'created_at', source: 'yyyyMMdd', target: 'yyyy-MM-dd' },
  TYPE_CONVERT: { field: 'amount', target: 'DECIMAL' },
  NUMBER_SCALE: { field: 'amount', divide: 100, scale: 2, roundingMode: 'HALF_UP' },
  EXPRESSION: { target: 'total', expression: 'price * quantity' },
  LOOKUP: {
    resourceId: 1,
    sourceField: 'code',
    targetField: 'name',
    sql: 'SELECT code, name FROM lookup_table WHERE code IN (:keys)',
    keyColumn: 'code',
    valueColumn: 'name',
  },
  DEDUPLICATE: { fields: ['id'], maxKeys: 1000000, keep: 'FIRST' },
}
export const validationExamples: Record<string, unknown> = {
  REQUIRED: { fields: ['id'] },
  REGEX: { field: 'code', pattern: '^[A-Z]+$' },
  RANGE: { field: 'amount', min: 0 },
  ENUM: { field: 'status', values: ['NEW', 'DONE'] },
  CONDITION: { expression: 'amount >= 0' },
  SQL: {
    resourceId: 1,
    mode: 'FIELD',
    field: 'id',
    sql: 'SELECT id FROM orders WHERE id IN (:keys)',
  },
  FOREIGN_KEY: {
    resourceId: 1,
    field: 'customer_id',
    sql: 'SELECT id FROM customers WHERE id IN (:keys)',
  },
}
export const jsonObject = (value: unknown): Record<string, unknown> => {
  if (
    !value ||
    typeof value !== 'object' ||
    Array.isArray(value) ||
    value instanceof LosslessNumber
  )
    throw Error('Expected object')
  return value as Record<string, unknown>
}
export const idValid = (s: string) => /^[1-9]\d*$/.test(s) && BigInt(s) <= 9223372036854775807n
export const list = (s: string) => (s.trim() ? s.split(',').map((x) => x.trim()) : [])
export interface Rule {
  form?: FormNode
  type: string
  ruleId: string
  onFailure: string
  properties: string
  original: string
}
const defaults = (fields: Field[]) =>
  Object.fromEntries(fields.map((x) => [x.key, x.value ?? ''])) as Values
function hydrate(fields: Field[], c: Record<string, unknown>) {
  const values = defaults(fields)
  for (const field of fields) {
    const v = c[field.key]
    if (v === undefined) continue
    if (field.kind === 'bool') {
      if (typeof v !== 'boolean') throw Error()
      values[field.key] = v
    } else if (field.kind === 'json') values[field.key] = stringify(v, null, 2)!
    else if (field.kind === 'list') {
      if (!Array.isArray(v) || v.some((x) => typeof x !== 'string')) throw Error()
      values[field.key] = v.join(', ')
    } else {
      if (v !== null && typeof v !== 'string' && !(v instanceof LosslessNumber)) throw Error()
      values[field.key] = v == null ? '' : String(v)
    }
    if (field.options && !field.options.includes(String(values[field.key]))) throw Error()
  }
  return values
}
export function etlDraft(text?: string) {
  const d = {
    original: text ?? '{}',
    blocked: false,
    source: defaults(sourceFields),
    message: defaults(messageFields),
    metadataColumns: [] as { key: string; field: string }[],
    sourceDrafts: {} as Record<string, Values>,
    target: defaults(targetFields),
    incremental: defaults(incrementalFields),
    file: defaults(fileFields),
    execution: defaults(executionFields),
    columns: [] as { target: string; source: string }[],
    targetForms: {} as Record<string, FormNode>,
    transforms: [] as Rule[],
    validations: [] as Rule[],
    allowNonIdempotentRetry: false,
  }
  if (!text) return d
  try {
    const c = jsonObject(parse(text)),
      s = jsonObject(c.source),
      t = jsonObject(c.target),
      i = jsonObject(c.incremental ?? {})
    if (c.version != null && String(c.version) !== (isMessageSource(s.type) ? '2' : '1'))
      throw Error()
    if (!s.type || !t.type) throw Error()
    d.source = hydrate(sourceFields, s)
    d.message = hydrate(messageFields, jsonObject(c.message ?? {}))
    d.metadataColumns = Object.entries(
      jsonObject(jsonObject(c.message ?? {}).metadataColumns ?? {}),
    ).map(([key, field]) => {
      if (typeof field !== 'string') throw Error()
      return { key, field }
    })
    d.target = hydrate(targetFields, t)
    if (!Object.hasOwn(t, 'mappingMode')) d.target.mappingMode = 'MANUAL'
    d.incremental = hydrate(incrementalFields, i)
    d.file = hydrate(fileFields, jsonObject(i.file ?? {}))
    d.execution = hydrate(executionFields, jsonObject(c.execution ?? {}))
    d.columns = Object.entries(jsonObject(t.columns ?? {})).map(([target, source]) => {
      if (typeof source !== 'string') throw Error()
      return { target, source }
    })
    for (const key of ['transforms', 'validations'] as const) {
      const rows = c[key] ?? []
      if (!Array.isArray(rows)) throw Error()
      d[key] = rows.map((value) => {
        const r = jsonObject(value)
        if (!(String(r.type) in (key === 'transforms' ? transformExamples : validationExamples)))
          throw Error()
        return {
          type: String(r.type),
          ruleId: typeof r.ruleId === 'string' ? r.ruleId : '',
          onFailure: String(r.onFailure ?? 'FILTER'),
          properties: stringify(jsonObject(r.properties ?? {}), null, 2)!,
          original: stringify(r)!,
        }
      })
    }
    if (c.allowNonIdempotentRetry != null && typeof c.allowNonIdempotentRetry !== 'boolean')
      throw Error()
    d.allowNonIdempotentRetry = c.allowNonIdempotentRetry === true
  } catch {
    d.blocked = true
  }
  return d
}
export type EtlDraft = ReturnType<typeof etlDraft>
export function prepareEtlForms(d: EtlDraft) {
  if (d.blocked) return
  for (const rule of [...d.transforms, ...d.validations])
    rule.form ??= prepareForm(rule.properties, ruleSchemas[rule.type]!)
  for (const [key, schema] of Object.entries(targetSchemas))
    d.targetForms[key] ??= prepareForm(String(d.target[key]), schema)
}
export function sourceKeys(d: EtlDraft) {
  if (d.source.type === 'KAFKA')
    return ['type', 'resourceId', 'topic', 'groupId', 'initialPosition']
  if (d.source.type === 'RABBITMQ') return ['type', 'resourceId', 'queue', 'prefetch']
  if (d.source.type === 'DATABASE')
    return ['type', 'resourceId', 'sql', 'parameters', 'beforeSql', 'fetchSize']
  const keys = ['type', 'resourceId', 'path', 'format', 'readMode', 'charset']
  if (['CSV', 'TXT'].includes(String(d.source.format)))
    keys.push('delimiter', 'quote', 'escape', 'header', 'skipLines')
  if (d.source.format === 'EXCEL')
    keys.push('header', 'headerRow', 'startRow', 'sheetName', 'sheetIndex')
  if (d.source.format === 'REGEX') {
    keys.push('regexMode', 'pattern', 'regexFields', 'maxBlockLines', 'maxBlockChars')
    if (d.source.regexMode === 'BLOCK') keys.push('blockStart', 'blockEnd')
  }
  if (d.source.readMode === 'SQL') {
    keys.push('sql', 'parameters', 'stagingTable', 'stagingColumns')
    if (d.source.format === 'EXCEL') keys.push('sheets')
  }
  return keys
}
export function targetKeys(d: EtlDraft) {
  if (isMessageSource(d.source.type))
    return [
      'type',
      'resourceId',
      'schema',
      'table',
      'mode',
      'logicalKeys',
      'mappingMode',
      ...(d.target.mappingMode === 'AUTO' ? ['excludedFields'] : []),
      'nullPolicy',
      'columnNullPolicies',
      'protection',
    ]
  return d.target.type === 'FILE'
    ? [
        'type',
        'resourceId',
        'mappingMode',
        ...(d.target.mappingMode === 'AUTO' ? ['excludedFields'] : []),
        'path',
        'charset',
        'delimiter',
        'quote',
        'header',
        'existingFilePolicy',
        'emptyFilePolicy',
      ]
    : [
        'type',
        'resourceId',
        'mappingMode',
        ...(d.target.mappingMode === 'AUTO' ? ['excludedFields'] : []),
        'schema',
        'table',
        'mode',
        'logicalKeys',
        'transactionScope',
        'nullPolicy',
        'columnNullPolicies',
        'protection',
        'beforeSql',
        'afterSql',
        ...(d.target.mode === 'SCD2' ? ['scd2'] : []),
      ]
}
export function incrementalKeys(d: EtlDraft) {
  if (isMessageSource(d.source.type)) return []
  return [
    'enabled',
    ...(d.incremental.enabled
      ? [
          'checkpointKey',
          'watermarkType',
          'initialWatermark',
          ...(d.source.type === 'DATABASE'
            ? [
                'watermarkField',
                'upperBoundSql',
                'tieBreakerFields',
                'initialTieBreakerValues',
                'allowCheckpointPastInvalidRecords',
              ]
            : []),
        ]
      : []),
  ]
}
export function fileKeys(d: EtlDraft) {
  return d.incremental.enabled && d.source.type === 'FILE'
    ? [
        'markerLocation',
        'matchMode',
        'expression',
        'changedFilePolicy',
        'selectionMode',
        ...(['DATE', 'TIMESTAMP'].includes(String(d.incremental.watermarkType))
          ? ['markerFormat', 'zoneId']
          : []),
        ...(d.file.selectionMode === 'LOOKBACK' ? ['lookback'] : []),
        ...(d.file.selectionMode === 'LOWER_BOUND' ? ['lowerBound'] : []),
        'upperBound',
        'recursive',
        'maxDepth',
        'maxScanEntries',
        'maxFiles',
        'maxTotalBytes',
        'stableAgeSeconds',
        'readySuffix',
      ]
    : []
}
function section(original: unknown, fields: Field[], values: Values, keys: string[]) {
  const c = { ...jsonObject(original ?? {}) }
  for (const field of fields) {
    delete c[field.key]
    if (!keys.includes(field.key)) continue
    const v = values[field.key]
    if (field.kind === 'bool') c[field.key] = v
    else if (field.kind === 'json') c[field.key] = parse(String(v))
    else if (field.kind === 'list') c[field.key] = list(String(v))
    else if (v !== '' && v != null)
      c[field.key] =
        field.kind === 'number' || field.kind === 'resource' ? new LosslessNumber(String(v)) : v
    else if (['quote', 'escape'].includes(field.key)) c[field.key] = null
  }
  return c
}
export function etlConfig(d: EtlDraft) {
  if (d.blocked) throw Error('Unsupported ETL configuration')
  const c = jsonObject(parse(d.original)),
    i = jsonObject(c.incremental ?? {})
  const incremental = section(i, incrementalFields, d.incremental, incrementalKeys(d))
  delete incremental.file
  if (d.incremental.enabled && d.source.type === 'FILE') {
    incremental.file = section(i.file, fileFields, d.file, fileKeys(d))
    incremental.allowCheckpointPastInvalidRecords = false
  }
  const target = section(c.target, targetFields, targetValues(d), targetKeys(d))
  if (isMessageSource(d.source.type)) {
    target.transactionScope = 'BATCH'
    incremental.enabled = false
  }
  const message = section(
    c.message,
    messageFields,
    d.message,
    messageFields.map((f) => f.key),
  )
  message.metadataColumns = Object.fromEntries(d.metadataColumns.map((r) => [r.key, r.field]))
  delete c.message
  target.columns = Object.fromEntries(d.columns.map((r) => [r.target, r.source]))
  const rules = (rows: Rule[], validation: boolean) =>
    rows.map((r) => ({
      ...jsonObject(parse(r.original)),
      type: r.type,
      properties: jsonObject(parse(ruleFormText(r.type, r.properties, r.form))),
      ...(validation ? { ruleId: r.ruleId, onFailure: r.onFailure } : {}),
    }))
  return stringify({
    ...c,
    version: isMessageSource(d.source.type) ? 2 : 1,
    ...(isMessageSource(d.source.type) ? { message } : {}),
    source: section(c.source, sourceFields, d.source, sourceKeys(d)),
    target,
    incremental,
    execution: section(
      c.execution,
      executionFields,
      d.execution,
      executionFields.map((f) => f.key),
    ),
    transforms: rules(d.transforms, false),
    validations: rules(d.validations, true),
    allowNonIdempotentRetry: d.allowNonIdempotentRetry,
  })!
}
function targetValues(d: EtlDraft) {
  const values = { ...d.target }
  for (const key of targetKeys(d))
    if (d.targetForms[key]) values[key] = formText(d.targetForms[key], targetSchemas[key])
  return values
}
export function etlIssues(d: EtlDraft, retries = 0) {
  const errors: Record<string, string> = {},
    fail = (key: string, code = 'invalid') => {
      errors['etl.' + key] = code
    }
  if (d.blocked) return { 'etl.source.type': 'unsupported' }
  const check = (name: string, fields: Field[], values: Values, keys: string[]) => {
    for (const field of fields.filter((x) => keys.includes(x.key))) {
      const value = String(values[field.key] ?? ''),
        key = name + '.' + field.key
      if (field.required && !value.trim()) fail(key, 'required')
      if (field.options && !field.options.includes(value)) fail(key)
      if (value.includes('\0')) fail(key)
      if (
        field.kind === 'number' &&
        value &&
        (!/^\d+$/.test(value) ||
          BigInt(value) < BigInt(field.min ?? '0') ||
          BigInt(value) > BigInt(field.max ?? '2147483647'))
      )
        fail(key)
      if (field.kind === 'number' && !value && field.value) fail(key, 'required')
      if (field.kind === 'resource' && !idValid(value)) fail(key, 'required')
      if (field.kind === 'json')
        try {
          const v = parse(value)
          if (
            ['beforeSql', 'afterSql', 'sheets', 'regexFields', 'initialTieBreakerValues'].includes(
              field.key,
            )
          ) {
            if (!Array.isArray(v)) throw Error()
          } else if (v !== null || !['protection', 'scd2'].includes(field.key)) jsonObject(v)
        } catch {
          fail(key, 'jsonInvalid')
        }
      if (field.kind === 'list' && list(value).some((x, i, a) => !x || a.indexOf(x) !== i))
        fail(key)
    }
  }
  check('source', sourceFields, d.source, sourceKeys(d))
  if (isMessageSource(d.source.type)) {
    check(
      'message',
      messageFields,
      d.message,
      messageFields.map((f) => f.key),
    )
    if (d.target.type !== 'DATABASE' || !['UPSERT', 'INSERT_ONLY'].includes(String(d.target.mode)))
      fail('target.mode')
    if (d.incremental.enabled) fail('source.type')
    for (const key of ['maxExpandedBytes', 'maxBufferBytes', 'maxTotalBytes'])
      if (
        /^\d+$/.test(String(d.message.maxMessageBytes)) &&
        /^\d+$/.test(String(d.message[key])) &&
        BigInt(String(d.message[key])) < BigInt(String(d.message.maxMessageBytes || '0'))
      )
        fail('message.' + key)
    if (d.source.type === 'KAFKA') {
      if (
        !/^[a-zA-Z0-9._-]{1,249}$/.test(String(d.source.topic)) ||
        ['.', '..'].includes(String(d.source.topic))
      )
        fail('source.topic')
      if (!/^[a-zA-Z0-9._-]{1,200}$/.test(String(d.source.groupId))) fail('source.groupId')
    } else if (new TextEncoder().encode(String(d.source.queue)).length > 255) fail('source.queue')
    const keys =
      d.source.type === 'KAFKA'
        ? ['topic', 'partition', 'offset', 'key', 'timestamp', 'headers']
        : ['queue', 'exchange', 'routingKey', 'messageId', 'timestamp', 'headers', 'redelivered']
    if (
      d.metadataColumns.some(
        (r, i, a) =>
          !keys.includes(r.key) ||
          !/^[A-Za-z_][A-Za-z0-9_$#]{0,127}$/.test(r.field) ||
          a.some((v, j) => j < i && (v.key === r.key || v.field === r.field)),
      )
    )
      fail('message.metadataColumns')
    if (d.metadataColumns.length > 16) fail('message.metadataColumns')
    d.transforms.forEach((r, i) => {
      if (r.type === 'DEDUPLICATE') fail('transforms.' + i, 'noDedupe')
    })
    d.validations.forEach((r, i) => {
      if (r.onFailure !== 'FAIL_TASK') fail('validations.' + i, 'ruleInvalid')
    })
  }
  let target = d.target
  try {
    target = targetValues(d)
  } catch {
    for (const key of targetKeys(d))
      if (d.targetForms[key])
        try {
          formText(d.targetForms[key], targetSchemas[key])
        } catch {
          fail('target.' + key, 'invalid')
        }
  }
  check('target', targetFields, target, targetKeys(d))
  check('incremental', incrementalFields, d.incremental, incrementalKeys(d))
  check('file', fileFields, d.file, fileKeys(d))
  check(
    'execution',
    executionFields,
    d.execution,
    executionFields.map((f) => f.key),
  )
  const required = (name: string, keys: string[], v: Values) =>
    keys.forEach((k) => {
      if (!String(v[k] ?? '').trim()) fail(name + '.' + k, 'required')
    })
  required(
    'source',
    isMessageSource(d.source.type)
      ? []
      : d.source.type === 'DATABASE'
        ? ['sql']
        : ['path', 'charset'],
    d.source,
  )
  if (d.source.type === 'FILE') {
    if (d.source.readMode === 'SQL') required('source', ['sql', 'stagingTable'], d.source)
    if (['SQLITE', 'MDB'].includes(String(d.source.format)) && d.source.readMode !== 'SQL')
      fail('source.readMode')
    if (d.source.format === 'REGEX') {
      required('source', ['pattern'], d.source)
      if (d.source.readMode === 'SQL') fail('source.readMode')
      if (d.source.regexFields === '[]') fail('source.regexFields', 'required')
    }
    if (
      ['CSV', 'TXT'].includes(String(d.source.format)) &&
      (String(d.source.delimiter).length !== 1 || d.source.quote === d.source.delimiter)
    )
      fail('source.delimiter')
    for (const k of ['quote', 'escape']) if (String(d.source[k]).length > 1) fail('source.' + k)
  }
  required(
    'target',
    d.target.type === 'DATABASE' ? ['table'] : ['path', 'charset', 'delimiter'],
    d.target,
  )
  const identifier = /^[A-Za-z_][A-Za-z0-9_$#]{0,127}$/
  if (d.target.type === 'DATABASE') {
    if (
      !String(d.target.table)
        .split('.')
        .every((s) => identifier.test(s))
    )
      fail('target.table')
    if (d.target.schema && !identifier.test(String(d.target.schema))) fail('target.schema')
  }
  const automatic = d.target.mappingMode === 'AUTO'
  if (!automatic && !d.columns.length) fail('target.columns', 'required')
  if (
    automatic &&
    list(String(d.target.excludedFields)).some((field) =>
      d.columns.some((row) => row.source === field),
    )
  )
    fail('target.excludedFields', 'excludedConflict')
  d.columns.forEach((r, i) => {
    if (
      !r.source.trim() ||
      !r.target.trim() ||
      d.columns.some((p, j) => j < i && r.target === p.target) ||
      (d.target.type === 'DATABASE' && !identifier.test(r.target))
    )
      fail('target.columns')
  })
  if (
    d.target.type === 'DATABASE' &&
    ((!['APPEND', 'SCD2'].includes(String(d.target.mode)) &&
      !list(String(d.target.logicalKeys)).length) ||
      (!automatic &&
        list(String(d.target.logicalKeys)).some((k) => !d.columns.some((r) => r.target === k))))
  )
    fail('target.logicalKeys', 'keysRequired')
  if (d.target.type === 'DATABASE' && d.target.mode === 'SCD2' && target.scd2 === 'null')
    fail('target.scd2', 'required')
  if (
    d.target.type === 'DATABASE' &&
    d.target.mode === 'APPEND' &&
    retries > 0 &&
    !d.allowNonIdempotentRetry
  )
    fail('target.mode', 'appendRetry')
  for (const key of ['transforms', 'validations'] as const)
    d[key].forEach((r, index) => {
      try {
        const props = jsonObject(parse(ruleFormText(r.type, r.properties, r.form)))
        if (!Object.keys(props).length) throw Error()
        if (props.resourceId != null && !idValid(String(props.resourceId))) throw Error()
        if (
          key === 'validations' &&
          (!r.ruleId.trim() ||
            d.validations.some((p, j) => j < index && p.ruleId === r.ruleId) ||
            !['FILTER', 'FAIL_TASK'].includes(r.onFailure))
        )
          throw Error()
      } catch {
        fail(key + '.' + index, 'ruleInvalid')
      }
      if (d.incremental.enabled && r.type === 'DEDUPLICATE') fail(key + '.' + index, 'noDedupe')
    })
  if (d.incremental.enabled) {
    required('incremental', ['checkpointKey', 'initialWatermark'], d.incremental)
    if (d.target.type === 'DATABASE' && d.target.mode === 'APPEND')
      fail('target.mode', 'incrementalTarget')
    if (d.target.type === 'FILE' && d.target.existingFilePolicy !== 'OVERWRITE')
      fail('target.existingFilePolicy', 'incrementalTarget')
    const type = String(d.incremental.watermarkType),
      mark = String(d.incremental.initialWatermark)
    if (
      type === 'LONG' &&
      !mark.includes('${') &&
      (!/^-?\d+$/.test(mark) ||
        BigInt(mark) < -9223372036854775808n ||
        BigInt(mark) > 9223372036854775807n)
    )
      fail('incremental.initialWatermark')
    if (type === 'DECIMAL' && !mark.includes('${') && !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(mark))
      fail('incremental.initialWatermark')
    if (d.source.type === 'DATABASE') {
      required('incremental', ['watermarkField', 'upperBoundSql'], d.incremental)
      const sql = String(d.source.sql)
      if (!sql.includes(':etlLastWatermark') || !sql.includes(':etlUpperWatermark'))
        fail('source.sql', 'sqlMarkers')
      try {
        const ties = parse(String(d.incremental.initialTieBreakerValues))
        if (
          !Array.isArray(ties) ||
          ties.length !== list(String(d.incremental.tieBreakerFields)).length ||
          ties.some((x) => x == null)
        )
          fail('incremental.initialTieBreakerValues')
        list(String(d.incremental.tieBreakerFields)).forEach((_, i) => {
          if (!sql.includes(':etlLastTie' + (i + 1))) fail('source.sql', 'sqlMarkers')
        })
      } catch {
        /* JSON error already reported */
      }
    } else {
      if (!/^[A-Za-z0-9._-]{1,128}$/.test(String(d.incremental.checkpointKey)))
        fail('incremental.checkpointKey')
      if (d.target.type !== 'DATABASE') fail('target.type', 'incrementalTarget')
      for (const k of ['beforeSql', 'afterSql'])
        if (String(target[k]).replace(/\s/g, '') !== '[]') fail('target.' + k, 'noScripts')
      required('file', ['expression'], d.file)
      const expression = String(d.file.expression)
      if (
        expression.length > 512 ||
        (d.file.matchMode === 'TEMPLATE' && expression.match(/\{mark\}/g)?.length !== 1) ||
        (d.file.matchMode === 'REGEX' && !expression.includes('(?<mark>'))
      )
        fail('file.expression', 'markerExpression')
      if (
        d.file.selectionMode === 'LOOKBACK' &&
        (type === 'STRING' ||
          !String(d.file.lookback).trim() ||
          (['LONG', 'DECIMAL'].includes(type) && !(Number(d.file.lookback) > 0)))
      )
        fail('file.lookback')
      if (d.file.selectionMode === 'LOWER_BOUND') required('file', ['lowerBound'], d.file)
      if (d.file.readySuffix && !/^\.[A-Za-z0-9._-]{1,63}$/.test(String(d.file.readySuffix)))
        fail('file.readySuffix')
      if (Number(d.file.maxFiles) > Number(d.file.maxScanEntries)) fail('file.maxFiles')
    }
  }
  if (!Object.keys(errors).length)
    try {
      if (new TextEncoder().encode(etlConfig(d)).length > 8 * 1024 * 1024)
        fail('source.type', 'tooLarge')
    } catch {
      fail('source.type', 'jsonInvalid')
    }
  return errors
}
