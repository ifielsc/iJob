<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { formatUtc } from '../../shared/time/format'
import RequestError from '../../shared/ui/RequestError.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import { pageData, version } from './model'
import './jobs.css'
const route = useRoute(),
  router = useRouter(),
  runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  page = ref(1),
  size = ref(20)
const query = useQuery(
  computed(() => ({
    queryKey: [
      'job-versions',
      runtime.session.revision,
      runtime.preferences.locale,
      route.params.id,
      page.value,
      size.value,
    ],
    enabled: !runtime.accessIssue.value && can(user.value, 'DEFINITION_READ'),
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      pageData(
        await runtime.request(
          `/api/jobs/${String(route.params.id)}/versions?pageNum=${page.value}&pageSize=${size.value}`,
          { signal },
        ),
        version,
      ),
  })),
)
function time(value: string) {
  try {
    return formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone)
  } catch {
    return '—'
  }
}
function resize(value: number) {
  size.value = value
  page.value = 1
}
const listBack = computed(() =>
  typeof route.query.back === 'string' && /^\/jobs(?:\?[^#]*)?$/.test(route.query.back)
    ? route.query.back
    : '/jobs',
)
function jobLink(path: string) {
  return { path, query: { back: listBack.value } }
}
</script>
<template>
  <section class="jobs-page">
    <header class="job-heading list-heading">
      <div>
        <NButton text @click="router.push(listBack)">{{ t('jobs.back') }}</NButton>
        <h1>{{ t('jobs.versions') }}</h1>
        <p>{{ t('jobs.job') }} #{{ route.params.id }}</p>
      </div>
      <NButton
        class="list-query-action"
        :loading="query.isFetching.value"
        @click="query.refetch()"
        >{{ t('jobs.refresh') }}</NButton
      >
    </header>
    <div class="job-panel list-panel">
      <RequestError :error="query.error.value" />
      <div class="job-table-scroll">
        <table class="job-table job-version-table">
          <thead>
            <tr>
              <th>{{ t('unified.version') }}</th>
              <th>{{ t('jobs.name') }}</th>
              <th>{{ t('jobs.definition') }}</th>
              <th>{{ t('jobs.publishedAt') }}</th>
              <th>{{ t('tasks.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in runtime.accessIssue.value ? [] : query.data.value?.list"
              :key="item.definitionId"
            >
              <td>v{{ item.revision }} {{ item.current ? '· ' + t('jobs.current') : '' }}</td>
              <td>{{ item.name }}</td>
              <td>{{ item.definitionId }}</td>
              <td>{{ time(item.publishedAt) }}</td>
              <td>
                <NButton
                  v-if="can(user, 'SENSITIVE_READ')"
                  text
                  type="primary"
                  @click="
                    router.push(jobLink('/jobs/' + item.jobId + '/versions/' + item.definitionId))
                  "
                  >{{ t('jobs.view') }}</NButton
                ><span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-if="!query.data.value?.list.length && !query.isFetching.value && !query.isError.value"
        compact
        :message="t('jobs.noVersions')"
      />
      <Pagination
        :page="page"
        :size="size"
        :total="query.data.value?.total ?? 0"
        :busy="query.isFetching.value"
        @page="page = $event"
        @size="resize"
      />
    </div>
  </section>
</template>
