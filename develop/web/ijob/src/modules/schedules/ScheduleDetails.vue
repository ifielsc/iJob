<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { NDrawer, NDrawerContent, NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { formatUtc } from '../../shared/time/format'
import SchedulePreview from './SchedulePreview.vue'
import { useCalendarName } from './use-calendar-name'
import RequestError from '../../shared/ui/RequestError.vue'
import { draftOf, type Schedule, type Metadata } from './model'
const props = defineProps<{ row: Schedule; metadata?: Metadata }>()
const calendarName = useCalendarName(() => props.row.calendarId)
defineEmits<{ close: [] }>()
const { t } = useI18n()
const runtime = useRuntime()
const fields = [
  'targetType',
  'targetName',
  'targetId',
  'scheduleType',
  'cronExpr',
  'intervalSec',
  'calendarId',
  'enabled',
  'startTime',
  'endTime',
  'lastFireTime',
  'nextFireTime',
  'misfirePolicy',
  'misfireGraceSec',
  'maxCatchupCount',
  'maxQueueDelaySec',
  'createTime',
  'updateTime',
] as const
function value(row: Schedule, key: (typeof fields)[number]) {
  const v = row[key]
  if ((key === 'createTime' || key === 'updateTime') && typeof v === 'string' && v)
    return formatUtc(v, runtime.preferences.locale, runtime.preferences.displayTimezone)
  if (key === 'calendarId' && v)
    return (calendarName.data.value || t('schedules.nameUnavailable')) + ' · #' + v
  if (key === 'targetName') return v || t('schedules.nameUnavailable')
  if (key === 'startTime' && row.scheduleType !== 'ONCE' && !v) return t('schedules.noStartBound')
  if (key === 'enabled') return t(v ? 'schedules.on' : 'schedules.off')
  if (['targetType', 'scheduleType', 'misfirePolicy'].includes(key))
    return v ? t('schedules.' + v) : t('schedules.inherit')
  return v === null || v === '' ? '—' : String(v).replace('T', ' ')
}
</script>
<template>
  <NDrawer show width="min(520px, 100vw)" class="schedule-drawer" @update:show="$emit('close')"
    ><NDrawerContent
      :title="t('schedules.detail') + ' #' + row.id"
      closable
      :native-scrollbar="true"
    >
      <p class="schedule-note">{{ t('schedules.zone') }}: {{ metadata?.zoneId ?? '—' }}</p>
      <p v-if="!metadata" class="schedule-error">{{ t('schedules.zoneError') }}</p>
      <dl class="schedule-meta">
        <template v-for="key in fields" :key="key"
          ><dt>
            {{
              t(
                row.scheduleType === 'ONCE' && key === 'startTime'
                  ? 'schedules.onceTime'
                  : 'schedules.' + key,
              )
            }}
          </dt>
          <dd>{{ value(row, key) }}</dd></template
        >
      </dl>
      <RequestError :error="calendarName.error.value" />
      <NButton v-if="calendarName.isError.value" text @click="calendarName.refetch()">{{
        t('schedules.retryCalendarName')
      }}</NButton>
      <h3>{{ t('schedules.variablesText') }}</h3>
      <pre v-if="row.variablesText !== undefined" class="schedule-code">{{
        row.variablesText
      }}</pre>
      <p v-else class="schedule-note">{{ t('schedules.noVariables') }}</p>
      <SchedulePreview v-if="metadata" :draft="draftOf(row)" :metadata="metadata" />
      <template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent></NDrawer
  >
</template>
