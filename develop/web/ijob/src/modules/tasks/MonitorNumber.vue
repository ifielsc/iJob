<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { NInput } from 'naive-ui'
const value = defineModel<string>('value', { required: true })
defineProps<{ id: string; label: string; disabled: boolean; error?: string }>()
</script>
<template>
  <div>
    <NInput
      v-model:value="value"
      :disabled="disabled"
      :input-props="{ id, 'aria-label': label, 'aria-invalid': !!error, inputmode: 'decimal' }"
    />
    <p v-if="error" class="monitor-error">{{ error }}</p>
  </div>
</template>
<style scoped>
.monitor-error {
  color: var(--error);
  font-size: 12px;
  margin: 6px 0 0;
}
</style>
