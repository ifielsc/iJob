<script setup lang="ts">
import { computed, watch } from 'vue'
import {
  NConfigProvider,
  NDialogProvider,
  NMessageProvider,
  darkTheme,
  dateEnUS,
  dateZhCN,
  enUS,
  zhCN,
} from 'naive-ui'
import { usePreferences } from '../shared/preferences/store'
import { naiveTheme, themes } from '../shared/theme/tokens'
import { compactTheme, compactThemes, isCompactRoute } from '../shared/theme/compact'
import { i18n } from '../shared/i18n'
import AppShell from './layout/AppShell.vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useRuntime } from './runtime'

const preferences = usePreferences()
const route = useRoute()
const router = useRouter()
const runtime = useRuntime()
watch(
  () => runtime.accessIssue.value?.kind,
  (kind) => {
    if (kind === 'unauthorized' && route.name !== 'login')
      void router.replace({ name: 'login', query: { reason: 'expired' } })
    if (kind === 'passwordRequired' && route.name !== 'change-password')
      void router.replace({ name: 'change-password' })
  },
)
const compact = computed(() => isCompactRoute(route.name))
const overrides = computed(() =>
  compact.value ? compactTheme(preferences.theme) : naiveTheme(preferences.theme),
)
watch(
  () => preferences.locale,
  (locale) => {
    i18n.global.locale.value = locale
  },
  { immediate: true, flush: 'sync' },
)
watch(
  [() => preferences.theme, compact],
  ([mode, isCompact]) => {
    for (const [key, value] of Object.entries((isCompact ? compactThemes : themes)[mode]))
      document.documentElement.style.setProperty(`--${key}`, value)
  },
  { immediate: true, flush: 'sync' },
)
</script>

<template>
  <NConfigProvider
    :theme="preferences.theme === 'dark' ? darkTheme : null"
    :theme-overrides="overrides"
    :locale="preferences.locale === 'zh-CN' ? zhCN : enUS"
    :date-locale="preferences.locale === 'zh-CN' ? dateZhCN : dateEnUS"
  >
    <NMessageProvider>
      <NDialogProvider
        ><RouterView v-if="route.meta.standalone" /><AppShell v-else
      /></NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
