import { parse, stringify, LosslessNumber } from 'lossless-json'
export const pushLimits = {
  fetchSize: [500, 1, 10000],
  batchSize: [500, 1, 10000],
  maxInflight: [500, 1, 10000],
  publishTimeoutSeconds: [60, 1, 3600],
  retryTimes: [3, 0, 10],
  maxMessageBytes: [1048576, 1024, 16777216],
  maxMessages: [1000000, 1, 100000000],
  maxTotalPayloadBytes: [1073741824, 1024, 107374182400],
} as const
export type PushLimit = keyof typeof pushLimits
export type PushType = 'KAFKA' | 'RABBITMQ'
const object = (v: unknown): Record<string, unknown> => {
  if (!v || typeof v !== 'object' || Array.isArray(v) || v instanceof LosslessNumber) throw Error()
  return v as Record<string, unknown>
}
export const pushId = (s: string) => /^[1-9]\d*$/.test(s) && BigInt(s) <= 9223372036854775807n
export function pushDraft(text?: string) {
  const d = {
    original: text ?? '{}',
    blocked: false,
    sourceResourceId: '',
    sql: '',
    type: 'KAFKA' as PushType,
    targets: {
      KAFKA: { resourceId: '', topic: '' },
      RABBITMQ: { resourceId: '', exchange: '', routingKey: '' },
    },
    serializer: 'json',
    compression: 'none',
    limits: Object.fromEntries(Object.entries(pushLimits).map(([k, v]) => [k, v[0]])) as Record<
      PushLimit,
      number | null
    >,
  }
  if (!text) return d
  try {
    const c = object(parse(text)),
      target = object(c.target),
      props = object(target.properties)
    if (!['KAFKA', 'RABBITMQ'].includes(String(target.type))) throw Error()
    d.type = target.type as PushType
    for (const key of ['sql', 'serializer', 'compression'] as const) {
      if (c[key] !== undefined) {
        if (typeof c[key] !== 'string') throw Error()
        d[key] = c[key]
      }
    }
    if (d.serializer !== 'json' || !['none', 'gzip'].includes(d.compression)) throw Error()
    d.sourceResourceId = String(c.sourceResourceId)
    d.targets[d.type].resourceId = String(target.resourceId)
    if (!pushId(d.sourceResourceId) || !pushId(d.targets[d.type].resourceId)) throw Error()
    if (d.type === 'KAFKA') {
      if (typeof props.topic !== 'string') throw Error()
      d.targets.KAFKA.topic = props.topic
    } else {
      if (typeof props.exchange !== 'string' || typeof props.routingKey !== 'string') throw Error()
      d.targets.RABBITMQ.exchange = props.exchange
      d.targets.RABBITMQ.routingKey = props.routingKey
    }
    for (const key of Object.keys(pushLimits) as PushLimit[])
      if (c[key] !== undefined) {
        const s = String(c[key]),
          n = Number(s)
        if (!/^\d+$/.test(s) || !Number.isSafeInteger(n)) throw Error()
        d.limits[key] = n
      }
  } catch {
    d.blocked = true
  }
  return d
}
export type PushDraft = ReturnType<typeof pushDraft>
export function pushIssues(d: PushDraft) {
  const e: Record<string, string> = {},
    fail = (key: string) => {
      e['push.' + key] = 'invalid'
    }
  if (d.blocked) return { 'push.sql': 'unsupported' }
  if (!pushId(d.sourceResourceId)) fail('sourceResourceId')
  if (!d.sql.trim() || d.sql.length > 1000000 || d.sql.includes('\0')) fail('sql')
  if (!pushId(d.targets[d.type].resourceId)) fail('resourceId')
  const target = d.targets
  if (d.type === 'KAFKA') {
    const topic = target.KAFKA.topic
    if (
      !topic.trim() ||
      topic.length > 249 ||
      (!topic.includes('${') && (!/^[a-zA-Z0-9._-]+$/.test(topic) || ['.', '..'].includes(topic)))
    )
      fail('topic')
  } else {
    if (target.RABBITMQ.exchange.length > 255) fail('exchange')
    if (!target.RABBITMQ.routingKey.trim() || target.RABBITMQ.routingKey.length > 255)
      fail('routingKey')
  }
  for (const key of Object.keys(pushLimits) as PushLimit[]) {
    const n = d.limits[key]
    if (n == null || !Number.isInteger(n) || n < pushLimits[key][1] || n > pushLimits[key][2])
      fail(key)
  }
  if (d.limits.maxTotalPayloadBytes! < d.limits.maxMessageBytes!) fail('maxTotalPayloadBytes')
  if (!Object.keys(e).length && new TextEncoder().encode(pushConfig(d)).length > 8 * 1024 * 1024)
    e['push.sql'] = 'tooLarge'
  return e
}
export function pushConfig(d: PushDraft) {
  if (d.blocked) throw Error('Unsupported PUSH configuration')
  const c = object(parse(d.original)),
    target = c.target == null ? {} : object(c.target)
  const props = target.properties == null ? {} : { ...object(target.properties) }
  delete props.topic
  delete props.exchange
  delete props.routingKey
  Object.assign(
    props,
    d.type === 'KAFKA'
      ? { topic: d.targets.KAFKA.topic }
      : { exchange: d.targets.RABBITMQ.exchange, routingKey: d.targets.RABBITMQ.routingKey },
  )
  return stringify({
    ...c,
    sourceResourceId: new LosslessNumber(d.sourceResourceId),
    sql: d.sql,
    target: {
      ...target,
      type: d.type,
      resourceId: new LosslessNumber(d.targets[d.type].resourceId),
      properties: props,
    },
    serializer: d.serializer,
    compression: d.compression,
    ...d.limits,
  })!
}
