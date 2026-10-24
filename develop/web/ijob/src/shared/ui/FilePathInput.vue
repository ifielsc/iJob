<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import { NButton, NInput } from 'naive-ui'
import { FolderOpen } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import type { FileChoice } from './file-browser'
const Browser = defineAsyncComponent(() => import('./FilePathBrowser.vue'))
defineProps<{
  resourceId?: string
  id?: string
  disabled?: boolean
  invalid?: boolean
  mode?: 'file' | 'directory' | 'both'
  transfer?: boolean
  maxlength?: number
}>()
const value = defineModel<string>('value', { required: true })
const emit = defineEmits<{ select: [boolean]; choose: [FileChoice] }>()
const { t } = useI18n(),
  opened = ref(false)
function open(show: boolean) {
  opened.value = show
  emit('select', show)
}
function choose(choice: FileChoice) {
  value.value = choice.path
  emit('choose', choice)
  open(false)
}
</script>
<template>
  <div class="file-path-input">
    <NInput
      v-model:value="value"
      :disabled="disabled"
      :maxlength="maxlength ?? 2048"
      :input-props="{ id, 'aria-invalid': !!invalid }"
    /><NButton
      :disabled="disabled || !resourceId"
      :title="t(resourceId ? 'browse.title' : 'browse.resourceFirst')"
      :aria-label="t('browse.title')"
      @click="open(true)"
      ><FolderOpen :size="17"
    /></NButton>
  </div>
  <Browser
    v-if="opened"
    :resource-id="resourceId"
    :mode="mode ?? 'directory'"
    :transfer="transfer"
    :initial-path="value"
    @close="open(false)"
    @choose="choose"
  />
</template>
<style scoped>
.file-path-input {
  display: flex;
  gap: 6px;
  min-width: 0;
  width: 100%;
}
.file-path-input > .n-input {
  min-width: 0;
  flex: 1;
}
.file-path-input > .n-button {
  flex: none;
}
</style>
