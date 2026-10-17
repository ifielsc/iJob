import { randomUuid } from '../../shared/runtime/uuid'
import {
  parse,
  stringify,
  LosslessNumber,
  isLosslessNumber,
  compareLosslessNumber,
} from 'lossless-json'

export const checkTypes = ['NORMAL', 'COUNT', 'EXISTS', 'EMPTY'] as const
export const comparisons = ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'BETWEEN'] as const
export type CheckType = (typeof checkTypes)[number]
export type ValueKind = 'text' | 'number' | 'boolean' | 'expression' | 'json'
export interface CheckValue {
  kind: ValueKind
  text: string
}
export interface CheckItem {
  key: string
  original: string
  name: string
  sql: string
  type: CheckType
  level: 'ERROR' | 'WARN'
  compareType: string
  expected: CheckValue
  minValue: CheckValue
  maxValue: CheckValue
  variable: string
}
export interface DataCheckDraft {
  resourceId: string
  original: string
  blocked: boolean
  checks: CheckItem[]
  queryTimeoutSeconds: number | null
  exportVariables: boolean
  notify: boolean
  notifyConfigId: string
  notifyOnSuccess: boolean
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value) || isLosslessNumber(value))
    throw Error('object')
  return value as Record<string, unknown>
}
export function checkId(value: unknown): string {
  const text = String(value)
  if (!/^[1-9]\d*$/.test(text) || BigInt(text) > 9223372036854775807n) throw Error('id')
  return text
}
function valueDraft(value: unknown): CheckValue {
  if (value == null) return { kind: 'text', text: '' }
  if (isLosslessNumber(value)) return { kind: 'number', text: value.value }
  if (typeof value === 'boolean') return { kind: 'boolean', text: String(value) }
  if (typeof value === 'string')
    return { kind: value.includes('${') ? 'expression' : 'text', text: value }
  return { kind: 'json', text: stringify(value)! }
}
export function newCheck(): CheckItem {
  return {
    key: randomUuid(),
    original: '{}',
    name: '',
    sql: '',
    type: 'NORMAL',
    level: 'ERROR',
    compareType: 'EQ',
    expected: valueDraft(null),
    minValue: valueDraft(null),
    maxValue: valueDraft(null),
    variable: '',
  }
}
export function dataCheckDraft(text?: string): DataCheckDraft {
  const result: DataCheckDraft = {
    resourceId: '',
    original: text ?? '{}',
    blocked: false,
    checks: [newCheck()],
    queryTimeoutSeconds: 30,
    exportVariables: false,
    notify: false,
    notifyConfigId: '',
    notifyOnSuccess: false,
  }
  if (!text) return result
  try {
    const c = object(parse(text))
    result.resourceId = checkId(c.resourceId)
    if (!Array.isArray(c.checks) || !Array.isArray(c.handlers ?? [])) throw Error('arrays')
    result.checks = c.checks.map((raw) => {
      const item = object(raw),
        draft = newCheck()
      if (
        !checkTypes.includes(item.type as CheckType) ||
        !['ERROR', 'WARN'].includes(String(item.level ?? 'ERROR'))
      )
        throw Error('enum')
      for (const key of ['name', 'sql', 'variable'] as const) {
        if (item[key] != null && typeof item[key] !== 'string') throw Error('text')
        draft[key] = (item[key] as string) ?? ''
      }
      draft.original = stringify(raw)!
      draft.type = item.type as CheckType
      draft.level = (item.level as 'ERROR' | 'WARN') ?? 'ERROR'
      draft.compareType = String(item.compareType ?? 'EQ')
      if (!comparisons.includes(draft.compareType as (typeof comparisons)[number]))
        throw Error('compare')
      draft.expected = valueDraft(item.expected ?? (draft.type === 'EXISTS' ? true : null))
      draft.minValue = valueDraft(item.minValue)
      draft.maxValue = valueDraft(item.maxValue)
      if (
        draft.type === 'EMPTY' &&
        ['compareType', 'expected', 'minValue', 'maxValue'].some((k) => item[k] != null)
      )
        throw Error('empty')
      if (
        draft.type === 'EXISTS' &&
        (!['EQ', 'NE'].includes(draft.compareType) ||
          ['minValue', 'maxValue'].some((k) => item[k] != null))
      )
        throw Error('exists')
      if (
        draft.type !== 'EMPTY' &&
        (draft.compareType === 'BETWEEN'
          ? item.expected != null
          : item.minValue != null || item.maxValue != null)
      )
        throw Error('mixed')
      return draft
    })
    const timeout = c.queryTimeoutSeconds === undefined ? 30 : Number(String(c.queryTimeoutSeconds))
    result.queryTimeoutSeconds = c.queryTimeoutSeconds === null ? null : timeout
    if (timeout != null && !Number.isSafeInteger(timeout)) throw Error('timeout')
    const seen = new Set<string>()
    for (const raw of (c.handlers ?? []) as unknown[]) {
      const handler = object(raw),
        type = String(handler.type).toUpperCase()
      if (!['VARIABLE', 'NOTIFY'].includes(type) || seen.has(type)) throw Error('handler')
      seen.add(type)
      if (type === 'VARIABLE') result.exportVariables = true
      else {
        const params = object(handler.params)
        result.notify = true
        result.notifyConfigId = checkId(params.notifyConfigId)
        if (params.notifyOnSuccess != null && typeof params.notifyOnSuccess !== 'boolean')
          throw Error('boolean')
        result.notifyOnSuccess = params.notifyOnSuccess === true
      }
    }
  } catch {
    result.blocked = true
  }
  return result
}
const decimal = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/
export function checkValue(value: CheckValue): unknown {
  if (value.kind === 'number') {
    if (!decimal.test(value.text) || value.text.length > 1000) throw Error('number')
    return new LosslessNumber(value.text)
  }
  if (value.kind === 'boolean') {
    if (!['true', 'false'].includes(value.text)) throw Error('boolean')
    return value.text === 'true'
  }
  if (value.kind === 'expression' && !value.text.includes('${')) throw Error('expression')
  if (value.kind === 'json') return parse(value.text)
  return value.text
}
export function dataCheckConfig(d: DataCheckDraft, resourceId: string): string {
  if (d.blocked) throw Error('Unsupported DATA_CHECK configuration')
  const config = object(parse(d.original))
  config.resourceId = new LosslessNumber(checkId(resourceId))
  config.queryTimeoutSeconds = d.queryTimeoutSeconds
  config.checks = d.checks.map((item) => {
    const c = object(parse(item.original))
    Object.assign(c, {
      name: item.name,
      sql: item.sql,
      type: item.type,
      level: item.level,
      variable: item.variable || null,
    })
    for (const key of ['compareType', 'expected', 'minValue', 'maxValue']) delete c[key]
    if (item.type !== 'EMPTY') {
      c.compareType = item.compareType
      if (item.compareType === 'BETWEEN') {
        c.minValue = checkValue(item.minValue)
        c.maxValue = checkValue(item.maxValue)
      } else c.expected = checkValue(item.expected)
    }
    return c
  })
  const old = (config.handlers ?? []) as Record<string, unknown>[]
  const types = [
    ...new Set([...old.map((h) => String(h.type).toUpperCase()), 'VARIABLE', 'NOTIFY']),
  ]
  config.handlers = types
    .filter((type) => (type === 'VARIABLE' ? d.exportVariables : d.notify))
    .map((type) => {
      const handler = old.find((h) => String(h.type).toUpperCase() === type) ?? { type }
      if (type === 'NOTIFY')
        handler.params = {
          ...object(handler.params ?? {}),
          notifyConfigId: new LosslessNumber(checkId(d.notifyConfigId)),
          notifyOnSuccess: d.notifyOnSuccess,
        }
      return handler
    })
  return stringify(config)!
}
export function dataCheckIssues(d: DataCheckDraft): Record<string, string> {
  const errors: Record<string, string> = {},
    fail = (key: string, code = 'invalid') => {
      errors['check.' + key] = code
    }
  if (d.blocked) {
    fail('checks', 'unsupported')
    return errors
  }
  if (!d.checks.length || d.checks.length > 100) fail('checks')
  if (
    !Number.isInteger(d.queryTimeoutSeconds) ||
    d.queryTimeoutSeconds! < 1 ||
    d.queryTimeoutSeconds! > 3600
  )
    fail('queryTimeoutSeconds')
  const names = new Set<string>(),
    variables = new Set<string>()
  const reserved = new Set([
    'data_check_success',
    'data_check_total',
    'data_check_failed',
    'data_check_result',
  ])
  const validName = (s: string) => !!s && s === s.trim() && s.length <= 128 && !s.includes('${')
  d.checks.forEach((item, i) => {
    const field = (key: string, code = 'invalid') => fail(`${i}.${key}`, code)
    if (
      !validName(item.name) ||
      names.has(item.name) ||
      (d.exportVariables && reserved.has(item.name))
    )
      field('name')
    names.add(item.name)
    if (!item.sql.trim() || item.sql.length > 1000000 || item.sql.includes('\0')) field('sql')
    if (item.variable && (!validName(item.variable) || variables.has(item.variable)))
      field('variable')
    if (item.variable) variables.add(item.variable)
    if (item.type !== 'EMPTY') {
      if (
        !comparisons.includes(item.compareType as (typeof comparisons)[number]) ||
        (item.type === 'EXISTS' && !['EQ', 'NE'].includes(item.compareType))
      )
        field('compareType')
      for (const key of item.compareType === 'BETWEEN'
        ? (['minValue', 'maxValue'] as const)
        : (['expected'] as const)) {
        try {
          const value = checkValue(item[key])
          if (
            value == null ||
            (item.type === 'COUNT' &&
              !isLosslessNumber(value) &&
              !(typeof value === 'string' && (decimal.test(value) || value.includes('${'))))
          )
            throw Error()
          if (
            item.type === 'EXISTS' &&
            typeof value !== 'boolean' &&
            !(typeof value === 'string' && value.includes('${'))
          )
            throw Error()
        } catch {
          field(key)
        }
      }
      if (item.type === 'COUNT' && item.compareType === 'BETWEEN') {
        try {
          const min = checkValue(item.minValue),
            max = checkValue(item.maxValue)
          const minText = String(min),
            maxText = String(max)
          if (
            decimal.test(minText) &&
            decimal.test(maxText) &&
            compareLosslessNumber(new LosslessNumber(minText), new LosslessNumber(maxText)) > 0
          )
            field('minValue')
        } catch {
          /* Individual value errors above retain their field paths. */
        }
      }
    }
  })
  if (d.exportVariables)
    d.checks.forEach((item, i) => {
      if (item.variable && (reserved.has(item.variable) || names.has(item.variable)))
        fail(`${i}.variable`)
    })
  if (d.notify)
    try {
      checkId(d.notifyConfigId)
    } catch {
      fail('notifyConfigId', 'required')
    }
  return errors
}
