<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { NButton } from 'naive-ui'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
</script>
<template>
  <NButton
    class="drawer-close-button"
    quaternary
    circle
    :disabled="disabled"
    :aria-label="t('common.close')"
    @click="emit('close')"
    ><X :size="18"
  /></NButton>
</template>
<style scoped>
.drawer-close-button {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}
@media (max-width: 600px) {
  .drawer-close-button {
    width: 44px;
    height: 44px;
  }
}
</style>
