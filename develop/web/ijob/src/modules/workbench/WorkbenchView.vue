<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import RuntimeOverview from './RuntimeOverview.vue'
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NSkeleton } from 'naive-ui'
import {
  ChartNoAxesCombined,
  CircleCheck,
  CircleAlert,
  ClipboardList,
  FileChartColumn,
  Info,
} from '@lucide/vue'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { formatUtc } from '../../shared/time/format'
import DateTime from '../../shared/ui/UtcDateTimePicker.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import {
  activeOrder,
  barWidth,
  formatCount,
  parseWorkbench,
  recentWindow,
  runLink,
  statusOrder,
  sum,
  validateWindow,
  type Workbench,
  type WindowRange,
} from './model'
import './workbench.css'

const technicalOpen = ref(!window.matchMedia('(max-width: 700px)').matches)
const runtime = useRuntime(),
  { user } = useIdentity(),
  route = useRoute(),
  router = useRouter(),
  { t, te, locale } = useI18n()
const allowed = computed(() => !runtime.accessIssue.value && can(user.value, 'SUMMARY_READ'))
const draft = reactive({ from: '', to: '' }),
  data = shallowRef<Workbench>(),
  error = shallowRef<unknown>(),
  busy = ref(false),
  invalid = ref(false)
let controller: AbortController | undefined,
  generation = 0
const number = (v: string) => formatCount(v, locale.value)
const time = (v: string) =>
  formatUtc(v, runtime.preferences.locale, runtime.preferences.displayTimezone)
const label = (s: string) =>
  s === 'RUNNING' ? t('workbench.running') : te('tasks.status_' + s) ? t('tasks.status_' + s) : s
const ordered = computed(() =>
  data.value
    ? [...statusOrder, ...Object.keys(data.value.statuses).filter((s) => !statusOrder.includes(s))]
    : [],
)
const failures = computed(() =>
  data.value ? sum([data.value.statuses.FAILED!, data.value.statuses.TIMED_OUT!]) : '0',
)
const returnPath = computed(
  () =>
    router.resolve({ name: 'workbench', query: { from: data.value?.from, to: data.value?.to } })
      .fullPath,
)
function cancel() {
  generation++
  controller?.abort()
  busy.value = false
}
async function load(window?: WindowRange) {
  cancel()
  if (!allowed.value) return
  const current = generation
  controller = new AbortController()
  error.value = undefined
  busy.value = true
  try {
    const query = window ? '?' + new URLSearchParams({ ...window }).toString() : ''
    const result = parseWorkbench(
      await runtime.request('/api/workbench' + query, { signal: controller.signal }),
    )
    if (current !== generation || !allowed.value) return
    data.value = result
    Object.assign(draft, { from: result.from, to: result.to })
  } catch (e) {
    if (current === generation) {
      error.value = e
    }
  } finally {
    if (current === generation) busy.value = false
  }
}
function routeWindow() {
  if (!('from' in route.query) && !('to' in route.query)) return undefined
  return validateWindow(
    typeof route.query.from === 'string' ? route.query.from : '',
    typeof route.query.to === 'string' ? route.query.to : '',
  )
}
watch(
  () => [allowed.value, runtime.session.revision, route.query.from, route.query.to],
  () => {
    cancel()
    data.value = undefined
    error.value = undefined
    invalid.value = false
    if (!allowed.value) return
    try {
      const window = routeWindow()
      if (window) Object.assign(draft, window)
      void load(window)
    } catch {
      invalid.value = true
    }
  },
  { immediate: true },
)
async function search() {
  invalid.value = false
  let window: WindowRange
  try {
    window = validateWindow(draft.from, draft.to)
  } catch {
    invalid.value = true
    await nextTick()
    document.getElementById('workbench-from')?.focus()
    return
  }
  if (route.query.from === window.from && route.query.to === window.to) await load(window)
  else await router.replace({ query: { ...window } })
}
function shortcut(days: number) {
  Object.assign(draft, recentWindow(days))
  void search()
}
function retry() {
  try {
    void load(routeWindow())
  } catch {
    invalid.value = true
  }
}
onBeforeUnmount(cancel)
</script>

