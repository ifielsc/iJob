<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { NButton, NTag } from 'naive-ui'
import {
  ArrowUpRight,
  ArrowRight,
  BookOpen,
  CheckCircle2,
  CircleDashed,
  RefreshCw,
  ShieldCheck,
  Workflow,
} from '@lucide/vue'
import { useIdentity, useRuntime } from '../runtime'
import { navigation } from '../navigation'
import {
  setupSteps,
  reviewSteps,
  plugins,
  resources,
  operations,
  modulePath,
  type GuideLink,
} from '../workspace-guide'
import { describeError } from '../../shared/api/errors'
import { can } from '../../shared/auth/access'
const { t } = useI18n()
const { user, isFetching, error, passwordRequired } = useIdentity()
const runtime = useRuntime()
const route = useRoute()
const activeTab = ref('modules')
const state = computed(() =>
  error.value
    ? 'identityFailed'
    : passwordRequired.value
      ? 'changePassword'
      : user.value
        ? 'connected'
        : 'login',
)
const errorText = computed(() => (error.value ? describeError(error.value, t) : ''))
const modules = computed(() => navigation.filter((entry) => can(user.value, entry.permission)))
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE'))
const canReadDefinitions = computed(() => can(user.value, 'DEFINITION_READ'))
const allowed = (entry: GuideLink) =>
  entry.permissions.every((permission) => can(user.value, permission))
