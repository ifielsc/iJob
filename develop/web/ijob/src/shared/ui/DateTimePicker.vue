<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NDatePicker, NInput, NPopover, NTimePicker } from 'naive-ui'
import type { InputInst } from 'naive-ui'
import { CalendarDays } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { wallTime, nowInZone } from '../time/wall-time'
import { calendarCoordinate, calendarDay, clockCoordinate, clockText } from '../time/utc-picker'

const props = withDefaults(
  defineProps<{
    zoneId?: string
    id: string
    label: string
    invalid?: boolean
    describedby?: string
    required?: boolean
  }>(),
  { required: true, invalid: false, describedby: undefined, zoneId: 'UTC' },
)
const value = defineModel<string>({ required: true })
const { t } = useI18n()
const input = ref<InputInst | null>(null)
const show = ref(false)
const day = ref('2000-01-15')
const time = ref('00:00:00')
const dayValue = computed(() => {
  try {
    return calendarCoordinate(day.value)
  } catch {
    return null
  }
})
const timeValue = computed(() => clockCoordinate(time.value))
defineExpose({ focus: () => input.value?.focus() })
function seed(source: string) {
  day.value = source.slice(0, 10)
  time.value = source.slice(11, 19)
}
function open() {
  if (show.value) return
  try {
    seed(wallTime(value.value.length === 16 ? `${value.value}:00` : value.value))
  } catch {
    seed(nowInZone(props.zoneId))
  }
  show.value = true
}
function close() {
  show.value = false
  input.value?.focus()
}
function confirm() {
  if (dayValue.value === null) return
  const result = `${day.value}T${time.value}`
  wallTime(result)
  value.value = result
  close()
}
function clear() {
  value.value = ''
  close()
}
function handleKeydown(event: KeyboardEvent) {
  if (event.altKey && event.key === 'ArrowDown') {
    event.preventDefault()
    open()
  }
  if (event.key === 'Escape') show.value = false
}
</script>
<template>
  <NPopover
    :show="show"
    trigger="manual"
    placement="bottom-start"
    :show-arrow="false"
    raw
    @clickoutside="show = false"
  >
    <template #trigger>
      <NInput
        ref="input"
        :value="value.replace('T', ' ')"
        placeholder="yyyy-MM-dd HH:mm:ss"
        :input-props="{
          id,
          'aria-label': label,
          required,
          autocomplete: 'off',
          'aria-invalid': invalid || undefined,
          'aria-describedby': describedby,
          'aria-haspopup': 'dialog',
          'aria-expanded': show,
          'aria-controls': `${id}-picker`,
        }"
        @update:value="value = $event.replace(' ', 'T')"
        @keydown="handleKeydown"
      >
        <template #suffix
          ><NButton
            quaternary
            circle
            size="tiny"
            :aria-label="`${t('audits.chooseDateTime')} · ${label}`"
            :aria-expanded="show"
            @click="show ? close() : open()"
          >
            <template #icon><CalendarDays :size="16" aria-hidden="true" /></template> </NButton
        ></template>
      </NInput>
    </template>
    <div
      :id="`${id}-picker`"
      class="utc-picker"
      role="dialog"
      :aria-label="label"
      @keydown.esc.stop.prevent="close"
    >
      <div class="utc-picker-heading">
        {{ label }}<span>{{ zoneId }}</span>
      </div>
      <div class="utc-picker-inputs">
        <NInput
          v-model:value="day"
          placeholder="yyyy-MM-dd"
          :status="dayValue === null ? 'error' : undefined"
          :input-props="{ 'aria-label': label + ' · ' + t('details.date') }"
        />
        <NTimePicker
          :value="timeValue"
          placement="bottom-end"
          format="HH:mm:ss"
          :actions="[]"
          :to="false"
          :input-props="{ 'aria-label': label + ' · ' + t('audits.clockTime') }"
          @update:value="
            (next: number | null) => {
              if (next !== null) time = clockText(next)
            }
          "
        />
      </div>
      <NDatePicker
        panel
        type="date"
        :value="dayValue"
        :actions="[]"
        :close-on-select="false"
        format="yyyy-MM-dd"
        @update:value="
          (next: number | null) => {
            if (next !== null) day = calendarDay(next)
          }
        "
      />
      <div class="utc-picker-actions">
        <NButton size="small" @click="clear">{{ t('common.clear') }}</NButton>
        <NButton size="small" @click="seed(nowInZone(props.zoneId))">{{ t('common.now') }}</NButton>
        <NButton size="small" type="primary" :disabled="dayValue === null" @click="confirm">{{
          t('common.confirm')
        }}</NButton>
      </div>
    </div>
  </NPopover>
</template>
<style scoped>
.utc-picker {
  width: 310px;
  max-width: calc(100vw - 24px);
  max-height: calc(100dvh - 24px);
  overflow: auto;
  color: var(--text);
  background: var(--overlay);
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 8px 28px rgb(0 0 0 / 15%);
  font-size: 13px;
}
.utc-picker-heading,
.utc-picker-inputs,
.utc-picker-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
}
.utc-picker-heading {
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
}
.utc-picker-heading span {
  color: var(--muted);
  font-size: 12px;
}
.utc-picker-inputs {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  border-bottom: 1px solid var(--border);
}
.utc-picker-inputs > * {
  min-width: 0;
}
.utc-picker-actions {
  justify-content: flex-end;
  border-top: 1px solid var(--border);
}
</style>
