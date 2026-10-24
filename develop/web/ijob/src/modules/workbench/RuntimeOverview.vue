<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { record } from '../../shared/api/values'
import { formatUtc } from '../../shared/time/format'
import LineTabs from '../../shared/ui/LineTabs.vue'
import RequestError from '../../shared/ui/RequestError.vue'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t, te } = useI18n()
const tab = ref('nodes')
const allowed = computed(() => !runtime.accessIssue.value && can(user.value, 'SUMMARY_READ'))
const query = useQuery({
  queryKey: computed(() => ['workbench-runtime', runtime.session.revision]),
  enabled: allowed,
  retry: false,
  refetchInterval: 10000,
  gcTime: 0,
  queryFn: async ({ signal }) => {
    const value = record(await runtime.request('/api/workbench/runtime', { signal }))
    const nodes = (v: unknown) => {
      if (!Array.isArray(v)) throw Error('Invalid runtime response')
      return v.map((r) => {
        const row = record(r),
          text = (key: string) => (row[key] == null ? '' : String(row[key]))
        return {
          runId: text('runId'),
          name: text('name'),
          roles: text('roles'),
          groups: text('groups'),
          status: text('status'),
          startedAt: text('startedAt'),
          heartbeatAt: text('heartbeatAt'),
          stoppedAt: text('stoppedAt'),
          health: text('health'),
          active: text('active'),
          capacity: text('capacity'),
          renewalFailures: text('renewalFailures'),
          lastScanAt: text('lastScanAt'),
          lastRenewAt: text('lastRenewAt'),
          scheduleEnabled: row.scheduleEnabled === true,
        }
      })
    }
    return {
      queriedAt: String(value.queriedAt),
      mode: String(value.mode),
      staleAfterSeconds: String(value.staleAfterSeconds),
      counts: Object.fromEntries(
        ['online', 'workers', 'stale', 'starting', 'queued', 'claimed', 'expired'].map((k) => [
          k,
          String(value[k]),
        ]),
      ),
      oldestQueuedAt: value.oldestQueuedAt ? String(value.oldestQueuedAt) : '',
      truncated: value.truncated === true,
      nodes: nodes(value.nodes),
      history: nodes(value.history),
    }
  },
})
const data = query.data
const rows = computed(() =>
  tab.value === 'history' ? (data.value?.history ?? []) : (data.value?.nodes ?? []),
)
const label = (v: string) => (te('insights.' + v) ? t('insights.' + v) : v)
const time = (v: string) =>
  v ? formatUtc(v, runtime.preferences.locale, runtime.preferences.displayTimezone) : '—'
const tag = (v: string) =>
  ['AVAILABLE', 'ONLINE', 'BUSY'].includes(v)
    ? ('success' as const)
    : ['DEGRADED', 'STALE', 'CRASHED', 'UNAVAILABLE'].includes(v)
      ? ('warning' as const)
      : ('default' as const)
