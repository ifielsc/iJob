import { splitValues } from './contracts'
import type { HiddenField } from './contracts'
export const jdbcExamples: Record<string, string> = {
  MYSQL: 'jdbc:mysql://localhost:3306/database',
  POSTGRESQL: 'jdbc:postgresql://localhost:5432/database',
  ORACLE: 'jdbc:oracle:thin:@//localhost:1521/service',
  SQLSERVER: 'jdbc:sqlserver://localhost:1433;databaseName=database',
}
interface Draft {
  name: string
  code: string
  type: string
  dbType: string
  schema: string
  description: string
}
export interface FieldIssue {
  key: string
  args?: Record<string, string | number>
}
export function configurationIssues(draft: Draft, fields: Record<string, HiddenField>) {
  const issues: Record<string, FieldIssue> = {}
  const max = (field: string, value: string, count: number) => {
    if (value.length > count) issues[field] = { key: 'maxLength', args: { count } }
  }
  max('name', draft.name.trim(), 128)
  if (draft.type === 'DB') {
    if (draft.code.trim() && !/^[A-Za-z0-9._-]{1,128}$/.test(draft.code.trim()))
      issues.code = { key: 'codeError' }
    max('schema', draft.schema, 128)
    max('description', draft.description, 500)
    const url = fields.jdbcUrl!
    if (url.mode === 'replace' && url.value.trim()) {
      const prefix = jdbcExamples[draft.dbType]?.split('//')[0]
      const validPrefix = draft.dbType === 'ORACLE' ? 'jdbc:oracle:' : prefix?.replace(/\/$/, '')
      if (!validPrefix || !url.value.trim().startsWith(validPrefix))
        issues.jdbcUrl = {
          key: 'jdbcError',
          args: { example: jdbcExamples[draft.dbType] ?? 'jdbc:…' },
        }
      if (/(?:[?&;])(password|passwd|pwd)=/i.test(url.value))
        issues.jdbcUrl = { key: 'jdbcSecretError' }
      max('jdbcUrl', url.value.trim(), 2048)
    }
    if (fields.username!.mode === 'replace') max('username', fields.username!.value.trim(), 128)
  }
  const secretName = draft.type === 'DB' ? 'password' : 'secret'
  const secret = fields[secretName]!
  if (['DB', 'DINGTALK', 'FEISHU'].includes(draft.type) && secret.mode === 'replace') {
    if (secret.configured && !secret.value.trim()) issues[secretName] = { key: 'emptyReplacement' }
    max(secretName, secret.value, 4096)
  }
  if (draft.type === 'EMAIL') {
    for (const key of ['from', 'to', 'cc']) {
      const f = fields[key]!
      if (f.mode !== 'replace' || !f.value.trim()) continue
      const values = key === 'from' ? [f.value.trim()] : splitValues(f.value)
      if (values.some((value) => value.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)))
        issues[key] = { key: 'emailError' }
      else if (values.length > 100) issues[key] = { key: 'listLimit' }
      else if (new Set(values.map((value) => value.toLowerCase())).size !== values.length)
        issues[key] = { key: 'duplicateError' }
    }
    if (fields.to!.mode === 'replace' && fields.cc!.mode === 'replace') {
      const recipients = new Set(splitValues(fields.to!.value).map((value) => value.toLowerCase()))
      if (splitValues(fields.cc!.value).some((value) => recipients.has(value.toLowerCase())))
        issues.cc = { key: 'crossRecipientError' }
    }
  }
  if (draft.type === 'DINGTALK') {
    const f = fields.webhook!
    if (f.mode === 'replace' && f.value.trim()) {
      try {
        const url = new URL(f.value.trim())
        if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.hash)
          issues.webhook = { key: 'webhookError' }
      } catch {
        issues.webhook = { key: 'webhookError' }
      }
      max('webhook', f.value.trim(), 2048)
    }
    if (fields.mobiles!.mode === 'replace') {
      const values = splitValues(fields.mobiles!.value)
      if (values.some((value) => !/^\+?[0-9]{6,20}$/.test(value)))
        issues.mobiles = { key: 'mobileError' }
      else if (values.length > 100) issues.mobiles = { key: 'listLimit' }
      else if (new Set(values).size !== values.length) issues.mobiles = { key: 'duplicateError' }
    }
  }
  return issues
}
