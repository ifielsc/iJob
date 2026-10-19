<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, provide } from 'vue'
import {
  collectReferences,
  referenceNamesKey,
  type ReferenceName,
} from '../../shared/runtime/reference-names'
import { useQuery } from '@tanstack/vue-query'
import { NDrawer, NDrawerContent, NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { parse } from 'lossless-json'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { taskRow } from './model'
import RequestError from '../../shared/ui/RequestError.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import ConfigFacts from '../../shared/ui/ConfigFacts.vue'
import PluginConfigDetails from './PluginConfigDetails.vue'
import PluginConfigTechnical from './PluginConfigTechnical.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import { pluginLabel } from '../../shared/plugins'
const props = defineProps<{ id: string; definitionId?: string }>()
defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const allowed = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const query = useQuery(
  computed(() => ({
    queryKey: ['task-readonly', runtime.session.revision, props.id, props.definitionId],
    enabled: allowed.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      taskRow(
        await runtime.request(
          '/api/tasks/' + props.id + (props.definitionId ? '/versions/' + props.definitionId : ''),
          { signal },
        ),
        true,
      ),
  })),
)
const row = computed(() => (allowed.value && !query.isError.value ? query.data.value : undefined))
const configuration = computed(() => {
  try {
    const value = parse(row.value?.pluginConfig ?? '{}')
    if (
      row.value?.pluginType === 'API_CALL' &&
      value &&
      typeof value === 'object' &&
      !Array.isArray(value)
    ) {
      return {
        ...value,
        headerNames: row.value.headerNames,
        remoteStopHeaderNames: row.value.remoteStopHeaderNames,
      }
    }
    return value
  } catch {
    return row.value?.pluginConfig ?? ''
  }
})
const references = computed(() => collectReferences(configuration.value))
const referenceQuery = useQuery(
  computed(() => ({
    queryKey: [
      'task-reference-names',
      runtime.session.revision,
      props.id,
      props.definitionId,
      references.value,
    ],
    enabled: allowed.value && !!row.value && references.value.length > 0,
    retry: false,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const result: ReferenceName[] = []
      for (let offset = 0; offset < references.value.length; offset += 200) {
        const batch = references.value.slice(offset, offset + 200)
        result.push(
          ...(await runtime.request<ReferenceName[]>('/api/reference-summaries', {
            method: 'POST',
            signal,
            body: {
              resourceIds: batch.filter((r) => r.kind === 'RESOURCE').map((r) => r.id),
              notificationIds: batch.filter((r) => r.kind === 'NOTIFICATION').map((r) => r.id),
            },
          })),
        )
      }
      return result
    },
  })),
)
const referenceNames = computed(
  () =>
    new Map(
      (allowed.value ? (referenceQuery.data.value ?? []) : []).map((r) => [r.kind + ':' + r.id, r]),
    ),
)
provide(referenceNamesKey, referenceNames)
const pluginScopes: Record<string, string> = {
  SQL: 'tasks',
  SCRIPT: 'tasks',
  API_CALL: 'apiCall',
  EVENT: 'eventTask',
  DATA_CHECK: 'dataCheck',
  FILE_TRANSFER: 'files',
  PUSH: 'push',
  ETL: 'etl',
  BATCH_ETL: 'batchEtl',
  SERVER_MONITOR: 'monitor',
  LOCAL_JAR: 'localJar',
}
const configScope = computed(() => pluginScopes[row.value?.pluginType ?? ''] ?? 'tasks')
</script>
<template>
  <NDrawer
    :show="allowed"
    width="min(640px, 100vw)"
    class="task-drawer"
    @update:show="$emit('close')"
    ><NDrawerContent :title="t('unified.taskDetails')" closable :native-scrollbar="true"
      ><IdentityText :value="id" :label="t('unified.taskId')" />
      <p class="task-note">
        {{ definitionId ? t('unified.historical') : t('unified.current') }} ·
        {{ runtime.preferences.displayTimezone }}
      </p>
      <RequestError :error="query.error.value" /><NButton
        v-if="query.isError.value"
        @click="query.refetch()"
        >{{ t('common.retry') }}</NButton
      >
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="row"
        ><h3>{{ t('unified.basic') }}</h3>
        <dl class="unified-facts">
          <dt>{{ t('tasks.name') }}</dt>
          <dd>{{ row.name }}</dd>
          <dt>{{ t('tasks.type') }}</dt>
          <dd>{{ pluginLabel(row.pluginType, t) }}</dd>
          <dt>{{ t('tasks.status') }}</dt>
          <dd :class="row.enabled ? 'status-success' : 'status-disabled'">
            {{ t(row.enabled ? 'tasks.enabled' : 'tasks.disabled') }}
          </dd>
          <dt>{{ t('tasks.version') }}</dt>
          <dd>v{{ row.currentRevision }}</dd>
          <dt>{{ t('tasks.group') }}</dt>
          <dd>{{ row.groups.join(', ') || '—' }}</dd>
          <dt>{{ t('tasks.updated') }}</dt>
          <dd><TimeEvidence :value="row.updateTime" /></dd>
          <dt>{{ t('tasks.description') }}</dt>
          <dd>{{ row.description || '—' }}</dd>
        </dl>
        <template v-if="references.length">
          <p class="experience-note">{{ t('experience.currentNames') }}</p>
          <RequestError :error="referenceQuery.error.value" />
          <NButton v-if="referenceQuery.isError.value" @click="referenceQuery.refetch()">{{
            t('common.retry')
          }}</NButton>
        </template>
        <h3>{{ t('unified.configuration') }}</h3>
        <PluginConfigDetails :plugin="row.pluginType" :value="configuration" :scope="configScope" />
        <p v-if="row.pluginType === 'SERVER_MONITOR'" class="task-note">
          {{ t('monitor.resultHelp') }}
        </p>
        <h3>{{ t('unified.strategy') }}</h3>
        <ConfigFacts
          :value="{
            retryTimes: row.retryTimes,
            retryInterval: row.retryInterval,
            timeout: row.timeout,
          }" />
        <details class="unified-technical">
          <summary>{{ t('unified.technical') }}</summary>
          <IdentityText :value="row.currentTaskDefinitionId" :label="t('jobs.definition')" />
          <PluginConfigTechnical
            :plugin="row.pluginType"
            :value="configuration"
            :scope="configScope"
          /></details></template
      ><template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      ></NDrawerContent
    ></NDrawer
  >
</template>
