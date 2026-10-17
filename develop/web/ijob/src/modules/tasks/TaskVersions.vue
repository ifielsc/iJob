<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NDrawer, NDrawerContent, NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { pageData, record, exactId } from './model'
import RequestError from '../../shared/ui/RequestError.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import TaskDetails from './TaskDetails.vue'
const props = defineProps<{ id: string }>()
defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  page = ref(1),
  size = ref(20),
  selected = ref<string>()
const allowed = computed(() => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value)
const query = useQuery(
  computed(() => ({
    queryKey: ['task-versions', runtime.session.revision, props.id, page.value, size.value],
    enabled: allowed.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      pageData(
        await runtime.request(
          `/api/tasks/${props.id}/versions?pageNum=${page.value}&pageSize=${size.value}`,
          { signal },
        ),
        (value) => {
          const r = record(value)
          return {
            definitionId: exactId(r.definitionId),
            revision: exactId(r.revision),
            name: String(r.name),
            publishedAt: String(r.publishedAt),
            current: r.current === true,
          }
        },
      ),
  })),
)
</script>
<template>
  <NDrawer
    :show="allowed && !selected"
    width="min(640px, 100vw)"
    class="task-drawer"
    @update:show="$emit('close')"
    ><NDrawerContent :title="t('unified.versionHistory')" closable :native-scrollbar="true"
      ><IdentityText :value="id" :label="t('unified.taskId')" /><RequestError
        :error="query.error.value"
      /><NButton v-if="query.isError.value" @click="query.refetch()">{{
        t('common.retry')
      }}</NButton>
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <div v-if="allowed && query.data.value && !query.isError.value" class="task-table-scroll">
        <table class="task-table task-version-table">
          <thead>
            <tr>
              <th>{{ t('unified.version') }}</th>
              <th>{{ t('tasks.name') }}</th>
              <th>{{ t('jobs.publishedAt') }} ({{ runtime.preferences.displayTimezone }})</th>
              <th class="task-actions-col">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in query.data.value.list" :key="v.definitionId">
              <td>v{{ v.revision }} {{ v.current ? '· ' + t('unified.current') : '' }}</td>
              <td>{{ v.name }}</td>
              <td><TimeEvidence :value="v.publishedAt" /></td>
              <td class="task-actions-col">
                <NButton
                  v-if="can(user, 'SENSITIVE_READ')"
                  text
                  type="primary"
                  @click="selected = v.definitionId"
                  >{{ t('common.details') }}</NButton
                >
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="!query.data.value.list.length" compact :message="t('common.empty')" />
      </div>
      <Pagination
        :page="page"
        :size="size"
        :total="query.data.value?.total ?? 0"
        :busy="query.isFetching.value"
        @page="page = $event"
        @size="((size = $event), (page = 1))"
      /><template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      ></NDrawerContent
    ></NDrawer
  ><TaskDetails
    v-if="selected && allowed"
    :id="id"
    :definition-id="selected"
    @close="selected = undefined"
  />
</template>
<style scoped>
.task-version-table {
  min-width: 560px;
}
.task-version-table .task-actions-col {
  min-width: 72px;
  width: 72px;
}
</style>
