<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NDrawer, NDrawerContent, NSkeleton } from 'naive-ui'
import { Info, RefreshCw } from '@lucide/vue'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import { formatUtc } from '../../shared/time/format'
import RequestError from '../../shared/ui/RequestError.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import {
  categories,
  diagnostics,
  drilldown,
  selection,
  type Diagnostics,
  type DiagnosticItem,
} from './model'
import './diagnostics.css'

const runtime = useRuntime(),
  { user } = useIdentity(),
  route = useRoute(),
  router = useRouter(),
  { t, te } = useI18n()
const allowed = computed(() => can(user.value, 'SUMMARY_READ') && !runtime.accessIssue.value)
const options = computed(() => {
  try {
    return selection(route.query.category, route.query.limit)
  } catch {
    return undefined
  }
})
const data = shallowRef<Diagnostics>(),
  selected = shallowRef<DiagnosticItem>(),
  error = shallowRef<unknown>(),
  busy = ref(false)
let controller: AbortController | undefined,
  generation = 0,
  returnFocus: HTMLElement | undefined
const category = computed(() => options.value?.category ?? 'LEASE_EXPIRED')
const time = (v: string) =>
  formatUtc(v, runtime.preferences.locale, runtime.preferences.displayTimezone)
const deadlineKey = computed(
  () =>
    ({
      LEASE_EXPIRED: 'leaseTime',
      QUEUE_DELAYED: 'queueTime',
      ROOT_DEADLINE_EXCEEDED: 'rootTime',
      COMMAND_EXPIRED: 'commandTime',
    })[category.value],
)
const rule = computed(() =>
  t('diagnostics.rule_' + category.value, { seconds: data.value?.queueDelaySeconds ?? 300 }),
)
const title = (row: DiagnosticItem) =>
  category.value === 'COMMAND_EXPIRED'
    ? row.id
    : row.name || [row.targetType, row.targetId].filter(Boolean).join(' · ') || row.id
const status = (code: string) =>
  te('diagnostics.state_' + code)
    ? t('diagnostics.state_' + code)
    : te('tasks.status_' + code)
      ? t('tasks.status_' + code)
      : code
const back = computed(
  () =>
    router.resolve({
      name: 'diagnostics',
      query: { category: category.value, limit: String(options.value?.limit ?? 20) },
    }).fullPath,
)
const link = computed(() =>
  selected.value ? drilldown(selected.value, category.value, back.value) : undefined,
)
const linkLabel = computed(() =>
  category.value === 'COMMAND_EXPIRED'
    ? 'viewInstance'
    : selected.value?.rootInstanceId
      ? 'viewRoot'
      : 'viewTrigger',
)
const limits = computed(() =>
  [...new Set([20, 50, 100, options.value?.limit ?? 20])]
    .sort((a, b) => a - b)
    .map((value) => ({ value, label: String(value) })),
)
function cancel() {
  generation++
  controller?.abort()
  busy.value = false
}
async function load() {
  cancel()
  data.value = undefined
  selected.value = undefined
  error.value = undefined
  if (!allowed.value || !options.value) return
  const current = generation,
    expected = options.value
  controller = new AbortController()
  busy.value = true
  try {
    const result = diagnostics(
      await runtime.request(
        '/api/diagnostics?' +
          new URLSearchParams({ category: expected.category, limit: String(expected.limit) }),
        { signal: controller.signal },
      ),
      expected,
    )
    if (generation === current && allowed.value) data.value = result
  } catch (e) {
    if (generation === current) error.value = e
  } finally {
    if (generation === current) busy.value = false
  }
}
watch(
  () => [allowed.value, runtime.session.revision, route.query.category, route.query.limit],
  () => void load(),
  { immediate: true },
)
function change(value: string, limit = options.value?.limit ?? 20) {
  void router.replace({ query: { category: value, limit: String(limit) } })
}
function open(row: DiagnosticItem, event: MouseEvent) {
  returnFocus = event.currentTarget as HTMLElement
  selected.value = row
}
async function close() {
  selected.value = undefined
  await nextTick()
  returnFocus?.focus()
}
onBeforeUnmount(cancel)
</script>

