// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useRequiredFields } from '../../src/shared/forms/required-fields'

describe('auth required feedback', () => {
  it('stays quiet until submit and finds the first empty field', () => {
    const check = useRequiredFields({ username: ref(''), password: ref('') })
    expect(check.missing('username')).toBe(false)
    expect(check.firstMissing()).toBe('username')
    expect(check.missing('username')).toBe(true)
    expect(check.missing('password')).toBe(true)
  })
  it('clears each marker as the field is filled', () => {
    const username = ref(''),
      password = ref('')
    const check = useRequiredFields({ username, password })
    check.firstMissing()
    username.value = 'draft'
    expect(check.missing('username')).toBe(false)
    expect(check.firstMissing()).toBe('password')
  })
  it('does not create new required errors when an API submission clears passwords', () => {
    const password = ref('')
    const check = useRequiredFields({ password })
    check.firstMissing()
    password.value = 'test-only-password'
    expect(check.firstMissing()).toBeUndefined()
    password.value = ''
    expect(check.missing('password')).toBe(false)
    expect(check.firstMissing()).toBe('password')
  })
  it('does not trim or replace the password policy', () => {
    const password = ref(' ')
    const check = useRequiredFields({ password })
    expect(check.firstMissing()).toBeUndefined()
    expect(password.value).toBe(' ')
  })
})
