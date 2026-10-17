import type { MessageKey } from '../i18n/keys'
import type { ApiResponse } from './contracts'

export type ErrorKind =
  | 'network'
  | 'timeout'
  | 'protocol'
  | 'unauthorized'
  | 'forbidden'
  | 'passwordRequired'
  | 'conflict'
  | 'generic'
export class ApiError extends Error {
  constructor(
    public kind: ErrorKind,
    public status = 0,
    public envelope?: Partial<ApiResponse<unknown>>,
  ) {
    super(kind)
    this.name = 'ApiError'
  }
}

export function isWriteOutcomeUnknown(error: unknown): boolean {
  return (
    !(error instanceof ApiError) ||
    ['network', 'timeout', 'protocol'].includes(error.kind) ||
    error.status >= 500
  )
}

export const messageTranslations: Readonly<Record<string, MessageKey>> = {
  'error.concurrency.version': 'concurrency.conflict',
  'error.detail.concurrency-policy-cannot-change-while-permits-are-active':
    'concurrency.permitsBusy',
  'error.detail.concurrency-resource-code-already-exists': 'concurrency.codeExists',
  'error.detail.plugin-concurrency-policy-version-changed': 'concurrency.conflict',
  'error.detail.task-concurrency-policy-version-changed': 'concurrency.conflict',
  'error.detail.concurrency-resource-cannot-change-while-matching-task-instances-are-active':
    'concurrency.resourceBusy',
  'error.detail.plugin-concurrency-policy-cannot-change-while-matching-task-instances-are-active':
    'concurrency.policyBusy',
  'error.detail.task-concurrency-policy-cannot-change-while-task-instances-are-active':
    'concurrency.policyBusy',
  'error.detail.concurrency-resource-is-still-referenced-by-tasks': 'concurrency.referenced',
  'error.detail.some-concurrency-resources-do-not-exist-or-are-disabled':
    'concurrency.resourceError',
  'error.business.ssh-host-key-mismatch': 'config.sshIdentityMismatch',
  'error.business.ssh-host-key-required': 'config.sshIdentityRequired',
  'error.business.ssh-host-key-invalid': 'config.sshIdentityInvalid',
  'error.business.ssh-host-key-probe-failed': 'config.sshProbeFailed',
  'error.detail.notify-config-is-still-referenced-by-bindings': 'config.notifyReferenced',
  'error.business.resource-is-still-referenced-by-task-definitions': 'config.resourceReferenced',
  'error.business.resource-code-already-exists': 'config.resourceCodeExists',
  'error.auth.credentials-required': 'backend.credentialsRequired',
  'error.auth.invalid-credentials': 'backend.invalidCredentials',
  'error.auth.disabled': 'backend.authDisabled',
  'error.auth.misconfigured': 'backend.authMisconfigured',
  'error.security.approver-invalid': 'eventTask.peopleInvalid',
  'error.security.input': 'backend.invalidInput',
  'error.security.password': 'backend.passwordPolicy',
  'error.security.denied': 'error.forbidden',
  'error.security.not-found': 'backend.notFound',
  'error.security.change-required': 'error.passwordRequired',
  'error.security.last-admin': 'backend.lastAdmin',
  'error.security.self-disable': 'backend.selfDisable',
  'error.security.self-delete': 'backend.selfDelete',
  'error.security.conflict': 'backend.accountConflict',
  'error.security.database-required': 'backend.databaseRequired',
}

export function describeError(
  error: unknown,
  t: (key: string, args?: unknown[]) => string,
): string {
  if (!(error instanceof ApiError)) return t('error.generic')
  const key = error.envelope?.messageKey && messageTranslations[error.envelope.messageKey]
  if (key) return t(key, error.envelope?.messageArgs ?? [])
  // Stable client translations re-render on locale changes. Unknown backend messages remain original.
  if (error.kind !== 'generic') return t(`error.${error.kind}`)
  return error.envelope?.message || t('error.generic')
}
