// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { can, accessDecision } from '../../src/shared/auth/access'
import { parseCurrentUser } from '../../src/shared/api/contracts'
import type { CurrentUser } from '../../src/shared/api/contracts'
import { useSession } from '../../src/shared/auth/session'

const viewer: CurrentUser = {
  loginId: 'user:test',
  username: 'viewer',
  displayName: 'Viewer',
  roleCode: 'VIEWER',
  permissions: ['SUMMARY_READ'],
  mustChangePassword: false,
}
describe('effective permission boundaries', () => {
  beforeEach(() => setActivePinia(createPinia()))
  it('never grants access from a role label', () => {
    expect(can({ ...viewer, roleCode: 'ADMIN' }, 'USER_MANAGE')).toBe(false)
    expect(can(viewer, 'SUMMARY_READ')).toBe(true)
    expect(accessDecision(undefined, 'SUMMARY_READ')).toBe('unauthorized')
    expect(accessDecision(viewer, 'USER_MANAGE')).toBe('forbidden')
    expect(accessDecision({ ...viewer, mustChangePassword: true }, 'SUMMARY_READ')).toBe(
      'passwordRequired',
    )
  })
  it('treats STATIC effective permissions and unknown permissions conservatively', () => {
    const identity = parseCurrentUser({
      ...viewer,
      roleCode: 'ADMIN',
      permissions: ['SUMMARY_READ', 'NEW_PERMISSION'],
    })
    expect(identity.permissions).toEqual(['SUMMARY_READ'])
    expect(can(identity, 'SELF_PASSWORD_CHANGE')).toBe(false)
  })
  it('rejects malformed identities rather than guessing permissions', () => {
    expect(() => parseCurrentUser({ ...viewer, loginId: 9007199254740992 })).toThrow()
    expect(() => parseCurrentUser({ ...viewer, roleCode: 'SUPERADMIN' })).toThrow()
    expect(() => parseCurrentUser({ ...viewer, permissions: 'ALL' })).toThrow()
  })
  it.each([null, undefined])(
    'accepts an absent role without granting permissions: %s',
    (roleCode) => {
      const identity = parseCurrentUser({ ...viewer, roleCode, permissions: [] })
      expect(identity.roleCode).toBeNull()
      expect(identity.permissions).toEqual([])
      expect(accessDecision(identity, 'SUMMARY_READ')).toBe('forbidden')
      expect(can(identity, 'USER_MANAGE')).toBe(false)
    },
  )
  it('keeps tokens out of storage and rejects unsafe header names', () => {
    const session = useSession()
    const previousStorage = JSON.stringify(localStorage)
    session.replace({ tokenName: 'ijob-token', tokenValue: 'ephemeral' })
    expect(JSON.stringify(localStorage)).toBe(previousStorage)
    expect(() => session.replace({ tokenName: 'Cookie', tokenValue: 'secret' })).toThrow()
    expect(() => session.replace({ tokenName: 'ijob-token', tokenValue: 'x\r\ny' })).toThrow()
    session.replace(null)
    expect(session.token).toBeNull()
  })
})
