<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import {
  NButton,
  NCollapse,
  NCollapseItem,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  useMessage,
  useDialog,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError, describeError, isWriteOutcomeUnknown } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import ConfiguredField from './ConfiguredField.vue'
import { useEditor } from '../../shared/forms/use-editor'
import { sshDraft, sshSecrets, sshIssues, sshBody, sshTrustValid } from './ssh-model'
import type { ConfigRow } from './contracts'

const props = defineProps<{ row?: ConfigRow }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  message = useMessage(),
  dialog = useDialog()
const draft = reactive(sshDraft(props.row)),
  secrets = reactive(sshSecrets(props.row))
const fallbackCode = draft.code
const advanced = ref(draft.hostKeyVerification === 'KNOWN_HOSTS' ? ['advanced'] : [])
const { changed, busy, error, submitted, close, focusError } = useEditor(
  () => ({ draft, secrets }),
  () => emit('close'),
)
const root = ref<HTMLElement | null>(null),
  uncertain = ref(false),
  closing = ref(false),
  selectOpen = ref(false)
const issues = computed(() => sshIssues(draft, secrets, !!props.row))
const fieldError = (key: string) =>
  submitted.value && issues.value[key] ? t('config.' + issues.value[key]) : undefined
const visible = computed<('password' | 'privateKey' | 'passphrase')[]>(() =>
  props.row
    ? ['password', 'privateKey', 'passphrase']
    : draft.auth === 'PASSWORD'
      ? ['password']
      : ['privateKey', 'passphrase'],
)
const controller = new AbortController()
const trusted = computed(() => sshTrustValid(draft))
let activeTrustDialog: { destroy: () => void } | undefined
let settleTrust: ((value: boolean) => void) | undefined
async function verifyHost() {
  const host = draft.host.trim(),
    port = draft.port
  const data = await runtime.request<{
    host: string
    port: number
    fingerprint: string
    keyType: string
    publicKey: string
  }>('/api/resources/ssh/host-key', {
    method: 'POST',
    body: { host, port, connectTimeout: draft.connectTimeout },
    signal: controller.signal,
  })
  if (controller.signal.aborted || runtime.accessIssue.value) return false
  if (
    data.host !== host ||
    data.port !== port ||
    !/^SHA256:[A-Za-z0-9+/]{43}$/.test(data.fingerprint) ||
    typeof data.keyType !== 'string' ||
    typeof data.publicKey !== 'string' ||
    data.publicKey.length > 16384
  )
    throw new ApiError('protocol')
  const changed = trusted.value && draft.fingerprint !== data.fingerprint
  const accepted = await new Promise<boolean>((resolve) => {
    settleTrust = resolve
    activeTrustDialog = dialog.warning({
      title: t(changed ? 'config.sshHostChanged' : 'config.sshConfirmHost'),
      content: () =>
        h('div', { class: 'ssh-trust-confirm' }, [
          h('p', t('config.sshConfirmHelp', { host, port })),
          ...(changed
            ? [
                h('p', t('config.sshPreviousKey')),
                h('code', draft.fingerprint),
                h('p', t('config.sshChangedHelp')),
              ]
            : []),
          h('p', data.keyType),
          h('code', data.fingerprint),
          h('p', t('config.sshFingerprintHelp')),
        ]),
      positiveText: t(changed ? 'config.sshTrustReplacement' : 'config.sshTrustConfirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
      onEsc: () => resolve(false),
    })
  })
  settleTrust = undefined
  activeTrustDialog = undefined
  if (
    !accepted ||
    controller.signal.aborted ||
    runtime.accessIssue.value ||
    draft.host.trim() !== host ||
    draft.port !== port
  )
    return false
  Object.assign(draft, {
    fingerprint: data.fingerprint,
    keyType: data.keyType,
    hostKey: data.publicKey,
    trustedHost: host,
    trustedPort: port,
  })
  return true
}
async function inspectHost() {
  if (busy.value || runtime.accessIssue.value) return
  const invalid = issues.value
  if (invalid.host || invalid.port || invalid.connectTimeout) {
    submitted.value = true
    await nextTick()
    focusError(root.value)
    return
  }
  busy.value = true
  try {
    await verifyHost()
  } catch (e) {
    if (!controller.signal.aborted && !runtime.accessIssue.value)
      message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
  } finally {
    busy.value = false
  }
}
async function requestClose() {
  if (busy.value || closing.value) return
  closing.value = true
  try {
    await close()
  } finally {
    closing.value = false
  }
}
async function submit() {
  if (props.row && !changed.value) return
  if (busy.value || uncertain.value || runtime.accessIssue.value) return
  submitted.value = true
  if (Object.keys(issues.value).length) {
    await nextTick()
    focusError(root.value)
    return
  }
  busy.value = true
  error.value = null
  let writeStarted = false
  try {
    if (draft.hostKeyVerification === 'MANAGED' && !trusted.value && !(await verifyHost())) return
    writeStarted = true
    await runtime.request('/api/resources' + (props.row ? '/' + props.row.id : ''), {
      method: props.row ? 'PUT' : 'POST',
      body: sshBody(draft, secrets, fallbackCode, !!props.row),
      signal: controller.signal,
    })
    if (!controller.signal.aborted && !runtime.accessIssue.value) emit('saved')
  } catch (e) {
    if (!controller.signal.aborted && !runtime.accessIssue.value) {
      if (writeStarted && isWriteOutcomeUnknown(e)) uncertain.value = true
      if (e instanceof ApiError && e.kind === 'conflict') error.value = e
      else message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
    }
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(() => {
  controller.abort()
  settleTrust?.(false)
  activeTrustDialog?.destroy()
  for (const secret of Object.values(secrets)) secret.value = ''
})
</script>

<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="resource-drawer ssh-drawer editor-drawer"
    :mask-closable="false"
    :close-on-esc="!busy && !selectOpen"
    @update:show="requestClose"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="resource-editor-title">
          <h2>{{ t(row ? 'config.editSsh' : 'config.createSsh') }}</h2>
          <DrawerCloseButton :disabled="busy" @close="requestClose" /></div
      ></template>
      <div ref="root" class="configuration-editor">
        <RequestError :error="error" />
        <p v-if="uncertain" role="alert">{{ t('eventBindings.unknown') }}</p>
        <NForm
          :disabled="busy || uncertain"
          :show-feedback="false"
          novalidate
          @submit.prevent="submit"
        >
          <h3>{{ t('unified.basic') }}</h3>
          <div class="unified-basic-row">
            <FormItem
              id="ssh-name"
              :label="t('config.name')"
              required
              :invalid="!!fieldError('name')"
              :message="fieldError('name')"
              ><NInput
                v-model:value="draft.name"
                :maxlength="128"
                :input-props="{ id: 'ssh-name', 'aria-invalid': !!fieldError('name') }"
            /></FormItem>
            <div class="resource-enabled">
              <label for="ssh-enabled">{{ t('config.enable') }}</label
              ><NSwitch
                id="ssh-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('config.enable')"
              />
            </div>
          </div>
          <FormItem
            id="ssh-code"
            :label="t('config.code')"
            required
            :missing="submitted && !draft.code.trim()"
            :invalid="!!fieldError('code')"
            :message="fieldError('code')"
            ><NInput
              v-model:value="draft.code"
              :maxlength="128"
              :input-props="{ id: 'ssh-code', 'aria-invalid': !!fieldError('code') }"
            />
            <p class="configuration-note">{{ t('config.codeHint') }}</p>
          </FormItem>
          <h3>{{ t('unified.connection') }}</h3>
          <div class="ssh-fields-grid">
            <FormItem
              id="ssh-host"
              :label="t('config.host')"
              required
              :invalid="!!fieldError('host')"
              :message="fieldError('host')"
              ><NInput
                v-model:value="draft.host"
                :maxlength="255"
                :input-props="{ id: 'ssh-host', 'aria-invalid': !!fieldError('host') }"
            /></FormItem>
            <FormItem
              id="ssh-port"
              :label="t('config.port')"
              required
              :invalid="!!fieldError('port')"
              :message="fieldError('port')"
              ><NInputNumber
                v-model:value="draft.port"
                :precision="0"
                :input-props="{ id: 'ssh-port', 'aria-invalid': !!fieldError('port') }"
            /></FormItem>
          </div>
          <FormItem
            id="ssh-username"
            :label="t('config.username')"
            required
            :invalid="!!fieldError('username')"
            :message="fieldError('username')"
            ><NInput
              v-model:value="draft.username"
              :maxlength="128"
              :input-props="{
                id: 'ssh-username',
                'aria-invalid': !!fieldError('username'),
                autocomplete: 'off',
              }"
          /></FormItem>
          <FormItem v-if="!row" id="ssh-auth" :label="t('config.sshAuth')"
            ><NSelect
              v-model:value="draft.auth"
              filterable
              :options="[
                { value: 'PASSWORD', label: t('config.password') },
                { value: 'KEY', label: t('config.sshKeyAuth') },
              ]"
              :input-props="{ id: 'ssh-auth', 'aria-label': t('config.sshAuth') }"
              @update:show="selectOpen = $event"
          /></FormItem>
          <p v-else class="configuration-note">{{ t('config.sshPreserveHelp') }}</p>
          <ConfiguredField
            v-for="key in visible"
            :id="'ssh-' + key"
            :key="key"
            :label="t('config.' + key)"
            :field="secrets[key]"
            :required="!row && key !== 'passphrase'"
            :message="fieldError(key)"
            :secret="key !== 'privateKey'"
            :password-toggle="key !== 'privateKey'"
            :disabled="busy"
            :hint="key === 'privateKey' ? t('config.privateKeyHelp') : undefined"
            @change="secrets[key] = $event"
          />
          <section
            v-if="draft.hostKeyVerification === 'MANAGED'"
            class="ssh-host-trust"
            aria-live="polite"
          >
            <div class="ssh-trust-heading">
              <strong>{{ t('config.sshHostIdentity') }}</strong
              ><NButton size="small" :disabled="busy" @click="inspectHost">{{
                t(trusted ? 'config.sshRecheckHost' : 'config.sshInspectHost')
              }}</NButton>
            </div>
            <p>{{ t(trusted ? 'config.sshTrusted' : 'config.sshUnconfirmed') }}</p>
            <code v-if="trusted">{{ draft.keyType }} · {{ draft.fingerprint }}</code>
            <p class="configuration-note">{{ t('config.sshManagedHelp') }}</p>
          </section>
          <NCollapse v-model:expanded-names="advanced">
            <NCollapseItem name="advanced" :title="t('config.sshAdvanced')">
              <div class="ssh-advanced-fields">
                <FormItem id="ssh-hostKeyVerification" :label="t('config.sshVerification')"
                  ><NSelect
                    v-model:value="draft.hostKeyVerification"
                    :options="[
                      { label: t('config.sshManaged'), value: 'MANAGED' },
                      { label: t('config.sshKnownHostsMode'), value: 'KNOWN_HOSTS' },
                    ]"
                    :input-props="{
                      id: 'ssh-hostKeyVerification',
                      'aria-label': t('config.sshVerification'),
                    }"
                    @update:show="selectOpen = $event"
                /></FormItem>
                <FormItem
                  v-if="draft.hostKeyVerification === 'KNOWN_HOSTS'"
                  id="ssh-knownHosts"
                  :label="t('config.knownHosts')"
                  required
                  :invalid="!!fieldError('knownHosts')"
                  :message="fieldError('knownHosts')"
                  ><NInput
                    v-model:value="draft.knownHosts"
                    :maxlength="2048"
                    :input-props="{
                      id: 'ssh-knownHosts',
                      'aria-invalid': !!fieldError('knownHosts'),
                    }"
                  />
                  <p class="configuration-note">{{ t('config.knownHostsHelp') }}</p></FormItem
                >
                <FormItem
                  id="ssh-connectTimeout"
                  :label="t('config.connectTimeout')"
                  :invalid="!!fieldError('connectTimeout')"
                  :message="fieldError('connectTimeout')"
                  ><NInputNumber
                    v-model:value="draft.connectTimeout"
                    :precision="0"
                    :input-props="{
                      id: 'ssh-connectTimeout',
                      'aria-invalid': !!fieldError('connectTimeout'),
                    }"
                /></FormItem>
              </div>
            </NCollapseItem>
          </NCollapse>

          <FormItem
            id="ssh-description"
            :label="t('config.description')"
            :invalid="!!fieldError('description')"
            :message="fieldError('description')"
            ><NInput
              v-model:value="draft.description"
              type="textarea"
              :maxlength="500"
              :input-props="{ id: 'ssh-description', 'aria-invalid': !!fieldError('description') }"
          /></FormItem>
        </NForm>
      </div>
      <template #footer
        ><div class="configuration-editor-actions">
          <NButton :disabled="busy" @click="requestClose">{{ t('common.cancel') }}</NButton
          ><NButton
            type="primary"
            :loading="busy"
            :disabled="busy || uncertain || (!!row && !changed)"
            @click="submit"
            >{{ t('config.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
