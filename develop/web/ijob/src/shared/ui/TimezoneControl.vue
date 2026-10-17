<script setup lang="ts">
import { computed, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../auth/access'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const query = useQuery(
  computed(() => ({
    queryKey: ['business-timezone', runtime.session.revision],
    staleTime: Infinity,
    enabled: can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const v = await runtime.request<{ zoneId: string }>('/api/schedules/metadata', { signal })
      if (typeof v.zoneId !== 'string') throw new Error('Missing business timezone')
      new Intl.DateTimeFormat('en', { timeZone: v.zoneId }).format()
      return v.zoneId
    },
  })),
)
watch(
  () => query.data.value,
  (value) => runtime.preferences.setBusinessZone(value ?? null),
  { immediate: true },
)
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) runtime.preferences.setBusinessZone(null)
  },
)
</script>
<template>
  <div class="timezone-control">
    <NButton
      text
      size="small"
      :title="t('unified.timezoneHint')"
      :aria-label="t('unified.displayZone') + ': ' + runtime.preferences.displayTimezone"
      @click="
        runtime.preferences.setTimeMode(runtime.preferences.timeMode === 'UTC' ? 'business' : 'UTC')
      "
      >{{ t('unified.displayZone') }}: {{ runtime.preferences.displayTimezone }}</NButton
    ><NButton
      v-if="query.isError.value"
      text
      size="tiny"
      :title="t('unified.utcFallback')"
      @click="query.refetch()"
      >{{ t('unified.fallbackShort') }} · {{ t('common.retry') }}</NButton
    >
  </div>
</template>
<style scoped>
.timezone-control {
  display: flex;
  flex-wrap: wrap;
  max-width: 100%;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
</style>
