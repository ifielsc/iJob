import type { MonitorResourceView } from './monitor-resource-model'
import type { FileConfigView } from './file-model'
import { fileProtocols } from './file-protocols'
import { ApiError } from '../../shared/api/errors'
export type ConfigKind = 'resources' | 'notifications'
export interface ConfigView {
  mailChannelId?: string | null
  mailChannelName?: string | null
  monitor?: MonitorResourceView
  bootstrapServers?: string
  acks?: string
  virtualHost?: string
  publisherConfirm?: boolean
  exchange?: string | null
  routingKey?: string | null
  file?: FileConfigView
  endpoint?: string
  wsdl?: string | null
  namespace?: string | null
  serviceName?: string | null
  portName?: string | null
  oauth2TokenEndpoint?: string | null
  oauth2ClientId?: string | null
  oauth2Scopes?: string | null
  oauth2Audience?: string | null
  protocol?: 'HTTP' | 'WEB_SERVICE'
  authType?: 'NONE' | 'OAUTH2_CLIENT_CREDENTIALS'
  endpointConfigured?: boolean
  headerNames?: string[]
  readTimeout?: number
  writeTimeout?: number
  callTimeout?: number
  webServiceConfigured?: boolean
  oauth2Configured?: boolean
  oauth2TokenEndpointConfigured?: boolean
  oauth2ClientIdConfigured?: boolean
  oauth2ClientSecretConfigured?: boolean
  oauth2ScopesConfigured?: boolean
  oauth2AudienceConfigured?: boolean
  oauth2ClientAuthenticationMethod?: string | null
  oauth2RefreshSkewSeconds?: number | null

