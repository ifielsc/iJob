// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export type ThemeMode = 'light' | 'dark'
export type AppLocale = 'zh-CN' | 'en-US'
export type EdgeRoutingMode = 'avoid' | 'simple'
export interface Preferences {
  timeMode?: 'business' | 'UTC'
  edgeRouting?: EdgeRoutingMode
  theme: ThemeMode
  locale: AppLocale
}
export const defaults: Preferences = { theme: 'light', locale: 'zh-CN' }

export function preferenceKey(loginId: string | null) {
  return `ijob:preferences:v1:${loginId === null ? 'guest' : `user:${encodeURIComponent(loginId)}`}`
}

export function createPreferenceRepository(storage: () => Pick<Storage, 'getItem' | 'setItem'>) {
  const memory = new Map<string, Preferences>()
  return {
    read(loginId: string | null): Preferences | undefined {
      const key = preferenceKey(loginId)
      if (memory.has(key)) return { ...memory.get(key)! }
      try {
        const value: unknown = JSON.parse(storage().getItem(key) ?? 'null')
        if (
          typeof value === 'object' &&
          value !== null &&
          'theme' in value &&
          'locale' in value &&
          (value.theme === 'light' || value.theme === 'dark') &&
          (value.locale === 'zh-CN' || value.locale === 'en-US')
        ) {
          const preference: Preferences = {
            theme: value.theme,
            locale: value.locale,
            ...('timeMode' in value && (value.timeMode === 'UTC' || value.timeMode === 'business')
              ? { timeMode: value.timeMode }
              : {}),
            ...('edgeRouting' in value &&
            (value.edgeRouting === 'avoid' || value.edgeRouting === 'simple')
              ? { edgeRouting: value.edgeRouting }
              : {}),
          }
          memory.set(key, preference)
          return { ...preference }
        }
      } catch {
        /* A malformed value or blocked storage must not block the app. */
      }
      return undefined
    },
    write(loginId: string | null, value: Preferences) {
      const key = preferenceKey(loginId)
      const safe = {
        theme: value.theme,
        locale: value.locale,
        ...(value.timeMode ? { timeMode: value.timeMode } : {}),
        ...(value.edgeRouting === 'avoid' || value.edgeRouting === 'simple'
          ? { edgeRouting: value.edgeRouting }
          : {}),
      }
      memory.set(key, safe)
      try {
        storage().setItem(key, JSON.stringify(safe))
      } catch {
        /* Session memory fallback. */
      }
    },
  }
}
