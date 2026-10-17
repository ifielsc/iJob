<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import { approval } from './model'
import ApprovalDrawer from './ApprovalDrawer.vue'
const props = defineProps<{ instanceId: string; waiting: boolean }>(),
  emit = defineEmits<{ resolved: [] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  selected = ref(''),
  button = ref<InstanceType<typeof NButton>>()
const query = useQuery(
  computed(() => ({
    queryKey: [
      'instance-approval',
      runtime.session.revision,
      runtime.preferences.locale,
      props.instanceId,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      approval(
        await runtime.request('/api/event-waits/by-instance/' + props.instanceId, {
          signal,
          localForbidden: true,
        }),
      ),
    retry: false,
    gcTime: 0,
    enabled: props.waiting,
  })),
)
const status = computed(() =>
  query.error.value instanceof ApiError ? query.error.value.status : 0,
)
async function close() {
  selected.value = ''
  await nextTick()
  button.value?.$el?.focus()
}
</script>
<template>
  <div v-if="waiting || selected" class="approval-entry">
    <p v-if="status === 403" role="status">{{ t('approvals.noAccess') }}</p>
    <p v-else-if="status === 404" role="status">{{ t('approvals.notFound') }}</p>
    <RequestError v-else :error="query.error.value" />
    <NButton v-if="query.isError.value" @click="query.refetch()">{{ t('common.retry') }}</NButton>
    <NButton
      v-if="waiting && query.data.value?.waitType === 'APPROVAL' && !query.isError.value"
      ref="button"
      type="primary"
      secondary
      @click="selected = query.data.value!.id"
      >{{ t('approvals.detail') }}</NButton
    >
    <ApprovalDrawer v-if="selected" :id="selected" @close="close" @resolved="emit('resolved')" />
  </div>
</template>
