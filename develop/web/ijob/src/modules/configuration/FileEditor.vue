<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NSelect,
  NSwitch,
  useMessage,
  useDialog,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError, describeError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import ConfiguredField from './ConfiguredField.vue'
import { useEditor } from '../../shared/forms/use-editor'
import {
  fileDraft,
  fileSecrets,
  fileIssues,
  fileBody,
  fileTrustValid,
  fileProtocols,
  fileCredentialKeys,
  type FileProtocol,
} from './file-model'
import IntegerInput from '../../shared/ui/IntegerInput.vue'
import type { ConfigRow } from './contracts'

const props = defineProps<{ row?: ConfigRow; readonly?: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  message = useMessage(),
  dialog = useDialog()
const draft = reactive(fileDraft(props.row)),
  secrets = reactive(fileSecrets(props.row))
const fallbackCode = draft.code
const protocolDrafts = reactive(
  new Map<
    FileProtocol,
    { draft: ReturnType<typeof fileDraft>; secrets: ReturnType<typeof fileSecrets> }
  >(),
)
const { changed, busy, error, submitted, close, focusError } = useEditor(
  () => ({ draft, secrets, protocolDrafts: [...protocolDrafts] }),
  () => emit('close'),
)
const root = ref<HTMLElement | null>(null),
  closing = ref(false),
  selectOpen = ref(false)
const issues = computed(() => fileIssues(draft, secrets, !!props.row))
const fieldError = (key: string) =>
  submitted.value && issues.value[key] ? t('config.' + issues.value[key]) : undefined
const visible = computed(() => fileCredentialKeys(draft, !!props.row))
const uncertain = ref(false)
function changeProtocol(protocol: FileProtocol) {
  if (busy.value || props.readonly || uncertain.value || protocol === draft.protocol) return
  protocolDrafts.set(draft.protocol, JSON.parse(JSON.stringify({ draft, secrets })))
  const common = {
    name: draft.name,
    code: draft.code,
    enabled: draft.enabled,
    description: draft.description,
  }
  const restored = protocolDrafts.get(protocol) ?? {
    draft: {
      ...fileDraft(),
      protocol,
      basePath: protocol === 'LOCAL' ? '' : '/',
      port: protocol === 'FTP' ? 21 : 22,
    },
    secrets: fileSecrets(),
  }
  Object.assign(draft, restored.draft, common)
  Object.assign(secrets, restored.secrets)
}
const controller = new AbortController()
const trusted = computed(() => fileTrustValid(draft))
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
  if (busy.value || props.readonly || uncertain.value || runtime.accessIssue.value) return
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
    if (props.readonly) emit('close')
    else await close()
  } finally {
    closing.value = false
  }
}
async function submit() {
  if (props.row && !changed.value) return
  if (busy.value || props.readonly || uncertain.value || runtime.accessIssue.value) return
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
    if (
      draft.protocol === 'SFTP' &&
      draft.hostKeyVerification === 'MANAGED' &&
      !trusted.value &&
      !(await verifyHost())
    )
      return
    const body = fileBody(draft, secrets, fallbackCode, !!props.row)
    writeStarted = true
    await runtime.request('/api/resources' + (props.row ? '/' + props.row.id : ''), {
      method: props.row ? 'PUT' : 'POST',
      body,
      signal: controller.signal,
    })
    if (!controller.signal.aborted && !runtime.accessIssue.value) emit('saved')
  } catch (e) {
    if (!controller.signal.aborted && !runtime.accessIssue.value) {
      if (
        writeStarted &&
        (!(e instanceof ApiError) ||
          ['network', 'timeout', 'protocol'].includes(e.kind) ||
          e.status >= 500)
      )
        uncertain.value = true
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
  protocolDrafts.clear()
})
</script>

