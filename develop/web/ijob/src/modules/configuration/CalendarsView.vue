<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import { computed, h, nextTick, ref, shallowRef, watch } from 'vue'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { NButton, NInput, NInputNumber, NTag, useDialog, useMessage } from 'naive-ui'
import { Search, ChevronRight, Plus, RefreshCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import RequestError from '../../shared/ui/RequestError.vue'
import { parseCalendar, parsePreview, validDate, weekdays } from './contracts'
import type { CalendarRow } from './contracts'
import { parseCalendarContext, calendarCells } from './calendar-data'
import { parsePage } from '../security-contracts'
import CalendarEditor from './CalendarEditor.vue'
import BusinessDatePicker from './BusinessDatePicker.vue'
import { describeError } from '../../shared/api/errors'
import './configuration.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const listOpen = ref(true),
  calendarLimit = ref(100),
  calendarTotal = ref(0)
function selectCalendar(id: string) {
  selectedId.value = id
  listOpen.value = false
}
const name = ref(''),
  searchName = ref(''),
  selectedId = ref(''),
  editing = ref(false),
  selected = shallowRef<CalendarRow>()
const from = ref(''),
  days = ref<number | null>(14),
  range = ref({ from: '', days: 14 }),
  invalid = ref(false),
  busy = ref(false)
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value),
  allowed = computed(() => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value)
