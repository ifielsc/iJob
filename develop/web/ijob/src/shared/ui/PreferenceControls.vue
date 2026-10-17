<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Globe2, Moon, Sun } from '@lucide/vue'
import { usePreferences } from '../preferences/store'
import type { AppLocale, ThemeMode } from '../preferences/preferences'

const preferences = usePreferences()
const { t } = useI18n()
function setTheme(event: Event) {
  preferences.setTheme((event.target as HTMLSelectElement).value as ThemeMode)
}
function setLocale(event: Event) {
  preferences.setLocale((event.target as HTMLSelectElement).value as AppLocale)
}
</script>

<template>
  <div class="preference-controls">
    <label class="preference-field">
      <component :is="preferences.theme === 'light' ? Sun : Moon" :size="18" aria-hidden="true" />
      <span class="sr-only">{{ t('preference.theme') }}</span>
      <select :value="preferences.theme" :aria-label="t('preference.theme')" @change="setTheme">
        <option value="light">{{ t('preference.light') }}</option>
        <option value="dark">{{ t('preference.dark') }}</option>
      </select>
    </label>
    <label class="preference-field">
      <Globe2 :size="18" aria-hidden="true" />
      <span class="sr-only">{{ t('preference.language') }}</span>
      <select
        :value="preferences.locale"
        :aria-label="t('preference.language')"
        @change="setLocale"
      >
        <option value="zh-CN">{{ t('preference.zh') }}</option>
        <option value="en-US">{{ t('preference.en') }}</option>
      </select>
    </label>
  </div>
</template>
