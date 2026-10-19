// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { CurrentUser, Permission } from '../api/contracts'

export function can(user: CurrentUser | undefined, permission: Permission): boolean {
  return !!user && !user.mustChangePassword && user.permissions.includes(permission)
}

export function accessDecision(user: CurrentUser | undefined, permission?: Permission) {
  if (!user) return 'unauthorized'
  if (user.mustChangePassword) return 'passwordRequired'
  if (permission && !can(user, permission)) return 'forbidden'
  return 'allowed'
}
