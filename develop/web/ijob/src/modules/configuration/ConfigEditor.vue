<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { randomUuid } from '../../shared/runtime/uuid'
import { Eye } from '@lucide/vue'
import type { PreviewOptions } from './notification-preview'
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  useMessage,
  NButton,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NSwitch,
  NCollapse,
  NCollapseItem,
} from 'naive-ui'
import { useRuntime } from '../../app/runtime'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import { ApiError, describeError, isWriteOutcomeUnknown } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import ConfiguredField from './ConfiguredField.vue'
import NotificationPreview from './NotificationPreview.vue'
import {
  newWebhookTypes,
  notificationTypes,
  notificationLabel,
  webhookMatches,
} from './notification-channels'
import MailChannelSelect from './MailChannelSelect.vue'
import { fieldValue, hidden } from './contracts'
import { configurationIssues, jdbcExamples } from './validation'
import type { ConfigKind, ConfigRow, HiddenField } from './contracts'
import { useEditor } from '../../shared/forms/use-editor'
const props = defineProps<{ kind: ConfigKind; row?: ConfigRow }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  message = useMessage()
const resource = props.kind === 'resources',
  c = props.row?.config
const draft = reactive({
  name: props.row?.name ?? '',
  code: props.row?.code ?? 'res-' + randomUuid(),
  type: props.row?.type ?? (resource ? 'DB' : 'EMAIL'),
  enabled: props.row?.enabled ?? true,
  dbType: c?.dbType ?? '',
  schema: c?.schema ?? '',
  description: props.row?.description ?? '',
  atAll: c?.atAll ?? false,
  format: c?.format ?? 'Markdown',
  mentionMode: c?.mentionMode ?? 'none',
  keyword: c?.keyword ?? '',
  destination: c?.destination ?? '',
  channelId: c?.channelId ?? '',
  mailChannelId: c?.mailChannelId ?? '',
})
const dbExisting = props.row?.type === 'DB',
  emailExisting = props.row?.type === 'EMAIL',
  dingExisting = props.row?.type === 'DINGTALK',
  botExisting = !!props.row && props.row.type !== 'EMAIL' && !resource
const fields = reactive<Record<string, HiddenField>>({
  jdbcUrl: { ...hidden(false, false), value: c?.jdbcUrl ?? '' },
  username: { ...hidden(false, false), value: c?.username ?? '' },
  password: hidden(!!c?.passwordConfigured, dbExisting),
  from: { ...hidden(false, false), value: c?.from ?? '' },
  to: { ...hidden(false, false), value: c?.to?.join('\n') ?? '' },
  cc: { ...hidden(false, false), value: c?.cc?.join('\n') ?? '' },
  webhook: hidden(!!c?.webhookConfigured, botExisting),
  secret: hidden(!!c?.secretConfigured, botExisting),
  mobiles: { ...hidden(false, false), value: (c?.atMobiles ?? c?.mobiles)?.join('\n') ?? '' },
  members: { ...hidden(false, false), value: c?.members?.join('\n') ?? '' },
  roles: { ...hidden(false, false), value: c?.roles?.join('\n') ?? '' },
})
const preview = ref(false)
const previewOptions = computed<PreviewOptions>(() => {
  const list = (key: string) =>
    fields[key]!.mode === 'clear'
      ? []
      : fields[key]!.value.split(/[,\n]/)
          .map((v) => v.trim())
          .filter(Boolean)
  return {
    type: draft.type,
    format: draft.format,
    members:
      ['EMAIL', 'DINGTALK'].includes(draft.type) ||
      (draft.type === 'SLACK' && draft.mentionMode !== 'members')
        ? []
        : list('members'),
    mobiles:
      draft.type === 'DINGTALK' || (draft.type === 'WECOM' && draft.format === 'text')
        ? list('mobiles')
        : [],
    roles: draft.type === 'DISCORD' ? list('roles') : [],
    atAll:
      ['DINGTALK', 'FEISHU'].includes(draft.type) ||
      (draft.type === 'WECOM' && draft.format === 'text')
        ? draft.atAll
        : false,
    mentionMode: draft.type === 'SLACK' ? draft.mentionMode : 'none',
    keyword: draft.type === 'FEISHU' ? draft.keyword : undefined,
  }
})
const channelDrafts = new Map<string, { draft: typeof draft; fields: typeof fields }>()
watch(
  () => draft.type,
  (next, previous) => {
    if (resource || props.row) return
    channelDrafts.set(
      previous,
      JSON.parse(JSON.stringify({ draft: { ...draft, type: previous }, fields })),
    )
    const saved = channelDrafts.get(next)
    const name = draft.name
    if (saved) {
      Object.assign(draft, saved.draft, { name, type: next })
      Object.assign(fields, saved.fields)
    } else {
      Object.assign(draft, {
        atAll: false,
        format: 'Markdown',
        mentionMode: 'none',
        keyword: '',
        destination: '',
        channelId: '',
      })
      for (const key of ['webhook', 'secret', 'members', 'roles', 'mobiles'])
        fields[key] = hidden(false, false)
    }
  },
)
const { changed, busy, error, submitted, confirm, close, focusError } = useEditor(
  () => ({ draft, fields }),
  () => emit('close'),
)
const title = computed(() => t(rowTitle()))
function rowTitle() {
  return props.row
    ? resource
      ? 'config.editResource'
      : 'config.editNotification'
    : resource
      ? 'config.createResource'
      : 'config.createNotification'
}
const closing = ref(false)
const uncertain = ref(false)
async function requestClose() {
  if (busy.value || closing.value) return
  closing.value = true
  try {
    await close()
  } finally {
    closing.value = false
  }
}
const form = ref<HTMLElement | null>(null),
  expanded = ref<string[]>([])