  hostKeyVerification?: 'MANAGED' | 'KNOWN_HOSTS'
  hostKeyFingerprint?: string | null
  hostKeyType?: string | null
  host?: string
  port?: number
  knownHosts?: string
  connectTimeout?: number
  hostConfigured?: boolean
  privateKeyConfigured?: boolean
  passphraseConfigured?: boolean
  knownHostsConfigured?: boolean
  from?: string
  to?: string[]
  cc?: string[]
  webhookDisplay?: string | null
  atMobiles?: string[]
  format?: string
  members?: string[]
  mobiles?: string[]
  roles?: string[]
  mentionMode?: string
  keyword?: string
  destination?: string
  channelId?: string
  jdbcUrl?: string
  username?: string
  dbType?: string
  schema?: string | null
  jdbcUrlConfigured?: boolean
  usernameConfigured?: boolean
  passwordConfigured?: boolean
  fromConfigured?: boolean
  toCount?: number
  ccCount?: number
  webhookConfigured?: boolean
  secretConfigured?: boolean
  atAll?: boolean
  atMobileCount?: number
}
export interface ConfigRow {
  id: string
  name: string
  type: string
  enabled: boolean
  code?: string
  description?: string | null
  updateTime?: string
  config?: ConfigView
}
export const weekdays = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const
export type Weekday = (typeof weekdays)[number]
export interface CalendarException {
  date: string
  type: 'WORKDAY' | 'HOLIDAY'
}
export interface CalendarRow {
  id: string
  name: string
  description?: string | null
  workdays: Weekday[]
  exceptions?: CalendarException[]
}
export interface PreviewDay {
  date: string
  allowed: boolean
  exceptionType: 'WORKDAY' | 'HOLIDAY' | null
}
export function record(v: unknown): Record<string, unknown> {
  if (!v || typeof v !== 'object' || Array.isArray(v)) throw new ApiError('protocol')
  return v as Record<string, unknown>
}
export function id(v: unknown): string {
  if (typeof v !== 'string' || !/^[1-9]\d*$/.test(v)) throw new ApiError('protocol')
  return v
}
export function parseConfig(value: unknown, detail = false): ConfigRow {
  const r = record(value)
  id(r.id)
  if (
    ![
      'DB',
      'SSH',
      'SERVICE',
      'FILE',
      'KAFKA',
      'RABBITMQ',
      'MONITOR_SERVER',
      'EMAIL',
      'DINGTALK',
      'WECOM',
      'FEISHU',
      'SLACK',
      'DISCORD',
    ].includes(String(r.type)) ||
    (['DB', 'SSH', 'SERVICE', 'FILE', 'KAFKA', 'RABBITMQ', 'MONITOR_SERVER'].includes(
      String(r.type),
    ) &&
      typeof r.code !== 'string')
  )
    throw new ApiError('protocol')
  if (r.description != null && typeof r.description !== 'string') throw new ApiError('protocol')
  if (typeof r.name !== 'string' || typeof r.type !== 'string' || typeof r.enabled !== 'boolean')
    throw new ApiError('protocol')
  if (detail) {
    const c = record(r.config)
    if (['WECOM', 'FEISHU', 'SLACK', 'DISCORD'].includes(String(r.type))) {
      for (const key of ['webhookConfigured', 'secretConfigured', 'atAll'])
        if (typeof c[key] !== 'boolean') throw new ApiError('protocol')
      for (const key of ['members', 'mobiles', 'roles'])
        if (!Array.isArray(c[key]) || (c[key] as unknown[]).some((v) => typeof v !== 'string'))
          throw new ApiError('protocol')
      if (typeof c.webhookDisplay !== 'string' || typeof c.format !== 'string')
        throw new ApiError('protocol')
      return r as unknown as ConfigRow
    }
    if (r.type === 'EMAIL' && c.mailChannelId != null) id(c.mailChannelId)
    if (r.type === 'MONITOR_SERVER') {
      if (
        !['HTTP', 'HTTPS'].includes(String(c.protocol)) ||
        typeof c.endpoint !== 'string' ||
        typeof c.tokenConfigured !== 'boolean' ||
        !Array.isArray(c.headerNames) ||
        c.headerNames.some((v) => typeof v !== 'string') ||
        !Number.isInteger(c.connectTimeout) ||
        !Number.isInteger(c.readTimeout) ||
        (c.osType != null && !['LINUX', 'WINDOWS', 'MACOS'].includes(String(c.osType))) ||
        (c.agentVersion != null && typeof c.agentVersion !== 'string')
      )
        throw new ApiError('protocol')
      return { ...r, config: { monitor: c } } as unknown as ConfigRow
    }
    if (r.type === 'KAFKA' || r.type === 'RABBITMQ') {
      if (r.type === 'KAFKA') {
        if (typeof c.bootstrapServers !== 'string' || !['all', '1', '0'].includes(String(c.acks)))
          throw new ApiError('protocol')
      } else {
        if (
          typeof c.host !== 'string' ||
          typeof c.username !== 'string' ||
          typeof c.virtualHost !== 'string' ||
          !Number.isInteger(c.port) ||
          typeof c.passwordConfigured !== 'boolean' ||
          typeof c.publisherConfirm !== 'boolean'
        )
          throw new ApiError('protocol')
        for (const key of ['exchange', 'routingKey'])
          if (c[key] != null && typeof c[key] !== 'string') throw new ApiError('protocol')
      }
      return r as unknown as ConfigRow
    }
    if (r.type === 'FILE') {
      if (
        !fileProtocols.includes(c.protocol as FileConfigView['protocol']) ||
        typeof c.basePath !== 'string' ||
        !Number.isInteger(c.timeout) ||
        typeof c.pathStyleAccess !== 'boolean'
      )
        throw new ApiError('protocol')
      for (const key of [
        'passwordConfigured',
        'privateKeyConfigured',
        'passphraseConfigured',
        'accessKeyConfigured',
        'secretKeyConfigured',
        'sessionTokenConfigured',
      ])
        if (typeof c[key] !== 'boolean') throw new ApiError('protocol')
      for (const key of [
        'host',
        'username',
        'basePath',
        'endpoint',
        'region',
        'bucket',
        'knownHosts',
        'hostKeyFingerprint',
        'hostKeyType',
      ])
        if (c[key] != null && typeof c[key] !== 'string') throw new ApiError('protocol')
      if (
        ['FTP', 'SFTP'].includes(String(c.protocol)) &&
        (typeof c.host !== 'string' || typeof c.username !== 'string' || !Number.isInteger(c.port))
      )
        throw new ApiError('protocol')
      if (
        c.protocol === 'SFTP' &&
        (!['MANAGED', 'KNOWN_HOSTS'].includes(String(c.hostKeyVerification)) ||
          (c.hostKeyVerification === 'MANAGED' &&
            (typeof c.hostKeyFingerprint !== 'string' || typeof c.hostKeyType !== 'string')))
      )
        throw new ApiError('protocol')
      return { ...r, config: { file: c } } as unknown as ConfigRow
    }

    if (r.type === 'SERVICE') {
      for (const key of [
        'endpoint',
        'wsdl',
        'namespace',
        'serviceName',
        'portName',
        'oauth2TokenEndpoint',
        'oauth2ClientId',
        'oauth2Scopes',
        'oauth2Audience',
      ]) {
        if (c[key] != null && typeof c[key] !== 'string') throw new ApiError('protocol')
      }
    }
    if (c.schema != null && typeof c.schema !== 'string') throw new ApiError('protocol')
    const keys =
      r.type === 'SERVICE'
        ? [
            'endpointConfigured',
            'webServiceConfigured',
            'oauth2Configured',
            'oauth2TokenEndpointConfigured',
            'oauth2ClientIdConfigured',
            'oauth2ClientSecretConfigured',
            'oauth2ScopesConfigured',
            'oauth2AudienceConfigured',
          ]
        : r.type === 'SSH'
          ? [
              'hostConfigured',
              'usernameConfigured',
              'passwordConfigured',
              'privateKeyConfigured',
              'passphraseConfigured',
              'knownHostsConfigured',
            ]
          : r.type === 'DB'
            ? ['jdbcUrlConfigured', 'usernameConfigured', 'passwordConfigured']
            : r.type === 'EMAIL'
              ? ['fromConfigured']
              : r.type === 'DINGTALK'
                ? ['webhookConfigured', 'secretConfigured', 'atAll']
                : []
    if (!keys.length || keys.some((k) => typeof c[k] !== 'boolean')) throw new ApiError('protocol')
    if (
      r.type === 'SERVICE' &&
      (!['HTTP', 'WEB_SERVICE'].includes(String(c.protocol)) ||
        !['NONE', 'OAUTH2_CLIENT_CREDENTIALS'].includes(String(c.authType)) ||
        !Array.isArray(c.headerNames) ||
        c.headerNames.some((v) => typeof v !== 'string') ||
        ['connectTimeout', 'readTimeout', 'writeTimeout', 'callTimeout'].some(
          (k) => !Number.isInteger(c[k]),
        ))
    )
      throw new ApiError('protocol')
    if (
      r.type === 'SSH' &&
      (typeof c.host !== 'string' ||
        typeof c.username !== 'string' ||
        (c.hostKeyVerification !== 'MANAGED' && typeof c.knownHosts !== 'string') ||
        (c.hostKeyVerification != null &&
          !['MANAGED', 'KNOWN_HOSTS'].includes(String(c.hostKeyVerification))) ||
        (c.hostKeyVerification === 'MANAGED' &&
          (typeof c.hostKeyFingerprint !== 'string' || typeof c.hostKeyType !== 'string')) ||
        !Number.isInteger(c.port) ||
        !Number.isInteger(c.connectTimeout))
    )
      throw new ApiError('protocol')
    if (
      r.type === 'DB' &&
      (!['MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER'].includes(String(c.dbType)) ||
        typeof c.jdbcUrl !== 'string' ||
        typeof c.username !== 'string')
    )
      throw new ApiError('protocol')
    const stringList = (value: unknown) =>
      Array.isArray(value) && value.every((item) => typeof item === 'string')
    if (
      r.type === 'EMAIL' &&
      (typeof c.from !== 'string' || !stringList(c.to) || !stringList(c.cc))
    )
      throw new ApiError('protocol')
    if (
      r.type === 'DINGTALK' &&
      (!stringList(c.atMobiles) ||
        (c.webhookDisplay != null && typeof c.webhookDisplay !== 'string'))
    )
      throw new ApiError('protocol')
    for (const k of r.type === 'EMAIL'
      ? ['toCount', 'ccCount']
      : r.type === 'DINGTALK'
        ? ['atMobileCount']
        : [])
      if (!Number.isSafeInteger(c[k]) || Number(c[k]) < 0) throw new ApiError('protocol')
  }
  return r as unknown as ConfigRow
}
export function validDate(v: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) return false
  const date = new Date(v + 'T12:00:00Z')
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === v
}
export function parseCalendar(value: unknown, detail = false): CalendarRow {
  const r = record(value)
  id(r.id)
  if (
    typeof r.name !== 'string' ||
    !Array.isArray(r.workdays) ||
    !r.workdays.length ||
    r.workdays.some((d) => !weekdays.includes(d as Weekday))
  )
    throw new ApiError('protocol')
  if (
    detail &&
    (!Array.isArray(r.exceptions) ||
      r.exceptions.some((v) => {
        const e = record(v)
        return !validDate(String(e.date)) || !['WORKDAY', 'HOLIDAY'].includes(String(e.type))
      }))
  )
    throw new ApiError('protocol')
  return r as unknown as CalendarRow
}
export function parsePreview(value: unknown): PreviewDay[] {
  if (!Array.isArray(value) || value.length > 366) throw new ApiError('protocol')
  return value.map((v) => {
    const r = record(v)
    const exceptionType = r.exceptionType ?? null
    if (
      !validDate(String(r.date)) ||
      typeof r.allowed !== 'boolean' ||
      ![null, 'WORKDAY', 'HOLIDAY'].includes(exceptionType as null)
    )
      throw new ApiError('protocol')
    return { ...r, exceptionType } as unknown as PreviewDay
  })
}
export type FieldMode = 'keep' | 'replace' | 'clear'
export interface HiddenField {
  mode: FieldMode
  value: string
  configured: boolean
  count?: number
}
export function hidden(configured: boolean, existing: boolean, count?: number): HiddenField {
  return { mode: existing ? 'keep' : 'replace', value: '', configured, count }
}
export function splitValues(value: string): string[] {
  return value
    .split(/[,\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
}
export function fieldValue(field: HiddenField, list = false) {
  if (field.mode === 'keep') return undefined
  if (field.mode === 'clear') return list ? [] : undefined
  return list ? splitValues(field.value) : field.value
}
