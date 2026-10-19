<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import { NConfigProvider } from 'naive-ui'
import { usePreferences } from '../../shared/preferences/store'
import { compactTheme, compactThemes } from '../../shared/theme/compact'
import AuthPreferences from '../../shared/ui/CompactPreferences.vue'
import BrandMark from '../../shared/ui/BrandMark.vue'
import './auth.css'
const { t } = useI18n()
const preferences = usePreferences()
const overrides = computed(() => {
  const base = compactTheme(preferences.theme)
  if (preferences.theme === 'light') return base
  return {
    ...base,
    Input: { ...base.Input, color: '#182B4A', colorFocus: '#182B4A' },
    Button: {
      ...base.Button,
      colorPrimary: '#6544E8',
      colorHoverPrimary: '#7453EE',
      colorPressedPrimary: '#5837D5',
      borderPrimary: '1px solid #805FFF',
      borderHoverPrimary: '1px solid #967AFF',
      borderPressedPrimary: '1px solid #805FFF',
      textColorPrimary: '#FFFFFF',
      textColorHoverPrimary: '#FFFFFF',
      textColorPressedPrimary: '#FFFFFF',
    },
  }
})
const variables = computed(() =>
  Object.fromEntries(
    Object.entries(compactThemes[preferences.theme]).map(([key, value]) => [`--${key}`, value]),
  ),
)
</script>
<template>
  <NConfigProvider :theme-overrides="overrides">
    <div class="auth-layout" :data-theme="preferences.theme" :style="variables">
      <div class="auth-decoration" aria-hidden="true" />
      <header class="auth-header">
        <a class="auth-brand" href="/" aria-label="iJob"><BrandMark /></a><AuthPreferences />
      </header>
      <div class="auth-content">
        <main id="main" class="auth-card glass" tabindex="-1">
          <slot />
        </main>
      </div>
      <footer class="auth-footer">{{ t('auth.memoryOnly') }}</footer>
    </div>
  </NConfigProvider>
</template>
