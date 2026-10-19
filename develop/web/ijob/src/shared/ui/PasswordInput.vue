<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NInput } from 'naive-ui'
import type { InputInst } from 'naive-ui'
import { Eye, EyeOff, LockKeyhole } from '@lucide/vue'
defineProps<{
  id: string
  label: string
  autocomplete: string
  disabled: boolean
  describedby?: string
  invalid?: boolean
  missing?: boolean
}>()
const input = ref<InputInst | null>(null)
defineExpose({ focus: () => input.value?.focus() })
const value = defineModel<string>({ required: true })
const visible = ref(false)
const { t } = useI18n()
</script>
<template>
  <NInput
    ref="input"
    v-model:value="value"
    :type="visible ? 'text' : 'password'"
    :disabled="disabled"
    :input-props="{
      id,
      autocomplete,
      required: true,
      'aria-label': label,
      'aria-invalid': invalid || undefined,
      'aria-describedby':
        [describedby, missing ? `${id}-required` : undefined].filter(Boolean).join(' ') ||
        undefined,
    }"
  >
    <template #prefix><LockKeyhole :size="16" aria-hidden="true" /></template>
    <template #suffix>
      <NButton
        quaternary
        circle
        size="tiny"
        :disabled="disabled"
        :aria-pressed="visible"
        :aria-label="`${t('auth.showPassword')} · ${label}`"
        @click="visible = !visible"
      >
        <template #icon
          ><component :is="visible ? EyeOff : Eye" :size="16" aria-hidden="true"
        /></template>
      </NButton>
    </template>
  </NInput>
</template>