const types = computed(() =>
  notificationTypes.map((value) => ({ value, label: t(notificationLabel(value)) })),
)
const dbTypes = ['MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER'].map((value) => ({
  label:
    value === 'POSTGRESQL'
      ? 'PostgreSQL'
      : value === 'MYSQL'
        ? 'MySQL'
        : value === 'SQLSERVER'
          ? 'SQL Server'
          : 'Oracle',
  value,
}))
const visible = computed(() =>
  draft.type === 'DB'
    ? ['jdbcUrl', 'username', 'password']
    : draft.type === 'EMAIL'
      ? ['from', 'to', 'cc']
      : draft.type === 'DINGTALK'
        ? ['webhook', 'secret', 'mobiles']
        : newWebhookTypes.includes(draft.type)
          ? [
              'webhook',
              ...(draft.type === 'FEISHU' ? ['secret'] : []),
              ...(draft.type === 'SLACK' && draft.mentionMode !== 'members' ? [] : ['members']),
              ...(draft.type === 'DISCORD' ? ['roles'] : []),
              ...(draft.type === 'WECOM' && draft.format === 'text' ? ['mobiles'] : []),
            ]
          : [],
)
const required = (key: string) => ['jdbcUrl', 'username', 'from', 'to', 'webhook'].includes(key)
const missing = (key: string) => {
  const f = fields[key]!
  return (
    required(key) && (f.mode === 'keep' ? !f.configured : f.mode === 'clear' || !f.value.trim())
  )
}
const dbDrafts = new Map<
  string,
  { jdbcUrl: HiddenField; username: HiddenField; password: HiddenField }
