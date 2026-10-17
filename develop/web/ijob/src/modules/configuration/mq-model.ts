import { randomUuid } from '../../shared/runtime/uuid'
import { hidden, fieldValue, type ConfigRow, type HiddenField } from './contracts'
export type MqType = 'KAFKA' | 'RABBITMQ'
export function mqDraft(type: MqType, row?: ConfigRow) {
  const c = row?.config
  return {
    type,
    name: row?.name ?? '',
    code: row?.code ?? 'res-' + randomUuid(),
    enabled: row?.enabled ?? true,
    description: row?.description ?? '',
    bootstrapServers: c?.bootstrapServers ?? '',
    acks: c?.acks ?? 'all',
    host: c?.host ?? '',
    port: (c?.port ?? 5672) as number | null,
    username: c?.username ?? '',
    virtualHost: c?.virtualHost ?? '/',
    publisherConfirm: c?.publisherConfirm ?? true,
    password: hidden(c?.passwordConfigured ?? false, !!row),
  }
}
export type MqDraft = ReturnType<typeof mqDraft>
export function mqIssues(d: MqDraft) {
  const e: Record<string, string> = {}
  const text = (k: 'name' | 'code' | 'host' | 'username' | 'bootstrapServers', max: number) => {
    if (!d[k].trim()) e[k] = 'required'
    else if (d[k].trim().length > max) e[k] = 'tooLong'
  }
  text('name', 128)
  text('code', 128)
  if (!/^[A-Za-z0-9._-]+$/.test(d.code.trim())) e.code = 'invalid'
  if (d.description.length > 500) e.description = 'tooLong'
  if (d.type === 'KAFKA') {
    text('bootstrapServers', 2048)
    if (!['all', '1', '0'].includes(d.acks)) e.acks = 'invalid'
  } else {
    text('host', 255)
    text('username', 128)
    if (d.port == null || !Number.isInteger(d.port) || d.port < 1 || d.port > 65535)
      e.port = 'invalid'
    if (d.virtualHost.length > 255) e.virtualHost = 'tooLong'
    const p = d.password
    if (
      p.mode === 'clear' ||
      (p.mode === 'keep' && !p.configured) ||
      (p.mode === 'replace' && !p.value.trim())
    )
      e.password = 'required'
    if (p.mode === 'replace' && p.value.length > 4096) e.password = 'tooLong'
  }
  return e
}
export function mqBody(d: MqDraft, existing: boolean) {
  return {
    name: d.name.trim(),
    code: d.code.trim(),
    type: d.type,
    enabled: d.enabled,
    description: d.description,
    ...(d.type === 'KAFKA'
      ? {
          kafkaConfig: {
            preserveExisting: existing,
            bootstrapServers: d.bootstrapServers.trim(),
            acks: d.acks,
          },
        }
      : {
          rabbitConfig: {
            preserveExisting: existing,
            host: d.host.trim(),
            port: d.port,
            username: d.username.trim(),
            virtualHost: d.virtualHost || '/',
            publisherConfirm: d.publisherConfirm,
            password: fieldValue(d.password as HiddenField),
          },
        }),
  }
}
