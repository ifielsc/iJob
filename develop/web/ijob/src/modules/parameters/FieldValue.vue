<script setup lang="ts">
import { NInput, NSelect, NSwitch } from 'naive-ui'
import type { Field } from './model'
defineProps<{ modelValue: string; field: Field; disabled?: boolean; label: string }>()
defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <NSwitch
    v-if="field.type === 'BOOLEAN'"
    :value="modelValue === 'true'"
    :disabled="disabled"
    :aria-label="label"
    @update:value="$emit('update:modelValue', String($event))"
  />
  <NSelect
    v-else-if="field.type === 'ENUM'"
    :value="modelValue || null"
    :options="(field.options ?? []).map((v) => ({ label: v, value: v }))"
    :disabled="disabled"
    :aria-label="label"
    @update:value="$emit('update:modelValue', $event ?? '')"
  />
  <NInput
    v-else
    :value="modelValue"
    :type="
      field.sensitive ? 'password' : ['OBJECT', 'ARRAY'].includes(field.type) ? 'textarea' : 'text'
    "
    :placeholder="
      field.type === 'DATE'
        ? 'YYYY-MM-DD'
        : field.type === 'DATETIME'
          ? 'YYYY-MM-DDTHH:mm:ss'
          : undefined
    "
    :disabled="disabled"
    :input-props="{ 'aria-label': label }"
    @update:value="$emit('update:modelValue', $event)"
  />
</template>
