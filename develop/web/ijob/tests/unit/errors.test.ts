import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { ApiError, describeError } from '../../src/shared/api/errors'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import { localizedKey } from '../../src/shared/api/query'

describe('stable backend error metadata', () => {
  it('uses the message key for precise 409 errors and rerenders with locale', () => {
    const i18n = createI18n({
      legacy: false,
      locale: 'zh-CN',
      messages: { 'zh-CN': zhCN, 'en-US': enUS },
    })
    const error = new ApiError('conflict', 409, {
      messageKey: 'error.security.last-admin',
      messageArgs: [],
      message: 'At least one enabled administrator must remain',
    })
    expect(describeError(error, i18n.global.t)).toBe(zhCN.backend.lastAdmin)
    i18n.global.locale.value = 'en-US'
    expect(describeError(error, i18n.global.t)).toBe(enUS.backend.lastAdmin)
  })
  it('passes messageArgs and preserves unknown original diagnostics', () => {
    const error = new ApiError('generic', 400, {
      messageKey: 'error.security.input',
      messageArgs: ['safe-argument'],
    })
    expect(describeError(error, (key, args) => `${key}:${args?.[0]}`)).toBe(
      'backend.invalidInput:safe-argument',
    )
    const unknown = new ApiError('generic', 500, {
      messageKey: 'external.error',
      message: 'Raw external message',
    })
    expect(describeError(unknown, (key) => key)).toBe('Raw external message')
  })
  it('isolates localized server data by identity and language', () => {
    expect(localizedKey('alice', 'zh-CN', 'audit')).not.toEqual(
      localizedKey('bob', 'zh-CN', 'audit'),
    )
    expect(localizedKey('alice', 'zh-CN', 'audit')).not.toEqual(
      localizedKey('alice', 'en-US', 'audit'),
    )
  })
})
