<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import RequestError from '../../shared/ui/RequestError.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import { parseMailChannel } from './mail-channel-model'
const props = defineProps<{ id: string }>()
defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const allowed = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const query = useQuery(
  computed(() => ({
    queryKey: ['mail-channel-detail', props.id, runtime.session.revision],
    enabled: allowed.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseMailChannel(await runtime.request('/api/mail-channels/' + props.id, { signal })),
  })),
)
const row = computed(() => (allowed.value && !query.isError.value ? query.data.value : undefined))
</script>
<template>
  <NDrawer
    :show="allowed"
    width="min(520px, 100vw)"
    class="configuration-drawer mail-channel-details"
    @update:show="$emit('close')"
  >
    <NDrawerContent :title="t('common.details')" closable :native-scrollbar="true">
      <RequestError :error="query.error.value" />
      <NButton v-if="query.isError.value" @click="query.refetch()">{{ t('common.retry') }}</NButton>
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="row">
        <h3>{{ t('unified.basic') }}</h3>
        <dl class="unified-facts">
          <dt>{{ t('mailChannels.name') }}</dt>
          <dd>{{ row.name }}</dd>
          <dt>{{ t('config.status') }}</dt>
          <dd :class="row.enabled ? 'status-success' : 'status-disabled'">
            {{ t(row.enabled ? 'config.enabled' : 'config.disabled') }}
          </dd>
          <dt>{{ t('tasks.updated') }}</dt>
          <dd>
            <TimeEvidence v-if="row.updateTime" :value="row.updateTime" /><template v-else
              >—</template
            >
          </dd>
        </dl>
        <h3>{{ t('mailChannels.connection') }}</h3>
        <dl class="unified-facts">
          <dt>{{ t('mailChannels.host') }}</dt>
          <dd>{{ row.host }}</dd>
          <dt>{{ t('mailChannels.port') }}</dt>
          <dd>{{ row.port }}</dd>
          <dt>{{ t('mailChannels.security') }}</dt>
          <dd>{{ t('mailChannels.' + row.security) }}</dd>
          <dt>{{ t('mailChannels.authentication') }}</dt>
          <dd>{{ t(row.authentication ? 'config.enabled' : 'config.disabled') }}</dd>
          <dt v-if="row.authentication">{{ t('mailChannels.username') }}</dt>
          <dd v-if="row.authentication">{{ row.username }}</dd>
          <dt>{{ t('mailChannels.from') }}</dt>
          <dd>{{ row.from }}</dd>
        </dl>
        <details class="unified-technical">
          <summary>{{ t('unified.technical') }}</summary>
          <IdentityText :value="row.id" />
          <dl class="unified-facts">
            <dt>{{ t('mailChannels.revision') }}</dt>
            <dd>{{ row.revision }}</dd>
            <dt>{{ t('mailChannels.password') }}</dt>
            <dd>
              {{
                t(row.passwordConfigured ? 'mailChannels.configured' : 'mailChannels.notConfigured')
              }}
            </dd>
          </dl>
        </details>
      </template>
      <template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