<template>
  <div class="diagnostics-page">
    <div class="page-heading">
      <div>
        <h1>{{ t('diagnostics.title') }}</h1>
        <p class="subtitle">{{ t('diagnostics.subtitle') }}</p>
      </div>
      <span v-if="data" class="diagnostics-note"
        >{{ t('workbench.businessZone') }} · {{ data.businessZone }}</span
      >
    </div>
    <p v-if="!allowed" role="alert">{{ t('diagnostics.denied') }}</p>
    <template v-else>
      <LineTabs
        id="diagnostics-tabs"
        :label="t('diagnostics.title')"
        :value="category"
        :tabs="categories.map((value) => ({ value, label: t('diagnostics.' + value) }))"
        panel-id="diagnostics-panel"
        @change="change($event)"
      />
      <section
        id="diagnostics-panel"
        role="tabpanel"
        :aria-labelledby="'diagnostics-tabs-' + category"
      >
        <div class="diagnostics-toolbar">
          <label for="diagnostics-limit">{{ t('diagnostics.limit') }}</label
          ><CompactSelect
            id="diagnostics-limit"
            :label="t('diagnostics.limit')"
            :value="options?.limit ?? 20"
            :options="limits"
            @update:value="change(category, Number($event))"
          /><NButton
            class="list-query-action"
            type="primary"
            :loading="busy"
            :disabled="!options"
            @click="load"
            ><template #icon><RefreshCw :size="15" /></template
            >{{ t('diagnostics.refresh') }}</NButton
          ><span v-if="data" class="diagnostics-note diagnostics-observed">{{
            t('diagnostics.observed', {
              time: time(data.observedAt),
              zone: runtime.preferences.displayTimezone,
            })
          }}</span>
        </div>
        <div v-if="!options" role="alert" class="diagnostics-panel">
          <p>{{ t('diagnostics.invalid') }}</p>
          <NButton class="list-query-action" @click="change('LEASE_EXPIRED', 20)">{{
            t('diagnostics.reset')
          }}</NButton>
        </div>
        <div v-else-if="error" class="diagnostics-panel">
          <RequestError :error="error" /><NButton @click="load">{{ t('workbench.retry') }}</NButton>
        </div>
        <div v-else-if="busy" role="status" class="diagnostics-panel">
          <p>{{ t('diagnostics.loading') }}</p>
          <NSkeleton height="220px" />
        </div>
        <template v-else-if="data">
          <div class="diagnostics-explanation">
            <Info :size="18" aria-hidden="true" />
            <div>
              <p>{{ rule }}</p>
              <p class="diagnostics-note">
                {{
                  t(
                    category === 'QUEUE_DELAYED'
                      ? 'diagnostics.queueCaution'
                      : 'diagnostics.caution',
                  )
                }}
              </p>
            </div>
          </div>
          <section class="diagnostics-panel diagnostics-node">
            <div class="diagnostics-section-heading">
              <h2>{{ t('workbench.node') }}</h2>
              <span class="diagnostics-badge">{{
                data.node.roles.length === 1 && data.node.roles[0] === 'API'
                  ? 'API-only'
                  : data.node.roles.join(' · ')
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
                      (data.node.schedulerRoleConfigured && data.node.scheduleEnabled
                        ? 'enabled'
                        : 'disabled'),
                  )
                }}
              </dd>
            </dl>
            <p class="diagnostics-note">{{ t('workbench.clusterUnknown') }}</p>
          </section>
          <section class="diagnostics-panel diagnostics-results">
            <div class="diagnostics-section-heading">
              <h2>{{ t('diagnostics.results') }}</h2>
              <span data-testid="diagnostics-count">{{
                t('diagnostics.shown', { count: data.items.length })
              }}</span>
            </div>
            <p v-if="data.hasMore" role="status" class="diagnostics-more">
              {{ t('diagnostics.more', { count: data.items.length }) }}
            </p>
            <div class="diagnostics-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ t('diagnostics.target') }}</th>
                    <th>{{ t('diagnostics.trigger') }}</th>
                    <th>{{ t('diagnostics.status') }}</th>
                    <th>
                      {{ t('diagnostics.' + deadlineKey) }} ({{
                        runtime.preferences.displayTimezone
                      }})
                    </th>
                    <th>{{ t('diagnostics.action') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in data.items" :key="row.id">
                    <td>
                      <strong>{{ title(row) }}</strong
                      ><small>{{
                        category === 'COMMAND_EXPIRED'
                          ? row.commandType + ' · ' + row.instanceId
                          : [row.targetType, row.targetId].filter(Boolean).join(' · ')
                      }}</small>
                    </td>
                    <td>{{ row.triggerId || '—' }}</td>
                    <td>
                      <span class="diagnostics-state">{{ status(row.status) }}</span>
                    </td>
                    <td class="diagnostics-time">{{ time(row.observedDeadline) }}</td>
                    <td>
                      <NButton
                        text
                        type="primary"
                        :aria-label="t('diagnostics.evidenceFor', { id: row.id })"
                        @click="open(row, $event)"
                        >{{ t('diagnostics.evidence') }}</NButton
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <EmptyState
              v-if="!data.items.length"
              :message="t('diagnostics.empty') + ' · ' + t('diagnostics.emptyHint')"
            />
            <p class="diagnostics-note diagnostics-footnote">
              {{ t('diagnostics.bounded', { limit: data.limit }) }}
            </p>
          </section>
        </template>
      </section>
    </template>
    <NDrawer
      :show="!!selected && !!data && allowed"
      width="min(520px, 100vw)"
      class="diagnostics-drawer"
      :auto-focus="true"
      :trap-focus="true"
      @update:show="close"
      ><NDrawerContent :title="t('diagnostics.detail')" closable :native-scrollbar="true"
        ><template v-if="selected && data"
          ><p class="diagnostics-condition">{{ t('diagnostics.' + category) }}</p>
          <dl class="diagnostics-evidence">
            <dt>{{ t('diagnostics.target') }}</dt>
            <dd>{{ title(selected) }}</dd>
            <template
              v-for="field in ['triggerId', 'rootInstanceId', 'instanceId'] as const"
              :key="field"
              ><template v-if="selected[field]"
                ><dt>
                  {{
                    t(
                      'diagnostics.' +
                        { triggerId: 'trigger', rootInstanceId: 'root', instanceId: 'instance' }[
                          field
                        ],
                    )
                  }}
                </dt>
                <dd>{{ selected[field] }}</dd></template
              ></template
            >
            <dt>{{ t('diagnostics.status') }}</dt>
            <dd>
              {{ status(selected.status) }} <small>({{ selected.status }})</small>
            </dd>
            <template v-if="selected.commandType">
              <dt>{{ t('diagnostics.command') }}</dt>
              <dd>{{ selected.commandType }}</dd>
            </template>
            <dt>{{ t('diagnostics.' + deadlineKey) }}</dt>
            <dd>
              <TimeEvidence :value="selected.observedDeadline" />
              {{ runtime.preferences.displayTimezone }}
            </dd>
            <dt>{{ t('diagnostics.created') }}</dt>
            <dd>
              <TimeEvidence :value="selected.createTime" />
              {{ runtime.preferences.displayTimezone }}
            </dd>
          </dl>
          <p class="diagnostics-note">
            {{
              t('diagnostics.observed', {
                time: time(data.observedAt),
                zone: runtime.preferences.displayTimezone,
              })
            }}
          </p>
          <section class="diagnostics-evidence-section">
            <h3>{{ t('diagnostics.basis') }}</h3>
            <p>{{ rule }}</p>
          </section>
          <section class="diagnostics-evidence-section">
            <h3>{{ t('diagnostics.advice') }}</h3>
            <ol>
              <li>{{ t('diagnostics.adviceOne') }}</li>
              <li>{{ t('diagnostics.adviceTwo') }}</li>
            </ol>
            <p class="diagnostics-note">{{ t('diagnostics.caution') }}</p>
          </section></template
        ><template #footer
          ><NButton @click="close">{{ t('common.close') }}</NButton
          ><RouterLink v-if="link" :to="link" class="diagnostics-primary-link">{{
            t('diagnostics.' + linkLabel)
          }}</RouterLink></template
        ></NDrawerContent
      ></NDrawer
    >
  </div>
</template>
