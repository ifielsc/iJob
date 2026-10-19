// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { createPreferenceRepository, defaults } from './preferences'
import type { AppLocale, Preferences, ThemeMode } from './preferences'

export const usePreferences = defineStore('preferences', () => {
  const repository = createPreferenceRepository(() => window.localStorage)
  const initial = repository.read(null) ?? defaults
  const theme = ref<ThemeMode>(initial.theme)
  const locale = ref<AppLocale>(initial.locale)
  const timeMode = ref<'business' | 'UTC'>(initial.timeMode ?? 'business')
  const businessZone = ref<string | null>(null)
  const displayTimezone = computed(() =>
    timeMode.value === 'UTC' ? 'UTC' : (businessZone.value ?? 'UTC'),
  )
  function setTimeMode(value: 'business' | 'UTC') {
    timeMode.value = value
    save()
  }
  function setBusinessZone(value: string | null) {
    if (value) new Intl.DateTimeFormat('en', { timeZone: value }).format()
    businessZone.value = value
  }
  let identity: string | null = null
  function apply(value: Preferences) {
    timeMode.value = value.timeMode ?? 'business'
    theme.value = value.theme
    locale.value = value.locale
  }
  function switchIdentity(loginId: string | null, options: { preserveAppearance?: boolean } = {}) {
    if (loginId === identity) return
    const next =
      repository.read(loginId) ??
      (loginId === null
        ? defaults
        : { theme: theme.value, locale: locale.value, timeMode: timeMode.value })
    // The appearance visible at sign-in takes precedence over an older account preference.
    if (loginId !== null && options.preserveAppearance) {
      next.theme = theme.value
      next.locale = locale.value
    }
    identity = loginId
    apply(next)
    if (loginId !== null && options.preserveAppearance) save()
  }
  function setTheme(value: ThemeMode) {
    theme.value = value
    save()
  }
  function setLocale(value: AppLocale) {
    locale.value = value
    save()
  }
  function save() {
    repository.write(identity, {
      theme: theme.value,
      locale: locale.value,
      timeMode: timeMode.value,
    })
  }
  watch(
    [theme, locale],
    () => {
      document.documentElement.dataset.theme = theme.value
      document.documentElement.lang = locale.value
      document.documentElement.style.colorScheme = theme.value
    },
    { immediate: true, flush: 'sync' },
  )
  return {
    theme,
    locale,
    timeMode,
    businessZone,
    displayTimezone,
    setTimeMode,
    setBusinessZone,
    switchIdentity,
    setTheme,
    setLocale,
  }
})
