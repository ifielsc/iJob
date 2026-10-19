// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { LosslessNumber, parse, stringify } from 'lossless-json'

export type NodeKind = 'string' | 'number' | 'boolean' | 'null' | 'object' | 'array'
export interface FormNode {
  kind: NodeKind
  omitted?: boolean
  value: string
  entries: { key: string; node: FormNode }[]
}
export interface FormSchema {
  label?: string
  keyLabel?: string
  valueLabel?: string
  kind?: NodeKind | 'resource' | 'sql'
  fields?: Record<string, FormSchema>
  item?: FormSchema
  options?: string[]
  required?: boolean
  nullable?: boolean
  min?: number
  max?: number
  integer?: boolean
  nonzero?: boolean
}
const text = (required = true): FormSchema => ({ kind: 'string', required })
const number = (min?: number, max?: number, integer = false): FormSchema => ({
  kind: 'number',
  min,
  max,
  integer,
})
const choice = (...options: string[]): FormSchema => ({ ...text(), options })
const array = (item: FormSchema = text(), required = true): FormSchema => ({
  kind: 'array',
  valueLabel:
    item.kind === 'string' ? 'form_fieldName' : item.kind === 'sql' ? 'field_sql' : undefined,
  item,
  required,
})
const object = (fields: Record<string, FormSchema>): FormSchema => ({ kind: 'object', fields })
const map = (item: FormSchema = {}, required = true): FormSchema => ({
  kind: 'object',
  item,
  required,
})
const lookup = {
  resourceId: { kind: 'resource', required: true } as FormSchema,
  sql: { kind: 'sql', required: true } as FormSchema,
  queryTimeoutSeconds: number(1, 86400, true),
  batchSize: number(1, 2000, true),
}
export const ruleSchemas: Record<string, FormSchema> = {
  FIELD_MAPPING: { ...map(text()), keyLabel: 'targetColumn', valueLabel: 'sourceColumn' },
  DEFAULT_VALUE: { ...map(), keyLabel: 'form_field' },
  TRIM: object({ fields: array() }),
  REPLACE: object({ field: text(), replace: map({}, false) }),
  DATE_FORMAT: object({ field: text(), source: text(), target: text() }),
  TYPE_CONVERT: object({
    field: text(),
    target: choice(
      'STRING',
      'INTEGER',
      'LONG',
      'DOUBLE',
      'DECIMAL',
      'BOOLEAN',
      'DATE',
      'TIMESTAMP',
    ),
  }),
  NUMBER_SCALE: object({
    field: text(),
    multiply: number(),
    divide: { ...number(), nonzero: true },
    add: number(),
    scale: number(-1, 1000, true),
    roundingMode: choice(
      'UP',
      'DOWN',
      'CEILING',
      'FLOOR',
      'HALF_UP',
      'HALF_DOWN',
      'HALF_EVEN',
      'UNNECESSARY',
    ),
  }),
  EXPRESSION: object({ target: text(), expression: text() }),
  LOOKUP: object({
    ...lookup,
    sourceField: text(),
    targetField: text(),
    keyColumn: text(),
    valueColumn: text(),
  }),
  DEDUPLICATE: object({
    fields: array(),
    maxKeys: number(1, 10000000, true),
    keep: choice('FIRST'),
  }),
  REQUIRED: object({ fields: array() }),
  REGEX: object({ field: text(), pattern: text() }),
  RANGE: object({ field: text(), min: number(), max: number() }),
  ENUM: object({ field: text(), values: array({}) }),
  CONDITION: object({ expression: text() }),
  SQL: object({ ...lookup, mode: choice('GLOBAL', 'FIELD'), field: text(false) }),
  FOREIGN_KEY: object({ ...lookup, field: text() }),
}
ruleSchemas.DATE_FORMAT!.fields!.target!.label = 'form_targetFormat'
ruleSchemas.TYPE_CONVERT!.fields!.target!.label = 'form_targetType'
ruleSchemas.EXPRESSION!.fields!.target!.label = 'targetColumn'
// These optional properties have server defaults; leaving them blank keeps those defaults.
for (const [type, keys] of Object.entries({
  NUMBER_SCALE: ['roundingMode'],
  DEDUPLICATE: ['keep'],
}))
  for (const key of keys) ruleSchemas[type]!.fields![key]!.required = false
