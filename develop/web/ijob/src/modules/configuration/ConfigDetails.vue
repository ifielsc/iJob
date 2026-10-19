<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Eye } from '@lucide/vue'
import type { PreviewOptions } from './notification-preview'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { parseConfig } from './contracts'
import RequestError from '../../shared/ui/RequestError.vue'
import NotificationPreview from './NotificationPreview.vue'
const preview = ref(false)
import ConfigFacts from '../../shared/ui/ConfigFacts.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
const props = defineProps<{ id: string; kind: 'resources' | 'notifications' }>()
defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const allowed = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const query = useQuery(
  computed(() => ({
    queryKey: ['config-details', props.kind, props.id, runtime.session.revision],
    enabled: allowed.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseConfig(
        await runtime.request(
          `/api/${props.kind === 'resources' ? 'resources' : 'notify-configs'}/${props.id}`,
          { signal },
        ),
        true,
      ),
  })),
)
const row = computed(() => (allowed.value && !query.isError.value ? query.data.value : undefined))
const previewOptions = computed<PreviewOptions>(() => {
  const c = row.value?.config
  return {
    type: row.value?.type ?? 'EMAIL',
    format: c?.format ?? 'Markdown',
    members: c?.members ?? [],
    mobiles: c?.atMobiles ?? c?.mobiles ?? [],
    roles: c?.roles ?? [],
    atAll: c?.atAll ?? false,
    mentionMode: c?.mentionMode ?? 'none',
    keyword: c?.keyword,
  }
})
const parsedConfiguration = computed(
  () => row.value?.config?.file ?? row.value?.config?.monitor ?? row.value?.config,
)
const configuration = computed(() =>
  Object.fromEntries(
    Object.entries(parsedConfiguration.value ?? {}).filter(([key]) => !key.endsWith('Configured')),
  ),
)
const configurationStates = computed(() =>
  Object.fromEntries(
    Object.entries(parsedConfiguration.value ?? {}).filter(([key]) => key.endsWith('Configured')),
  ),
)
const configScope = computed(
  () =>
    (
      ({
        FILE: 'fileResource',
        MONITOR_SERVER: 'monitorResource',
        SERVICE: 'serviceResource',
        KAFKA: 'mqResource',
        RABBITMQ: 'mqResource',
      }) as Record<string, string>
    )[row.value?.type ?? ''] ?? 'config',
)
</script>
<template>
  <NDrawer
    :show="allowed"
    width="min(520px, 100vw)"
    class="configuration-drawer"
    @update:show="$emit('close')"
    ><NDrawerContent :title="t('common.details')" closable :native-scrollbar="true"
      ><RequestError :error="query.error.value" /><NButton
        v-if="query.isError.value"
        @click="query.refetch()"
        >{{ t('common.retry') }}</NButton
      >
      <p v-if="query.isPending.value">{{ t('common.loading') }}</p>
      <template v-if="row"
        ><h3>{{ t('unified.basic') }}</h3>
        <dl class="unified-facts">
          <dt>{{ t('config.name') }}</dt>
          <dd>{{ row.name }}</dd>
          <template v-if="row.code"
            ><dt>{{ t('config.code') }}</dt>
            <dd>{{ row.code }}</dd></template
          >
          <dt>{{ t('config.status') }}</dt>
          <dd>{{ t(row.enabled ? 'config.enabled' : 'config.disabled') }}</dd>
          <dt>{{ t('config.description') }}</dt>
          <dd>{{ row.description || '—' }}</dd>
          <template v-if="row.updateTime"
            ><dt>{{ t('tasks.updated') }}</dt>
            <dd><TimeEvidence :value="row.updateTime" /></dd
          ></template>
        </dl>
        <h3>
          {{
            t(
              kind === 'resources'
                ? 'details.resourceConfiguration'
                : 'details.notificationConfiguration',
            )
          }}
        </h3>
        <ConfigFacts :value="configuration" :scope="configScope" />
        <p v-if="kind === 'notifications' && row.type !== 'EMAIL'" class="notification-protection">
          {{ t('config.rateProtectionHint') }}
        </p>
        <p v-if="kind === 'notifications'" class="configuration-note">
          {{ t('config.snapshotHint') }}
        </p>
        <p v-if="row.type === 'EMAIL' && !row.config?.mailChannelId" class="configuration-note">
          {{ t('mailChannels.requiredHint') }}
        </p>
        <details class="unified-technical">
          <summary>{{ t('unified.technical') }}</summary>
          <IdentityText :value="row.id" />
          <template v-if="Object.keys(configurationStates).length">
            <h4>{{ t('details.configurationState') }}</h4>
            <ConfigFacts :value="configurationStates" :scope="configScope" />
          </template></details></template
      ><template #footer
        ><div class="configuration-footer">
          <NButton
            v-if="kind === 'notifications' && row"
            text
            type="primary"
            class="notification-preview-action"
            @click="preview = true"
            ><template #icon><Eye :size="16" aria-hidden="true" /></template
            >{{ t('config.previewContent') }}</NButton
          ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton>
        </div></template
      ></NDrawerContent
    ></NDrawer
  >
  <NotificationPreview v-if="preview && row" :options="previewOptions" @close="preview = false" />
</template>
