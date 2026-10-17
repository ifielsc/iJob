import { parseExact as parse } from '../../shared/ui/json-form'
import { validFileId } from './file-transfer-model'
import { stringify, LosslessNumber } from 'lossless-json'
export const eventActions = ['PUBLISH', 'WAIT_EVENT', 'WAIT_APPROVAL', 'PROBE_FILE'] as const
export type EventAction = (typeof eventActions)[number]
export const validEventCode = (value: string) => /^[A-Za-z0-9._:-]{1,128}$/.test(value)
export interface EventValues {
  fileResourceId: string
  filePath: string
  filePattern: string
  recursive: boolean
  fileCaseSensitive: boolean
  minStableAgeSeconds: number | null
  maxMatches: number | null
  maxDepth: number | null
  maxScannedEntries: number | null
  scanTimeoutSeconds: number | null
  eventCode: string
  businessKey: string
  correlationKey: string
  summary: string
  payload: string
  idempotencyKey: string
  occurredAt: string
  suppressSameInbound: boolean
  timeoutSeconds: number | null
  lookbackSeconds: number | null
  allowedOperatorIds: string[]
}
export interface EventDraft {
  action: EventAction
  originalAction: string
  original: string
  blocked: boolean
  drafts: Record<EventAction, EventValues>
}
const defaults = (): EventValues => ({
  fileResourceId: '',
  filePath: '/',
  filePattern: '*',
  recursive: false,
  fileCaseSensitive: true,
  minStableAgeSeconds: 30,
  maxMatches: 100,
  maxDepth: 16,
  maxScannedEntries: 100000,
  scanTimeoutSeconds: 60,
  eventCode: '',
  businessKey: '',
  correlationKey: '',
  summary: '',
  payload: '',
  idempotencyKey: '',
  occurredAt: '',
  suppressSameInbound: true,
  timeoutSeconds: null,
  lookbackSeconds: 0,
  allowedOperatorIds: [],
})
export function eventDraft(text?: string): EventDraft {
  const result: EventDraft = {
    action: 'PUBLISH',
    originalAction: 'PUBLISH',
    original: text ?? '{}',
    blocked: false,
    drafts: {
      PUBLISH: defaults(),
      WAIT_EVENT: defaults(),
      WAIT_APPROVAL: defaults(),
      PROBE_FILE: defaults(),
    },
  }
  if (!text) return result
  try {
    const c = parse(text) as Record<string, unknown>
    if (!c || typeof c !== 'object' || Array.isArray(c)) throw Error()
    const action = c.action ?? 'PUBLISH'
    result.originalAction = String(action)
    if (!eventActions.includes(action as EventAction)) throw Error()
    result.action = action as EventAction
    const v = result.drafts[result.action]
    if (result.action === 'PROBE_FILE') {
      if (c.fileResourceId != null) {
        v.fileResourceId = String(c.fileResourceId)
        if (!validFileId(v.fileResourceId)) throw Error()
      }
      for (const key of ['filePath', 'filePattern'] as const)
        if (c[key] != null) {
          if (typeof c[key] !== 'string') throw Error()
          v[key] = c[key]
        }
      for (const key of ['recursive', 'fileCaseSensitive'] as const)
        if (c[key] != null) {
          if (typeof c[key] !== 'boolean') throw Error()
          v[key] = c[key]
        }
      for (const key of [
        'minStableAgeSeconds',
        'maxMatches',
        'maxDepth',
        'maxScannedEntries',
        'scanTimeoutSeconds',
      ] as const)
        if (c[key] != null) {
          const n = Number(String(c[key]))
          if (!/^-?\d+$/.test(String(c[key])) || !Number.isSafeInteger(n)) throw Error()
          v[key] = n
        }
    }

    for (const key of [
      'eventCode',
      'businessKey',
      'correlationKey',
      'summary',
      'idempotencyKey',
      'occurredAt',
    ] as const) {
      if (c[key] != null && typeof c[key] !== 'string') throw Error()
      v[key] = (c[key] as string) ?? ''
    }
    for (const key of ['timeoutSeconds', 'lookbackSeconds'] as const) {
      const number = c[key] == null ? null : Number(String(c[key]))
      if (number != null && (!Number.isInteger(number) || number > 2147483647)) throw Error()
      v[key] = number ?? (key === 'lookbackSeconds' ? 0 : null)
    }
    if (c.suppressSameInbound != null && typeof c.suppressSameInbound !== 'boolean') throw Error()
    v.suppressSameInbound =
      c.suppressSameInbound == null ? true : (c.suppressSameInbound as boolean)
    if (
      c.allowedOperatorIds != null &&
      (!Array.isArray(c.allowedOperatorIds) ||
        c.allowedOperatorIds.some((x) => typeof x !== 'string'))
    )
      throw Error()
    v.allowedOperatorIds = (c.allowedOperatorIds as string[]) ?? []
    v.payload = c.payload === undefined ? '' : stringify(c.payload, undefined, 2)!
  } catch {
    result.blocked = true
  }
  return result
}
export const eventActionFields: Record<EventAction, string[]> = {
  PROBE_FILE: [
    'eventCode',
    'businessKey',
    'correlationKey',
    'summary',
    'fileResourceId',
    'filePath',
    'filePattern',
    'recursive',
    'fileCaseSensitive',
    'minStableAgeSeconds',
    'maxMatches',
    'maxDepth',
    'maxScannedEntries',
    'scanTimeoutSeconds',
  ],
  PUBLISH: [
    'eventCode',
    'businessKey',
    'correlationKey',
    'summary',
    'payload',
    'idempotencyKey',
    'occurredAt',
    'suppressSameInbound',
  ],
  WAIT_EVENT: ['eventCode', 'businessKey', 'correlationKey', 'timeoutSeconds', 'lookbackSeconds'],
  WAIT_APPROVAL: ['allowedOperatorIds', 'timeoutSeconds'],
}
export function removedEventFields(d: EventDraft, next: EventAction): string[] {
  return Object.keys(parse(d.original) as object).filter(
    (key) => key !== 'action' && !eventActionFields[next].includes(key),
  )
}
export function eventConfig(d: EventDraft): string {
  if (d.blocked) throw Error('Unsupported event configuration')
  const v = d.drafts[d.action]
  const config = (d.action === d.originalAction ? parse(d.original) : {}) as Record<string, unknown>
  config.action = d.action
  for (const field of eventActionFields[d.action]) {
    const key = field as keyof EventValues
    config[key] =
      key === 'fileResourceId'
        ? new LosslessNumber(v.fileResourceId)
        : key === 'payload'
          ? v.payload.trim()
            ? parse(v.payload)
            : null
          : v[key] === ''
            ? null
            : v[key]
  }
  return stringify(config)!
}
export function eventIssues(d: EventDraft): Record<string, string> {
  const errors: Record<string, string> = {}
  const fail = (key: string, value = 'invalid') => {
    errors['event.' + key] = value
  }
  if (d.blocked) {
    fail('action', 'unsupported')
    return errors
  }
  const v = d.drafts[d.action]
  if (d.action !== 'WAIT_APPROVAL' && !validEventCode(v.eventCode)) fail('eventCode', 'codeInvalid')
  if (
    d.action === 'WAIT_APPROVAL' &&
    (!v.allowedOperatorIds.length ||
      v.allowedOperatorIds.some((id) => !id.trim() || id.length > 128 || id.includes('${')))
  )
    fail('allowedOperatorIds', 'approversRequired')
  if (d.action === 'WAIT_EVENT' || d.action === 'WAIT_APPROVAL') {
    if (
      v.timeoutSeconds !== null &&
      (!Number.isInteger(v.timeoutSeconds) ||
        v.timeoutSeconds <= 0 ||
        v.timeoutSeconds > 2147483647)
    )
      fail('timeoutSeconds')
    if (
      d.action === 'WAIT_EVENT' &&
      (v.lookbackSeconds === null ||
        !Number.isInteger(v.lookbackSeconds) ||
        v.lookbackSeconds < 0 ||
        v.lookbackSeconds > 604800)
    )
      fail('lookbackSeconds')
  }
  if (d.action === 'PROBE_FILE') {
    if (!validFileId(v.fileResourceId)) fail('fileResourceId')
    if (!v.filePath.trim() || v.filePath.length > 1024) fail('filePath')
    if (
      !v.filePattern.trim() ||
      v.filePattern.length > 128 ||
      /[/\\\\]/.test(v.filePattern) ||
      v.filePattern.includes('$' + '{')
    )
      fail('filePattern')
    for (const [key, min, max] of [
      ['minStableAgeSeconds', 0, 86400],
      ['maxMatches', 1, 1000],
      ['maxDepth', 0, 128],
      ['maxScannedEntries', 1, 1000000],
      ['scanTimeoutSeconds', 1, 3600],
    ] as const) {
      const n = v[key]
      if (n == null || !Number.isInteger(n) || n < min || n > max) fail(key)
    }
    if (v.summary.length > 500) fail('summary')
  }
  if (d.action !== 'WAIT_APPROVAL')
    for (const key of ['businessKey', 'correlationKey'] as const) if (v[key].length > 256) fail(key)
  if (d.action === 'PUBLISH') {
    if (v.idempotencyKey.length > 256) fail('idempotencyKey')
    if (v.summary.length > 500) fail('summary')
    if (v.occurredAt && !v.occurredAt.includes('${')) {
      const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?$/.exec(
        v.occurredAt,
      )
      const date = match ? new Date(Date.UTC(+match[1]!, +match[2]! - 1, +match[3]!)) : null
      if (
        !match ||
        !date ||
        date.getUTCFullYear() !== +match[1]! ||
        date.getUTCMonth() + 1 !== +match[2]! ||
        date.getUTCDate() !== +match[3]! ||
        +match[4]! > 23 ||
        +match[5]! > 59 ||
        +(match[6] ?? 0) > 59
      )
        fail('occurredAt', 'timeInvalid')
    }
    if (v.payload.trim())
      try {
        const normalized = stringify(parse(v.payload))!
        if (!normalized.includes('${') && new TextEncoder().encode(normalized).length > 16384)
          fail('payload', 'payloadLarge')
      } catch {
        fail('payload', 'jsonInvalid')
      }
  }
  return errors
}
