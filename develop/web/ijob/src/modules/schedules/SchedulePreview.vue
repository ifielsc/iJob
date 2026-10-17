<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NInputNumber } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import DateTimePicker from '../../shared/ui/DateTimePicker.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import { nowInZone, wallTime } from '../../shared/time/wall-time'
import { issues, previewBody, previewRows, type Draft, type Metadata } from './model'
const props = defineProps<{ draft: Draft; metadata: Metadata; disabled?: boolean }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  from = ref(nowInZone(props.metadata.zoneId)),
  count = ref<number | null>(10),
  busy = ref(false),
  error = shallowRef<unknown>(),
  validation = ref(''),
  rows = ref<ReturnType<typeof previewRows>>(),
  stale = ref(false)
let controller: AbortController | undefined
const signature = computed(() =>
  JSON.stringify([
    props.draft.scheduleType,
    props.draft.cronExpr,
    props.draft.intervalSec,
    props.draft.calendarId,
    props.draft.startTime,
    props.draft.endTime,
    from.value,
    count.value,
    props.metadata.zoneId,
  ]),
)
watch(signature, () => {
  controller?.abort()
  busy.value = false
  stale.value = !!rows.value
  rows.value = undefined
  error.value = undefined
  validation.value = ''
})
async function preview() {
  if (busy.value || props.disabled || runtime.accessIssue.value) return
  validation.value = ''
  error.value = undefined
  const errors = issues(props.draft, props.metadata, true)
  try {
    wallTime(from.value)
  } catch {
    errors.fromTime = 'invalidTime'
  }
  if (
    !Number.isInteger(count.value) ||
    !count.value ||
    count.value < 1 ||
    count.value > props.metadata.maxPreviewCount
  )
    errors.count = 'invalidNumber'
  if (Object.keys(errors).length) {
    validation.value = Object.entries(errors)
      .map(([k, v]) => t('schedules.' + k) + ': ' + t('schedules.' + v))
      .join('；')
    return
  }
  controller?.abort()
  const current = new AbortController()
  controller = current
  busy.value = true
  rows.value = undefined
  try {
    const result = previewRows(
      await runtime.request('/api/schedules/preview', {
        method: 'POST',
        body: { ...previewBody(props.draft), fromTime: wallTime(from.value), count: count.value },
        signal: current.signal,
      }),
    )
    if (!current.signal.aborted) {
      rows.value = result
      stale.value = false
    }
  } catch (e) {
    if (!current.signal.aborted) error.value = e
  } finally {
    if (controller === current) busy.value = false
  }
}
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <section class="schedule-preview" :aria-label="t('schedules.preview')">
    <h3>{{ t('schedules.preview') }}</h3>
    <p class="schedule-note">{{ t('schedules.previewHint') }}</p>
    <div class="schedule-preview-fields">
      <div>
        <label for="schedule-preview-from">{{ t('schedules.fromTime') }}</label
        ><DateTimePicker
          id="schedule-preview-from"
          v-model="from"
          :zone-id="metadata.zoneId"
          :label="t('schedules.fromTime')"
        />
      </div>
      <div>
        <label for="schedule-preview-count">{{ t('schedules.count') }}</label
        ><NInputNumber
          v-model:value="count"
          :min="1"
          :max="metadata.maxPreviewCount"
          :precision="0"
          :input-props="{ id: 'schedule-preview-count', 'aria-label': t('schedules.count') }"
        />
      </div>
      <NButton :loading="busy" :disabled="disabled" @click="preview">{{
        t('schedules.compute')
      }}</NButton>
    </div>
    <p v-if="validation" class="schedule-error" role="alert">{{ validation }}</p>
    <RequestError :error="error" />
    <p v-if="stale" class="schedule-note" role="status">{{ t('schedules.stalePreview') }}</p>
    <div
      v-if="rows"
      class="schedule-table-scroll"
      :class="{ 'bounded-data-scroll': rows.length > 6 }"
    >
      <table class="schedule-preview-table">
        <thead>
          <tr>
            <th>{{ t('schedules.fireTime') }} ({{ metadata.zoneId }})</th>
            <th>{{ t('schedules.calendarAllowed') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in rows" :key="i">
            <td>{{ row.fireTime.replace('T', ' ') }}</td>
            <td :class="row.calendarAllowed ? 'schedule-allowed' : 'schedule-error'">
              {{ t(row.calendarAllowed ? 'schedules.allowed' : 'schedules.excluded') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <EmptyState v-if="rows && !rows.length" compact :message="t('schedules.noCandidates')" />
    <p
      v-if="draft.scheduleType === 'ONCE' && rows?.[0]?.calendarAllowed === false"
      class="schedule-note"
    >
      {{ t('schedules.onceExcluded') }}
    </p>
  </section>
</template>
