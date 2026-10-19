<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NInput } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { pageData, exactId } from '../../shared/api/values'
import { approval, emptyFilters, filterIssues, statuses, time, type Filters } from './model'
import RequestError from '../../shared/ui/RequestError.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import AdvancedFilter from '../../shared/ui/AdvancedFilter.vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import ApprovalDrawer from './ApprovalDrawer.vue'
import './approvals.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  route = useRoute(),
  router = useRouter()
const allowed = computed(() => can(user.value, 'APPROVAL_DECIDE') && !runtime.accessIssue.value)
const routeText = (key: string) =>
  typeof route.query[key] === 'string' ? String(route.query[key]) : ''
const applied = computed<Filters>(() => ({
  taskName: routeText('taskName'),
  status: routeText('status') || 'WAITING',
  instanceId: routeText('instanceId'),
  rootInstanceId: routeText('rootInstanceId'),
}))
const page = computed(() =>
  Math.min(100000, Math.max(1, Math.floor(Number(routeText('page'))) || 1)),
)
const size = computed(() =>
  [10, 20, 50, 100].includes(Number(routeText('size'))) ? Number(routeText('size')) : 20,
)
const draft = reactive({ ...applied.value }),
  errors = ref<Record<string, string>>({}),
  advanced = ref(false)
const form = ref<HTMLElement>(),
  selected = computed(() => {
    try {
      return exactId(routeText('waitId'))
    } catch {
      return ''
    }
  })
