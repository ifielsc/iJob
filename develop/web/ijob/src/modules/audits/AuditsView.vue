<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { useTableOverflow } from '../../shared/ui/use-table-overflow'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NForm, NInput } from 'naive-ui'
import AdvancedFilter from '../../shared/ui/AdvancedFilter.vue'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { parseAudit, parsePage } from '../security-contracts'
import { formatUtc } from '../../shared/time/format'
import RequestError from '../../shared/ui/RequestError.vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import UtcDateTimePicker from '../../shared/ui/UtcDateTimePicker.vue'
import { useRequiredFields } from '../../shared/forms/required-fields'
import './audits.css'
import AuditDetail from './AuditDetail.vue'
import { normalizeRange, recentRange, resultCodes } from './filters'
const { t } = useI18n(),
  runtime = useRuntime(),
  { user } = useIdentity()
const draft = reactive({
  ...recentRange(),
  resultCode: '',
  actorLoginId: '',
  actionCode: '',
  targetType: '',
  targetId: '',
})
const applied = ref({ ...draft }),
  page = ref(1),
  size = ref(20),
  invalid = ref(false),
  selected = ref<string>()
const advanced = ref(false)
const sortDirection = ref<'asc' | 'desc'>('desc')
const advancedKeys = ['actorLoginId', 'actionCode', 'targetType', 'targetId'] as const
const appliedAdvancedCount = computed(
  () => advancedKeys.filter((key) => applied.value[key] !== '').length,
)
const tableScroll = ref<HTMLElement | null>(null)
const tableOverflow = useTableOverflow(tableScroll)
let returnFocus: HTMLElement | null = null
function openDetail(id: string, event: MouseEvent) {
  returnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  selected.value = id
}
async function closeDetail() {
  selected.value = undefined
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
function toggleSort() {
  page.value = 1
  sortDirection.value = sortDirection.value === 'desc' ? 'asc' : 'desc'
}
const fromInput = ref<InstanceType<typeof UtcDateTimePicker> | null>(null)
const toInput = ref<InstanceType<typeof UtcDateTimePicker> | null>(null)
const { missing, firstMissing, reset } = useRequiredFields({
  from: computed(() => draft.from),
  to: computed(() => draft.to),
})
const resultOptions = computed(() => [
  { value: '', label: t('common.all') },
  ...resultCodes.map((value) => ({ value, label: t(`status.${value}`) })),
])
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) selected.value = undefined
  },
)
const queryString = computed(() => {
  const values: Record<string, string> = {
    ...applied.value,
    sortBy: 'occurredAt',
    sortDirection: sortDirection.value,
    pageNum: String(page.value),
    pageSize: String(size.value),
  }
  return new URLSearchParams(Object.entries(values).filter(([, v]) => !!v)).toString()
})
const query = useQuery(
  computed(() => ({
    staleTime: 0,
    queryKey: ['audits', runtime.session.revision, runtime.preferences.locale, queryString.value],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parsePage(await runtime.request(`/api/audits?${queryString.value}`, { signal }), parseAudit),
    enabled: can(user.value, 'AUDIT_READ') && !runtime.accessIssue.value,
  })),
)
function search(resetPage = false) {
  if (query.isFetching.value || runtime.accessIssue.value) return
  invalid.value = false
  const first = firstMissing()
  if (first) {
    const input = first === 'from' ? fromInput.value : toInput.value
    input?.focus()
    return
  }
  try {
    const range = normalizeRange(draft.from, draft.to)
    invalid.value = false
    const previous = queryString.value
    const next = { ...draft, ...range }
    if (resetPage || JSON.stringify(next) !== JSON.stringify(applied.value)) page.value = 1
    applied.value = next
    if (queryString.value === previous) void query.refetch()
  } catch {
    invalid.value = true
    fromInput.value?.focus()
  }
}
function recent() {
  reset()
  sortDirection.value = 'desc'
  Object.assign(draft, recentRange(), {
    resultCode: '',
    actorLoginId: '',
    actionCode: '',
    targetType: '',
    targetId: '',
  })
  search(true)
}
function setSize(value: number) {
  size.value = value
  page.value = 1
}
const symbols: Record<string, string> = {
  SUCCESS: '✓',
  REJECTED: '⊘',
  FAILED: '×',
  ATTEMPTED: '◷',
  ACCEPTED: '→',
  REPLAYED: '↻',
}
</script>
<template>
  <div class="page-heading list-heading">
    <div>
      <h1>{{ t('audits.title') }}</h1>
      <p class="subtitle">{{ t('audits.subtitle') }}</p>
    </div>
  </div>
  <section class="panel glass data-panel list-panel">
    <NForm
      class="audit-filters list-filter-block"
      novalidate
      :show-feedback="false"
      label-placement="left"
      @submit.prevent="search()"
    >
      <div class="audit-filter-grid">
        <div class="audit-date-pair">
          <FormItem
            id="audit-from"
            class="audit-basic-filter"
            :label="t('audits.from')"
            required
            :missing="missing('from')"
            :invalid="invalid"
          >
            <UtcDateTimePicker
              id="audit-from"
              ref="fromInput"
              v-model="draft.from"
              :label="t('audits.from')"
              :invalid="missing('from') || invalid"
              :describedby="`audit-range-hint${missing('from') ? ' audit-from-required' : ''}${invalid ? ' audit-range-error' : ''}`"
            />
          </FormItem>
          <span class="audit-range-separator" aria-hidden="true">－</span
          ><FormItem
            id="audit-to"
            class="audit-basic-filter audit-range-end"
            :label="t('audits.to')"
            required
            :missing="missing('to')"
            :invalid="invalid"
          >
            <UtcDateTimePicker
              id="audit-to"
              ref="toInput"
              v-model="draft.to"
              :label="t('audits.to')"
              :invalid="missing('to') || invalid"
              :describedby="`audit-range-hint${missing('to') ? ' audit-to-required' : ''}${invalid ? ' audit-range-error' : ''}`"
            />
          </FormItem>
        </div>
        <FormItem id="audit-result" class="audit-basic-filter" :label="t('audits.resultCode')">
          <CompactSelect
            id="audit-result"
            :label="t('audits.resultCode')"
            :value="draft.resultCode"
            :options="resultOptions"
            @update:value="draft.resultCode = String($event)"
          />
        </FormItem>
        <div class="audit-filter-actions">
          <NButton
            class="list-query-action"
            attr-type="submit"
            type="primary"
            :loading="query.isFetching.value"
            >{{ t('audits.search') }}</NButton
          ><NButton class="list-query-action" @click="recent">{{ t('audits.reset') }}</NButton>
          <AdvancedFilter
            :expanded="advanced"
            :count="appliedAdvancedCount"
            :label="t('audits.advanced')"
            controls="audit-advanced"
            @toggle="advanced = !advanced"
          />
        </div>
      </div>
      <div class="audit-filter-meta">
        <p id="audit-range-hint">{{ t('audits.rangeHint') }}</p>
      </div>
      <div v-show="advanced" id="audit-advanced" class="audit-advanced-grid">
        <FormItem
          v-for="key in advancedKeys"
          :id="`audit-${key}`"
          :key="key"
          :label="t(`audits.${key}`)"
          ><NInput
            v-model:value="draft[key]"
            :maxlength="key === 'actionCode' || key === 'targetType' ? 64 : 128"
            :input-props="{
              id: `audit-${key}`,
              'aria-label': t(`audits.${key}`),
              autocomplete: 'off',
            }"
        /></FormItem>
      </div>
      <p v-if="invalid" id="audit-range-error" class="request-error" role="alert">
        {{ t('audits.invalidRange') }}
      </p>
    </NForm>
    <RequestError :error="query.error.value" />
    <p v-if="query.isPending.value && !runtime.accessIssue.value" role="status">
      {{ t('common.loading') }}
    </p>
    <template v-else-if="query.data.value && !query.isError.value && !runtime.accessIssue.value"
      ><div
        ref="tableScroll"
        class="table-scroll audit-table-scroll"
        :class="{ 'has-overflow': tableOverflow }"
      >
        <table class="audit-table">
          <thead>
            <tr>
              <th :aria-sort="sortDirection === 'desc' ? 'descending' : 'ascending'">
                <button
                  type="button"
                  class="audit-sort"
                  :title="
                    t(sortDirection === 'desc' ? 'audits.sortAscending' : 'audits.sortDescending')
                  "
                  @click="toggleSort"
                >
                  {{ t('audits.time')
                  }}<span aria-hidden="true">{{ sortDirection === 'desc' ? '↓' : '↑' }}</span>
                </button>
              </th>
              <th>{{ t('audits.actor') }}</th>
              <th>{{ t('audits.actionCode') }}</th>
              <th>{{ t('audits.targetType') }}</th>
              <th>{{ t('audits.resultCode') }}</th>
              <th class="audit-actions-cell">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in query.data.value.list" :key="row.id">
              <td class="time-cell">
                {{
                  formatUtc(
                    row.occurredAt,
                    runtime.preferences.locale,
                    runtime.preferences.displayTimezone,
                  )
                }}
              </td>
              <td>
                {{ row.actorName || row.actorLoginId
                }}<small class="cell-note">{{ row.actorLoginId }}</small>
              </td>
              <td>
                <code>{{ row.actionCode }}</code>
              </td>
              <td>
                {{ row.targetType }}<small class="cell-note">{{ row.targetId || '—' }}</small>
              </td>
              <td>
                <span class="result-badge" :class="`result-${row.resultCode.toLowerCase()}`"
                  ><span aria-hidden="true">{{ symbols[row.resultCode] || '·' }}</span>
                  {{ t(`status.${row.resultCode}`, row.resultCode) }}</span
                >
              </td>
              <td class="audit-actions-cell">
                <NButton
                  text
                  size="small"
                  type="primary"
                  :aria-label="`${t('common.details')} ${row.id}`"
                  @click="openDetail(row.id, $event)"
                  >{{ t('common.details') }}</NButton
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-if="!query.data.value.list.length" :message="t('common.empty')" />
      <PageControls
        :page="page"
        :size="size"
        :total="query.data.value.total"
        :busy="query.isFetching.value"
        @page="page = $event"
        @size="setSize"
    /></template>
  </section>
  <AuditDetail v-if="selected" :id="selected" @close="closeDetail" />
</template>
