<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { NFormItem } from 'naive-ui'
import { useI18n } from 'vue-i18n'
defineProps<{
  hideLabel?: boolean
  id: string
  label: string
  required?: boolean
  missing?: boolean
  invalid?: boolean
  message?: string
}>()
const { t } = useI18n()
</script>
<template>
  <NFormItem
    :show-label="!hideLabel"
    :show-feedback="false"
    :required="required"
    require-mark-placement="left"
    :label-props="{ for: id }"
    :validation-status="missing || invalid ? 'error' : undefined"
  >
    <template #label>
      <span>{{ label }}</span>
      <span v-if="missing" :id="`${id}-required`" class="auth-required" role="alert">{{
        t('common.required')
      }}</span>
    </template>
    <div class="field-content">
      <slot />
      <div v-if="message && !missing" :id="`${id}-error`" class="field-error" role="alert">
        {{ message }}
      </div>
    </div>
  </NFormItem>
</template>
<style scoped>
.field-content {
  width: 100%;
  min-width: 0;
}
.field-error {
  width: 100%;
  margin-top: 5px;
  color: var(--error);
  font-size: 12px;
  line-height: 1.5;
}
.auth-required {
  margin-left: 8px;
  color: var(--error);
  font-size: 12px;
  font-weight: 400;
}
</style>
