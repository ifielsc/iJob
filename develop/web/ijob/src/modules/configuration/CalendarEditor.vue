<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { randomUuid } from '../../shared/runtime/uuid'
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, h, nextTick, reactive, ref } from 'vue'
import {
  NButton,
  NCheckbox,
  NCheckboxGroup,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  useMessage,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import { Plus } from '@lucide/vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import BusinessDatePicker from './BusinessDatePicker.vue'
import { id, validDate, weekdays } from './contracts'
import type { CalendarRow, CalendarException } from './contracts'
import { useEditor } from '../../shared/forms/use-editor'
import { ApiError, describeError, isWriteOutcomeUnknown } from '../../shared/api/errors'
import { addExceptionRange } from './calendar-exceptions'
const props = defineProps<{ row?: CalendarRow; today: string }>(),
  emit = defineEmits<{ close: []; saved: [string] }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  message = useMessage(),
  form = ref<HTMLElement | null>(null)
const draft = reactive({
  name: props.row?.name ?? '',
  description: props.row?.description ?? '',
  workdays: [...(props.row?.workdays ?? weekdays.slice(0, 5))] as string[],
  exceptions: (props.row?.exceptions ?? []).map((e) => ({ ...e, key: randomUuid() })),
})
const rangeDraft = reactive({
  from: props.today,
  to: props.today,
  type: 'HOLIDAY' as CalendarException['type'],
})
const batchOpen = ref(false),
  batchError = ref(''),
  batchSummary = ref<{ added: number; skipped: number }>()
const closing = ref(false)
const uncertain = ref(false)
const { changed, busy, error, submitted, close, focusError } = useEditor(
  () => ({ draft, rangeDraft }),
  () => emit('close'),
)
async function requestClose() {
  if (closing.value || busy.value) return
  closing.value = true
  try {
    await close()
  } finally {
    closing.value = false
  }
}
const invalid = ref(false)
const exceptionPage = ref(1),
  exceptionSize = ref(20)
const visibleExceptions = computed(() =>
  draft.exceptions
    .map((entry, index) => ({ entry, index }))
    .slice(
      (exceptionPage.value - 1) * exceptionSize.value,
      exceptionPage.value * exceptionSize.value,
    ),
)
function invalidDate(index: number) {
  const entry = draft.exceptions[index]!
  return (
    !validDate(entry.date) ||
    draft.exceptions.some((other, i) => i !== index && other.date === entry.date)
  )
}
function removeDate(index: number) {
  if (busy.value) return
  draft.exceptions.splice(index, 1)
  exceptionPage.value = Math.min(
    exceptionPage.value,
    Math.max(1, Math.ceil(draft.exceptions.length / exceptionSize.value)),
  )
}
const options = computed(() => [
  { label: t('config.holiday'), value: 'HOLIDAY' },
  { label: t('config.workday'), value: 'WORKDAY' },
])
async function submit() {
  if (props.row && !changed.value) return
  if (busy.value || uncertain.value || runtime.accessIssue.value) return
  submitted.value = true
  error.value = null
  const missing =
    !draft.name.trim() || !draft.workdays.length || draft.exceptions.some((e) => !e.date)
  invalid.value =
    !missing &&
    (draft.exceptions.some((e) => !validDate(e.date)) ||
      new Set(draft.exceptions.map((e) => e.date)).size !== draft.exceptions.length)
  if (missing || invalid.value) {
    const index = draft.exceptions.findIndex((_, i) => invalidDate(i))
    if (index >= 0) exceptionPage.value = Math.floor(index / exceptionSize.value) + 1
    await nextTick()
    focusError(form.value)
    return
  }
  busy.value = true
  try {
    const result = await runtime.request('/api/calendars' + (props.row ? '/' + props.row.id : ''), {
      method: props.row ? 'PUT' : 'POST',
      body: {
        name: draft.name.trim(),
        description: draft.description,
        workdays: draft.workdays,
        exceptions: draft.exceptions.map(({ date, type }) => ({ date, type })),
      },
    })
    emit('saved', props.row?.id ?? id(result))
  } catch (e) {
    if (isWriteOutcomeUnknown(e)) uncertain.value = true
    if (e instanceof ApiError && e.kind === 'conflict') error.value = e
    else message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
  } finally {
    busy.value = false
  }
}
async function focusDate(index: number) {
  await nextTick()
  const key = draft.exceptions[index]?.key
  if (key) form.value?.querySelector<HTMLInputElement>('#date-' + key)?.focus()
}
function add() {
  if (!busy.value && draft.exceptions.length < 2000) {
    draft.exceptions.push({ date: props.today, type: 'HOLIDAY', key: randomUuid() })
    exceptionPage.value = Math.ceil(draft.exceptions.length / exceptionSize.value)
    void focusDate(draft.exceptions.length - 1)
  }
}
async function addRange() {
  if (busy.value) return
  batchError.value = ''
  batchSummary.value = undefined
  try {
    const { added, skipped } = addExceptionRange(
      draft.exceptions,
      rangeDraft.from,
      rangeDraft.to,
      rangeDraft.type,
    )
    const firstNew = draft.exceptions.length
    draft.exceptions.push(...added.map((entry) => ({ ...entry, key: randomUuid() })))
    batchSummary.value = { added: added.length, skipped }
    if (added.length) {
      exceptionPage.value = Math.floor(firstNew / exceptionSize.value) + 1
      void focusDate(firstNew)
    }
  } catch (e) {
    batchError.value =
      e instanceof Error && e.message === 'limit'
        ? 'config.exceptionLimit'
        : 'config.batchRangeError'
    await nextTick()
    form.value?.querySelector<HTMLInputElement>('#exception-range-from')?.focus()
  }
}
</script>
<template>
  <NDrawer
    class="calendar-drawer editor-drawer"
    :show="true"
    width="min(640px, 100vw)"
    :mask-closable="false"
    :close-on-esc="true"
    @update:show="
      (show) => {
        if (!show) requestClose()
      }
    "
    ><NDrawerContent
      :title="t(row ? 'config.rules' : 'config.createCalendar')"
      :native-scrollbar="true"
    >
      <template #header>
        <div class="calendar-editor-title">
          <h2>{{ t(row ? 'config.rules' : 'config.createCalendar') }}</h2>
          <DrawerCloseButton class="calendar-editor-close" :disabled="busy" @close="requestClose" />
        </div>
      </template>
      <div ref="form" class="configuration-editor calendar-editor">
        <RequestError :error="error" />
        <p v-if="uncertain" role="alert">{{ t('eventBindings.unknown') }}</p>
        <p v-if="invalid" class="error-summary" role="alert">
          {{ t('config.exceptionDateError') }}
        </p>
        <NForm
          :disabled="busy || uncertain"
          :show-feedback="false"
          novalidate
          @submit.prevent="submit"
        >
          <FormItem
            id="calendar-name"
            :label="t('config.name')"
            required
            :missing="submitted && !draft.name.trim()"
            ><NInput
              v-model:value="draft.name"
              :maxlength="128"
              :input-props="{
                id: 'calendar-name',
                'aria-invalid': (submitted && !draft.name.trim()) || undefined,
              }"
          /></FormItem>
          <FormItem
            id="calendar-workdays"
            :label="t('config.workdays')"
            required
            :missing="submitted && !draft.workdays.length"
          >
            <NCheckboxGroup
              id="calendar-workdays"
              v-model:value="draft.workdays"
              tabindex="-1"
              :aria-label="t('config.workdays')"
              :aria-invalid="(submitted && !draft.workdays.length) || undefined"
              ><div class="calendar-days">
                <NCheckbox
                  v-for="day in weekdays"
                  :key="day"
                  :value="day"
                  :label="t('config.' + day)"
                /></div
            ></NCheckboxGroup>
          </FormItem>
          <p v-if="!row" class="configuration-note">{{ t('config.weekdays') }}</p>
          <FormItem id="calendar-description" :label="t('config.description')"
            ><NInput
              v-model:value="draft.description"
              type="textarea"
              :maxlength="500"
              :input-props="{ id: 'calendar-description' }"
          /></FormItem>
          <div class="calendar-exception-heading">
            <h3>{{ t('config.exceptions') }} ({{ draft.exceptions.length }} / 2000)</h3>
            <div class="configuration-actions">
              <NButton
                class="calendar-add-date"
                size="small"
                :disabled="busy || draft.exceptions.length >= 2000"
                @click="add"
              >
                <template #icon><Plus :size="14" /></template>{{ t('config.addDate') }}
              </NButton>
              <NButton
                size="small"
                :disabled="busy"
                :aria-expanded="batchOpen"
                aria-controls="calendar-batch-dates"
                @click="batchOpen = !batchOpen"
                >{{ t('config.batchDates') }}</NButton
              >
            </div>
          </div>
          <div v-if="batchOpen" id="calendar-batch-dates" class="calendar-batch-dates">
            <p class="configuration-note">{{ t('config.batchDatesHint') }}</p>
            <div class="calendar-batch-fields">
              <FormItem id="exception-range-from" :label="t('config.fromDate')" required>
                <BusinessDatePicker
                  id="exception-range-from"
                  v-model="rangeDraft.from"
                  :label="t('config.fromDate')"
                  :disabled="busy"
                  :invalid="!!batchError"
                />
              </FormItem>
              <FormItem id="exception-range-to" :label="t('config.toDate')" required>
                <BusinessDatePicker
                  id="exception-range-to"
                  v-model="rangeDraft.to"
                  :label="t('config.toDate')"
                  :disabled="busy"
                  :invalid="!!batchError"
                />
              </FormItem>
              <FormItem id="exception-range-type" :label="t('config.type')">
                <CompactSelect
                  id="exception-range-type"
                  :value="rangeDraft.type"
                  :label="t('config.type')"
                  :options="options"
                  :disabled="busy"
                  @update:value="rangeDraft.type = $event as CalendarException['type']"
                />
              </FormItem>
            </div>
            <p v-if="batchError" role="alert" class="configuration-field-error">
              {{ t(batchError) }}
            </p>
            <div class="calendar-batch-actions">
              <NButton size="small" :disabled="busy" @click="addRange">{{
                t('config.addToTable')
              }}</NButton>
              <span v-if="batchSummary" role="status" class="configuration-note">{{
                t('config.batchDatesAdded', batchSummary)
              }}</span>
            </div>
          </div>
          <div
            class="table-scroll calendar-exception-scroll"
            :class="{ 'bounded-data-scroll': visibleExceptions.length > 6 }"
          >
            <table class="calendar-exception-table">
              <thead>
                <tr>
                  <th scope="col">
                    {{ t('config.date') }}
                    <span class="calendar-required" aria-hidden="true">*</span>
                  </th>
                  <th scope="col">{{ t('config.type') }}</th>
                  <th scope="col" class="calendar-exception-action">{{ t('common.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="{ entry, index } in visibleExceptions"
                  :key="entry.key"
                  class="calendar-exception"
                >
                  <td>
                    <BusinessDatePicker
                      :id="'date-' + entry.key"
                      v-model="entry.date"
                      :label="t('config.date') + ' ' + (index + 1)"
                      :invalid="submitted && invalidDate(index)"
                      :disabled="busy"
                      :describedby="
                        submitted && invalidDate(index) ? 'date-error-' + entry.key : undefined
                      "
                    />
                    <p
                      v-if="submitted && invalidDate(index)"
                      :id="'date-error-' + entry.key"
                      class="configuration-field-error"
                      role="alert"
                    >
                      {{ entry.date ? t('config.exceptionDateError') : t('common.required') }}
                    </p>
                  </td>
                  <td>
                    <CompactSelect
                      :id="'type-' + entry.key"
                      :value="entry.type"
                      :label="t('config.type') + ' ' + (index + 1)"
                      :options="options"
                      :disabled="busy"
                      @update:value="entry.type = $event as CalendarException['type']"
                    />
                  </td>
                  <td class="calendar-exception-action">
                    <NButton
                      class="calendar-remove-date"
                      size="small"
                      text
                      type="error"
                      :disabled="busy"
                      :aria-label="t('config.removeDate', { index: index + 1 })"
                      @click="removeDate(index)"
                      >{{ t('config.delete') }}</NButton
                    >
                  </td>
                </tr>
                <tr v-if="!draft.exceptions.length">
                  <td colspan="3" class="list-empty-cell">
                    <EmptyState compact :message="t('config.noExceptions')" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <PageControls
            v-if="draft.exceptions.length > 20"
            :page="exceptionPage"
            :size="exceptionSize"
            :total="draft.exceptions.length"
            :busy="busy"
            @page="exceptionPage = $event"
            @size="
              (value) => {
                exceptionSize = value
                exceptionPage = 1
              }
            "
          />
        </NForm>
      </div>
      <template #footer
        ><div class="configuration-footer calendar-editor-footer">
          <NButton :disabled="busy" @click="requestClose">{{ t('common.cancel') }}</NButton
          ><NButton
            type="primary"
            :loading="busy"
            :disabled="busy || uncertain || (!!row && !changed)"
            @click="submit"
            >{{ t('config.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent></NDrawer
  >
</template>
