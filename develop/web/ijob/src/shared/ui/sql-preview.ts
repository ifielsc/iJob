// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { parse, LosslessNumber, compareLosslessNumber } from 'lossless-json'
import type { InjectionKey } from 'vue'
export const previewFieldsKey: InjectionKey<() => Promise<unknown>> = Symbol('previewFields')
import { ApiError } from '../api/errors'
import { record } from '../api/values'

export interface QueryContext {
  resourceId: string
  mode: 'SQL' | 'PUSH' | 'ETL' | 'DATA_CHECK'
  parameters?: string
}
export interface QuerySelection {
  text: string
  from: number
  count: number
}
export interface PreviewParameter {
  name: string
  kind: 'text' | 'number' | 'boolean' | 'null'
  value: string
  named: boolean
  source: 'default' | 'etl' | 'runtime' | 'input'
  sensitive: boolean
  definition?: Record<string, unknown>
}
export function applyPreviewFields(rows: PreviewParameter[], binding: unknown) {
  if (binding == null) return
  const fields = record(binding).fields
  if (!Array.isArray(fields)) throw new ApiError('protocol')
  for (const row of rows) {
    if (row.named || row.name.startsWith('ijob.')) continue
    const field = fields.map(record).find((f) => f.name === row.name)
    if (!field) continue
    row.definition = field
    row.sensitive ||= field.sensitive === true
    if (['INTEGER', 'DECIMAL'].includes(String(field.type))) row.kind = 'number'
    else if (field.type === 'BOOLEAN') row.kind = 'boolean'
    if (field.hasDefault && row.source === 'input' && !row.sensitive) {
      row.value = String(field.defaultValue ?? '')
      row.source = 'default'
    }
  }
}
export function previewParameters(sql: string, context: QueryContext): PreviewParameter[] {
  const result: PreviewParameter[] = []
  const seen = new Set<string>()
  for (const match of sql.matchAll(/\$\{([^}]+)}/g)) {
    const expression = match[1]!.replace(/!$/, '')
    const colon = expression.indexOf(':')
    const name = colon > 0 ? expression.slice(0, colon) : expression
    if (seen.has(name)) continue
    seen.add(name)
    const value = colon > 0 ? expression.slice(colon + 1) : ''
    result.push({
      name,
      kind: 'text',
      value,
      named: false,
      source: name.startsWith('ijob.') ? 'runtime' : colon > 0 ? 'default' : 'input',
      sensitive: /password|secret|token|credential/i.test(name),
    })
  }
  if (context.mode === 'ETL') {
    const parameters = record(parse(context.parameters?.trim() || '{}'))
    // Preserve the plugin's quoted-literal and PostgreSQL cast rules.
    const cleaned = sql.replace(/\$\{[^}]+}/g, '0').replace(/'(?:''|[^'])*'|"(?:""|[^"])*"/g, '')
    for (const match of cleaned.matchAll(/(?<!:):([A-Za-z_][A-Za-z0-9_]*)/g)) {
      const name = match[1]!
      if (seen.has(':' + name)) continue
      seen.add(':' + name)
      const value = parameters[name]
      const kind =
        value === null
          ? 'null'
          : typeof value === 'boolean'
            ? 'boolean'
            : typeof value === 'number' || value instanceof LosslessNumber
              ? 'number'
              : 'text'
      result.push({
        name,
        kind,
        value: value == null ? '' : String(value),
        named: true,
        source: name in parameters ? 'etl' : /^etl(Last|Upper)/.test(name) ? 'runtime' : 'input',
        sensitive: /password|secret|token|credential/i.test(name),
      })
      if (typeof value === 'string') {
        for (const dependency of previewParameters(value, { ...context, mode: 'SQL' })) {
          if (!seen.has(dependency.name)) {
            seen.add(dependency.name)
            result.push(dependency)
          }
        }
      }
    }
  }
  return result
}
export function parameterValues(rows: PreviewParameter[]) {
  const variables: Record<string, unknown> = {},
    parameters: Record<string, unknown> = {}
  for (const row of rows) {
    if (!/^[A-Za-z_][A-Za-z0-9_.]{0,127}$/.test(row.name)) throw new Error('parameter')
    let value: unknown = row.value
    if (row.kind === 'number') {
      if (!/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(row.value))
        throw new Error('number')
      value = new LosslessNumber(row.value)
    } else if (row.kind === 'boolean') {
      if (!['true', 'false'].includes(row.value)) throw new Error('boolean')
      value = row.value === 'true'
    } else if (row.kind === 'null') value = null
    if (!row.named && value == null) throw new Error('text null')
    const field = row.definition
    if (field) {
      if (field.required && (value == null || value === '')) throw new Error('required')
      if (
        field.type === 'ENUM' &&
        (!Array.isArray(field.options) || !field.options.includes(value))
      )
        throw new Error('option')
      if (typeof field.maxLength === 'number' && String(value).length > field.maxLength)
        throw new Error('length')
      if (field.type === 'INTEGER' && !/^-?\d+$/.test(String(value))) throw new Error('integer')
      if (field.type === 'DATE' && !/^\d{4}-\d{2}-\d{2}$/.test(String(value)))
        throw new Error('date')
      if (field.type === 'DATETIME' && !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(String(value)))
        throw new Error('datetime')
      if (['OBJECT', 'ARRAY'].includes(String(field.type))) throw new Error('scalar required')
      if (row.kind === 'number') {
        if (
          field.minimum != null &&
          compareLosslessNumber(
            new LosslessNumber(String(value)),
            new LosslessNumber(String(field.minimum)),
          ) < 0
        )
          throw new Error('minimum')
        if (
          field.maximum != null &&
          compareLosslessNumber(
            new LosslessNumber(String(value)),
            new LosslessNumber(String(field.maximum)),
          ) > 0
        )
          throw new Error('maximum')
      }
    }
    ;(row.named ? parameters : variables)[row.name] = value
  }
  return { variables, parameters }
}
export interface PreviewResult {
  status: string
  columns: { name: string; type: string }[]
  rows: (string | null)[][]
  elapsedMillis: number
  truncated: string
  sqlState: string | null
  position: number | null
  traceId: string
  vendorCode: number | null
  databaseMessage: string | null
}
export function previewResult(value: unknown): PreviewResult {
  const r = record(value)
  if (
    typeof r.status !== 'string' ||
    !Array.isArray(r.columns) ||
    !Array.isArray(r.rows) ||
    typeof r.elapsedMillis !== 'number' ||
    typeof r.truncated !== 'string' ||
    typeof r.traceId !== 'string'
  )
    throw new ApiError('protocol')
  const columns = r.columns.map((v) => {
    const c = record(v)
    if (typeof c.name !== 'string' || typeof c.type !== 'string') throw new ApiError('protocol')
    return { name: c.name, type: c.type }
  })
  const rows = r.rows.map((v) => {
    if (
      !Array.isArray(v) ||
      v.length !== columns.length ||
      v.some((c) => c !== null && typeof c !== 'string')
    )
      throw new ApiError('protocol')
    return v as (string | null)[]
  })
  return {
    status: r.status,
    columns,
    rows,
    elapsedMillis: r.elapsedMillis,
    truncated: r.truncated,
    traceId: r.traceId,
    vendorCode: typeof r.vendorCode === 'number' ? r.vendorCode : null,
    databaseMessage: typeof r.databaseMessage === 'string' ? r.databaseMessage : null,
    sqlState: typeof r.sqlState === 'string' ? r.sqlState : null,
    position: typeof r.position === 'number' ? r.position : null,
  }
}
