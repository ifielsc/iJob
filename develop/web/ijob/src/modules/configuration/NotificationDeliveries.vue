<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { parsePage } from '../security-contracts'
import { record, id } from './contracts'
import { ApiError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import DateTime from '../../shared/ui/UtcDateTimePicker.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import { parseUtc, toApiUtc } from '../../shared/time/format'
import { RunList } from '../runs/public/components'
import { notificationLabel } from './notification-channels'

interface Delivery {
  id: string
  notifyConfigId: string
  configName: string | null
  notifyType: string
  sourceType: string
  subject: string
  targetId: string | null
  instanceId: string | null
  status: string
  attemptCount: number
  maxAttempts: number
  failureCode: string | null
  failureSummary: string | null
  httpStatus: number | null
  availableAt: string
  createTime: string
  deliveredAt: string | null
  updateTime: string
}
function parseDelivery(value: unknown): Delivery {
  const r = record(value)
  id(r.id)
  id(r.notifyConfigId)
  if (
    typeof r.subject !== 'string' ||
    typeof r.notifyType !== 'string' ||
    !['READY', 'CLAIMED', 'RETRY_WAIT', 'DELIVERED', 'DEAD'].includes(String(r.status)) ||
    !Number.isSafeInteger(r.attemptCount) ||
    !Number.isSafeInteger(r.maxAttempts)
  )
    throw new ApiError('protocol')
  return r as unknown as Delivery
}
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const allowed = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const route = useRoute(),
  router = useRouter()
const attempts = computed(() => route.query.view === 'attempts')
function showAttempts(value: boolean) {
  return router.push({ query: value ? { tab: 'records', view: 'attempts' } : { tab: 'records' } })
}
const page = ref(1),
  size = ref(20),
  selected = ref<string>()
function setSize(value: number) {
  size.value = value
  page.value = 1
}
function defaults() {
  const now = new Date()
  return {
    fromTime: toApiUtc(new Date(now.getTime() - 86400000)),
    toTime: toApiUtc(now),
    deliveryStatus: '',
  }
}
const filters = reactive(defaults()),
  applied = ref({ ...filters }),
  filterError = ref(false)
async function search(reset = false) {
  if (reset) Object.assign(filters, defaults())
  try {
    const duration = parseUtc(filters.toTime).getTime() - parseUtc(filters.fromTime).getTime()
    if (duration <= 0 || duration > 31 * 86400000) throw new Error()
    filterError.value = false
    const same = page.value === 1 && JSON.stringify(applied.value) === JSON.stringify(filters)
    page.value = 1
    applied.value = { ...filters }
    if (same) await query.refetch()
  } catch {
    filterError.value = true
  }
}
const query = useQuery(
  computed(() => ({
    queryKey: [
      'notification-deliveries',
      page.value,
      size.value,
      applied.value,
      runtime.session.revision,
    ],
    enabled: allowed.value && !attempts.value,
    refetchInterval: 10000,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parsePage(
        await runtime.request(
          '/api/notification-deliveries?' +
            new URLSearchParams({
              pageNum: String(page.value),
              pageSize: String(size.value),
              ...applied.value,
            }),
          { signal },
        ),
        parseDelivery,
      ),
  })),
)
const detail = useQuery(
  computed(() => ({
    queryKey: ['notification-delivery', selected.value, runtime.session.revision],
    enabled: allowed.value && !!selected.value,
    refetchInterval: 10000,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseDelivery(
        await runtime.request('/api/notification-deliveries/' + selected.value, { signal }),
      ),
  })),
)
const row = computed(() => (allowed.value && !detail.isError.value ? detail.data.value : undefined))
function state(row: Delivery) {
  return row.failureCode === 'RATE_WAIT' && row.status === 'RETRY_WAIT' ? 'RATE_WAIT' : row.status
}
function failure(row: Delivery) {
  return row.failureCode &&
    ['RATE_WAIT', 'RATE_LIMITED', 'REMOTE_REJECTED'].includes(row.failureCode)
    ? t('config.deliveryReason_' + row.failureCode)
    : row.failureSummary || '—'
}
</script>
<template>
  <div v-if="attempts">
    <NButton text type="primary" @click="showAttempts(false)">{{
      t('config.backToDeliveries')
    }}</NButton>
    <RunList :notifications="true"
      ><template #tabs><slot name="tabs" /></template
    ></RunList>
  </div>
  <template v-else>
    <div class="page-heading list-heading">
      <div>
        <h1>{{ t('config.titleNotifications') }}</h1>
        <p class="subtitle">{{ t('config.deliverySubtitle') }}</p>
      </div>
    </div>
    <slot name="tabs" />
    <section
      id="run-list-panel"
      class="panel glass notification-list-panel list-panel"
      role="tabpanel"
      aria-labelledby="notification-tabs-records"
    >
      <form class="configuration-filter list-filters delivery-toolbar" @submit.prevent="search()">
        <div class="filter-item">
          <label for="delivery-from">{{ t('runs.from') }}</label
          ><DateTime
            id="delivery-from"
            v-model="filters.fromTime"
            :label="t('runs.from')"
            :invalid="filterError"
            required
          />
        </div>
        <div class="filter-item">
          <label for="delivery-to">{{ t('runs.to') }}</label
          ><DateTime
            id="delivery-to"
            v-model="filters.toTime"
            :label="t('runs.to')"
            :invalid="filterError"
            required
          />
        </div>
        <div class="filter-item">
          <label for="delivery-status">{{ t('config.deliveryStatus') }}</label
          ><CompactSelect
            id="delivery-status"
            v-model:value="filters.deliveryStatus"
            :label="t('config.deliveryStatus')"
            :options="[
              { value: '', label: t('config.all') },
              ...['READY', 'RETRY_WAIT', 'RATE_WAIT', 'DELIVERED', 'DEAD'].map((value) => ({
                value,
                label: t('config.delivery_' + value),
              })),
            ]"
          />
        </div>
        <NButton type="primary" :loading="query.isFetching.value" @click="search()">{{
          t('config.search')
        }}</NButton>
        <NButton @click="search(true)">{{ t('config.resetFilters') }}</NButton>
        <NButton @click="showAttempts(true)">{{ t('config.attemptHistory') }}</NButton>
      </form>
      <p v-if="filterError" role="alert">{{ t('config.deliveryRangeError') }}</p>
      <RequestError :error="query.error.value" />
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="allowed && query.data.value && !query.isError.value">
        <div class="delivery-table-scroll">
          <table class="configuration-table notification-table">
            <thead>
              <tr>
                <th>{{ t('config.deliveryNotification') }}</th>
                <th>{{ t('config.notificationSource') }}</th>
                <th>{{ t('config.deliveryStatus') }}</th>
                <th>{{ t('config.nextAttempt') }}</th>
                <th>{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in query.data.value.list" :key="item.id">
                <td>
                  <div>{{ item.subject }}</div>
                  <small
                    >{{ t(notificationLabel(item.notifyType)) }} ·
                    {{ item.configName || '#' + item.notifyConfigId }}</small
                  >
                </td>
                <td>{{ item.sourceType }}</td>
                <td>
                  {{ t('config.delivery_' + state(item))
                  }}<small class="delivery-reason">{{ failure(item) }}</small>
                </td>
                <td>
                  <TimeEvidence
                    v-if="['READY', 'RETRY_WAIT'].includes(item.status)"
                    :value="item.availableAt"
                  /><span v-else>—</span>
                </td>
                <td>
                  <NButton text type="primary" @click="selected = item.id">{{
                    t('common.details')
                  }}</NButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState v-if="!query.data.value.list.length" :message="t('common.empty')" />
        <p class="configuration-note">{{ t('config.acceptedHint') }}</p>
        <PageControls
          :page="page"
          :size="size"
          :total="query.data.value.total"
          :busy="query.isFetching.value"
          @page="page = $event"
          @size="setSize"
        />
      </template>
    </section>
  </template>
  <NDrawer
    :show="!!selected && allowed"
    width="min(520px,100vw)"
    class="configuration-drawer"
    @update:show="selected = undefined"
  >
    <NDrawerContent :title="t('config.deliveryDetails')" closable :native-scrollbar="true">
      <RequestError :error="detail.error.value" />
      <p v-if="detail.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="row">
        <div class="notification-protection">
          <strong>{{ t('config.delivery_' + state(row)) }}</strong>
          <p>{{ failure(row) }}</p>
        </div>
        <dl class="unified-facts">
          <dt>{{ t('config.name') }}</dt>
          <dd>{{ row.configName || '#' + row.notifyConfigId }}</dd>
          <dt>{{ t('config.type') }}</dt>
          <dd>{{ t(notificationLabel(row.notifyType)) }}</dd>
          <dt>{{ t('config.notificationSource') }}</dt>
          <dd>{{ row.sourceType }}</dd>
          <dt>{{ t('config.subject') }}</dt>
          <dd>{{ row.subject }}</dd>
          <dt>{{ t('config.previewIdentity') }}</dt>
          <dd>{{ row.targetId || '—' }} · {{ row.instanceId || '—' }}</dd>
          <dt>{{ t('config.attemptCount') }}</dt>
          <dd>{{ row.attemptCount }} / {{ row.maxAttempts }}</dd>
          <dt>{{ t('config.nextAttempt') }}</dt>
          <dd>
            <TimeEvidence
              v-if="['READY', 'RETRY_WAIT'].includes(row.status)"
              :value="row.availableAt"
            /><span v-else>—</span>
          </dd>
          <dt>HTTP</dt>
          <dd>{{ row.httpStatus || '—' }}</dd>
          <dt>{{ t('config.queuedAt') }}</dt>
          <dd><TimeEvidence :value="row.createTime" /></dd>
          <dt>{{ t('config.lastUpdate') }}</dt>
          <dd><TimeEvidence :value="row.updateTime" /></dd>
          <template v-if="row.deliveredAt"
            ><dt>{{ t('config.acceptedAt') }}</dt>
            <dd><TimeEvidence :value="row.deliveredAt" /></dd
          ></template>
        </dl>
        <p class="configuration-note">
          {{ t(row.failureCode === 'RATE_WAIT' ? 'config.rateWaitHint' : 'config.acceptedHint') }}
        </p>
        <h3>{{ t('config.deliveryHistory') }}</h3>
        <ol class="delivery-timeline">
          <li>
            <TimeEvidence :value="row.createTime" />
            <p>{{ t('config.queuedAt') }} · {{ t('config.snapshotSaved') }}</p>
          </li>
          <li>
            <TimeEvidence :value="row.updateTime" />
            <p>{{ t('config.delivery_' + state(row)) }} · {{ failure(row) }}</p>
          </li>
          <li v-if="['READY', 'RETRY_WAIT'].includes(row.status)">
            <TimeEvidence :value="row.availableAt" />
            <p>{{ t('config.nextAttempt') }}</p>
          </li>
        </ol>
      </template>
      <template #footer
        ><NButton @click="selected = undefined">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
<style scoped>
.delivery-toolbar {
  margin: 16px 0;
  flex-wrap: wrap;
}
.delivery-table-scroll {
  overflow-x: auto;
}
.delivery-reason {
  display: block;
  color: var(--muted);
  margin-top: 4px;
}
.delivery-timeline {
  padding-left: 22px;
}
.delivery-timeline li {
  padding: 8px 0;
}
.delivery-timeline p {
  color: var(--muted);
  margin: 4px 0;
}
</style>
