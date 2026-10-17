<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { NButton } from 'naive-ui'
import { Globe2, Moon, Sun } from '@lucide/vue'
import { usePreferences } from '../preferences/store'
const preferences = usePreferences()
const { t } = useI18n()
</script>
<template>
  <div class="compact-preferences">
    <NButton
      size="small"
      secondary
      :aria-label="`${t('preference.theme')}: ${t(`preference.${preferences.theme}`)}`"
      @click="preferences.setTheme(preferences.theme === 'light' ? 'dark' : 'light')"
    >
      <template #icon
        ><component :is="preferences.theme === 'light' ? Sun : Moon" :size="15" aria-hidden="true"
      /></template>
      {{ t(`preference.${preferences.theme}`) }}
    </NButton>
    <NButton
      size="small"
      secondary
      :aria-label="`${t('preference.language')}: ${t(preferences.locale === 'zh-CN' ? 'preference.zh' : 'preference.en')}`"
      @click="preferences.setLocale(preferences.locale === 'zh-CN' ? 'en-US' : 'zh-CN')"
    >
      <template #icon><Globe2 :size="15" aria-hidden="true" /></template>
      {{ t(preferences.locale === 'zh-CN' ? 'preference.zh' : 'preference.en') }}
    </NButton>
  </div>
</template>
<style scoped>
.compact-preferences {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