>()
async function changeDb(next: string | number) {
  const value = String(next)
  if (value === draft.dbType || busy.value) return
  if (draft.dbType && !(await confirm(t('config.dbChange'), t('config.dbChangeHint')))) return
  dbDrafts.set(
    draft.dbType,
    JSON.parse(
      JSON.stringify({
        jdbcUrl: fields.jdbcUrl,
        username: fields.username,
        password: fields.password,
      }),
    ),
  )
  Object.assign(
    fields,
    dbDrafts.get(value) ?? {
      jdbcUrl: hidden(false, false),
      username: hidden(false, false),
      password: hidden(false, false),
    },
  )
  draft.dbType = value
}
const issues = computed(() => {
  const result = configurationIssues(draft, fields)
  if (newWebhookTypes.includes(draft.type)) {
    if (
      fields.webhook!.mode === 'replace' &&
      fields.webhook!.value.trim() &&
      !webhookMatches(draft.type, fields.webhook!.value.trim())
    )
      result.webhook = { key: 'providerWebhookError' }
    for (const key of ['members', 'roles']) {
      const values = String(fields[key]!.value)
        .split(/[,\n]/)
        .map((v) => v.trim())
        .filter(Boolean)
      const pattern =
        draft.type === 'DISCORD'
          ? /^[0-9]{1,20}$/
          : draft.type === 'SLACK'
            ? /^[UW][A-Z0-9]{2,40}$/
            : /^[A-Za-z0-9_.@-]{1,128}$/
      if (values.some((v) => !pattern.test(v))) result[key] = { key: 'mentionIdError' }
      else if (values.length > 100) result[key] = { key: 'listLimit' }
      else if (new Set(values).size !== values.length) result[key] = { key: 'duplicateError' }
    }
  }
  return result
})
function fieldMessage(key: string) {
  const issue = submitted.value && issues.value[key]
  return issue ? t('config.' + issue.key, issue.args ?? {}) : undefined
}
function fieldHint(key: string) {
  if (key === 'password') return t('config.passwordHint')
  if (key === 'to' || key === 'cc') return t('config.addressesHint')
  if (key === 'secret') return t('config.signingHint')
  if (key === 'mobiles') return t('config.mobilesHint')
  if (key === 'members' || key === 'roles') return t('config.mentionsHint')
  if (key === 'webhook')
    return (
      t('config.webhookHint') +
      (newWebhookTypes.includes(draft.type) ? ' ' + t('config.endpoint_' + draft.type) : '')
    )
  if (key !== 'jdbcUrl') return undefined
  return jdbcExamples[draft.dbType]
    ? t('config.jdbcFormat') +
        ': ' +
        jdbcExamples[draft.dbType] +
        '\n' +
        t(draft.dbType === 'ORACLE' ? 'config.jdbcOracleHelp' : 'config.jdbcHelp')
    : t('config.jdbcChoose')
}
async function submit() {
  if (props.row && !changed.value) return
  if (busy.value || uncertain.value || runtime.accessIssue.value) return
  submitted.value = true
  error.value = null
  const absent =
    !draft.name.trim() ||
    !draft.type ||
    (draft.type === 'EMAIL' && !draft.mailChannelId) ||
    (resource && (!draft.code.trim() || !draft.dbType)) ||
    visible.value.some(missing)
  const invalid = Object.keys(issues.value).length > 0
  if (absent || invalid) {
    if (resource && (draft.schema.length > 128 || draft.description.length > 500))
      expanded.value = ['advanced']
    await nextTick()
    focusError(form.value)
    return
  }
  const body: Record<string, unknown> = {
    name: draft.name.trim(),
    type: draft.type,
    enabled: draft.enabled,
  }
  if (resource)
    Object.assign(body, {
      code: draft.code.trim(),
      description: draft.description,
      dbConfig: {
        preserveExisting: !!props.row && c?.dbType === draft.dbType,
        dbType: draft.dbType,
        schema: draft.schema,
        jdbcUrl: fieldValue(fields.jdbcUrl!),
        username: fieldValue(fields.username!),
        password:
          fields.password!.mode === 'replace' && !fields.password!.value
            ? undefined
            : fieldValue(fields.password!),
        clearPassword: fields.password!.mode === 'clear',
      },
    })
  else if (draft.type === 'EMAIL')
    body.emailConfig = {
      mailChannelId: draft.mailChannelId || undefined,
      preserveExisting: emailExisting,
      from: fieldValue(fields.from!),
      to: fieldValue(fields.to!, true),
      cc: fieldValue(fields.cc!, true),
    }
  else if (draft.type === 'DINGTALK')
    body.dingTalkConfig = {
      preserveExisting: dingExisting,
      webhook: fieldValue(fields.webhook!),
      secret:
        fields.secret!.mode === 'replace' && !fields.secret!.value
          ? undefined
          : fieldValue(fields.secret!),
      clearSecret: fields.secret!.mode === 'clear',
      atAll: draft.atAll,
      atMobiles: fieldValue(fields.mobiles!, true),
    }
  else
    body.webhookConfig = {
      preserveExisting: botExisting,
      webhook: fieldValue(fields.webhook!),
      ...(draft.type === 'FEISHU'
        ? {
            secret:
              fields.secret!.mode === 'replace' && fields.secret!.value
                ? fields.secret!.value
                : undefined,
            clearSecret: fields.secret!.mode === 'clear',
          }
        : {}),
      format: draft.format,
      members:
        draft.type === 'SLACK' && draft.mentionMode !== 'members'
          ? []
          : fieldValue(fields.members!, true),
      roles: draft.type === 'DISCORD' ? fieldValue(fields.roles!, true) : [],
      mobiles:
        draft.type === 'WECOM' && draft.format === 'text' ? fieldValue(fields.mobiles!, true) : [],
      atAll:
        draft.type === 'FEISHU' || (draft.type === 'WECOM' && draft.format === 'text')
          ? draft.atAll
          : false,
      mentionMode: draft.type === 'SLACK' ? draft.mentionMode : 'none',
      keyword: draft.type === 'FEISHU' ? draft.keyword : undefined,
      destination: draft.destination,
      channelId: draft.type === 'SLACK' ? draft.channelId : undefined,
    }
  busy.value = true
  try {
    await runtime.request(
      `/api/${resource ? 'resources' : 'notify-configs'}${props.row ? '/' + props.row.id : ''}`,
      { method: props.row ? 'PUT' : 'POST', body },
    )
    emit('saved')
  } catch (e) {
    if (isWriteOutcomeUnknown(e)) uncertain.value = true
    if (e instanceof ApiError && e.kind === 'conflict') error.value = e
    else message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(() => {
  for (const field of Object.values(fields)) field.value = ''
  dbDrafts.clear()
  channelDrafts.clear()
})
</script>
<template>
  <NDrawer
    class="editor-drawer"
    :show="true"
    :class="{ 'resource-drawer': resource, 'notification-drawer': !resource }"
    width="min(520px, 100vw)"
    :mask-closable="false"
    :close-on-esc="true"
    @update:show="requestClose"
  >
    <NDrawerContent :native-scrollbar="true" :title="title">
      <template #header>
        <div :class="resource ? 'resource-editor-title' : 'notification-editor-title'">
          <h2>{{ title }}</h2>
          <DrawerCloseButton
            :class="resource ? 'resource-editor-close' : 'notification-editor-close'"
            :disabled="busy"
            @close="requestClose"
          />
        </div>
      </template>
      <div ref="form" class="configuration-editor">
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
              id="config-name"
              :label="t('config.name')"
              required
              :missing="submitted && !draft.name.trim()"
              :invalid="!!fieldMessage('name')"
              :message="fieldMessage('name')"
            >
              <NInput
                v-model:value="draft.name"
                :maxlength="128"
                :input-props="{
                  id: 'config-name',
                  'aria-invalid':
                    (submitted && !draft.name.trim()) || !!fieldMessage('name') || undefined,
                }"
              />
            </FormItem>
            <div :class="resource ? 'resource-enabled' : 'notification-enabled'">
              <label for="config-enabled">{{ t('config.enable') }}</label>
              <NSwitch
                id="config-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('config.enable')"
              />
            </div>
          </div>
          <FormItem
            v-if="resource"
            id="config-code"
            :label="t('config.code')"
            required
            :missing="submitted && !draft.code.trim()"
            :invalid="!!fieldMessage('code')"
            :message="fieldMessage('code')"
          >
            <NInput
              v-model:value="draft.code"
              :maxlength="128"
              :input-props="{
                id: 'config-code',
                'aria-invalid':
                  (submitted && !draft.code.trim()) || !!fieldMessage('code') || undefined,
              }"
            />

            <p v-if="resource" class="configuration-note">{{ t('config.codeHint') }}</p>
          </FormItem>
          <FormItem
            v-if="!resource"
            id="config-type"
            :label="t('config.type')"
            required
            :missing="submitted && !draft.type"
          >
            <CompactSelect
              id="config-type"
              v-model:value="draft.type"
              :invalid="submitted && !draft.type"
              :label="t('config.type')"
              :disabled="!!row || busy"
              :options="types"
            />
          </FormItem>

          <h3>
            {{
              t(
                resource
                  ? 'config.connection'
                  : draft.type === 'EMAIL'
                    ? 'config.recipients'
                    : draft.type === 'DINGTALK'
                      ? 'config.robot'
                      : notificationLabel(draft.type),
              )
            }}
          </h3>
          <FormItem
            v-if="resource"
            id="config-dbType"
            :label="t('config.dbType')"
            required
            :missing="submitted && !draft.dbType"
          >
            <CompactSelect
              id="config-dbType"
              :invalid="submitted && !draft.dbType"
              :value="draft.dbType"
              :label="t('config.dbType')"
              :options="dbTypes"
              :disabled="busy"
              @update:value="changeDb"
            />
          </FormItem>
          <FormItem
            v-if="draft.type === 'EMAIL'"
            id="config-mailChannel"
            :label="t('mailChannels.channel')"
            required
            :missing="submitted && !draft.mailChannelId"
          >
            <MailChannelSelect
              :value="draft.mailChannelId"
              :name="c?.mailChannelName ?? undefined"
              :disabled="busy"
              :invalid="submitted && !draft.mailChannelId"
              @change="
                (id, from) => {
                  draft.mailChannelId = id
                  if (from) fields.from!.value = from
                }
              "
            />
          </FormItem>
          <FormItem v-if="draft.type === 'WECOM'" id="notify-format" :label="t('config.format')">
            <CompactSelect
              id="notify-format"
              v-model:value="draft.format"
              :label="t('config.format')"
              :options="[
                { value: 'Markdown', label: 'Markdown' },
                { value: 'text', label: t('config.plainText') },
              ]"
            />
          </FormItem>
          <FormItem
            v-if="draft.type === 'SLACK'"
            id="notify-mention-mode"
            :label="t('config.mentionMode')"
          >
            <CompactSelect
              id="notify-mention-mode"
              v-model:value="draft.mentionMode"
              :label="t('config.mentionMode')"
              :options="
                ['none', 'members', 'here', 'channel'].map((value) => ({
                  value,
                  label: t('config.mention_' + value),
                }))
              "
            />
          </FormItem>
          <ConfiguredField
            v-for="key in visible"
            :id="'config-' + key"
            :key="resource && key === 'password' ? key + draft.dbType : key"
            :field="fields[key]!"
            :label="t('config.' + key)"
            :required="required(key)"
            :missing="submitted && missing(key)"
            :message="fieldMessage(key)"
            :placeholder="key === 'jdbcUrl' ? jdbcExamples[draft.dbType] : undefined"
            :hint="fieldHint(key)"
            :display-value="key === 'webhook' ? (c?.webhookDisplay ?? undefined) : undefined"
            :password-toggle="(resource && key === 'password') || key === 'webhook'"
            :reveal-label="key === 'webhook' ? t('config.showWebhook') : undefined"
            :conceal-label="key === 'webhook' ? t('config.hideWebhook') : undefined"
            :replace-label="
              resource && key === 'password'
                ? t('config.changePassword')
                : key === 'webhook'
                  ? t('config.replaceWebhook')
                  : undefined
            "
            :secret="key === 'password' || key === 'secret' || key === 'webhook'"
            :list="['to', 'cc', 'mobiles', 'members', 'roles'].includes(key)"
            :maxlength="
              key === 'username'
                ? 128
                : key === 'jdbcUrl' || key === 'webhook'
                  ? 2048
                  : key === 'from'
                    ? 320
                    : key === 'password' || key === 'secret'
                      ? 4096
                      : 33000
            "
            :disabled="busy || key === 'from'"
            @change="fields[key] = $event"
          />
          <template v-if="draft.type === 'DINGTALK'">
            <div class="notification-toggle-field">
              <div class="notification-at-all">
                <label for="config-atAll">{{ t('config.atAll') }}</label
                ><NSwitch
                  id="config-atAll"
                  v-model:value="draft.atAll"
                  :aria-label="t('config.atAll')"
                />
              </div>
              <p v-if="draft.atAll" class="configuration-note" role="status">
                {{ t('config.atAllHint') }}
              </p>
            </div>
          </template>
          <template v-if="newWebhookTypes.includes(draft.type)">
            <div
              v-if="draft.type === 'FEISHU' || (draft.type === 'WECOM' && draft.format === 'text')"
              class="notification-at-all"
            >
              <label for="notify-everyone">{{ t('config.atAll') }}</label
              ><NSwitch
                id="notify-everyone"
                v-model:value="draft.atAll"
                :aria-label="t('config.atAll')"
              />
            </div>
            <NCollapse
              ><NCollapseItem name="advanced" :title="t('config.advanced')">
                <FormItem
                  v-if="draft.type === 'FEISHU'"
                  id="notify-keyword"
                  :label="t('config.keyword')"
                  ><NInput
                    v-model:value="draft.keyword"
                    :maxlength="100"
                    :input-props="{ id: 'notify-keyword' }"
                /></FormItem>
                <FormItem id="notify-destination" :label="t('config.destination')"
                  ><NInput
                    v-model:value="draft.destination"
                    :maxlength="128"
                    :input-props="{ id: 'notify-destination' }"
                /></FormItem>
                <FormItem
                  v-if="draft.type === 'SLACK'"
                  id="notify-channel-id"
                  :label="t('config.channelId')"
                  ><NInput
                    v-model:value="draft.channelId"
                    :maxlength="64"
                    :input-props="{ id: 'notify-channel-id' }"
                  />
                  <p class="configuration-note">{{ t('config.channelIdHint') }}</p></FormItem
                >
              </NCollapseItem></NCollapse
            >
          </template>
          <p v-if="!resource && draft.type !== 'EMAIL'" class="notification-protection">
            {{ t('config.rateProtectionHint') }}
          </p>
          <NCollapse v-if="resource" v-model:expanded-names="expanded">
            <NCollapseItem name="advanced" :title="t('config.advanced') + ' · Schema'">
              <FormItem
                id="config-schema"
                :label="t('config.schema')"
                :invalid="!!fieldMessage('schema')"
                :message="fieldMessage('schema')"
                ><NInput
                  v-model:value="draft.schema"
                  :maxlength="128"
                  :input-props="{
                    id: 'config-schema',
                    'aria-invalid': !!fieldMessage('schema') || undefined,
                  }"
              /></FormItem>
              <FormItem
                id="config-description"
                :label="t('config.description')"
                :invalid="!!fieldMessage('description')"
                :message="fieldMessage('description')"
                ><NInput
                  v-model:value="draft.description"
                  type="textarea"
                  :maxlength="500"
                  :input-props="{
                    id: 'config-description',
                    'aria-invalid': !!fieldMessage('description') || undefined,
                  }"
              /></FormItem>
            </NCollapseItem>
          </NCollapse>
        </NForm>
      </div>
      <template #footer
        ><div class="configuration-footer">
          <NButton
            v-if="!resource"
            text
            type="primary"
            :disabled="busy"
            class="notification-preview-action"
            @click="preview = true"
            ><template #icon><Eye :size="16" aria-hidden="true" /></template
            >{{ t('config.previewContent') }}</NButton
          >
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
  <NotificationPreview v-if="preview" :options="previewOptions" @close="preview = false" />
</template>
