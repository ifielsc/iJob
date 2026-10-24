<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { presentationForRoute } from '../../shared/navigation/route-presentation'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  ChevronDown,
  ChevronLeft,
  ChevronsLeft,
  ChevronsRight,
  CircleUserRound,
  Globe2,
} from '@lucide/vue'
import { NButton } from 'naive-ui'
import TimezoneControl from '../../shared/ui/TimezoneControl.vue'
import '../../shared/theme/unified.css'
import {
  Boxes,
  Database,
  Terminal,
  Network,
  Folder,
  Radio,
  MessagesSquare,
  Monitor,
  Settings,
  Bell,
  Mail,
} from '@lucide/vue'
import CompactPreferences from '../../shared/ui/CompactPreferences.vue'
import BrandMark from '../../shared/ui/BrandMark.vue'
import '../../shared/theme/compact.css'
import { isCompactRoute } from '../../shared/theme/compact'
import { can } from '../../shared/auth/access'
import { navigation } from '../navigation'
import { useIdentity, useRuntime } from '../runtime'
import RequestError from '../../shared/ui/RequestError.vue'

const { t } = useI18n()
const { user, passwordRequired } = useIdentity()
const runtime = useRuntime()
const route = useRoute(),
  router = useRouter()
const logoutBusy = ref(false)
const pageTitle = computed(() => presentationForRoute(route.name).titleKey)
watch(
  passwordRequired,
  (required) => {
    if (required) void router.replace('/change-password')
  },
  { immediate: true },
)
async function logout() {
  logoutBusy.value = true
  let reason: string | undefined
  try {
    await runtime.logout()
  } catch {
    reason = 'logoutFailed'
  }
  await router.replace({ name: 'login', query: { reason } })
}
watch([user, () => route.meta.permission], ([identity, permission]) => {
  if (identity && permission && !identity.mustChangePassword && !can(identity, permission))
    void router.replace({ name: 'foundation', query: { access: 'forbidden' } })
})
const collapsed = ref(false)
const resourcesOpen = ref(true)
const systemOpen = ref(true)
const notificationsOpen = ref(true)
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/notifications')) notificationsOpen.value = true
  },
)
const resourceChildren = [
  { name: 'resources', path: '/resources/databases', label: 'nav.databases', icon: Database },
  { name: 'ssh-resources', path: '/resources/ssh', label: 'nav.ssh', icon: Terminal },
  { name: 'service-resources', path: '/resources/services', label: 'nav.services', icon: Network },
  { name: 'file-resources', path: '/resources/files', label: 'files.title', icon: Folder },
  { name: 'kafka-resources', path: '/resources/kafka', label: 'mq.kafkaTitle', icon: Radio },
  {
    name: 'rabbit-resources',
    path: '/resources/rabbitmq',
    label: 'mq.rabbitTitle',
    icon: MessagesSquare,
  },
  {
    name: 'monitor-resources',
    path: '/resources/monitor',
    label: 'monitorResource.title',
    icon: Monitor,
  },
]
watch(
  () => route.name,
  (name) => {
    if (name === 'users' || name === 'audits') systemOpen.value = true
  },
)
watch(
  () => route.name,
  (name) => {
    if (
      name === 'kafka-resources' ||
      name === 'rabbit-resources' ||
      name === 'monitor-resources' ||
      name === 'file-resources' ||
      name === 'resources' ||
      name === 'ssh-resources' ||
      name === 'service-resources'
    )
      resourcesOpen.value = true
  },
)
const entries = computed(() =>
  passwordRequired.value ? [] : navigation.filter((entry) => can(user.value, entry.permission)),
)
watch(
  () => user.value?.loginId,
  (loginId) => {
    if (loginId) runtime.preferences.switchIdentity(loginId)
  },
  // Route guards can resolve identity before the shell mounts, including on refresh.
  { immediate: true },
)
</script>