<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="resource-drawer file-drawer editor-drawer"
    :mask-closable="false"
    :close-on-esc="!busy && !selectOpen"
    @update:show="requestClose"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="resource-editor-title">
          <h2>{{ t(row ? 'files.edit' : 'files.create') }}</h2>
          <DrawerCloseButton :disabled="busy" @close="requestClose" /></div
      ></template>
      <div ref="root" class="configuration-editor">
        <RequestError :error="error" />
        <NForm
          :disabled="busy || readonly || uncertain"
          :show-feedback="false"
          novalidate
          @submit.prevent="submit"
        >
          <h3>{{ t('unified.basic') }}</h3>
          <div class="unified-basic-row">
            <FormItem
              :id="'file-' + 'name'"
              :label="t('config.' + 'name')"
              required
              :invalid="!!fieldError('name')"
              :message="fieldError('name')"
              ><NInput
                v-model:value="draft['name']"
                :maxlength="128"
                :input-props="{ id: 'file-' + 'name', 'aria-invalid': !!fieldError('name') }"
            /></FormItem>
            <div class="resource-enabled">
              <label for="file-enabled">{{ t('config.enable') }}</label
              ><NSwitch id="file-enabled" v-model:value="draft.enabled" />
            </div>
          </div>
          <FormItem
            :id="'file-' + 'code'"
            :label="t('config.' + 'code')"
            required
            :invalid="!!fieldError('code')"
            :message="fieldError('code')"
            ><NInput
              v-model:value="draft['code']"
              :maxlength="128"
              :input-props="{ id: 'file-' + 'code', 'aria-invalid': !!fieldError('code') }"
          /></FormItem>
          <h3>{{ t('unified.connection') }}</h3>
          <div class="file-protocol-row">
            <FormItem id="file-protocol" :label="t('files.protocol')" required
              ><NSelect
                filterable
                :value="draft.protocol"
                :options="fileProtocols.map((value) => ({ value, label: value }))"
                :input-props="{ id: 'file-protocol', 'aria-label': t('files.protocol') }"
                @update:value="changeProtocol"
                @update:show="selectOpen = $event"
              />
              <p class="configuration-note">{{ t('files.protocolHint') }}</p></FormItem
            >
          </div>
          <div v-if="draft.protocol === 'FTP' || draft.protocol === 'SFTP'" class="file-grid">
            <FormItem
              id="file-host"
              :label="t('config.host')"
              required
              :invalid="!!fieldError('host')"
              :message="fieldError('host')"
              ><NInput
                v-model:value="draft.host"
                :maxlength="255"
                :input-props="{ id: 'file-host', 'aria-invalid': !!fieldError('host') }"
            /></FormItem>
            <FormItem
              id="file-port"
              :label="t('config.port')"
              required
              :invalid="!!fieldError('port')"
              :message="fieldError('port')"
              ><IntegerInput
                id="file-port"
                :value="draft.port"
                :invalid="!!fieldError('port')"
                @update:value="draft.port = $event ?? 0"
            /></FormItem>
            <FormItem
              id="file-username"
              :label="t('config.username')"
              required
              :invalid="!!fieldError('username')"
              :message="fieldError('username')"
              ><NInput
                v-model:value="draft.username"
                :maxlength="128"
                :input-props="{ id: 'file-username', 'aria-invalid': !!fieldError('username') }"
            /></FormItem>
          </div>
          <template v-if="draft.protocol === 'S3_COMPATIBLE'">
            <FormItem
              v-for="key in ['endpoint', 'region', 'bucket'] as const"
              :id="'file-' + key"
              :key="key"
              :label="t('files.' + key)"
              :required="key !== 'endpoint'"
              :invalid="!!fieldError(key)"
              :message="fieldError(key)"
              ><NInput
                v-model:value="draft[key]"
                :input-props="{ id: 'file-' + key, 'aria-invalid': !!fieldError(key) }"
            /></FormItem>
            <div class="task-inline">
              <label for="file-pathStyle">{{ t('files.pathStyleAccess') }}</label
              ><NSwitch id="file-pathStyle" v-model:value="draft.pathStyleAccess" />
            </div>
          </template>
          <FormItem
            id="file-basePath"
            :label="t('files.basePath')"
            required
            :invalid="!!fieldError('basePath')"
            :message="fieldError('basePath')"
            ><NInput
              v-model:value="draft.basePath"
              :maxlength="2048"
              :input-props="{ id: 'file-basePath', 'aria-invalid': !!fieldError('basePath') }"
            />
            <p class="configuration-note">{{ t('files.rootHint') }}</p>
          </FormItem>
          <template v-if="draft.protocol === 'SFTP'">
            <FormItem v-if="!row" id="file-auth" :label="t('config.sshAuth')">
              <NSelect
                v-model:value="draft.auth"
                filterable
                :options="[
                  { value: 'PASSWORD', label: t('config.password') },
                  { value: 'KEY', label: t('config.sshKeyAuth') },
                ]"
                :input-props="{ id: 'file-auth', 'aria-label': t('config.sshAuth') }"
                @update:show="selectOpen = $event"
              />
            </FormItem>
            <p v-else class="configuration-note">{{ t('config.sshPreserveHelp') }}</p>
          </template>
          <ConfiguredField
            v-for="key in visible"
            :id="'file-' + key"
            :key="key"
            :label="
              t(
                ['accessKey', 'secretKey', 'sessionToken'].includes(key)
                  ? 'files.' + key
                  : 'config.' + key,
              )
            "
            :field="secrets[key]"
            :required="
              key === 'accessKey' ||
              key === 'secretKey' ||
              (draft.protocol === 'FTP' && key === 'password') ||
              (draft.protocol === 'SFTP' && !row && key !== 'passphrase')
            "
            :secret="key !== 'privateKey'"
            :password-toggle="key !== 'privateKey'"
            :hint="key === 'privateKey' ? t('config.privateKeyHelp') : undefined"
            :disabled="busy || readonly || uncertain"
            :message="fieldError(key)"
            @change="secrets[key] = $event"
          />
          <template v-if="draft.protocol === 'SFTP'">
            <FormItem id="file-trust" :label="t('config.sshVerification')"
              ><NSelect
                v-model:value="draft.hostKeyVerification"
                filterable
                :options="[
                  { value: 'MANAGED', label: t('config.sshManaged') },
                  { value: 'KNOWN_HOSTS', label: t('config.sshKnownHostsMode') },
                ]"
                :input-props="{ id: 'file-trust' }"
                @update:show="selectOpen = $event"
            /></FormItem>
            <template v-if="draft.hostKeyVerification === 'MANAGED'">
              <p v-if="trusted" class="file-fingerprint">
                {{ draft.keyType }} · {{ draft.fingerprint }}
              </p>
              <NButton
                v-if="!readonly"
                :disabled="busy || uncertain"
                :loading="busy"
                @click="inspectHost"
                >{{ t(trusted ? 'config.sshRecheckHost' : 'config.sshInspectHost') }}</NButton
              >
              <p class="configuration-note">{{ t('config.sshFingerprintHelp') }}</p>
            </template>
            <FormItem
              v-else
              id="file-knownHosts"
              :label="t('config.knownHosts')"
              required
              :invalid="!!fieldError('knownHosts')"
              :message="fieldError('knownHosts')"
              ><NInput
                v-model:value="draft.knownHosts"
                :maxlength="2048"
                :input-props="{
                  id: 'file-knownHosts',
                  'aria-invalid': !!fieldError('knownHosts'),
                }"
            /></FormItem>
          </template>
          <FormItem
            id="file-connectTimeout"
            :label="t('config.connectTimeout')"
            required
            :invalid="!!fieldError('connectTimeout')"
            :message="fieldError('connectTimeout')"
            ><IntegerInput
              id="file-connectTimeout"
              :value="draft.connectTimeout"
              :invalid="!!fieldError('connectTimeout')"
              @update:value="draft.connectTimeout = $event ?? 0"
          /></FormItem>
          <FormItem
            id="file-description"
            :label="t('config.description')"
            :invalid="!!fieldError('description')"
            :message="fieldError('description')"
            ><NInput
              v-model:value="draft.description"
              type="textarea"
              :rows="2"
              :maxlength="500"
              :input-props="{ id: 'file-description' }"
          /></FormItem>
          <p class="configuration-note">{{ t('files.saveHint') }}</p>
          <p v-if="uncertain" role="alert">{{ t('eventBindings.unknown') }}</p>
        </NForm>
      </div>
      <template #footer
        ><div class="resource-editor-footer">
          <NButton :disabled="busy" @click="requestClose">{{ t('common.cancel') }}</NButton
          ><NButton
            v-if="!readonly"
            type="primary"
            :loading="busy"
            :disabled="busy || uncertain || (!!row && !changed)"
            @click="submit"
            >{{ t('common.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent></NDrawer
  >
</template>
<style scoped>
.file-protocol-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}
.file-protocol-row .task-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  white-space: nowrap;
}
.file-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px;
  gap: 12px;
}
.file-grid > :last-child {
  grid-column: 1/-1;
}
.file-fingerprint {
  font: 12px monospace;
  overflow-wrap: anywhere;
}
.resource-editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.file-drawer .task-inline {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 8px 0;
}
</style>
