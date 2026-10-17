<script setup lang="ts">
import { NInput } from 'naive-ui'
defineProps<{ id: string; label?: string; disabled?: boolean; invalid?: boolean }>()
const value = defineModel<number | null>('value', { required: true })
function update(text: string) {
  value.value = text ? Number(text) : null
}
</script>
<template>
  <NInput
    :value="value == null ? '' : String(value)"
    :disabled="disabled"
    :allow-input="(text: string) => /^\d*$/.test(text)"
    :input-props="{
      id,
      inputmode: 'numeric',
      pattern: '[0-9]*',
      'aria-label': label,
      'aria-invalid': invalid,
    }"
    @update:value="update"
  />
</template>