</script>
<template>
  <section v-if="allowed" class="workbench-card runtime-overview">
    <div class="runtime-heading">
      <h2>{{ t('insights.cluster') }}</h2>
      <NButton size="small" :loading="query.isFetching.value" @click="query.refetch()">{{
        t('insights.refresh')
      }}</NButton>
    </div>
    <p class="workbench-note">{{ t('insights.clusterHint') }}</p>
    <RequestError :error="query.error.value" />
    <template v-if="data && !query.error.value">
      <p class="workbench-note">
        {{ t('insights.statusSnapshot') }}: {{ time(data.queriedAt) }} · {{ data.mode }} ·
        {{ data.staleAfterSeconds }}s
      </p>
      <p v-if="data.mode === 'MEMORY'">{{ t('insights.memoryMode') }}</p>
      <p v-if="data.truncated" role="alert">{{ t('insights.runtimeTruncated') }}</p>
      <div class="runtime-counts">
        <div v-for="(count, key) in data.counts" :key="key">
          <span>{{ t('insights.' + key) }}</span
          ><strong>{{ count }}</strong>
        </div>
      </div>
      <p class="workbench-note">{{ t('insights.oldest') }}: {{ time(data.oldestQueuedAt) }}</p>
      <LineTabs
        id="runtime-tabs"
        :label="t('insights.cluster')"
        :value="tab"
        :tabs="['nodes', 'history'].map((value) => ({ value, label: t('insights.' + value) }))"
        panel-id="runtime-panel"
        @change="tab = $event"
      />
      <div
        id="runtime-panel"
        role="tabpanel"
        :aria-labelledby="'runtime-tabs-' + tab"
        class="runtime-scroll workbench-table-scroll"
      >
        <table>
          <thead>
            <tr>
              <th>{{ t('insights.node') }}</th>
              <th>{{ t('insights.status') }}</th>
              <th>{{ t('insights.heartbeat') }}</th>
              <th>{{ t('insights.health') }}</th>
              <th>{{ t('insights.capacity') }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="row in rows" :key="row.runId">
              <tr>
                <td>
                  <strong>{{ row.name }}</strong
                  ><small>{{ row.roles }}</small>
                </td>
                <td>
                  <NTag size="small" :type="tag(row.status)">{{ label(row.status) }}</NTag>
                </td>
                <td>{{ time(row.heartbeatAt) }}</td>
                <td>
                  <NTag size="small" :type="tag(row.health)">{{ label(row.health) }}</NTag>
                </td>
                <td>{{ row.active || '—' }} / {{ row.capacity || '—' }}</td>
              </tr>
              <tr>
                <td colspan="5">
                  <details>
                    <summary>{{ t('insights.details') }} · {{ row.name }}</summary>
                    <dl>
                      <dt>{{ t('insights.runId') }}</dt>
                      <dd>{{ row.runId }}</dd>
                      <dt>{{ t('insights.groups') }}</dt>
                      <dd>{{ row.groups || '—' }}</dd>
                      <dt>{{ t('insights.started') }}</dt>
                      <dd>{{ time(row.startedAt) }}</dd>
                      <dt>{{ t('insights.stopped') }}</dt>
                      <dd>{{ time(row.stoppedAt) }}</dd>
                      <dt>{{ t('insights.lastScan') }}</dt>
                      <dd>{{ time(row.lastScanAt) }}</dd>
                      <dt>{{ t('insights.lastRenew') }}</dt>
                      <dd>{{ time(row.lastRenewAt) }}</dd>
                      <dt>{{ t('insights.renewalFailures') }}</dt>
                      <dd>{{ row.renewalFailures || '—' }}</dd>
                      <dt>{{ t('workbench.scheduler') }}</dt>
                      <dd>
                        {{ t(row.scheduleEnabled ? 'workbench.enabled' : 'workbench.disabled') }}
                      </dd>
                    </dl>
                  </details>
                </td>
              </tr>
            </template>
            <tr v-if="!rows.length">
              <td colspan="5">{{ t('insights.runtimeEmpty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="workbench-note">{{ t('insights.noHealthyClaim') }}</p>
    </template>
  </section>
</template>
<style scoped>
.runtime-overview {
  margin: 16px 0;
  min-width: 0;
}
.runtime-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.runtime-heading h2 {
  margin: 0;
}
.runtime-counts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.runtime-counts div {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
}
.runtime-counts span {
  color: var(--muted);
  font-size: 12px;
  display: block;
}
.runtime-counts strong {
  font-size: 24px;
}
.runtime-scroll {
  max-height: 480px;
  overflow: auto;
}
.runtime-scroll table {
  width: 100%;
  min-width: 700px;
  border-collapse: collapse;
  font-size: 13px;
}
.runtime-scroll :is(th, td):last-child {
  position: static;
  border-left: 0;
}
.runtime-scroll tbody tr:hover td {
  background: var(--selected);
}
.runtime-scroll small {
  display: block;
  color: var(--muted);
}
.runtime-scroll summary {
  cursor: pointer;
  color: var(--primary);
}
.runtime-scroll dl {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 6px;
}
.runtime-scroll dd {
  margin: 0;
  overflow-wrap: anywhere;
}
</style>
