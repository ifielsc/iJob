<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Eye, EyeOff } from '@lucide/vue'
import { NButton, NInput } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import type { HiddenField, FieldMode } from './contracts'
const props = defineProps<{
  tableCell?: boolean
  id: string
  label: string
  field: HiddenField
  required?: boolean
  missing?: boolean
  message?: string
  displayValue?: string
  revealLabel?: string
  concealLabel?: string
  passwordToggle?: boolean
  replaceLabel?: string
  hint?: string
  placeholder?: string
  secret?: boolean
  list?: boolean
  maxlength?: number
  disabled?: boolean
}>()
const emit = defineEmits<{ change: [HiddenField] }>()
const { t } = useI18n()
const revealed = ref(false)
watch(
  () => props.field.mode,
  () => {
    revealed.value = false
  },
)
function mode(mode: FieldMode) {
  if (!props.disabled) emit('change', { ...props.field, mode })
}
</script>
<template>
  <FormItem
    :id="id"
    :hide-label="tableCell"
    :label="label"
    :required="required"
    :missing="missing"
    :invalid="!!message"
    :message="message"
  >
    <div class="configured-field" role="group" :aria-label="label">
      <template v-if="field.mode !== 'replace'">
        <p v-if="displayValue && field.mode === 'keep'" class="masked-webhook">
          {{ displayValue }}
        </p>
        <div class="preservation">
          <span>{{
            tableCell && field.mode === 'keep'
              ? t(field.configured ? 'config.configured' : 'config.unset')
              : field.mode === 'clear'
                ? t('config.willClear')
                : (field.count !== undefined
                    ? t('config.count', { count: field.count })
                    : t(field.configured ? 'config.configured' : 'config.unset')) +
                  ' · ' +
                  t('config.keep')
          }}</span>
          <NButton size="small" text type="primary" :disabled="disabled" @click="mode('replace')">{{
            replaceLabel ?? t('config.replace')
          }}</NButton>
          <NButton
            v-if="!required && field.mode !== 'clear'"
            size="small"
            text
            :disabled="disabled"
            @click="mode('clear')"
            >{{ t('config.clear') }}</NButton
          >
          <NButton
            v-if="field.mode === 'clear'"
            size="small"
            text
            :disabled="disabled"
            @click="mode('keep')"
            >{{ t('config.undo') }}</NButton
          >
        </div>
      </template>
      <template v-else>
        <NInput
          :value="field.value"
          :placeholder="placeholder"
          :disabled="disabled"
          :type="secret && (!passwordToggle || !revealed) ? 'password' : list ? 'textarea' : 'text'"
          :show-password-on="passwordToggle ? undefined : 'click'"
          :maxlength="maxlength"
          :rows="list ? 2 : undefined"
          :input-props="{
            id,
            'aria-label': label,
            'aria-required': required,
            'aria-invalid': missing || !!message || undefined,
            'aria-describedby':
              [message ? id + '-error' : '', hint ? id + '-hint' : ''].filter(Boolean).join(' ') ||
              undefined,
            autocomplete: 'off',
          }"
          @update:value="emit('change', { ...field, value: $event })"
          ><template v-if="passwordToggle" #suffix
            ><NButton
              class="resource-password-eye"
              quaternary
              circle
              size="small"
              :disabled="disabled"
              :aria-label="
                revealed
                  ? (concealLabel ?? t('config.hidePassword'))
                  : (revealLabel ?? t('config.showPassword'))
              "
              :aria-pressed="revealed"
              @click="revealed = !revealed"
              ><component :is="revealed ? EyeOff : Eye" :size="16" /></NButton></template
        ></NInput>
        <NButton
          v-if="field.configured"
          class="field-undo"
          size="tiny"
          :disabled="disabled"
          text
          @click="mode('keep')"
          >{{ t('config.keep') }}</NButton
        >
      </template>
      <p v-if="hint" :id="id + '-hint'" class="configuration-note jdbc-format-hint">{{ hint }}</p>
    </div>
  </FormItem>
</template>