let returnFocus: HTMLElement | null = null
const queryString = computed(() =>
  new URLSearchParams(
    Object.entries({
      ...applied.value,
      pageNum: String(page.value),
      pageSize: String(size.value),
    }).filter(([, value]) => !!value),
  ).toString(),
)
watch(
  queryString,
  () => {
    Object.assign(draft, applied.value)
    errors.value = filterIssues(draft)
    if (errors.value.instanceId || errors.value.rootInstanceId) advanced.value = true
  },
  { immediate: true },
)
const query = useQuery(
  computed(() => ({
    queryKey: [
      'approvals',
      runtime.session.revision,
      runtime.preferences.locale,
      queryString.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      pageData(
        await runtime.request('/api/event-waits?' + queryString.value, { signal }),
        approval,
      ),
    enabled: allowed.value && !Object.keys(filterIssues(applied.value)).length,
    retry: false,
    staleTime: 0,
  })),
)
const options = computed(() =>
  statuses.map((value) => ({ value, label: t('approvals.state_' + value) })),
)
const hiddenCount = computed(
  () =>
    ['instanceId', 'rootInstanceId'].filter((key) => !!applied.value[key as keyof Filters]).length,
)
async function search(reset = false) {
  if (!allowed.value || query.isFetching.value) return
  if (reset) Object.assign(draft, emptyFilters())
  errors.value = filterIssues(draft)
  if (Object.keys(errors.value).length) {
    if (errors.value.instanceId || errors.value.rootInstanceId) advanced.value = true
    await nextTick()
    form.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    return
  }
  const before = queryString.value
  await router.replace({
    query: {
      ...draft,
      page: String(
        reset || JSON.stringify(draft) !== JSON.stringify(applied.value) ? 1 : page.value,
      ),
      size: String(size.value),
    },
  })
  if (before === queryString.value) void query.refetch()
}
async function paginate(next: number, nextSize = size.value) {
  await router.replace({
    query: { ...route.query, page: String(next), size: String(nextSize), waitId: undefined },
  })
}
function open(id: string, event: MouseEvent) {
  returnFocus = event.currentTarget as HTMLElement
  void router.replace({ query: { ...route.query, waitId: id } })
}
async function close() {
  await router.replace({ query: { ...route.query, waitId: undefined } })
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
watch([allowed, () => runtime.session.revision], ([access, revision], [, previous]) => {
  if (selected.value && (!access || revision !== previous)) void close()
})
</script>
<template>
  <div class="approvals-page">
    <header class="page-heading list-heading">
      <div>
        <h1>{{ t('approvals.title') }}</h1>
        <p class="subtitle">{{ t('approvals.subtitle') }}</p>
      </div>
    </header>
    <section class="approval-panel list-panel">
      <h2 class="approval-section">{{ t('approvals.mine') }}</h2>
      <form ref="form" class="approval-filters list-filters" @submit.prevent="search()">
        <div class="approval-filter">
          <label for="approval-name">{{ t('approvals.searchName') }}</label>
          <div>
            <NInput
              v-model:value="draft.taskName"
              :input-props="{ id: 'approval-name', 'aria-invalid': !!errors.taskName }"
              :placeholder="t('approvals.searchPlaceholder')"
            />
            <p v-if="errors.taskName" class="field-error">
              {{ t('approvals.' + errors.taskName) }}
            </p>
          </div>
        </div>
        <div class="approval-filter">
          <label for="approval-status">{{ t('approvals.status') }}</label
          ><CompactSelect
            id="approval-status"
            :label="t('approvals.status')"
            :value="draft.status"
            :options="options"
            :invalid="!!errors.status"
            @update:value="draft.status = String($event)"
          />
          <p v-if="errors.status" class="field-error">{{ t('approvals.invalidStatus') }}</p>
        </div>
        <div class="approval-actions">
          <NButton
            class="list-query-action"
            attr-type="submit"
            type="primary"
            :loading="query.isFetching.value"
            >{{ t('approvals.search') }}</NButton
          ><NButton
            class="list-query-action"
            :disabled="query.isFetching.value"
            @click="search(true)"
            >{{ t('approvals.reset') }}</NButton
          ><AdvancedFilter
            :expanded="advanced"
            :count="hiddenCount"
            :label="t('approvals.advanced')"
            controls="approvals-advanced"
            @toggle="advanced = !advanced"
          />
        </div>
        <div v-if="advanced" id="approvals-advanced" class="approval-advanced">
          <div
            v-for="key in ['instanceId', 'rootInstanceId'] as const"
            :key="key"
            class="approval-filter"
          >
            <label :for="'approval-' + key">{{ t('approvals.' + key) }}</label>
            <div>
              <NInput
                v-model:value="draft[key]"
                :input-props="{ id: 'approval-' + key, 'aria-invalid': !!errors[key] }"
                placeholder=" "
              />
              <p v-if="errors[key]" class="field-error" role="alert">
                {{ t('approvals.' + errors[key]) }}
              </p>
            </div>
          </div>
        </div>
      </form>
      <RequestError :error="query.error.value" />
      <NButton v-if="query.isError.value" @click="query.refetch()">{{ t('common.retry') }}</NButton>
      <p v-if="query.isPending.value && allowed && !Object.keys(errors).length" role="status">
        {{ t('common.loading') }}
      </p>
      <template v-if="allowed && query.data.value && !query.isError.value">
        <div class="approval-table-scroll">
          <table class="approval-table">
            <thead>
              <tr>
                <th>{{ t('approvals.taskName') }}</th>
                <th>{{ t('approvals.status') }}</th>
                <th>{{ t('approvals.started') }}</th>
                <th>{{ t('approvals.deadline') }}</th>
                <th class="approval-fixed">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in query.data.value.list" :key="row.id">
                <td>
                  {{ row.taskName || t('approvals.missingName')
                  }}<small>{{ t('approvals.id') }} {{ row.id }}</small>
                </td>
                <td>
                  <span class="approval-status" :data-status="row.status">{{
                    t('approvals.state_' + row.status)
                  }}</span>
                </td>
                <td class="approval-time">
                  {{
                    time(
                      row.createTime,
                      runtime.preferences.locale,
                      runtime.preferences.displayTimezone,
                    )
                  }}
                </td>
                <td class="approval-time">
                  {{
                    row.timeoutAt
                      ? time(
                          row.timeoutAt,
                          runtime.preferences.locale,
                          runtime.preferences.displayTimezone,
                        )
                      : t('approvals.unlimited')
                  }}
                </td>
                <td class="approval-fixed">
                  <NButton
                    text
                    type="primary"
                    :aria-label="t('approvals.view') + ' ' + row.id"
                    @click="open(row.id, $event)"
                    >{{ t('approvals.view') }}</NButton
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
          @page="paginate($event)"
          @size="paginate(1, $event)"
        />
      </template>
    </section>
    <ApprovalDrawer
      v-if="selected && allowed"
      :id="selected"
      :key="selected"
      @close="close"
      @resolved="query.refetch()"
    />
  </div>
</template>
