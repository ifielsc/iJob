<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { Copy } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { usePreferences } from '../preferences/store'
import { formatUtc } from '../time/format'
import { utcEvidence } from '../time/display'
const props = defineProps<{ value: string; milliseconds?: boolean }>()
const prefs = usePreferences(),
  { t } = useI18n(),
  message = useMessage()
const evidence = computed(() => {
  try {
    return utcEvidence(props.value)
  } catch {
    return ''
  }
})
const text = computed(() => {
  if (!evidence.value) return '—'
  return (
    formatUtc(props.value, prefs.locale, prefs.displayTimezone) +
    (props.milliseconds ? (evidence.value.match(/\.\d+/)?.[0] ?? '.000') : '')
  )
})
async function copy() {
  try {
    await navigator.clipboard.writeText(evidence.value)
    message.success(t('unified.copied'))
  } catch {
    message.error(t('unified.copyFailed'))
  }
}
</script>
<template>
  <span class="time-evidence" :title="evidence"
    ><span>{{ text }}</span
    ><NButton
      v-if="evidence"
      text
      :aria-label="t('unified.copy') + ' ' + t('unified.timeEvidence')"
      @click="copy"
      ><Copy :size="12" /></NButton
  ></span>
</template>
<style scoped>
.time-evidence {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-variant-numeric: tabular-nums;
}
</style>
