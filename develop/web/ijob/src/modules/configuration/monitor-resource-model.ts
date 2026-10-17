import { randomUuid } from '../../shared/runtime/uuid'
import { hidden, fieldValue, type ConfigRow } from './contracts'
import { headerDraft, headerBody, headerIssues } from './header-model'
export interface MonitorResourceView {
  protocol: 'HTTP' | 'HTTPS'
  endpoint: string
  tokenConfigured: boolean
  headerNames: string[]
  connectTimeout: number
  readTimeout: number
  osType?: 'LINUX' | 'WINDOWS' | 'MACOS' | null
  agentVersion?: string | null
}
export function monitorResourceDraft(row?: ConfigRow) {
  const c = row?.config?.monitor
  return {
    name: row?.name ?? '',
    code: row?.code ?? 'res-' + randomUuid(),
    enabled: row?.enabled ?? true,
    description: row?.description ?? '',
    protocol: c?.protocol ?? 'HTTP',
    endpoint: c?.endpoint ?? '',
    token: hidden(c?.tokenConfigured ?? false, !!row),
    headers: headerDraft(c?.headerNames),
    connectTimeout: (c?.connectTimeout ?? 5000) as number | null,
    readTimeout: (c?.readTimeout ?? 10000) as number | null,
    osType: c?.osType ?? null,
    agentVersion: c?.agentVersion ?? '',
  }
}
export type MonitorResourceDraft = ReturnType<typeof monitorResourceDraft>
export function monitorResourceIssues(d: MonitorResourceDraft) {
  const e: Record<string, string> = { ...headerIssues(d.headers, 50, 256, 4096) }
  for (const k of ['name', 'code'] as const)
    if (!d[k].trim()) e[k] = 'required'
    else if (d[k].trim().length > 128) e[k] = 'tooLong'
  if (!/^[A-Za-z0-9._-]+$/.test(d.code.trim())) e.code = 'invalid'
  if (d.description.length > 500) e.description = 'tooLong'
  try {
    const url = new URL(d.endpoint)
    if (
      d.endpoint !== d.endpoint.trim() ||
      url.protocol !== d.protocol.toLowerCase() + ':' ||
      url.username ||
      url.password ||
      d.endpoint.includes('?') ||
      d.endpoint.includes('#') ||
      !url.hostname ||
      d.endpoint.length > 2048
    )
      throw Error()
  } catch {
    e.endpoint = 'invalid'
  }
  for (const k of ['connectTimeout', 'readTimeout'] as const)
    if (d[k] == null || !Number.isInteger(d[k]) || d[k] < 100 || d[k] > 300000) e[k] = 'invalid'
  if (d.token.mode === 'replace' && (d.token.value.length > 4096 || /[\r\n\0]/.test(d.token.value)))
    e.token = 'invalid'
  if (d.agentVersion.length > 64) e.agentVersion = 'tooLong'
  return e
}
export function monitorResourceBody(d: MonitorResourceDraft) {
  return {
    name: d.name.trim(),
    code: d.code.trim(),
    enabled: d.enabled,
    description: d.description,
    type: 'MONITOR_SERVER',
    monitorConfig: {
      preserveExisting: false,
      protocol: d.protocol,
      endpoint: d.endpoint,
      token: fieldValue(d.token),
      clearToken: d.token.mode === 'clear',
      ...headerBody(d.headers),
      connectTimeout: d.connectTimeout,
      readTimeout: d.readTimeout,
      osType: d.osType,
      agentVersion: d.agentVersion || null,
    },
  }
}