<template>
  <div class="workbench-page">
    <div class="page-heading">
      <div>
        <h1>{{ t('workbench.title') }}</h1>
        <p class="subtitle">{{ t('workbench.subtitle') }}</p>
      </div>
      <p v-if="data && allowed" class="workbench-note">
        {{ t('workbench.businessZone') }} · {{ data.businessZone }}
      </p>
    </div>
    <form v-if="allowed" class="workbench-filter" novalidate @submit.prevent="search">
      <span>{{ t('workbench.created') }}</span>
      <div class="workbench-date-pair">
        <DateTime
          id="workbench-from"
          v-model="draft.from"
          :label="t('workbench.from')"
          :invalid="invalid"
          :describedby="invalid ? 'workbench-time-error' : undefined"
          required
        />
        <span aria-hidden="true">—</span>
        <DateTime
          id="workbench-to"
          v-model="draft.to"
          :label="t('workbench.to')"
          :invalid="invalid"
          :describedby="invalid ? 'workbench-time-error' : undefined"
          required
        />
      </div>
      <div class="workbench-filter-actions">
        <NButton class="list-query-action" type="primary" attr-type="submit" :loading="busy">{{
          t('workbench.query')
        }}</NButton>
        <NButton
          v-for="[days, key] in [
            [1, 'lastDay'],
            [7, 'lastWeek'],
            [30, 'lastMonth'],
          ] as const"
          :key="days"
          @click="shortcut(days)"
          >{{ t('workbench.' + key) }}</NButton
        >
      </div>
      <p v-if="invalid" id="workbench-time-error" class="workbench-time-error" role="alert">
        {{ t('workbench.invalidTime') }}
      </p>
    </form>
    <RequestError :error="error" />
    <NButton v-if="error && allowed" @click="retry">{{ t('workbench.retry') }}</NButton>
    <p v-if="!allowed" role="status">{{ t('workbench.denied') }}</p>
    <div
      v-if="busy && allowed"
      class="workbench-loading"
      role="status"
      :aria-label="t('workbench.loading')"
    >
      <span>{{ t('workbench.loading') }}</span>
      <div class="workbench-metrics"><NSkeleton v-for="n in 4" :key="n" height="114px" /></div>
      <NSkeleton height="300px" />
    </div>
    <template v-else-if="data && allowed">
      <p v-if="error" role="status">{{ t('experience.stale') }}</p>
      <div class="workbench-query-context">
        <span>{{
          t('workbench.applied', {
            from: time(data.from),
            to: time(data.to),
            zone: runtime.preferences.displayTimezone,
          })
        }}</span
        ><span>{{
          t('workbench.updated', {
            time: time(data.queriedAt),
            zone: runtime.preferences.displayTimezone,
          })
        }}</span>
      </div>
      <div class="workbench-metrics">
        <section class="workbench-card workbench-metric">
          <div>
            <h2>{{ t('workbench.total') }}</h2>
            <strong data-testid="total">{{ number(data.total) }}</strong>
          </div>
          <ClipboardList :size="24" aria-hidden="true" />
        </section>
        <section class="workbench-card workbench-metric workbench-success">
          <div>
            <h2>{{ t('workbench.succeeded') }}</h2>
            <strong>{{ number(data.statuses.SUCCESS!) }}</strong>
          </div>
          <CircleCheck :size="24" aria-hidden="true" />
        </section>
        <section class="workbench-card workbench-metric workbench-error">
          <div>
            <h2>{{ t('workbench.failures') }}</h2>
            <strong>{{ number(failures) }}</strong>
            <p class="workbench-note">
              {{
                t('workbench.failureSplit', {
                  failed: number(data.statuses.FAILED!),
                  timedOut: number(data.statuses.TIMED_OUT!),
                })
              }}
            </p>
          </div>
          <CircleAlert :size="24" aria-hidden="true" />
        </section>
        <section class="workbench-card workbench-metric">
          <div>
            <h2>{{ t('workbench.rate') }}</h2>
            <strong data-testid="rate">{{
              data.successRatePercent === null ? '—' : data.successRatePercent + '%'
            }}</strong>
            <p class="workbench-note">
              {{
                data.successRatePercent === null
                  ? t('workbench.noRate')
                  : number(data.statuses.SUCCESS!) + ' / ' + number(data.successRateDenominator)
              }}
            </p>
          </div>
          <ChartNoAxesCombined :size="24" aria-hidden="true" />
        </section>
      </div>
      <p class="workbench-formula workbench-note">{{ t('workbench.formula') }}</p>
      <div class="workbench-middle">
        <section class="workbench-card workbench-distribution">
          <h2>{{ t('workbench.distribution') }}</h2>
          <div v-if="data.total === '0'" class="workbench-empty">
            <FileChartColumn :size="40" aria-hidden="true" />
            <h3>{{ t('workbench.empty') }}</h3>
            <p>{{ t('workbench.emptyHint') }}</p>
          </div>
          <ul v-else class="workbench-status-list">
            <li v-for="status in ordered" :key="status" :data-status="status">
              <RouterLink
                v-if="statusOrder.includes(status)"
                :to="runLink(status, data)"
                :aria-label="t('workbench.viewStatus', { status: label(status) })"
                >{{ label(status) }}</RouterLink
              ><span v-else>{{ label(status) }}</span>
              <span class="workbench-bar" aria-hidden="true"
                ><span :style="{ width: barWidth(data.statuses[status]!, data.total) + '%' }"
              /></span>
              <strong>{{ number(data.statuses[status]!) }}</strong>
            </li>
          </ul>
        </section>
        <div class="workbench-side">
          <section class="workbench-card workbench-backlog">
            <h2>{{ t('workbench.current') }}</h2>
            <p class="workbench-note">{{ t('workbench.independent') }}</p>
            <dl>
              <template v-for="status in activeOrder" :key="status"
                ><dt>
                  <RouterLink
                    :to="runLink(status)"
                    :aria-label="t('workbench.viewStatus', { status: label(status) })"
                    >{{ label(status) }}</RouterLink
                  >
                </dt>
                <dd>{{ number(data.currentActiveRoots[status]!) }}</dd></template
              >
            </dl>
            <div
              v-if="data.approvalsVisible && can(user, 'APPROVAL_DECIDE')"
              class="workbench-approvals"
            >
              <span>{{ t('workbench.approvals') }}</span
              ><strong>{{ number(data.pendingApprovals!) }}</strong
              ><RouterLink :to="{ name: 'events', query: { status: 'WAITING' } }"
                >{{ t('workbench.viewApprovals') }} →</RouterLink
              >
            </div>
          </section>
          <details class="workbench-card workbench-node" :open="technicalOpen">
            <summary>{{ t('workbench.node') }}</summary>
            <div class="workbench-section-heading">
              <h2>{{ t('workbench.node') }}</h2>
              <span class="workbench-role">{{
                data.node.roles.length === 1 && data.node.roles[0] === 'API'
                  ? t('runEnums.API')
                  : data.node.roles.map((role) => t('runEnums.' + role)).join(' · ')
              }}</span>
            </div>
            <dl>
              <dt>{{ t('workbench.worker') }}</dt>
              <dd>
                {{
                  t(
                    'workbench.' +
                      (data.node.workerRoleConfigured ? 'configured' : 'notConfigured'),
                  )
                }}
              </dd>
              <dt>{{ t('workbench.scheduler') }}</dt>
              <dd>
                {{
                  t(
                    'workbench.' +
                      (data.node.scheduleEnabled && data.node.schedulerRoleConfigured
                        ? 'enabled'
                        : 'disabled'),
                  )
                }}
              </dd>
            </dl>
            <p class="workbench-node-note">
              <Info :size="16" aria-hidden="true" />{{ t('insights.noHealthyClaim') }}
            </p>
          </details>
        </div>
      </div>
      <RuntimeOverview />
      <section class="workbench-card workbench-recent">
        <div class="workbench-section-heading">
          <h2>{{ t('workbench.recent') }}</h2>
          <p class="workbench-note">{{ t('workbench.recentHint') }}</p>
        </div>
        <div class="workbench-table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ t('workbench.rootRun') }}</th>
                <th>{{ t('workbench.type') }}</th>
                <th>{{ t('workbench.status') }}</th>
                <th>{{ t('workbench.created') }}</th>
                <th>{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.recentFailures" :key="row.id">
                <td>
                  <strong>{{
                    row.name || t('runEnums.' + row.targetType) + ' #' + row.targetId
                  }}</strong
                  ><small>{{ row.id }}</small>
                </td>
                <td>{{ t('runEnums.' + row.targetType) }}</td>
                <td>
                  <span class="workbench-status" :data-status="row.status">{{
                    label(row.status)
                  }}</span>
                </td>
                <td>{{ time(row.createTime) }}</td>
                <td>
                  <RouterLink
                    :to="{
                      name: 'run-detail',
                      params: { id: row.id },
                      query: { back: returnPath },
                    }"
                    :aria-label="t('workbench.viewRun', { id: row.id })"
                    >{{ t('workbench.view') }}</RouterLink
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="!data.recentFailures.length"
          compact
          :message="t('workbench.emptyFailures')"
        />
        <div class="workbench-scope">
          <p>{{ t('workbench.scope') }}</p>
          <RouterLink
            :to="{ name: 'runs', query: { tab: 'triggers', from: data.from, to: data.to } }"
            >{{ t('workbench.triggers') }} →</RouterLink
          >
        </div>
      </section>
    </template>
  </div>
</template>
