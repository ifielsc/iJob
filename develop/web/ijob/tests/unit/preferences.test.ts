// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createPreferenceRepository, preferenceKey } from '../../src/shared/preferences/preferences'
import { usePreferences } from '../../src/shared/preferences/store'

describe('isolated local preferences', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })
  it('starts light and Chinese regardless of platform preferences', () => {
    const store = usePreferences()
    expect([store.theme, store.locale]).toEqual(['light', 'zh-CN'])
    expect(document.documentElement.lang).toBe('zh-CN')
  })
  it('restores guest and stable-loginId preferences without leaking between users', () => {
    const store = usePreferences()
    store.setTheme('dark')
    store.switchIdentity('user:alice')
    expect(store.theme).toBe('dark')
    store.setLocale('en-US')
    store.switchIdentity(null)
    expect([store.theme, store.locale]).toEqual(['dark', 'zh-CN'])
    store.switchIdentity('user:bob')
    store.setTheme('light')
    store.switchIdentity('user:alice')
    expect([store.theme, store.locale]).toEqual(['dark', 'en-US'])
    store.switchIdentity('user:bob')
    expect([store.theme, store.locale]).toEqual(['light', 'zh-CN'])
  })
  it.each([
    ['dark', 'zh-CN'],
    ['light', 'en-US'],
    ['dark', 'en-US'],
    ['light', 'zh-CN'],
  ] as const)('keeps login appearance %s / %s over saved account preferences', (theme, locale) => {
    const repository = createPreferenceRepository(() => localStorage)
    repository.write('alice', {
      theme: theme === 'light' ? 'dark' : 'light',
      locale: locale === 'zh-CN' ? 'en-US' : 'zh-CN',
      timeMode: 'UTC',
    })
    repository.write('bob', { theme: 'light', locale: 'zh-CN' })
    const store = usePreferences()
    store.setTheme(theme)
    store.setLocale(locale)
    store.switchIdentity('alice', { preserveAppearance: true })
    expect([store.theme, store.locale, store.timeMode]).toEqual([theme, locale, 'UTC'])
    expect(document.documentElement.dataset.theme).toBe(theme)
    expect(document.documentElement.lang).toBe(locale)
    expect(JSON.parse(localStorage.getItem(preferenceKey('alice'))!)).toEqual({
      theme,
      locale,
      timeMode: 'UTC',
    })
    store.switchIdentity('alice') // Identity refresh must not undo the selected appearance.
    expect([store.theme, store.locale]).toEqual([theme, locale])
    store.switchIdentity(null)
    store.switchIdentity('bob')
    expect([store.theme, store.locale, store.timeMode]).toEqual(['light', 'zh-CN', 'business'])
    setActivePinia(createPinia())
    const restored = usePreferences()
    restored.switchIdentity('alice')
    expect([restored.theme, restored.locale, restored.timeMode]).toEqual([theme, locale, 'UTC'])
  })
  it('persists the login appearance for a first-time account', () => {
    const store = usePreferences()
    store.setTheme('dark')
    store.setLocale('en-US')
    store.switchIdentity('new-user', { preserveAppearance: true })
    expect(JSON.parse(localStorage.getItem(preferenceKey('new-user'))!)).toEqual({
      theme: 'dark',
      locale: 'en-US',
      timeMode: 'business',
    })
  })
  it('isolates routing by account, restores it after reload and keeps it separate from login appearance', () => {
    const store = usePreferences()
    expect(store.edgeRouting).toBe('avoid')
    store.switchIdentity('alice')
    store.setEdgeRouting('simple')
    store.setTheme('dark')
    store.switchIdentity('bob', { preserveAppearance: true })
    expect(store.edgeRouting).toBe('avoid')
    store.switchIdentity(null)
    expect(store.edgeRouting).toBe('avoid')
    store.switchIdentity('alice', { preserveAppearance: true })
    expect(store.edgeRouting).toBe('simple')
    setActivePinia(createPinia())
    const restored = usePreferences()
    restored.switchIdentity('alice')
    expect(restored.edgeRouting).toBe('simple')
    restored.setEdgeRouting('avoid')
    setActivePinia(createPinia())
    const reset = usePreferences()
    reset.switchIdentity('alice')
    expect(reset.edgeRouting).toBe('avoid')
  })
  it.each([undefined, 'unexpected', null, 42])(
    'defaults missing or invalid routing %s without losing other preferences',
    (edgeRouting) => {
      localStorage.setItem(
        preferenceKey('alice'),
        JSON.stringify({ theme: 'dark', locale: 'en-US', timeMode: 'UTC', edgeRouting }),
      )
      const store = usePreferences()
      store.switchIdentity('alice')
      expect([store.theme, store.locale, store.timeMode, store.edgeRouting]).toEqual([
        'dark',
        'en-US',
        'UTC',
        'avoid',
      ])
    },
  )
  it('retains account routing for this session when local storage is blocked', () => {
    const repository = createPreferenceRepository(() => {
      throw new Error('Blocked')
    })
    repository.write('alice', { theme: 'light', locale: 'zh-CN', edgeRouting: 'simple' })
    expect(repository.read('alice')?.edgeRouting).toBe('simple')
    expect(repository.read('bob')).toBeUndefined()
  })
  it('rejects malformed values and restricts persisted fields', () => {
    const repository = createPreferenceRepository(() => localStorage)
    localStorage.setItem(preferenceKey(null), '{invalid')
    expect(repository.read(null)).toBeUndefined()
    localStorage.setItem(preferenceKey(null), JSON.stringify({ theme: 'system', locale: 'fr' }))
    expect(repository.read(null)).toBeUndefined()
    repository.write(null, { theme: 'light', locale: 'zh-CN' })
    expect(JSON.parse(localStorage.getItem(preferenceKey(null))!)).toEqual({
      theme: 'light',
      locale: 'zh-CN',
    })
  })
  it('uses memory when storage access throws and avoids stale disk after write failure', () => {
    const unavailable = createPreferenceRepository(() => {
      throw new Error('Storage blocked')
    })
    unavailable.write('alice', { theme: 'dark', locale: 'en-US' })
    expect(unavailable.read('alice')).toEqual({ theme: 'dark', locale: 'en-US' })
    const failedWrite = createPreferenceRepository(() => ({
      getItem: () => '{"theme":"light","locale":"zh-CN"}',
      setItem: () => {
        throw new Error('Quota')
      },
    }))
    failedWrite.write(null, { theme: 'dark', locale: 'en-US' })
    expect(failedWrite.read(null)?.theme).toBe('dark')
  })
})
