<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import ConfigList from './ConfigList.vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LineTabs from '../../shared/ui/LineTabs.vue'
import NotificationDeliveries from './NotificationDeliveries.vue'
const route = useRoute(),
  router = useRouter(),
  { t } = useI18n()
const memory = new Map<string, typeof route.query>()
async function change(tab: string) {
  const current = route.query.tab === 'records' ? 'records' : 'configurations'
  if (tab === current) return
  const previous = { ...route.query }
  const failure = await router.push({ query: memory.get(tab) ?? { tab } })
  if (!failure) memory.set(current, previous)
}
</script>
<template>
  <component :is="route.query.tab === 'records' ? NotificationDeliveries : ConfigList">
    <template #tabs>
      <LineTabs
        id="notification-tabs"
        :panel-id="route.query.tab === 'records' ? 'run-list-panel' : 'notification-list-panel'"
        :label="t('config.titleNotifications')"
        :value="route.query.tab === 'records' ? 'records' : 'configurations'"
        :tabs="['configurations', 'records'].map((value) => ({ value, label: t('runs.' + value) }))"
        @change="change"
      />
    </template>
  </component>
</template>
