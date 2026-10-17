<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { parseAudit } from '../security-contracts'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import { X } from '@lucide/vue'
import './audits.css'
const props = defineProps<{ id: string }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n(),
  runtime = useRuntime()
const { user } = useIdentity()
const query = useQuery(
  computed(() => ({
    queryKey: ['audit-detail', runtime.session.revision, runtime.preferences.locale, props.id],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseAudit(await runtime.request(`/api/audits/${props.id}`, { signal })),
    enabled: can(user.value, 'AUDIT_READ') && !runtime.accessIssue.value,
  })),
)
const fields = [
  'id',
  'actorLoginId',
  'actionCode',
  'targetType',
  'targetId',
  'traceId',
  'requestId',
  'changedFields',
] as const
</script>
<template>
  <NDrawer
    class="audit-drawer"
    :show="true"
    width="min(520px, 100vw)"
    :auto-focus="true"
    :trap-focus="true"
    :close-on-esc="true"
    :mask-closable="true"
    @update:show="emit('close')"
    ><NDrawerContent :title="t('audits.detail')"
      ><template #header
        ><div class="audit-drawer-title">
          <h2>{{ t('audits.detail') }}</h2>
          <NButton
            class="audit-close"
            quaternary
            circle
            :aria-label="t('common.close')"
            @click="emit('close')"
          >
            <template #icon><X :size="18" /></template>
          </NButton></div
      ></template>
      <div class="audit-detail">
        <p class="notice">{{ t('audits.detailHint') }}</p>
        <RequestError :error="query.error.value" /><NButton
          v-if="query.isError.value"
          @click="query.refetch()"
          >{{ t('common.retry') }}</NButton
        >
        <p v-if="query.isPending.value" role="status">{{ t('common.loading') }}</p>
        <dl v-else-if="query.data.value && !query.isError.value" class="detail-list">
          <dt>{{ t('audits.time') }}</dt>
          <dd><TimeEvidence :value="query.data.value.occurredAt" /></dd>
          <dt>{{ t('audits.resultCode') }}</dt>
          <dd>
            <span
              class="result-badge"
              :class="`result-${query.data.value.resultCode.toLowerCase()}`"
              >{{ t(`status.${query.data.value.resultCode}`, query.data.value.resultCode) }}</span
            >
          </dd>
          <dt>{{ t('audits.actor') }}</dt>
          <dd>{{ query.data.value.actorName || t('common.missing') }}</dd>
          <template v-for="field in fields" :key="field"
            ><dt>{{ t(`audits.${field}`) }}</dt>
            <dd>{{ query.data.value[field] || t('common.missing') }}</dd></template
          >
        </dl>
      </div>
      <template #footer
        ><NButton size="small" @click="emit('close')">{{ t('common.close') }}</NButton></template
      ></NDrawerContent
    ></NDrawer
  >
</template>
