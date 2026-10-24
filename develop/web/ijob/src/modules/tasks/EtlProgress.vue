<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NInput, NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { record } from '../../shared/api/values'
import { formatUtc } from '../../shared/time/format'
import RequestError from '../../shared/ui/RequestError.vue'
import EmptyState from '../../shared/ui/EmptyState.vue'
const props = defineProps<{ taskId?: string; revision?: string; dirty: boolean }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const filter = ref('')
const allowed = computed(() => !runtime.accessIssue.value && can(user.value, 'SENSITIVE_READ'))
const query = useQuery({
  queryKey: computed(() => [
    'etl-progress',
    runtime.session.revision,
    props.taskId,
    props.revision,
  ]),
  enabled: computed(() => allowed.value && !!props.taskId),
  retry: false,
  gcTime: 0,
  queryFn: async ({ signal }) => {
    const result = record(
      await runtime.request('/api/tasks/' + props.taskId + '/etl-progress', { signal }),
    )
    if (!Array.isArray(result.checkpoints)) throw Error('Invalid checkpoint response')
    return {
      mode: String(result.mode),
      revision: String(result.revision),
      rows: result.checkpoints.map((value) => {
        const r = record(value)
        const text = (key: string) => (r[key] == null ? '' : String(r[key]))
        return {
          sourceTable: text('sourceTable'),
          targetTable: text('targetTable'),
          checkpointKey: text('checkpointKey'),
          watermarkField: text('watermarkField'),
          watermarkType: text('watermarkType'),
          initialValue: text('initialValue'),
          watermarkValue: text('watermarkValue'),
          tieBreakerValue: text('tieBreakerValue'),
          version: text('version'),
          lastInstanceId: text('lastInstanceId'),
          updateTime: text('updateTime'),
          status: text('status'),
          configurationMatch: text('configurationMatch'),
        }
      }),
    }
  },
})
const data = query.data
const rows = computed(
  () =>
    data.value?.rows.filter((r) =>
      (r.sourceTable + r.targetTable + r.checkpointKey)
        .toLowerCase()
        .includes(filter.value.toLowerCase()),
    ) ?? [],
)
const time = (value: string) =>
  value ? formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone) : '—'
</script>
<template>
  <section class="checkpoint-panel">
    <p class="task-note">{{ t('insights.savedOnly') }}</p>
    <p v-if="dirty" role="status" class="checkpoint-warning">{{ t('insights.unsaved') }}</p>
    <p v-if="!taskId">{{ t('insights.newTask') }}</p>
    <p v-else-if="!allowed">{{ t('insights.permission') }}</p>
    <template v-else>
      <div class="checkpoint-tools">
        <NInput
          v-model:value="filter"
          clearable
          :placeholder="t('insights.search')"
          :input-props="{ 'aria-label': t('insights.search') }"
        />
        <NButton :loading="query.isFetching.value" @click="query.refetch()">{{
          t('insights.refresh')
        }}</NButton>
      </div>
      <RequestError :error="query.error.value" />
      <p v-if="data && data.revision !== revision" role="alert">
        {{ t('insights.revisionChanged') }}
      </p>
      <p v-if="data?.mode !== 'INCREMENTAL' && data">
        {{ t(data.mode === 'OTHER_SOURCE' ? 'insights.otherSource' : 'insights.disabled') }}
      </p>
      <div v-else-if="data" class="checkpoint-scroll">
        <EmptyState v-if="!rows.length" compact :message="t('common.empty')" />
        <article
          v-for="row in rows"
          :key="row.checkpointKey + row.sourceTable"
          class="checkpoint-card"
        >
          <div class="checkpoint-title">
            <strong
              >{{ row.sourceTable || t('insights.orphan')
              }}<template v-if="row.targetTable"> → {{ row.targetTable }}</template></strong
            >
            <NTag size="small" :type="row.status === 'ADVANCED' ? 'success' : 'default'">{{
              t('insights.' + row.status)
            }}</NTag>
          </div>
          <dl>
            <dt>{{ t('insights.field') }}</dt>
            <dd>{{ row.watermarkField || '—' }} / {{ row.watermarkType }}</dd>
            <dt>{{ t('insights.watermark') }}</dt>
            <dd class="checkpoint-value">{{ row.watermarkValue || '—' }}</dd>
            <dt>{{ t('insights.updated') }}</dt>
            <dd>{{ time(row.updateTime) }}</dd>
            <dt>{{ t('insights.match') }}</dt>
            <dd>{{ t('insights.' + row.configurationMatch) }}</dd>
          </dl>
          <details>
            <summary>{{ t('insights.details') }}</summary>
            <dl>
              <dt>{{ t('insights.initial') }}</dt>
              <dd>{{ row.initialValue || '—' }}</dd>
              <dt>{{ t('insights.ties') }}</dt>
              <dd>{{ row.tieBreakerValue || '—' }}</dd>
              <dt>{{ t('insights.instance') }}</dt>
              <dd>{{ row.lastInstanceId || '—' }}</dd>
              <dt>{{ t('insights.version') }}</dt>
              <dd>{{ row.version || '—' }}</dd>
              <dt>{{ t('insights.checkpointKey') }}</dt>
              <dd>
                {{ row.checkpointKey }}
                <p class="task-note">{{ t('insights.checkpointHint') }}</p>
              </dd>
            </dl>
          </details>
        </article>
      </div>
    </template>
    <p class="task-note">{{ t('insights.progressNote') }}</p>
  </section>
</template>
<style scoped>
.checkpoint-panel {
  min-width: 0;
}
.checkpoint-tools,
.checkpoint-title {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.checkpoint-title {
  flex-wrap: wrap;
}
.checkpoint-scroll {
  max-height: min(620px, 62dvh);
  overflow: auto;
}
.checkpoint-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.checkpoint-card dl {
  display: grid;
  grid-template-columns: minmax(90px, 32%) minmax(0, 1fr);
  gap: 8px;
  font-size: 12px;
}
.checkpoint-card dt {
  color: var(--muted);
}
.checkpoint-card dd {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.checkpoint-card summary {
  cursor: pointer;
  color: var(--primary);
}
.checkpoint-value {
  font-family: monospace;
}
.checkpoint-warning {
  color: var(--muted);
  border-left: 3px solid var(--primary);
  padding-left: 8px;
}
</style>