const steps = computed(() => (writer.value ? setupSteps : reviewSteps).filter(allowed))
const availableOperations = computed(() => operations.filter(allowed))
watch(canReadDefinitions, (value) => {
  if (!value && activeTab.value === 'plugins') activeTab.value = 'modules'
})
const tabs = computed(() => [
  { key: 'modules', label: 'foundation.modules' },
  ...(canReadDefinitions.value ? [{ key: 'plugins', label: 'foundation.pluginsTitle' }] : []),
  { key: 'operations', label: 'foundation.operationsTitle' },
])
function moveTab(event: KeyboardEvent, index: number) {
  let next: number
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.value.length
  else if (event.key === 'ArrowLeft') next = (index + tabs.value.length - 1) % tabs.value.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.value.length - 1
  else return
  event.preventDefault()
  activeTab.value = tabs.value[next]!.key
  const button = event.currentTarget as HTMLButtonElement
  button.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
}
</script>
<template>
  <div class="workspace">
    <p v-if="route.query.access === 'forbidden'" class="request-error" role="alert">
      {{ t('foundation.permission') }}
    </p>
    <div class="page-heading">
      <div>
        <h1>{{ t('foundation.title') }}</h1>
        <p class="subtitle">{{ t('foundation.workspaceIntro') }}</p>
      </div>
      <RouterLink v-if="can(user, 'SUMMARY_READ')" class="workspace-shortcut" to="/workbench"
        >{{ t('foundation.openWorkbench') }}<ArrowUpRight :size="16" aria-hidden="true"
      /></RouterLink>
    </div>
    <section class="workspace-identity glass" :aria-label="t('foundation.identity')">
      <div class="identity-name">
        <ShieldCheck :size="21" aria-hidden="true" /><span
          >{{ t('foundation.identity')
          }}<strong>{{ user?.displayName || user?.username || t('app.visitor') }}</strong></span
        ><NTag v-if="user?.roleCode" size="small" round :bordered="false">{{
          t(`users.${user.roleCode}`)
        }}</NTag>
      </div>
      <div class="identity-actions">
        <span class="identity-state" role="status"
          ><CheckCircle2
            v-if="user && !error && !isFetching"
            :size="15"
            aria-hidden="true"
          /><CircleDashed
            v-else
            :size="15"
            :class="{ spinning: isFetching }"
            aria-hidden="true"
          />{{ t(`foundation.${isFetching ? 'checking' : state}`) }}</span
        >
        <NButton size="small" secondary :loading="isFetching" @click="runtime.refreshIdentity"
          ><template #icon><RefreshCw :size="14" /></template
          >{{ t('foundation.refreshIdentity') }}</NButton
        >
        <RouterLink v-if="can(user, 'SELF_PASSWORD_CHANGE')" to="/change-password">{{
          t('auth.changePassword')
        }}</RouterLink
        ><RouterLink v-if="!user" to="/login">{{ t('auth.login') }}</RouterLink>
      </div>
      <p v-if="errorText" class="request-error identity-error" role="alert">{{ errorText }}</p>
    </section>
    <section
      v-if="steps.length"
      class="workspace-start panel glass"
      aria-labelledby="workspace-start-title"
    >
      <div class="workspace-section-heading">
        <div>
          <h2 id="workspace-start-title">
            <Workflow :size="19" aria-hidden="true" />{{
              t(writer ? 'foundation.startConfiguring' : 'foundation.startReviewing')
            }}
          </h2>
          <p>{{ t(writer ? 'foundation.setupHint' : 'foundation.reviewHint') }}</p>
        </div>
        <span class="workspace-guide-label"
          ><BookOpen :size="16" aria-hidden="true" />{{ t('foundation.pathGuide') }}</span
        >
      </div>
      <ol class="guide-steps" :class="{ 'review-steps': !writer }">
        <li v-for="(step, index) in steps" :key="step.key">
          <RouterLink :to="step.path" class="step-link"
            ><span class="step-number">{{ String(index + 1).padStart(2, '0') }}</span
            ><span>{{ t(`foundation.stepTitle_${step.key}`) }}</span
            ><ArrowRight :size="14" aria-hidden="true"
          /></RouterLink>
          <p>{{ t(`foundation.step_${step.key}`) }}</p>
          <span v-if="step.optional" class="step-optional">{{ t('foundation.whenNeeded') }}</span>
        </li>
      </ol>
    </section>
    <section class="workspace-content panel glass" :aria-label="t('foundation.guideTitle')">
      <div class="workspace-tabs" role="tablist" :aria-label="t('foundation.guideTitle')">
        <button
          v-for="(tab, index) in tabs"
          :id="`workspace-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :aria-controls="`workspace-panel-${tab.key}`"
          :tabindex="activeTab === tab.key ? 0 : -1"
          @click="activeTab = tab.key"
          @keydown="moveTab($event, index)"
        >
          {{ t(tab.label) }}
        </button>
      </div>
      <div
        v-show="activeTab === 'modules'"
        id="workspace-panel-modules"
        role="tabpanel"
        aria-labelledby="workspace-tab-modules"
        tabindex="0"
      >
        <p class="workspace-tab-intro">{{ t('foundation.navigationHint') }}</p>
        <ul class="guide-modules">
          <li v-for="entry in modules" :key="entry.key" class="module-card">
            <RouterLink class="module-link" :to="modulePath(entry.key)"
              ><span class="module-icon"
                ><component :is="entry.icon" :size="19" aria-hidden="true" /></span
              ><strong>{{ t(`nav.${entry.key}`) }}</strong
              ><ArrowUpRight :size="16" aria-hidden="true"
            /></RouterLink>
            <p>{{ t(`foundation.module_${entry.key}`) }}</p>
          </li>
        </ul>
        <p v-if="!modules.length" class="workspace-empty">{{ t('foundation.noModules') }}</p>
        <div v-if="can(user, 'SENSITIVE_READ')" class="resource-guide">
          <h3>{{ t('foundation.notificationConfigurations') }}</h3>
          <div class="resource-links">
            <RouterLink to="/notifications/mail-channels">{{
              t('foundation.mailChannels')
            }}</RouterLink
            ><RouterLink to="/notifications">{{
              t('foundation.notificationConfigurations')
            }}</RouterLink
            ><RouterLink to="/notifications?tab=records">{{
              t('foundation.notificationRecords')
            }}</RouterLink>
          </div>
        </div>
        <div v-if="canReadDefinitions" class="resource-guide">
          <h3>{{ t('foundation.resourceShortcuts') }}</h3>
          <div class="resource-links">
            <RouterLink v-for="resource in resources" :key="resource" :to="`/resources/${resource}`"
              >{{ t(`foundation.resource_${resource}`)
              }}<ArrowUpRight :size="13" aria-hidden="true"
            /></RouterLink>
          </div>
        </div>
      </div>
      <div
        v-if="canReadDefinitions"
        v-show="activeTab === 'plugins'"
        id="workspace-panel-plugins"
        role="tabpanel"
        aria-labelledby="workspace-tab-plugins"
        tabindex="0"
      >
        <p class="workspace-tab-intro">{{ t('foundation.pluginGuideHint') }}</p>
        <ul class="guide-plugins">
          <li v-for="plugin in plugins" :key="plugin.key" class="plugin-card">
            <div class="plugin-heading">
              <h3>{{ t(`pluginNames.${plugin.key}`) }}</h3>
              <span>{{ plugin.key }}</span>
            </div>
            <p>{{ t(`foundation.plugin_${plugin.key}`) }}</p>
            <dl>
              <div>
                <dt>{{ t('foundation.input') }}</dt>
                <dd>{{ t(`foundation.input_${plugin.key}`) }}</dd>
              </div>
              <div>
                <dt>{{ t('foundation.output') }}</dt>
                <dd>{{ t(`foundation.output_${plugin.key}`) }}</dd>
              </div>
            </dl>
            <div class="plugin-resources">
              <span>{{ t('foundation.dependencies') }}</span>
              <div v-if="plugin.resources.length">
                <RouterLink
                  v-for="resource in plugin.resources"
                  :key="resource"
                  :to="`/resources/${resource}`"
                  >{{ t(`foundation.resource_${resource}`) }}</RouterLink
                >
              </div>
              <span v-else>{{ t('foundation.installedExecutor') }}</span>
            </div>
          </li>
        </ul>
        <p class="plugin-note">{{ t('foundation.pluginsNote') }}</p>
      </div>
      <div
        v-show="activeTab === 'operations'"
        id="workspace-panel-operations"
        role="tabpanel"
        aria-labelledby="workspace-tab-operations"
        tabindex="0"
      >
        <p class="workspace-tab-intro">{{ t('foundation.operationsHint') }}</p>
        <div class="guide-operations">
          <details v-for="operation in availableOperations" :key="operation.key">
            <summary>{{ t(`foundation.operation_${operation.key}`) }}</summary>
            <p>{{ t(`foundation.how_${operation.key}`) }}</p>
            <RouterLink :to="operation.path"
              >{{ t(`foundation.open_${operation.key}`)
              }}<ArrowUpRight :size="14" aria-hidden="true"
            /></RouterLink>
          </details>
        </div>
        <p v-if="!availableOperations.length" class="workspace-empty">
          {{ t('foundation.noOperations') }}
        </p>
      </div>
    </section>
    <p class="page-footnote">
      <ShieldCheck :size="15" aria-hidden="true" />{{ t('foundation.notice') }}
    </p>
  </div>
</template>
<style scoped src="./workspace.css"></style>
