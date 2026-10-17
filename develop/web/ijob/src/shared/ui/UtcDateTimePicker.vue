<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePreferences } from '../preferences/store'
import { utcToWall, wallToUtc } from '../time/display'
import DateTimePicker from './DateTimePicker.vue'
const props = defineProps<{
  id: string
  label: string
  invalid?: boolean
  describedby?: string
  required?: boolean
}>()
const value = defineModel<string>({ required: true })
const picker = ref<InstanceType<typeof DateTimePicker> | null>(null)
const preferences = usePreferences(),
  { t } = useI18n()
const conversionError = computed(() => value.value.startsWith('invalid-wall:'))
const localValue = computed({
  get() {
    if (!value.value) return ''
    if (conversionError.value) return value.value.slice(value.value.indexOf('|') + 1)
    try {
      return utcToWall(value.value, preferences.displayTimezone)
    } catch {
      return value.value
    }
  },
  set(text: string) {
    if (!text) {
      value.value = ''
      return
    }
    try {
      value.value = wallToUtc(text, preferences.displayTimezone)
    } catch {
      value.value = `invalid-wall:${preferences.displayTimezone}|${text}`
    }
  },
})
defineExpose({ focus: () => picker.value?.focus() })
</script>
<template>
  <div class="zoned-picker">
    <DateTimePicker
      :id="props.id"
      ref="picker"
      v-model="localValue"
      :label="props.label"
      :required="props.required"
      :describedby="props.describedby"
      :invalid="invalid || conversionError"
      :zone-id="preferences.displayTimezone"
    /><small v-if="conversionError" class="field-error" role="alert">{{
      t('unified.invalidWall')
    }}</small>
  </div>
</template>
