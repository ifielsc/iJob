<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { resource } from './model'
import IdentityText from '../../shared/ui/IdentityText.vue'
import RequestError from '../../shared/ui/RequestError.vue'
const props = defineProps<{ id: string }>()
defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const allowed = computed(() => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value)
const query = useQuery(
  computed(() => ({
    queryKey: ['concurrency-details', runtime.session.revision, props.id],
    enabled: allowed.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      resource(await runtime.request('/api/concurrency/resources/' + props.id, { signal })),
  })),
)
const row = computed(() => (allowed.value && !query.isError.value ? query.data.value : undefined))
</script>
<template>
  <NDrawer
    :show="allowed"
    width="min(520px, 100vw)"
    class="concurrency-drawer"
    @update:show="$emit('close')"
  >
    <NDrawerContent :title="t('concurrency.viewResource')" closable :native-scrollbar="true">
      <RequestError :error="query.error.value" />
      <NButton v-if="query.isError.value" @click="query.refetch()">{{ t('common.retry') }}</NButton>
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="row">
        <h3>{{ t('unified.basic') }}</h3>
        <dl class="unified-facts">
          <dt>{{ t('concurrency.name') }}</dt>
          <dd>{{ row.name }}</dd>
          <dt>{{ t('concurrency.code') }}</dt>
          <dd>{{ row.code }}</dd>
          <dt>{{ t('concurrency.status') }}</dt>
          <dd>{{ t(row.enabled ? 'concurrency.on' : 'concurrency.off') }}</dd>
          <dt>{{ t('concurrency.description') }}</dt>
          <dd>{{ row.description || '—' }}</dd>
        </dl>
        <h3>{{ t('concurrency.policy') }}</h3>
        <dl class="unified-facts">
          <dt>{{ t('concurrency.maximum') }}</dt>
          <dd>{{ row.maxConcurrency }}</dd>
          <dt>{{ t('details.referenceCount') }}</dt>
          <dd>{{ row.referenceCount }}</dd>
          <dt>{{ t('details.activeInstanceCount') }}</dt>
          <dd>{{ row.activeInstanceCount }}</dd>
        </dl>
        <details class="unified-technical">
          <summary>{{ t('unified.technical') }}</summary>
          <IdentityText :value="row.id" />
          <p>{{ t('concurrency.version') }} {{ row.version }}</p>
        </details>
      </template>
      <template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
