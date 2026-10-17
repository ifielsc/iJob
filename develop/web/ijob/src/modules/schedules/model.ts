import type { Form } from '../parameters/public/model'
import { parse, stringify, isLosslessNumber } from 'lossless-json'
import { ApiError } from '../../shared/api/errors'
import { record, exactId } from '../../shared/api/values'
import { wallTime } from '../../shared/time/wall-time'
export const types = ['CRON', 'INTERVAL', 'ONCE'] as const
export const policies = ['SKIP', 'FIRE_ONCE', 'CATCH_UP_BOUNDED'] as const
export type ScheduleType = (typeof types)[number]
export function id(value: unknown) {
  return exactId(isLosslessNumber(value) ? value.value : value)
}
export interface Metadata {
  zoneId: string
  defaultMisfirePolicy: string
  defaultMisfireGraceSec: number
  defaultMaxCatchupCount: number
  maxCatchupWindowSec: number
  maxPreviewCount: number
  maxIntervalSec: number
  maxCatchupCount: number
  maxVariableCount: number
  maxVariableBytes: number
}
export function metadata(value: unknown): Metadata {
  const r = record(value)
  if (
    typeof r.zoneId !== 'string' ||
    !policies.includes(r.defaultMisfirePolicy as (typeof policies)[number])
  )
    throw new ApiError('protocol')
  try {
    new Intl.DateTimeFormat('en', { timeZone: r.zoneId }).format()
  } catch {
    throw new ApiError('protocol')
  }
  for (const key of [
    'defaultMisfireGraceSec',
    'defaultMaxCatchupCount',
    'maxCatchupWindowSec',
    'maxPreviewCount',
    'maxIntervalSec',
    'maxCatchupCount',
    'maxVariableCount',
    'maxVariableBytes',
  ]) {
    if (
      !Number.isSafeInteger(r[key]) ||
      Number(r[key]) < (key === 'defaultMisfireGraceSec' ? 0 : 1)
    )
      throw new ApiError('protocol')
  }
  return r as unknown as Metadata
}
export interface Schedule {
  id: string
  targetType: 'TASK' | 'JOB'
  targetId: string
  targetName: string
  scheduleType: ScheduleType
  cronExpr: string
  intervalSec: number | null
  calendarId: string
  enabled: boolean
  startTime: string
  endTime: string
  lastFireTime: string
  nextFireTime: string
  createTime: string
  updateTime: string
  misfirePolicy: string
  misfireGraceSec: number | null
  maxCatchupCount: number | null
  maxQueueDelaySec: number | null
  parameterForm?: Form | null
  variablesText?: string
}
export function schedule(value: unknown): Schedule {
  const r = record(value)
  if (
    !types.includes(r.scheduleType as ScheduleType) ||
    !['TASK', 'JOB'].includes(String(r.targetType)) ||
    typeof r.enabled !== 'boolean'
  )
    throw new ApiError('protocol')
  const result = {
    ...r,
    id: id(r.id),
    targetId: id(r.targetId),
    targetName: typeof r.targetName === 'string' ? r.targetName : '',
    calendarId: r.calendarId == null ? '' : id(r.calendarId),
  } as unknown as Schedule
  for (const key of ['cronExpr', 'misfirePolicy'] as const)
    result[key] = r[key] == null ? '' : String(r[key])
  for (const key of [
    'startTime',
    'endTime',
    'lastFireTime',
    'nextFireTime',
    'createTime',
    'updateTime',
  ] as const) {
    if (r[key] != null && typeof r[key] !== 'string') throw new ApiError('protocol')
    result[key] = r[key] == null ? '' : wallTime(String(r[key]))
  }
  for (const key of [
    'intervalSec',
    'misfireGraceSec',
    'maxCatchupCount',
    'maxQueueDelaySec',
  ] as const) {
    if (r[key] != null && (!Number.isSafeInteger(r[key]) || Number(r[key]) < 0))
      throw new ApiError('protocol')
    result[key] = r[key] == null ? null : Number(r[key])
  }
  if ('variables' in r)
    result.variablesText = stringify(r.variables == null ? {} : record(r.variables), null, 2)!
  return result
}
export function draftOf(row?: Schedule) {
  return {
    targetType: row?.targetType ?? 'TASK',
    targetId: row?.targetId ?? '',
    scheduleType: row?.scheduleType ?? 'CRON',
    cronExpr: row?.cronExpr ?? '',
    intervalSec: row?.intervalSec ?? null,
    calendarId: row?.calendarId ?? '',
    startTime: row?.startTime ?? '',
    endTime: row?.endTime ?? '',
    enabled: row?.enabled ?? false,
    misfirePolicy: row?.misfirePolicy ?? '',
    misfireGraceSec: row?.misfireGraceSec ?? null,
    maxCatchupCount: row?.maxCatchupCount ?? null,
    maxQueueDelaySec: row?.maxQueueDelaySec ?? null,
    variablesText: row?.variablesText ?? '{}',
    parameterForm: row?.parameterForm ?? null,
  }
}
export type Draft = ReturnType<typeof draftOf>
export function issues(d: Draft, m: Metadata, preview = false): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!preview) {
    try {
      id(d.targetId)
    } catch {
      errors.targetId = 'invalidId'
    }
  }
  if (d.calendarId) {
    try {
      id(d.calendarId)
    } catch {
      errors.calendarId = 'invalidId'
    }
  }
  for (const key of ['startTime', 'endTime'] as const)
    if (d[key]) {
      try {
        wallTime(d[key])
      } catch {
        errors[key] = 'invalidTime'
      }
    }
  if (!errors.startTime && !errors.endTime && d.startTime && d.endTime && d.startTime > d.endTime)
    errors.endTime = 'timeOrder'
  if (d.scheduleType === 'CRON' && !d.cronExpr.trim()) errors.cronExpr = 'required'
  if (
    d.scheduleType === 'INTERVAL' &&
    (!Number.isInteger(d.intervalSec) ||
      !d.intervalSec ||
      d.intervalSec < 1 ||
      d.intervalSec > m.maxIntervalSec)
  )
    errors.intervalSec = 'invalidNumber'
  if (d.scheduleType === 'ONCE' && !d.startTime) errors.startTime = 'required'
  if (!preview) {
    for (const [key, min, max] of [
      ['misfireGraceSec', 0, 2147483647],
      ['maxCatchupCount', 1, m.maxCatchupCount],
      ['maxQueueDelaySec', 1, m.maxIntervalSec],
    ] as const) {
      const v = d[key]
      if (v !== null && (!Number.isInteger(v) || v < min || v > max)) errors[key] = 'invalidNumber'
    }
    try {
      const vars = record(parse(d.variablesText)),
        keys = Object.keys(vars)
      if (
        keys.length > m.maxVariableCount ||
        keys.some((k) => !k.trim() || k.length > 128 || k.startsWith('ijob.')) ||
        new TextEncoder().encode(stringify(vars)).length > m.maxVariableBytes
      )
        throw Error()
    } catch {
      errors.variablesText = 'invalidVariables'
    }
  }
  return errors
}
export function previewBody(d: Draft) {
  return {
    scheduleType: d.scheduleType,
    cronExpr: d.scheduleType === 'CRON' ? d.cronExpr.trim() : null,
    intervalSec: d.scheduleType === 'INTERVAL' ? d.intervalSec : null,
    calendarId: d.calendarId || null,
    startTime: d.startTime ? wallTime(d.startTime) : null,
    endTime: d.endTime ? wallTime(d.endTime) : null,
  }
}
export function body(d: Draft) {
  return {
    ...previewBody(d),
    targetType: d.targetType,
    targetId: d.targetId,
    enabled: d.enabled,
    misfirePolicy: d.misfirePolicy || null,
    misfireGraceSec: d.misfireGraceSec,
    maxCatchupCount: d.maxCatchupCount,
    maxQueueDelaySec: d.maxQueueDelaySec,
    variables: record(parse(d.variablesText)),
    parameterForm: d.parameterForm,
  }
}
export function previewRows(value: unknown): { fireTime: string; calendarAllowed: boolean }[] {
  const r = record(value)
  if (!Array.isArray(r.items)) throw new ApiError('protocol')
  return r.items.map((v) => {
    const row = record(v)
    if (typeof row.fireTime !== 'string' || typeof row.calendarAllowed !== 'boolean')
      throw new ApiError('protocol')
    return { fireTime: wallTime(row.fireTime), calendarAllowed: row.calendarAllowed }
  })
}
