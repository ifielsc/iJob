<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { Copy } from '@lucide/vue'
import { NButton, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
const props = withDefaults(defineProps<{ value: string; label?: string }>(), { label: 'ID' })
const { t } = useI18n(),
  message = useMessage()
async function copy() {
  try {
    await navigator.clipboard.writeText(props.value)
    message.success(t('unified.copied'))
  } catch {
    message.error(t('unified.copyFailed'))
  }
}
</script>
<template>
  <span class="identity-text"
    ><span>{{ label }}: {{ value }}</span
    ><NButton text :aria-label="t('unified.copy') + ' ' + label + ' ' + value" @click.stop="copy"
      ><Copy :size="12" /></NButton
  ></span>
</template>
<style scoped>
.identity-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
  max-width: 100%;
}
.identity-text > span {
  overflow-wrap: anywhere;
  min-width: 0;
}
</style>
