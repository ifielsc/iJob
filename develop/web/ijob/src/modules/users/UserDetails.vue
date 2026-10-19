<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NDrawer, NDrawerContent, NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { parseAccount } from '../security-contracts'
import IdentityText from '../../shared/ui/IdentityText.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import RequestError from '../../shared/ui/RequestError.vue'
const props = defineProps<{ id: string }>()
defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const allowed = computed(() => can(user.value, 'USER_MANAGE') && !runtime.accessIssue.value)
const query = useQuery(
  computed(() => ({
    queryKey: ['user-detail', runtime.session.revision, props.id],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseAccount(await runtime.request('/api/users/' + props.id, { signal })),
    enabled: allowed.value,
    retry: false,
  })),
)
const row = computed(() => (allowed.value && !query.isError.value ? query.data.value : undefined))
</script>
<template>
  <NDrawer
    :show="allowed"
    width="min(480px, 100vw)"
    class="user-details"
    @update:show="$emit('close')"
    ><NDrawerContent :title="t('experience.viewUser')" closable>
      <RequestError :error="query.error.value" /><NButton
        v-if="query.isError.value"
        @click="query.refetch()"
        >{{ t('common.retry') }}</NButton
      >
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="row"
        ><h3>{{ row.displayName || row.username }}</h3>
        <IdentityText :value="row.id" />
        <dl class="unified-facts">
          <dt>{{ t('users.username') }}</dt>
          <dd>{{ row.username }}</dd>
          <dt>{{ t('users.displayName') }}</dt>
          <dd>{{ row.displayName }}</dd>
          <dt>{{ t('users.role') }}</dt>
          <dd>{{ t('users.' + row.roleCode) }}</dd>
          <dt>{{ t('users.status') }}</dt>
          <dd :class="row.enabled ? 'status-success' : 'status-disabled'">
            {{ t(row.enabled ? 'users.enabled' : 'users.disabled') }}
          </dd>
          <dt>{{ t('users.firstChange') }}</dt>
          <dd>{{ t(row.mustChangePassword ? 'experience.yes' : 'experience.no') }}</dd>
          <dt>{{ t('runs.createTime') }}</dt>
          <dd><TimeEvidence :value="row.createTime ?? ''" /></dd>
          <dt>{{ t('runs.updateTime') }}</dt>
          <dd><TimeEvidence :value="row.updateTime ?? ''" /></dd>
        </dl>
        <details class="unified-technical">
          <summary>{{ t('unified.technical') }}</summary>
          <IdentityText :value="row.loginId" label="Login ID" />
          <p>{{ t('users.revision') }}: {{ row.revision }}</p>
        </details>
      </template>
      <template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent></NDrawer
  >
</template>