const context = useQuery(
  computed(() => ({
    queryKey: ['calendar-context', runtime.session.revision],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseCalendarContext(await runtime.request('/api/calendars/context', { signal })),
    enabled: allowed.value,
  })),
)
watch(
  () => context.data.value,
  (value) => {
    if (value && !range.value.from) {
      from.value = value.today
      range.value = { from: value.today, days: 14 }
    }
  },
  { immediate: true },
)
const query = useQuery(
  computed(() => ({
    staleTime: 0,
    placeholderData: keepPreviousData,
    queryKey: [
      'calendars-all',
      runtime.session.revision,
      runtime.preferences.locale,
      searchName.value,
      calendarLimit.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const rows: CalendarRow[] = []
      for (let page = 1; rows.length < calendarLimit.value; page++) {
        const result = parsePage(
          await runtime.request(
            '/api/calendars?' +
              new URLSearchParams({
                name: searchName.value,
                pageNum: String(page),
                pageSize: '100',
              }),
            { signal },
          ),
          (v) => parseCalendar(v),
        )
        calendarTotal.value = result.total
        rows.push(...result.list)
        if (!result.list.length || rows.length >= result.total) break
      }
      return rows
    },
    enabled: allowed.value,
  })),
)
watch(
  () => query.data.value,
  (rows) => {
    if (rows && (!selectedId.value || !rows.some((row) => row.id === selectedId.value)))
      selectedId.value = rows[0]?.id ?? ''
  },
  { immediate: true },
)
watch(selectedId, async () => {
  await nextTick()
  const selected = document.querySelector<HTMLElement>('.calendar-choice.selected')
  const list = selected?.parentElement
  if (
    selected &&
    list &&
    (selected.offsetTop < list.scrollTop ||
      selected.offsetTop + selected.offsetHeight > list.scrollTop + list.clientHeight)
  )
    list.scrollTop = selected.offsetTop - list.offsetTop
})
const detail = useQuery(
  computed(() => ({
    queryKey: [
      'calendar-detail',
      runtime.session.revision,
      runtime.preferences.locale,
      selectedId.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseCalendar(await runtime.request('/api/calendars/' + selectedId.value, { signal }), true),
    enabled: allowed.value && !!selectedId.value,
  })),
)
const preview = useQuery(
  computed(() => ({
    queryKey: [
      'calendar-preview',
      runtime.session.revision,
      runtime.preferences.locale,
      selectedId.value,
      range.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parsePreview(
        await runtime.request(
          '/api/calendars/' +
            selectedId.value +
            '/preview?' +
            new URLSearchParams({ days: String(range.value.days), from: range.value.from }),
          { signal },
        ),
      ),
    enabled: allowed.value && !!selectedId.value && detail.isSuccess.value && !!range.value.from,
  })),
)
const cells = computed(() => calendarCells(preview.data.value ?? []))
let returnFocus: HTMLElement | null = null
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) {
      editing.value = false
      selected.value = undefined
      selectedId.value = ''
    }
  },
)
function open(row?: CalendarRow, event?: MouseEvent) {
  returnFocus = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  selected.value = row ? structuredClone(JSON.parse(JSON.stringify(row))) : undefined
  editing.value = true
}
function resetSearch() {
  name.value = ''
  void search()
}
async function search() {
  if (query.isFetching.value) return
  const previous = searchName.value
  calendarLimit.value = 100
  searchName.value = name.value.trim()
  if (previous === searchName.value) await query.refetch()
}
async function applyRange() {
  invalid.value =
    !validDate(from.value) ||
    !Number.isInteger(days.value) ||
    Number(days.value) < 1 ||
    Number(days.value) > 366
  if (invalid.value) {
    await nextTick()
    document.getElementById(!validDate(from.value) ? 'preview-from' : 'preview-days')?.focus()
    return
  }
  range.value = { from: from.value, days: Number(days.value) }
  await nextTick()
  void preview.refetch()
}
async function closeEditor() {
  editing.value = false
  selected.value = undefined
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
async function refresh() {
  await Promise.all([query.refetch(), context.refetch()])
  if (selectedId.value && allowed.value) {
    await detail.refetch()
    if (range.value.from) await preview.refetch()
  }
}
async function saved(id: string) {
  await closeEditor()
  message.success(() => h('span', { role: 'status' }, t('config.saved')), { duration: 3000 })
  await query.refetch()
  if (query.data.value?.some((row) => row.id === id)) selectedId.value = id
  await nextTick()
  if (selectedId.value && allowed.value) {
    await detail.refetch()
    if (range.value.from) await preview.refetch()
  }
}
function remove() {
  const row = detail.data.value
  if (!row || busy.value) return
  dialog.warning({
    title: t('config.delete'),
    content: t('config.deleteHint', { name: row.name }),
    positiveText: t('config.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      busy.value = true
      try {
        await runtime.request('/api/calendars/' + row.id, { method: 'DELETE' })
        message.success(() => h('span', { role: 'status' }, t('config.deleted')), {
          duration: 3000,
        })
        await query.refetch()
      } catch (e) {
        message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
      } finally {
        busy.value = false
      }
    },
  })
}
</script>
<template>
  <div class="page-heading list-heading">
    <div>
      <h1>{{ t('config.titleCalendars') }}</h1>
      <p class="subtitle">{{ t('config.calendarSubtitle') }}</p>
    </div>
    <NButton
      v-if="writer"
      class="calendar-create list-create-action"
      type="primary"
      :disabled="busy || !context.data.value"
      @click="open(undefined, $event)"
      ><template #icon><Plus :size="16" /></template>{{ t('config.createCalendar') }}</NButton
    >
  </div>
  <div v-if="!runtime.accessIssue.value" class="calendar-layout">
    <section class="panel glass calendar-sidebar list-panel" :class="{ 'is-collapsed': !listOpen }">
      <NButton
        class="calendar-mobile-toggle"
        :aria-expanded="listOpen"
        @click="listOpen = !listOpen"
        >{{ t('experience.calendarToggle') }} · {{ detail.data.value?.name }}</NButton
      >
      <h2>{{ t('config.calendarList') }}</h2>
      <form class="calendar-search" @submit.prevent="search">
        <NInput
          v-model:value="name"
          :placeholder="t('config.searchCalendar')"
          :input-props="{ id: 'calendar-filter', 'aria-label': t('config.searchCalendar') }"
        >
          <template #suffix
            ><NButton
              attr-type="submit"
              quaternary
              size="tiny"
              :aria-label="t('config.search')"
              :loading="query.isFetching.value"
              ><Search :size="16" /></NButton
          ></template>
        </NInput>
      </form>
      <div class="calendar-list-toolbar">
        <NButton class="list-query-action" size="small" :disabled="busy" @click="resetSearch">{{
          t('config.resetFilters')
        }}</NButton>
        <NButton
          class="list-query-action"
          size="small"
          :disabled="busy"
          :loading="query.isFetching.value"
          :title="t('config.refreshCalendar')"
          :aria-label="t('config.refreshCalendar')"
          @click="refresh"
        >
          <template #icon><RefreshCw :size="14" /></template>{{ t('common.refresh') }}
        </NButton>
      </div>
      <RequestError :error="context.error.value" /><NButton
        v-if="context.isError.value"
        @click="context.refetch()"
        >{{ t('common.retry') }}</NButton
      >
      <RequestError :error="query.error.value" /><NButton
        v-if="query.isError.value"
        @click="query.refetch()"
        >{{ t('common.retry') }}</NButton
      >
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <div v-if="query.data.value && !query.isError.value" class="calendar-choices">
        <button
          v-for="row in query.data.value"
          :key="row.id"
          type="button"
          class="calendar-choice"
          :class="{ selected: selectedId === row.id }"
          :aria-label="row.name"
          :title="[row.name, row.description].filter(Boolean).join(' — ')"
          :aria-pressed="selectedId === row.id"
          @click="selectCalendar(row.id)"
        >
          <span
            ><strong>{{ row.name }}</strong
            ><small>{{
              row.description || row.workdays.map((day) => t('config.' + day)).join(' · ')
            }}</small></span
          ><ChevronRight :size="16" aria-hidden="true" />
        </button>
        <NButton
          v-if="query.data.value.length < calendarTotal"
          :loading="query.isFetching.value"
          @click="calendarLimit += 100"
          >{{ t('experience.more') }} ({{ query.data.value.length }}/{{ calendarTotal }})</NButton
        >
        <EmptyState v-if="!query.data.value.length" :message="t('common.empty')" />
      </div>
    </section>
    <section class="panel glass calendar-detail list-panel">
      <p v-if="!selectedId" class="empty-state">{{ t('config.noCalendar') }}</p>
      <template v-else>
        <RequestError :error="detail.error.value" /><NButton
          v-if="detail.isError.value"
          @click="detail.refetch()"
          >{{ t('common.retry') }}</NButton
        >
        <p v-if="detail.isPending.value">{{ t('common.loading') }}</p>
        <template v-if="detail.data.value && !detail.isError.value">
          <div class="section-heading calendar-detail-heading">
            <div class="calendar-title">
              <h2>{{ detail.data.value.name }}</h2>
              <NTag size="small" type="success" :bordered="false">{{
                t('config.savedRules')
              }}</NTag>
            </div>
            <div class="configuration-actions">
              <NButton
                v-if="writer"
                size="small"
                secondary
                type="primary"
                :disabled="busy || !context.data.value"
                @click="open(detail.data.value, $event)"
                >{{ t('common.edit') }}</NButton
              ><NButton
                v-if="writer"
                size="small"
                text
                type="error"
                :disabled="busy"
                @click="remove"
                >{{ t('config.delete') }}</NButton
              >
            </div>
          </div>
          <p v-if="detail.data.value.description" class="configuration-note">
            {{ detail.data.value.description }}
          </p>
          <div class="calendar-week-rule">
            <h3>{{ t('config.workdays') }}</h3>
            <div class="calendar-days">
              <span
                v-for="day in weekdays"
                :key="day"
                class="calendar-week-chip"
                :class="{ active: detail.data.value.workdays.includes(day) }"
                >{{ t('config.' + day) }}</span
              >
            </div>
          </div>
          <h3>{{ t('config.exceptions') }}</h3>
          <EmptyState
            v-if="!detail.data.value.exceptions?.length"
            :message="t('config.noExceptions')"
          />
          <div v-else class="table-scroll calendar-exceptions">
            <table>
              <thead>
                <tr>
                  <th>{{ t('config.date') }}</th>
                  <th>{{ t('config.type') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in detail.data.value.exceptions" :key="entry.date">
                  <td>{{ entry.date }}</td>
                  <td>{{ t(entry.type === 'WORKDAY' ? 'config.workday' : 'config.holiday') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <details class="unified-technical">
            <summary>{{ t('unified.technical') }}</summary>
            <IdentityText :value="detail.data.value.id" />
          </details>
          <div class="calendar-preview-section">
            <h3>{{ t('config.preview') }}</h3>
            <form class="calendar-preview-toolbar" novalidate @submit.prevent="applyRange">
              <div class="calendar-preview-start">
                <BusinessDatePicker
                  id="preview-from"
                  v-model="from"
                  :label="t('config.fromDate')"
                  :invalid="invalid"
                  :describedby="invalid ? 'calendar-preview-error' : undefined"
                />
              </div>
              <NInputNumber
                v-model:value="days"
                class="calendar-preview-days"
                :min="1"
                :max="366"
                :precision="0"
                :input-props="{ id: 'preview-days', 'aria-label': t('config.days') }"
                ><template #suffix>{{ t('config.dayUnit') }}</template></NInputNumber
              >
              <NButton
                class="list-query-action"
                attr-type="submit"
                secondary
                type="primary"
                :loading="preview.isFetching.value"
                >{{ t('config.search') }}</NButton
              >
              <span class="configuration-note calendar-preview-hint"
                >{{ t('config.ruleHint')
                }}<span v-if="context.data.value"> · {{ context.data.value.timezone }}</span></span
              >
            </form>
            <p
              v-if="invalid"
              id="calendar-preview-error"
              role="alert"
              class="configuration-field-error"
            >
              {{ t('config.dateRangeError') }}
            </p>
            <RequestError :error="preview.error.value" /><NButton
              v-if="preview.isError.value"
              @click="preview.refetch()"
              >{{ t('common.retry') }}</NButton
            >
            <div
              v-if="preview.data.value && !preview.isError.value"
              class="calendar-grid-scroll"
              :aria-label="t('config.preview')"
              tabindex="0"
            >
              <div class="calendar-preview" role="table" :aria-label="t('config.preview')">
                <div class="calendar-week-head" role="row">
                  <div v-for="day in weekdays" :key="day" role="columnheader">
                    {{ t('config.' + day) }}
                  </div>
                </div>
                <div
                  v-for="(_, week) in Math.ceil(cells.length / 7)"
                  :key="week"
                  class="calendar-week-row"
                  role="row"
                >
                  <div
                    v-for="(day, index) in cells.slice(week * 7, week * 7 + 7)"
                    :key="day?.date ?? index"
                    class="calendar-day"
                    :class="{ blank: !day }"
                    role="cell"
                    :aria-label="day?.date"
                  >
                    <template v-if="day"
                      ><time :datetime="day.date">{{ day.date.slice(5) }}</time
                      ><span :class="day.allowed ? 'status-success' : 'calendar-rest'">
                        {{ t(day.allowed ? 'config.allowed' : 'config.rest') }}</span
                      ><small v-if="day.exceptionType" class="calendar-exception-badge">{{
                        t('config.exception')
                      }}</small></template
                    >
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </template>
    </section>
  </div>
  <CalendarEditor
    v-if="editing && !runtime.accessIssue.value && context.data.value"
    :row="selected"
    :today="context.data.value.today"
    @close="closeEditor"
    @saved="saved"
  />
</template>
