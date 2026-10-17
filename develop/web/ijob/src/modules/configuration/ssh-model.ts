import { randomUuid } from '../../shared/runtime/uuid'
import { hidden, fieldValue } from './contracts'
import type { ConfigRow, HiddenField } from './contracts'

export function sshDraft(row?: ConfigRow) {
  const c = row?.config
  return {
    name: row?.name ?? '',
    code: row?.code ?? 'res-' + randomUuid(),
    enabled: row?.enabled ?? true,
    description: row?.description ?? '',
    host: c?.host ?? '',
    port: c?.port ?? 22,
    username: c?.username ?? '',
    knownHosts: c?.knownHosts ?? '',
    hostKeyVerification: c?.hostKeyVerification ?? (row ? 'KNOWN_HOSTS' : 'MANAGED'),
    hostKey: '',
    fingerprint: c?.hostKeyFingerprint ?? '',
    keyType: c?.hostKeyType ?? '',
    trustedHost: c?.host ?? '',
    trustedPort: c?.port ?? 22,
    connectTimeout: c?.connectTimeout ?? 30000,
    auth:
      c?.privateKeyConfigured && c?.passwordConfigured
        ? 'BOTH'
        : c?.privateKeyConfigured
          ? 'KEY'
          : 'PASSWORD',
  }
}
export type SshDraft = ReturnType<typeof sshDraft>
export function sshTrustValid(d: SshDraft) {
  return !!d.fingerprint && d.trustedHost === d.host.trim() && d.trustedPort === d.port
}
export function sshSecrets(row?: ConfigRow) {
  return Object.fromEntries(
    ['password', 'privateKey', 'passphrase'].map((key) => [
      key,
      hidden(
        !!row?.config?.[(key + 'Configured') as keyof NonNullable<ConfigRow['config']>],
        !!row,
      ),
    ]),
  ) as Record<'password' | 'privateKey' | 'passphrase', HiddenField>
}
export function sshIssues(d: SshDraft, f: ReturnType<typeof sshSecrets>, existing = false) {
  const e: Record<string, string> = {}
  for (const key of ['name', 'code', 'host', 'username'] as const)
    if (!d[key].trim()) e[key] = 'required'
  if (d.hostKeyVerification === 'KNOWN_HOSTS' && !d.knownHosts.trim()) e.knownHosts = 'required'
  if (
    Array.from(d.host.trim()).some(
      (c) =>
        /\s/.test(c) || ',[]*?!|/\\'.includes(c) || c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127,
    )
  )
    e.host = 'invalid'
  for (const [key, max] of [
    ['name', 128],
    ['host', 255],
    ['username', 128],
    ['knownHosts', 2048],
    ['description', 500],
  ] as const)
    if (d[key].length > max) e[key] = 'invalid'
  if (d.code && !/^[A-Za-z0-9._-]{1,128}$/.test(d.code.trim())) e.code = 'codeError'
  if (!Number.isInteger(d.port) || d.port < 1 || d.port > 65535) e.port = 'invalid'
  if (!Number.isInteger(d.connectTimeout) || d.connectTimeout < 100 || d.connectTimeout > 300000)
    e.connectTimeout = 'invalid'
  const available = (key: keyof typeof f) =>
    f[key].mode === 'keep' ? f[key].configured : f[key].mode === 'replace' && !!f[key].value.trim()
  const keys: (keyof typeof f)[] = existing
    ? ['password', 'privateKey', 'passphrase']
    : d.auth === 'PASSWORD'
      ? ['password']
      : ['privateKey', 'passphrase']
  for (const key of keys) {
    const field = f[key]
    if (field.mode === 'replace' && field.configured && !field.value.trim())
      e[key] = 'emptyReplacement'
    if (field.value.length > (key === 'privateKey' ? 65535 : 4096)) e[key] = 'invalid'
  }
  if (
    !(keys.includes('password') && available('password')) &&
    !(keys.includes('privateKey') && available('privateKey'))
  )
    e[d.auth === 'KEY' ? 'privateKey' : 'password'] = 'sshAuthRequired'
  if (keys.includes('passphrase') && available('passphrase') && !available('privateKey'))
    e.passphrase = 'sshPassphraseRequiresKey'
  return e
}
export function sshBody(
  d: SshDraft,
  f: ReturnType<typeof sshSecrets>,
  fallbackCode: string,
  existing: boolean,
) {
  const sshConfig: Record<string, unknown> = {
    preserveExisting: existing,
    host: d.host.trim(),
    port: d.port,
    username: d.username.trim(),
    knownHosts: d.knownHosts.trim(),
    hostKeyVerification: d.hostKeyVerification,
    hostKey:
      d.hostKeyVerification === 'MANAGED' && sshTrustValid(d) && d.hostKey ? d.hostKey : undefined,
    connectTimeout: d.connectTimeout,
  }
  for (const key of ['password', 'privateKey', 'passphrase'] as const) {
    if (
      !existing &&
      ((d.auth === 'PASSWORD' && key !== 'password') || (d.auth === 'KEY' && key === 'password'))
    )
      continue
    const field = f[key]
    sshConfig[key] = field.mode === 'replace' && !field.value ? undefined : fieldValue(field)
    sshConfig['clear' + key[0]!.toUpperCase() + key.slice(1)] = field.mode === 'clear'
  }
  return {
    name: d.name.trim(),
    code: d.code.trim() || fallbackCode,
    type: 'SSH',
    enabled: d.enabled,
    description: d.description,
    sshConfig,
  }
}