export const targetSchemas: Record<string, FormSchema> = {
  columnNullPolicies: {
    ...map(choice('KEEP_TARGET', 'OVERWRITE'), false),
    keyLabel: 'targetColumn',
    valueLabel: 'field_nullPolicy',
  },
  beforeSql: array({ kind: 'sql', required: true }, false),
  afterSql: array({ kind: 'sql', required: true }, false),
  protection: {
    ...object({
      column: text(),
      protectedValues: array({}),
      onProtected: { ...choice('SKIP', 'FAIL'), required: false },
    }),
    nullable: true,
  },
  scd2: {
    ...object({
      businessKeys: array(),
      compareFields: array(),
      effectiveValueField: text(),
      validFromColumn: text(),
      validToColumn: text(),
      currentFlagColumn: text(false),
      currentValue: {},
      historyValue: {},
      granularity: { ...choice('DAY', 'TIMESTAMP'), required: false },
    }),
    nullable: true,
  },
}
export function formNode(value: unknown): FormNode {
  if (value === null) return { kind: 'null', value: '', entries: [] }
  if (value instanceof LosslessNumber || typeof value === 'number')
    return { kind: 'number', value: String(value), entries: [] }
  if (Array.isArray(value))
    return { kind: 'array', value: '', entries: value.map((v) => ({ key: '', node: formNode(v) })) }
  if (typeof value === 'object' && value)
    return {
      kind: 'object',
      value: '',
      entries: Object.entries(value).map(([key, v]) => ({ key, node: formNode(v) })),
    }
  return {
    kind: typeof value === 'boolean' ? 'boolean' : 'string',
    value: String(value ?? ''),
    entries: [],
  }
}
export const parseForm = (s: string) => formNode(parse(s))
export function prepareForm(raw: string, schema: FormSchema): FormNode {
  const node = parseForm(raw)
  if (node.kind === 'object' && schema.fields)
    for (const [key, field] of Object.entries(schema.fields))
      if (!node.entries.some((e) => e.key === key))
        node.entries.push({ key, node: emptyNode(field) })
  return node
}
export function emptyNode(schema: FormSchema = {}): FormNode {
  const kind =
    schema.kind === 'resource'
      ? 'number'
      : schema.kind === 'sql'
        ? 'string'
        : (schema.kind ?? 'string')
  return { kind, omitted: true, value: '', entries: [] }
}
export function formValue(node: FormNode, schema: FormSchema = {}, optional = false): unknown {
  if (node.kind === 'null') {
    if (schema.kind && !schema.nullable) throw Error('null')
    return null
  }
  const expected =
    schema.kind === 'resource' ? 'number' : schema.kind === 'sql' ? 'string' : schema.kind
  if (expected && node.kind !== expected && !(expected === 'number' && node.kind === 'string'))
    throw Error('type')
  if (node.kind === 'object') {
    const result: Record<string, unknown> = Object.create(null)
    for (const { key, node: child } of node.entries) {
      if (!key.trim() || Object.hasOwn(result, key)) throw Error('key')
      const childSchema = schema.fields?.[key] ?? schema.item ?? {}
      const value = formValue(child, childSchema, !!schema.fields?.[key] && !childSchema.required)
      if (value !== undefined) result[key] = value
    }
    for (const [key, field] of Object.entries(schema.fields ?? {}))
      if (field.required && !(key in result)) throw Error(key)
    if (schema.required && !Object.keys(result).length) throw Error('empty')
    return result
  }
  if (node.kind === 'array') {
    if (schema.required && !node.entries.length) throw Error('empty')
    return node.entries.map(({ node: child }) => formValue(child, schema.item))
  }
  if (!node.value && optional && (node.omitted || schema.kind)) return undefined
  if (schema.required && !node.value.trim()) throw Error('required')
  if (schema.options && !schema.options.includes(node.value)) throw Error('option')
  if (node.kind === 'number' || expected === 'number') {
    const n = new LosslessNumber(node.value)
    if (schema.integer && !/^-?\d+$/.test(node.value)) throw Error('integer')
    if (schema.min !== undefined && Number(node.value) < schema.min) throw Error('min')
    if (schema.max !== undefined && Number(node.value) > schema.max) throw Error('max')
    if (schema.nonzero && !/[1-9]/.test(node.value.split(/[eE]/)[0]!)) throw Error('zero')
    if (
      schema.kind === 'resource' &&
      (!/^[1-9]\d*$/.test(node.value) || BigInt(node.value) > 9223372036854775807n)
    )
      throw Error('resource')
    return node.kind === 'string' ? node.value : n
  }
  if (node.kind === 'boolean') {
    if (!['true', 'false'].includes(node.value)) throw Error('boolean')
    return node.value === 'true'
  }
  return node.value
}
export const formText = (node: FormNode, schema?: FormSchema) => stringify(formValue(node, schema))!
export function ruleFormText(type: string, raw: string, node?: FormNode) {
  const value = formValue(node ?? parseForm(raw), ruleSchemas[type]) as Record<string, unknown>
  if (type === 'RANGE' && value.min == null && value.max == null) throw Error('range')
  if (
    ['LOOKUP', 'FOREIGN_KEY'].includes(type) ||
    (type === 'SQL' && (value.mode ?? 'FIELD') === 'FIELD')
  ) {
    if (!String(value.sql ?? '').includes(':keys')) throw Error('keys')
    if (type === 'SQL' && !String(value.field ?? '').trim()) throw Error('field')
  }
  return stringify(value)!
}
