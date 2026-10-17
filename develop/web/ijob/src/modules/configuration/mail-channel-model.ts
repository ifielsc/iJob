import { record, id } from './contracts'
import { ApiError } from '../../shared/api/errors'
export interface MailChannel {
  id: string
  name: string
  enabled: boolean
  revision: string
  host: string
  port: number
  security: 'NONE' | 'STARTTLS' | 'TLS'
  authentication: boolean
  username: string | null
  passwordConfigured: boolean
  from: string
  updateTime?: string
}
export function parseMailChannel(value: unknown): MailChannel {
  const row = record(value)
  id(row.id)
  id(row.revision)
  if (
    ['name', 'host', 'from'].some((key) => typeof row[key] !== 'string') ||
    typeof row.enabled !== 'boolean' ||
    typeof row.authentication !== 'boolean' ||
    typeof row.passwordConfigured !== 'boolean' ||
    !Number.isInteger(row.port) ||
    !['NONE', 'STARTTLS', 'TLS'].includes(String(row.security)) ||
    (row.username != null && typeof row.username !== 'string') ||
    'password' in row
  )
    throw new ApiError('protocol')
  return row as unknown as MailChannel
}
