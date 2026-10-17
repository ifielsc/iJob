import { randomUuid } from '../../shared/runtime/uuid'
import { hidden, fieldValue, type ConfigRow, type HiddenField } from './contracts'
import { sshDraft, sshTrustValid } from './ssh-model'
import type { FileProtocol } from './file-protocols'
export { fileProtocols, type FileProtocol } from './file-protocols'
export interface FileConfigView {
  protocol: FileProtocol
  host?: string
  username?: string
  basePath?: string
  endpoint?: string
  region?: string
  bucket?: string
  knownHosts?: string
  port?: number
  timeout: number
  pathStyleAccess: boolean
  hostKeyVerification?: 'MANAGED' | 'KNOWN_HOSTS'
  hostKeyFingerprint?: string
  hostKeyType?: string
  passwordConfigured: boolean
  privateKeyConfigured: boolean
  passphraseConfigured: boolean
  accessKeyConfigured: boolean
  secretKeyConfigured: boolean
  sessionTokenConfigured: boolean
}
export const fileSecretKeys = [
  'password',
  'privateKey',
  'passphrase',
  'accessKey',
  'secretKey',
  'sessionToken',
] as const
export function fileDraft(row?: ConfigRow) {
  const c = row?.config?.file
  return {
    ...sshDraft(
      c && row
        ? { ...row, config: { ...c, protocol: undefined, connectTimeout: c.timeout } }
        : undefined,
    ),
    name: row?.name ?? '',
    code: row?.code ?? 'res-' + randomUuid(),
    enabled: row?.enabled ?? true,
    description: row?.description ?? '',
    protocol: c?.protocol ?? ('LOCAL' as FileProtocol),
    basePath: c?.basePath ?? '',
    endpoint: c?.endpoint ?? '',
    region: c?.region ?? '',
    bucket: c?.bucket ?? '',
    pathStyleAccess: c?.pathStyleAccess ?? true,
  }
}
export type FileDraft = ReturnType<typeof fileDraft>
export function fileSecrets(row?: ConfigRow) {
  const c = row?.config?.file
  return Object.fromEntries(
    fileSecretKeys.map((key) => [
      key,
      hidden(!!c?.[(key + 'Configured') as keyof FileConfigView], !!row),
    ]),
  ) as Record<(typeof fileSecretKeys)[number], HiddenField>
}
export const fileTrustValid = sshTrustValid
export function fileCredentialKeys(
  d: FileDraft,
  existing = true,
): readonly (typeof fileSecretKeys)[number][] {
  return d.protocol === 'LOCAL'
    ? []
    : d.protocol === 'S3_COMPATIBLE'
      ? (['accessKey', 'secretKey', 'sessionToken'] as const)
      : d.protocol === 'FTP'
        ? (['password'] as const)
        : existing
          ? (['password', 'privateKey', 'passphrase'] as const)
          : d.auth === 'PASSWORD'
            ? (['password'] as const)
            : (['privateKey', 'passphrase'] as const)
}
export function fileIssues(d: FileDraft, f: ReturnType<typeof fileSecrets>, existing = true) {
  const e: Record<string, string> = {}
  const required: (keyof FileDraft)[] = ['name', 'code', 'basePath']
  if (d.protocol === 'FTP' || d.protocol === 'SFTP') required.push('host', 'username')
  if (d.protocol === 'S3_COMPATIBLE') required.push('region', 'bucket')
  if (d.protocol === 'SFTP' && d.hostKeyVerification === 'KNOWN_HOSTS') required.push('knownHosts')
  for (const key of required) if (!String(d[key]).trim()) e[key] = 'required'
  for (const [key, max] of [
    ['name', 128],
    ['code', 128],
    ['basePath', 2048],
    ['host', 255],
    ['username', 128],
    ['endpoint', 2048],
    ['region', 128],
    ['bucket', 255],
    ['knownHosts', 2048],
    ['description', 500],
  ] as const)
    if (d[key].length > max) e[key] = 'invalid'
  if (d.code && !/^[A-Za-z0-9._-]{1,128}$/.test(d.code.trim())) e.code = 'codeError'
  if (!Number.isInteger(d.connectTimeout) || d.connectTimeout < 100 || d.connectTimeout > 300000)
    e.connectTimeout = 'invalid'
  const remote = d.protocol === 'FTP' || d.protocol === 'SFTP'
  if (remote && (!Number.isInteger(d.port) || d.port < 1 || d.port > 65535)) e.port = 'invalid'
  const available = (key: keyof typeof f) =>
    f[key].mode === 'keep' ? f[key].configured : f[key].mode === 'replace' && !!f[key].value.trim()
  const keys = fileCredentialKeys(d, existing)
  for (const key of keys) {
    if (f[key].mode === 'replace' && f[key].configured && !f[key].value.trim())
      e[key] = 'emptyReplacement'
    if (
      f[key].value.length >
      (key === 'privateKey' || key === 'sessionToken' ? 65535 : key === 'accessKey' ? 512 : 4096)
    )
      e[key] = 'invalid'
  }
  if (d.protocol === 'FTP' && !available('password')) e.password = 'required'
  if (d.protocol === 'SFTP') {
    if (
      !(keys.includes('password') && available('password')) &&
      !(keys.includes('privateKey') && available('privateKey'))
    )
      e[!existing && d.auth === 'KEY' ? 'privateKey' : 'password'] = 'sshAuthRequired'
    if (keys.includes('passphrase') && available('passphrase') && !available('privateKey'))
      e.passphrase = 'sshPassphraseRequiresKey'
    if (
      Array.from(d.host.trim()).some(
        (c) =>
          /\s/.test(c) ||
          ',[]*?!|/\\'.includes(c) ||
          c.charCodeAt(0) < 32 ||
          c.charCodeAt(0) === 127,
      )
    )
      e.host = 'invalid'
  }
  if (d.protocol === 'S3_COMPATIBLE')
    for (const key of ['accessKey', 'secretKey'] as const) if (!available(key)) e[key] = 'required'
  return e
}
export function fileBody(
  d: FileDraft,
  f: ReturnType<typeof fileSecrets>,
  fallback: string,
  existing: boolean,
) {
  const c: Record<string, unknown> = {
    preserveExisting: existing,
    protocol: d.protocol,
    basePath: d.basePath.trim(),
    timeout: d.connectTimeout,
  }
  if (d.protocol === 'FTP' || d.protocol === 'SFTP')
    Object.assign(c, { host: d.host.trim(), port: d.port, username: d.username.trim() })
  if (d.protocol === 'SFTP')
    Object.assign(c, {
      hostKeyVerification: d.hostKeyVerification,
      knownHosts: d.hostKeyVerification === 'KNOWN_HOSTS' ? d.knownHosts.trim() : undefined,
      hostKey:
        d.hostKeyVerification === 'MANAGED' && fileTrustValid(d) && d.hostKey
          ? d.hostKey
          : undefined,
    })
  if (d.protocol === 'S3_COMPATIBLE')
    Object.assign(c, {
      endpoint: d.endpoint.trim(),
      region: d.region.trim(),
      bucket: d.bucket.trim(),
      pathStyleAccess: d.pathStyleAccess,
    })
  const keys = fileCredentialKeys(d, existing)
  for (const key of keys) {
    c[key] = f[key].mode === 'replace' && !f[key].value ? undefined : fieldValue(f[key])
    if (key !== 'accessKey')
      c['clear' + key[0]!.toUpperCase() + key.slice(1)] = f[key].mode === 'clear'
  }
  return {
    name: d.name.trim(),
    code: d.code.trim() || fallback,
    type: 'FILE',
    enabled: d.enabled,
    description: d.description,
    fileConfig: c,
  }
}
