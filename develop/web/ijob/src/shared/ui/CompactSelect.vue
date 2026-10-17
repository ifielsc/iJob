<script setup lang="ts">
import { ref } from 'vue'
import { NSelect } from 'naive-ui'
import type { SelectProps } from 'naive-ui'
const props = defineProps<{
  id: string
  label: string
  value: string | number
  options: { label: string; value: string | number }[]
  disabled?: boolean
  invalid?: boolean
  size?: 'small' | 'medium'
}>()
const emit = defineEmits<{ 'update:value': [string | number]; show: [boolean] }>()
const show = ref(false)
function visibility(value: boolean) {
  show.value = value
  emit('show', value)
}
const nodeProps: SelectProps['nodeProps'] = ({ option }) => ({
  role: 'option',
  'aria-selected':
    typeof option === 'object' &&
    option !== null &&
    'value' in option &&
    option.value === props.value,
})
</script>
<template>
  <NSelect
    :value="value"
    :options="options"
    :disabled="disabled"
    :size="size ?? 'medium'"
    filterable
    :show="show"
    :input-props="{
      id,
      'aria-label': label,
      'aria-invalid': invalid || undefined,
      role: 'combobox',
      'aria-expanded': show,
      'aria-controls': `${id}-options`,
      'aria-autocomplete': 'list',
    }"
    :menu-props="{ id: `${id}-options`, role: 'listbox', 'aria-label': label }"
    :node-props="nodeProps"
    @update:show="visibility"
    @update:value="emit('update:value', $event)"
  />
</template>
