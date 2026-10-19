// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { LosslessNumber, parse, stringify } from 'lossless-json'

export const valueKinds = [
  'string',
  'integer',
  'decimal',
  'boolean',
  'null',
  'object',
  'array',
] as const
export type ValueKind = (typeof valueKinds)[number]
export interface JsonValue {
  kind: ValueKind
  text: string
}
export interface JsonRow extends JsonValue {
  name: string
  included: boolean
  key: number
}
export interface JsonDocument extends JsonValue {
  rows: JsonRow[]
  absent: boolean
}
let sequence = 0

// Parse object names under a prefix: lossless-json otherwise treats __proto__ specially.
// Its duplicate-key check still runs on the prefixed names, including escaped names.
export function parseExact(source: string): unknown {
  return decode(source, false)
}
function decode(source: string, rootEntries: boolean): unknown {
  const encoded = source.replace(/"(?:[^"\\]|\\.)*"/g, (token, offset: number) =>
    /^\s*:/.test(source.slice(offset + token.length))
      ? JSON.stringify('key:' + JSON.parse(token))
      : token,
  )
  return parse(encoded, (_key, value) => {
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      !(value instanceof LosslessNumber)
    ) {
      const entries = Object.entries(value).map(([name, child]) => [name.slice(4), child] as const)
      return rootEntries && _key === '' ? new Map(entries) : Object.fromEntries(entries)
    }
    return value
  })
}
export function newValue(kind: ValueKind): JsonValue {
  return {
    kind,
    text: {
      string: '',
      integer: '0',
      decimal: '0.0',
      boolean: 'false',
      null: 'null',
      object: '{}',
      array: '[]',
    }[kind],
  }
}
export function readValue(value: unknown): JsonValue {
  if (typeof value === 'string') return { kind: 'string', text: value }
  const text = stringify(value)!
  const kind: ValueKind =
    value === null
      ? 'null'
      : Array.isArray(value)
        ? 'array'
        : value instanceof LosslessNumber
          ? /^-?\d+$/.test(text)
            ? 'integer'
            : 'decimal'
          : typeof value === 'boolean'
            ? 'boolean'
            : 'object'
  return { kind, text }
}
export function valueText(value: JsonValue): string {
  if (value.kind === 'string') return JSON.stringify(value.text)
  const parsed = parseExact(value.text)
  if (value.kind === 'integer' && !/^-?(?:0|[1-9]\d*)$/.test(value.text)) throw Error('value')
  if (['integer', 'decimal'].includes(value.kind) && !(parsed instanceof LosslessNumber))
    throw Error('value')
  if (value.kind === 'boolean' && typeof parsed !== 'boolean') throw Error('value')
  if (value.kind === 'null' && parsed !== null) throw Error('value')
  if (value.kind === 'array' && !Array.isArray(parsed)) throw Error('value')
  if (
    value.kind === 'object' &&
    (!parsed ||
      typeof parsed !== 'object' ||
      Array.isArray(parsed) ||
      parsed instanceof LosslessNumber)
  )
    throw Error('value')
  return value.text
}
export function newRow(name = '', kind: ValueKind = 'string'): JsonRow {
  return { ...newValue(kind), name, included: true, key: ++sequence }
}
export function readDocument(text: string): JsonDocument {
  if (!text.trim()) return { ...newValue('object'), absent: true, rows: [] }
  const value = decode(text, true),
    root: JsonValue = value instanceof Map ? { kind: 'object', text } : readValue(value)
  const entries =
    root.kind === 'object'
      ? [...(value as Map<string, unknown>).entries()]
      : root.kind === 'array'
        ? (value as unknown[]).map((v, i) => [String(i), v] as const)
        : []
  return {
    ...root,
    absent: false,
    rows: entries.map(([name, child]) => ({
      ...readValue(child),
      name,
      included: true,
      key: ++sequence,
    })),
  }
}
export function writeDocument(draft: JsonDocument): string {
  if (draft.absent) return ''
  if (!['object', 'array'].includes(draft.kind)) return valueText(draft)
  const rows = draft.rows.filter((row) => row.included)
  if (draft.kind === 'object') {
    if (new Set(rows.map((row) => row.name)).size !== rows.length) throw Error('duplicate')
    return '{' + rows.map((row) => JSON.stringify(row.name) + ':' + valueText(row)).join(',') + '}'
  }
  return '[' + rows.map(valueText).join(',') + ']'
}
