import {
  parse as parseJSON,
  stringify,
  isLosslessNumber,
  isSafeNumber,
  LosslessNumber,
} from 'lossless-json'
import { record, exactId } from '../../shared/api/values'
function parse(text: string) {
  return parseJSON(text, undefined, {
    parseNumber: (n) => (isSafeNumber(n) ? Number(n) : new LosslessNumber(n)),
  })
}
export const fieldTypes = [
  'STRING',
  'INTEGER',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'ENUM',
  'OBJECT',
  'ARRAY',
] as const
export type FieldType = (typeof fieldTypes)[number]
export interface Field {
  name: string
  type: FieldType
  required?: boolean
  sensitive?: boolean
  hasDefault?: boolean
  defaultValue?: unknown
  description?: string
  options?: string[]
  minimum?: unknown
  maximum?: unknown
  maxLength?: number | null
}
export interface Form {
  templateId?: string | null
  fields: Field[]
}
export interface Template {
  id: string
  name: string
  description?: string
  revision: string
  fields: Field[]
}
export interface Row {
  field: Field
  included: boolean
  text: string
}
export function copy<T>(value: T): T {
  return parse(stringify(value)!) as T
}
export function template(value: unknown): Template {
  const r = record(value)
  if (typeof r.name !== 'string' || !Array.isArray(r.fields)) throw Error('Invalid template')
  return { ...r, id: exactId(r.id), revision: exactId(r.revision), fields: r.fields } as Template
}
export function emptyField(): Field {
  return { name: '', type: 'STRING', required: false, hasDefault: false, description: '' }
}
export function textOf(v: unknown, type: FieldType): string {
  return v == null
    ? ''
    : ['STRING', 'ENUM', 'DATE', 'DATETIME'].includes(type)
      ? String(v)
      : stringify(v)!
}
export function valueOf(text: string, type: FieldType): unknown {
  if (['STRING', 'ENUM', 'DATE', 'DATETIME'].includes(type)) return text
  const value: unknown = parse(text)
  if (
    (type === 'BOOLEAN' && typeof value !== 'boolean') ||
    (type === 'OBJECT' &&
      (!value || typeof value !== 'object' || Array.isArray(value) || isLosslessNumber(value))) ||
    (type === 'ARRAY' && !Array.isArray(value))
  )
    throw Error('type')
  if (type === 'INTEGER' || type === 'DECIMAL') {
    if (typeof value !== 'number' && !isLosslessNumber(value)) throw Error('type')
    if (type === 'INTEGER' && !/^-?\d+$/.test(text.trim())) throw Error('integer')
  }
  return value
}
export function infer(value: unknown): FieldType {
  if (typeof value === 'boolean') return 'BOOLEAN'
  if (typeof value === 'number' || isLosslessNumber(value))
    return /^-?\d+$/.test(String(value)) ? 'INTEGER' : 'DECIMAL'
  if (Array.isArray(value)) return 'ARRAY'
  if (value && typeof value === 'object') return 'OBJECT'
  return 'STRING'
}
export function rowsOf(text: string, form?: Form | null): Row[] {
  const values = record(parse(text.trim() || '{}'))
  const fields = copy(form?.fields ?? [])
  for (const [name, value] of Object.entries(values))
    if (!fields.some((f) => f.name === name))
      fields.push({ ...emptyField(), name, type: infer(value) })
  return fields.map((field) => ({
    field,
    included: values[field.name] != null,
    text: textOf(values[field.name], field.type),
  }))
}
export function fromTemplate(t: Template): Row[] {
  return copy(t.fields).map((field) => ({
    field,
    included: !!field.hasDefault || !!field.required,
    text: field.hasDefault
      ? textOf(field.defaultValue, field.type)
      : field.type === 'BOOLEAN'
        ? 'false'
        : '',
  }))
}
export function readRows(rows: Row[]): Record<string, unknown> {
  const values: Record<string, unknown> = Object.create(null) as Record<string, unknown>,
    names = new Set<string>()
  for (const { field: f, included, text } of rows) {
    if (!f.name.trim() || f.name.length > 128 || f.name.startsWith('ijob.') || names.has(f.name))
      throw Error(f.name || 'name')
    names.add(f.name)
    if (!included) {
      if (f.required) throw Error(f.name)
      continue
    }
    if (f.required && !text) throw Error(f.name)
    const v = valueOf(text, f.type)
    if (f.maxLength != null && typeof v === 'string' && v.length > f.maxLength) throw Error(f.name)
    if (f.type === 'ENUM' && !f.options?.includes(text)) throw Error(f.name)
    if (f.type === 'DATE' && (!/^\d{4}-\d\d-\d\d$/.test(text) || Number.isNaN(Date.parse(text))))
      throw Error(f.name)
    if (f.type === 'DATETIME' && Number.isNaN(Date.parse(text))) throw Error(f.name)
    values[f.name] = v
  }
  if (names.size > 100 || new TextEncoder().encode(stringify(values)).length > 65536)
    throw Error('limit')
  return values
}
