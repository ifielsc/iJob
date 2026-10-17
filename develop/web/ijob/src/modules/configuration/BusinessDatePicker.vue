<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NDatePicker, NInput, NPopover } from 'naive-ui'
import { CalendarDays } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { calendarCoordinate, calendarDay } from '../../shared/time/utc-picker'
import { validDate } from './contracts'
const props = defineProps<{
  id: string
  label: string
  invalid?: boolean
  disabled?: boolean
  borderless?: boolean
  describedby?: string
}>()
const value = defineModel<string>({ required: true })
const { t } = useI18n(),
  show = ref(false),
  draft = ref('')
const coordinate = computed(() => (validDate(draft.value) ? calendarCoordinate(draft.value) : null))
function open() {
  if (props.disabled) return
  draft.value = validDate(value.value) ? value.value : calendarDay(Date.now())
  show.value = true
}
function keydown(event: KeyboardEvent) {
  if (event.altKey && event.key === 'ArrowDown') {
    event.preventDefault()
    open()
  }
  if (event.key === 'Escape' && show.value) {
    event.stopPropagation()
    event.preventDefault()
    show.value = false
  }
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
    <template #trigger
      ><NInput
        v-model:value="value"
        :bordered="!borderless"
        :status="invalid ? 'error' : undefined"
        :disabled="disabled"
        placeholder="yyyy-MM-dd"
        :input-props="{
          id,
          'aria-label': label,
          'aria-describedby': describedby,
          'aria-invalid': invalid || undefined,
          autocomplete: 'off',
        }"
        @keydown="keydown"
        ><template #suffix
          ><NButton
            size="tiny"
            quaternary
            :disabled="disabled"
            :aria-label="label + ' · ' + t('config.date')"
            @click="show ? (show = false) : open()"
            ><CalendarDays :size="16" /></NButton></template></NInput
    ></template>
    <div
      class="business-date-panel"
      role="dialog"
      :aria-label="label"
      @keydown.esc.stop.prevent="show = false"
    >
      <NDatePicker
        panel
        type="date"
        :value="coordinate"
        :actions="[]"
        :close-on-select="false"
        @update:value="
          (v: number | null) => {
            if (v !== null) draft = calendarDay(v)
          }
        "
      />
      <div class="configuration-footer">
        <NButton
          size="small"
          @click="
            () => {
              value = ''
              show = false
            }
          "
          >{{ t('common.clear') }}</NButton
        ><NButton
          size="small"
          type="primary"
          @click="
            () => {
              value = draft
              show = false
            }
          "
          >{{ t('common.confirm') }}</NButton
        >
      </div>
    </div>
  </NPopover>
</template>
<style scoped>
.business-date-panel {
  width: 310px;
  max-width: calc(100vw - 24px);
  max-height: calc(100dvh - 24px);
  overflow: auto;
  padding: 10px;
  background: var(--panel);
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 10px;
}
</style>
