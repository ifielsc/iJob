<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { stringify } from 'lossless-json'
import { useI18n } from 'vue-i18n'
const SqlEditor = defineAsyncComponent(() => import('./SqlEditor.vue'))
const props = defineProps<{ value: unknown; contextKey?: string }>()
const { t } = useI18n()
const text = computed(() => stringify(props.value, null, 2) ?? '')
const height = computed(() => Math.max(48, Math.min(320, text.value.split('\n').length * 20 + 16)))
</script>
<template>
  <div class="json-viewer" :style="{ '--json-height': height + 'px' }">
    <SqlEditor
      :model-value="text"
      language="JSON"
      read-only
      :context-key="contextKey"
      :title="t('details.formattedJson')"
    />
  </div>
</template>
<style scoped>
.json-viewer {
  min-width: 0;
}
.json-viewer :deep(.sql-inline-host) {
  height: min(var(--json-height), 42dvh);
}
</style>