<template>
  <a class="skip-link" href="#main">{{ t('app.skip') }}</a>
  <div
    class="application"
    :class="{ 'is-collapsed': collapsed, 'compact-shell': isCompactRoute(route.name) }"
  >
    <aside class="sidebar glass">
      <RouterLink class="brand" to="/" aria-label="iJob"
        ><BrandMark :compact="collapsed" :subtitle="t('app.subtitle')"
      /></RouterLink>
      <nav :aria-label="t('app.navigation')" class="navigation">
        <RouterLink
          class="nav-link"
          :class="{ 'is-active': route.path === '/' }"
          :aria-label="t('foundation.title')"
          to="/"
          ><Globe2 :size="20" aria-hidden="true" /><span v-if="!collapsed">{{
            t('foundation.title')
          }}</span></RouterLink
        >
        <template v-for="entry in entries.filter((v) => !v.system)" :key="entry.key">
          <div v-if="entry.key === 'resources'" class="resource-navigation">
            <button
              class="nav-link resource-parent"
              :class="{ 'is-current-group': route.path.startsWith('/resources') }"
              :aria-label="t('nav.resources')"
              :aria-expanded="resourcesOpen"
              aria-controls="resource-submenu"
              @click="resourcesOpen = !resourcesOpen"
            >
              <Boxes :size="20" /><span v-if="!collapsed">{{ t('nav.resources') }}</span
              ><ChevronDown
                class="resource-chevron"
                :class="{ 'is-open': resourcesOpen }"
                :size="14"
              />
            </button>
            <div v-show="resourcesOpen" id="resource-submenu">
              <RouterLink
                v-for="child in resourceChildren"
                :key="child.name"
                class="nav-link resource-child"
                :class="{ 'is-active': route.name === child.name }"
                :to="child.path"
                :aria-label="t(child.label)"
                :title="t(child.label)"
              >
                <component :is="child.icon" :size="16" /><span v-if="!collapsed">{{
                  t(child.label)
                }}</span>
              </RouterLink>
            </div>
          </div>
          <div v-else-if="entry.key === 'notifications'" class="resource-navigation">
            <button
              class="nav-link resource-parent"
              :class="{ 'is-current-group': route.path.startsWith('/notifications') }"
              :aria-label="t('mailChannels.center')"
              :aria-expanded="notificationsOpen"
              aria-controls="notification-submenu"
              @click="notificationsOpen = !notificationsOpen"
            >
              <Bell :size="20" /><span v-if="!collapsed">{{ t('mailChannels.center') }}</span
              ><ChevronDown
                class="resource-chevron"
                :class="{ 'is-open': notificationsOpen }"
                :size="14"
              />
            </button>
            <div v-show="notificationsOpen" id="notification-submenu">
              <RouterLink
                class="nav-link resource-child"
                :class="{ 'is-active': route.name === 'mail-channels' }"
                to="/notifications/mail-channels"
                :aria-label="t('mailChannels.title')"
                :title="t('mailChannels.title')"
                ><Mail :size="16" /><span v-if="!collapsed">{{
                  t('mailChannels.title')
                }}</span></RouterLink
              >
              <RouterLink
                class="nav-link resource-child"
                :class="{ 'is-active': route.name === 'notifications' }"
                to="/notifications"
                :aria-label="t('nav.notifications')"
                :title="t('nav.notifications')"
                ><Bell :size="16" /><span v-if="!collapsed">{{
                  t('nav.notifications')
                }}</span></RouterLink
              >
            </div>
          </div>
          <RouterLink
            v-else
            class="nav-link"
            :class="{
              'is-active':
                entry.key === 'overview'
                  ? route.name === 'workbench'
                  : route.path.startsWith('/' + entry.key),
            }"
            :to="entry.key === 'overview' ? '/workbench' : '/' + entry.key"
            :aria-label="t('nav.' + entry.key)"
            :title="t('nav.' + entry.key)"
          >
            <component :is="entry.icon" :size="20" /><span v-if="!collapsed">{{
              t('nav.' + entry.key)
            }}</span>
          </RouterLink>
        </template>
        <div v-if="entries.some((v) => v.system)" class="resource-navigation">
          <button
            class="nav-link resource-parent"
            :class="{ 'is-current-group': route.name === 'users' || route.name === 'audits' }"
            :aria-label="t('nav.system')"
            :aria-expanded="systemOpen"
            aria-controls="system-submenu"
            @click="systemOpen = !systemOpen"
          >
            <Settings :size="20" /><span v-if="!collapsed">{{ t('nav.system') }}</span
            ><ChevronDown class="resource-chevron" :class="{ 'is-open': systemOpen }" :size="14" />
          </button>
          <div v-show="systemOpen" id="system-submenu">
            <RouterLink
              v-for="entry in entries.filter((v) => v.system)"
              :key="entry.key"
              class="nav-link resource-child"
              :class="{ 'is-active': route.name === entry.key }"
              :to="'/' + entry.key"
              :aria-label="t('nav.' + entry.key)"
              :title="t('nav.' + entry.key)"
              ><component :is="entry.icon" :size="16" /><span v-if="!collapsed">{{
                t('nav.' + entry.key)
              }}</span></RouterLink
            >
          </div>
        </div>
      </nav>
      <div class="sidebar-bottom">
        <div class="profile">
          <CircleUserRound :size="28" aria-hidden="true" />
          <div v-if="!collapsed">
            <strong>{{ user?.displayName || t('app.visitor') }}</strong
            ><span :title="user?.username || 'iJob'">{{ user?.username || 'iJob' }}</span>
          </div>
        </div>
        <NButton
          quaternary
          :aria-label="t(collapsed ? 'app.expand' : 'app.collapse')"
          @click="collapsed = !collapsed"
          ><component :is="collapsed ? ChevronsRight : ChevronsLeft" :size="18" aria-hidden="true"
        /></NButton>
      </div>
    </aside>
    <div class="main-column">
      <header class="topbar glass">
        <div class="breadcrumb">
          <span>{{ t('app.home') }}</span
          ><ChevronLeft :size="14" class="breadcrumb-divider" aria-hidden="true" /><template
            v-if="
              route.name === 'resources' ||
              route.name === 'ssh-resources' ||
              route.name === 'service-resources' ||
              route.name === 'file-resources' ||
              route.name === 'kafka-resources' ||
              route.name === 'rabbit-resources' ||
              route.name === 'monitor-resources'
            "
            ><span>{{ t('nav.resources') }}</span
            ><ChevronLeft :size="14" class="breadcrumb-divider" aria-hidden="true" /></template
          ><template v-if="route.path.startsWith('/notifications')"
            ><span>{{ t('mailChannels.center') }}</span
            ><ChevronLeft :size="14" class="breadcrumb-divider" aria-hidden="true" /></template
          ><strong>{{ t(pageTitle) }}</strong>
        </div>
        <div class="topbar-actions">
          <span class="environment">{{ t('app.environment') }}</span
          ><TimezoneControl /><CompactPreferences />
          <RouterLink v-if="can(user, 'SELF_PASSWORD_CHANGE')" to="/change-password">{{
            t('auth.changePassword')
          }}</RouterLink>
          <NButton v-if="user" size="small" :disabled="logoutBusy" @click="logout">{{
            t('auth.logout')
          }}</NButton>
          <RouterLink v-else to="/login">{{ t('auth.login') }}</RouterLink>
        </div>
      </header>
      <main id="main" tabindex="-1">
        <RequestError
          v-if="runtime.accessIssue.value?.kind === 'forbidden'"
          :error="runtime.accessIssue.value"
        /><RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.brand {
  min-height: 95px;
  justify-content: center;
}
@media (max-width: 900px) {
  .brand :deep(.brand-copy) {
    display: none;
  }
  .brand :deep(.brand-mark) {
    justify-content: center;
  }
}
</style>
