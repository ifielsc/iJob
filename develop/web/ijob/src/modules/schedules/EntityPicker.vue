<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NSelect, NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { pageData, record } from '../../shared/api/values'
import { id as exact } from './model'
import { useCalendarName } from './use-calendar-name'
import RequestError from '../../shared/ui/RequestError.vue'
const props = defineProps<{
  id: string
  label: string
  endpoint: 'tasks' | 'jobs' | 'calendars'
  selectedName?: string
  selectedId?: string
  invalid?: boolean
  disabled?: boolean
}>()
const value = defineModel<string>({ required: true }),
  emit = defineEmits<{ show: [boolean] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  search = ref(''),
  page = ref(1),
  show = ref(false)
const calendarName = useCalendarName(() => (props.endpoint === 'calendars' ? value.value : ''))
const known = ref<{ value: string; label: string }[]>([])
watch(
  () => props.endpoint,
  () => {
    clearTimeout(searchTimer)
    search.value = ''
    page.value = 1
    known.value = []
  },
)
const query = useQuery(
  computed(() => ({
    queryKey: [
      'schedule-options',
      runtime.session.revision,
      runtime.preferences.locale,
      props.endpoint,
      search.value,
      page.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      pageData(
        await runtime.request(
          '/api/' +
            props.endpoint +
            '?' +
            new URLSearchParams({
              pageNum: String(page.value),
              pageSize: '20',
              ...(search.value ? { name: search.value } : {}),
            }),
          { signal, lossless: true },
        ),
        (v) => {
          const r = record(v)
          return {
            value: exact(r.id),
            label: String(r.name ?? r.code ?? '') + ' · #' + exact(r.id),
          }
        },
      ),
    enabled: show.value && !runtime.accessIssue.value,
    retry: false,
  })),
)
watch(
  () => query.data.value,
  (data) => {
    if (data)
      known.value =
        page.value === 1
          ? data.list
          : [...new Map([...known.value, ...data.list].map((v) => [v.value, v])).values()]
  },
)
const options = computed(() =>
  value.value && !known.value.some((v) => v.value === value.value)
    ? [
        {
          value: value.value,
          label:
            (props.selectedId === value.value && props.selectedName
              ? props.selectedName + ' · '
              : calendarName.data.value
                ? calendarName.data.value + ' · '
                : '') +
            '#' +
            value.value,
        },
        ...known.value,
      ]
    : known.value,
)
let searchTimer: ReturnType<typeof setTimeout> | undefined
onBeforeUnmount(() => clearTimeout(searchTimer))
function changeSearch(text: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    search.value = text
    page.value = 1
    known.value = []
  }, 250)
}
function updateShow(value: boolean) {
  show.value = value
  emit('show', value)
}
function moreOptions() {
  page.value++
  updateShow(true)
}
</script>
<template>
  <div>
    <NSelect
      :value="value || null"
      filterable
      remote
      clearable
      :options="options"
      :loading="query.isFetching.value"
      :disabled="disabled"
      :input-props="{ id, 'aria-label': label, 'aria-invalid': invalid || undefined }"
      :show="show"
      @search="changeSearch"
      @update:value="value = $event ?? ''"
      @update:show="updateShow"
    />
    <RequestError :error="calendarName.error.value" />
    <NButton v-if="calendarName.isError.value" text @click="calendarName.refetch()">{{
      t('schedules.retryCalendarName')
    }}</NButton>
    <RequestError :error="query.error.value" />
    <NButton v-if="query.isError.value" text @click="query.refetch()">{{
      t('schedules.retryOptions')
    }}</NButton>
    <NButton
      v-else-if="query.data.value && page * 20 < query.data.value.total"
      text
      :disabled="query.isFetching.value"
      @click="moreOptions"
      >{{ t('schedules.nextOptions') }}</NButton
    >
  </div>
</template>
