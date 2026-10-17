<script setup lang="ts">
import { NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { BatchValue } from './batch-etl-model'
defineProps<{
  id: string
  label: string
  disabled: boolean
  invalid?: boolean
  boolean?: boolean
}>()
const value = defineModel<BatchValue>({ required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n()
</script>
<template>
  <div class="batch-value">
    <NSelect
      v-model:value="value.kind"
      filterable
      :node-props="() => ({ role: 'option' })"
      :menu-props="{ role: 'listbox', 'aria-label': label }"
      :disabled="disabled"
      :options="
        (boolean ? ['string', 'number', 'boolean'] : ['string', 'number']).map((v) => ({
          value: v,
          label: t('batchEtl.' + v),
        }))
      "
      :input-props="{ id: id + '-kind', 'aria-label': label + ' · ' + t('batchEtl.valueKind') }"
      @update:show="emit('select', $event)"
    />
    <NInput
      v-model:value="value.text"
      :disabled="disabled"
      :input-props="{ id, 'aria-label': label, 'aria-invalid': invalid }"
    />
  </div>
</template>
<style scoped>
.batch-value {
  display: grid;
  grid-template-columns: 94px minmax(0, 1fr);
  gap: 8px;
  min-width: 0;
}
</style>
