// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export interface ApiResponse<T> {
  success: boolean
  code: number
  errorCode: string | null
  messageKey: string | null
  messageArgs: unknown[]
  message: string | null
  traceId: string | null
  data: T
}

export const permissions = [
  'SUMMARY_READ',
  'DEFINITION_READ',
  'SENSITIVE_READ',
  'CONFIGURATION_WRITE',
  'EXECUTION_CONTROL',
  'APPROVAL_DECIDE',
  'EVENT_PUBLISH',
  'USER_MANAGE',
  'AUDIT_READ',
  'SELF_PASSWORD_CHANGE',
] as const
export type Permission = (typeof permissions)[number]
export interface CurrentUser {
  loginId: string
  username: string
  displayName: string
  roleCode: 'ADMIN' | 'OPERATOR' | 'VIEWER' | null
  permissions: Permission[]
  mustChangePassword: boolean
}
export interface Token {
  tokenName: string
  tokenValue: string
}

export function parseCurrentUser(value: unknown): CurrentUser {
  if (!value || typeof value !== 'object') throw new Error('Invalid identity contract')
  const v = value as Record<string, unknown>
  if (
    typeof v.loginId !== 'string' ||
    !v.loginId ||
    typeof v.username !== 'string' ||
    typeof v.displayName !== 'string' ||
    typeof v.mustChangePassword !== 'boolean' ||
    !Array.isArray(v.permissions) ||
    !v.permissions.every((p) => typeof p === 'string') ||
    ![null, 'ADMIN', 'OPERATOR', 'VIEWER'].includes((v.roleCode ?? null) as string | null)
  )
    throw new Error('Invalid identity contract')
  return {
    loginId: v.loginId,
    username: v.username,
    displayName: v.displayName,
    roleCode: (v.roleCode ?? null) as CurrentUser['roleCode'],
    mustChangePassword: v.mustChangePassword,
    permissions: v.permissions.filter((p): p is Permission =>
      permissions.includes(p as Permission),
    ),
  }
}
