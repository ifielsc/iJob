<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NSelect,
  NSwitch,
  useMessage,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError, describeError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import IntegerInput from '../../shared/ui/IntegerInput.vue'
import ConfiguredField from './ConfiguredField.vue'
import { useEditor } from '../../shared/forms/use-editor'
import {
  monitorResourceDraft,
  monitorResourceIssues,
  monitorResourceBody,
} from './monitor-resource-model'
import HeaderEditor from './HeaderEditor.vue'
import type { ConfigRow } from './contracts'
const props = defineProps<{ row?: ConfigRow; readonly?: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  message = useMessage()
const draft = reactive(monitorResourceDraft(props.row)),
  root = ref<HTMLElement | null>(null),
  selectOpen = ref(false),
  uncertain = ref(false),
  closing = ref(false)
const { changed, busy, error, submitted, close, focusError } = useEditor(
  () => draft,
  () => emit('close'),
)
const issues = computed(() => monitorResourceIssues(draft)),
  fieldError = (k: string) =>
    submitted.value && issues.value[k] ? t('config.' + issues.value[k]) : undefined
const disabled = computed(() => busy.value || uncertain.value || props.readonly)
const controller = new AbortController()
watch(
  () => runtime.session.revision,
  () => {
    controller.abort()
    draft.token.value = ''
    draft.headers.forEach((row) => (row.field.value = ''))
    emit('close')
  },
)
onBeforeUnmount(() => {
  controller.abort()
  draft.token.value = ''
  draft.headers.forEach((row) => (row.field.value = ''))
})
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
  if (disabled.value || runtime.accessIssue.value) return
  submitted.value = true
  if (Object.keys(issues.value).length) {
    await nextTick()
    focusError(root.value)
    return
  }
  busy.value = true
  error.value = null
  try {
    const result = await runtime.request(
      props.row ? '/api/resources/' + props.row.id : '/api/resources',
      {
        method: props.row ? 'PUT' : 'POST',
        body: monitorResourceBody(draft),
        signal: controller.signal,
      },
    )
    if (!props.row && (typeof result !== 'string' || !/^[1-9]\d*$/.test(result)))
      throw new ApiError('protocol')
    if (!controller.signal.aborted && !runtime.accessIssue.value) emit('saved')
  } catch (e) {
    if (!controller.signal.aborted && !runtime.accessIssue.value) {
      error.value = e
      if (
        !(e instanceof ApiError) ||
        ['network', 'timeout', 'protocol'].includes(e.kind) ||
        e.status >= 500
      )
        uncertain.value = true
      message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
    }
  } finally {
    busy.value = false
  }
}

async function testSaved() {
  if (disabled.value || !props.row?.enabled || runtime.accessIssue.value) return
  busy.value = true
  error.value = null
  try {
    const result = await runtime.request<{ successful: boolean }>(
      '/api/resources/' + props.row.id + '/connection-test',
      { method: 'POST', signal: controller.signal },
    )
    if (result?.successful !== true) throw new ApiError('protocol')
    if (!controller.signal.aborted) message.success(t('config.connectionOk'))
  } catch (e) {
    if (!controller.signal.aborted && !runtime.accessIssue.value) error.value = e
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="resource-drawer monitor-resource-drawer editor-drawer"
    :mask-closable="!!readonly"
    :close-on-esc="!busy && !selectOpen"
    @update:show="requestClose"
    ><NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="resource-editor-title">
          <span>{{
            t(
              readonly
                ? 'monitorResource.detail'
                : row
                  ? 'monitorResource.edit'
                  : 'monitorResource.create',
            )
          }}</span
          ><DrawerCloseButton :disabled="busy" @close="requestClose" /></div
      ></template>
      <div ref="root" class="configuration-editor monitor-resource-form">
        <RequestError :error="error" />
        <p v-if="uncertain" role="alert">{{ t('eventTask.unknown') }}</p>
        <NForm :disabled="disabled" :show-feedback="false" novalidate @submit.prevent="submit">
          <h3>{{ t('unified.basic') }}</h3>
          <div class="unified-basic-row">
            <FormItem
              :id="'monitor-resource-' + 'name'"
              :label="t('config.' + 'name')"
              required
              :invalid="!!fieldError('name')"
              :message="fieldError('name')"
              ><NInput
                v-model:value="draft['name']"
                :maxlength="128"
                :input-props="{
                  id: 'monitor-resource-' + 'name',
                  'aria-invalid': !!fieldError('name'),
                }"
            /></FormItem>
            <div class="resource-enabled">
              <label for="monitor-resource-enabled">{{ t('config.enable') }}</label
              ><NSwitch
                id="monitor-resource-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('config.enable')"
              />
            </div>
          </div>
          <FormItem
            :id="'monitor-resource-' + 'code'"
            :label="t('config.' + 'code')"
            required
            :invalid="!!fieldError('code')"
            :message="fieldError('code')"
            ><NInput
              v-model:value="draft['code']"
              :maxlength="128"
              :input-props="{
                id: 'monitor-resource-' + 'code',
                'aria-invalid': !!fieldError('code'),
              }"
          /></FormItem>

          <h3>{{ t('monitorResource.connection') }}</h3>
          <FormItem id="monitor-resource-protocol" :label="t('monitorResource.protocol')"
            ><NSelect
              v-model:value="draft.protocol"
              :options="['HTTP', 'HTTPS'].map((value) => ({ value, label: value }))"
              :input-props="{
                id: 'monitor-resource-protocol',
                'aria-label': t('monitorResource.protocol'),
              }"
              @update:show="selectOpen = $event"
          /></FormItem>
          <FormItem
            id="monitor-resource-endpoint"
            :label="t('monitorResource.endpoint')"
            required
            :invalid="!!fieldError('endpoint')"
            :message="fieldError('endpoint')"
            ><NInput
              v-model:value="draft.endpoint"
              placeholder="http://agent:19090"
              :input-props="{
                id: 'monitor-resource-endpoint',
                'aria-invalid': !!fieldError('endpoint'),
              }"
            />
            <p class="configuration-note">{{ t('monitorResource.endpointHelp') }}</p></FormItem
          >
          <ConfiguredField
            id="monitor-resource-token"
            :label="t('monitorResource.token')"
            :field="draft.token"
            secret
            password-toggle
            :disabled="disabled"
            :maxlength="4096"
            :message="fieldError('token')"
            :hint="t('monitorResource.tokenHelp')"
            @change="draft.token = $event"
          />
          <div class="monitor-resource-grid">
            <FormItem
              v-for="key in ['connectTimeout', 'readTimeout'] as const"
              :id="'monitor-resource-' + key"
              :key="key"
              :label="t('monitorResource.' + key)"
              required
              :invalid="!!fieldError(key)"
              :message="fieldError(key)"
              ><IntegerInput
                :id="'monitor-resource-' + key"
                v-model:value="draft[key]"
                :disabled="disabled"
                :invalid="!!fieldError(key)"
            /></FormItem>
          </div>
          <HeaderEditor
            v-model:rows="draft.headers"
            :title="t('monitorResource.headers')"
            :errors="issues"
            :submitted="submitted"
            :disabled="disabled"
            prefix="monitor-resource"
          />
          <h3>{{ t('monitorResource.metadata') }}</h3>
          <div class="monitor-resource-grid">
            <FormItem id="monitor-resource-osType" :label="t('monitorResource.osType')"
              ><NSelect
                v-model:value="draft.osType"
                clearable
                :options="['LINUX', 'WINDOWS', 'MACOS'].map((value) => ({ value, label: value }))"
                :input-props="{
                  id: 'monitor-resource-osType',
                  'aria-label': t('monitorResource.osType'),
                }"
                @update:show="selectOpen = $event" /></FormItem
            ><FormItem
              id="monitor-resource-agentVersion"
              :label="t('monitorResource.agentVersion')"
              :invalid="!!fieldError('agentVersion')"
              :message="fieldError('agentVersion')"
              ><NInput
                v-model:value="draft.agentVersion"
                :input-props="{
                  id: 'monitor-resource-agentVersion',
                  'aria-invalid': !!fieldError('agentVersion'),
                }"
            /></FormItem>
          </div>
          <FormItem
            id="monitor-resource-description"
            :label="t('config.description')"
            :invalid="!!fieldError('description')"
            :message="fieldError('description')"
            ><NInput
              v-model:value="draft.description"
              type="textarea"
              :rows="2"
              :maxlength="500"
              :input-props="{ id: 'monitor-resource-description' }"
          /></FormItem>
          <p class="configuration-note">{{ t('monitorResource.testHelp') }}</p>
        </NForm>
      </div>
      <template #footer
        ><div class="configuration-editor-actions">
          <NButton
            v-if="row && !readonly"
            :disabled="disabled || !row.enabled"
            @click="testSaved"
            >{{ t('monitorResource.testSaved') }}</NButton
          ><NButton :disabled="busy" @click="requestClose">{{
            t(readonly ? 'common.close' : 'common.cancel')
          }}</NButton
          ><NButton
            v-if="!readonly"
            type="primary"
            :loading="busy"
            :disabled="busy || uncertain || (!!row && !changed)"
            @click="submit"
            >{{ t('config.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent></NDrawer
  >
</template>
<style scoped>
.monitor-resource-form :deep(form) {
  display: grid;
  gap: 16px;
}
.monitor-resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
h3 {
  margin: 0;
  font-size: 13px;
}
p {
  margin: 0;
}
@media (max-width: 480px) {
  .monitor-resource-grid {
    grid-template-columns: 1fr;
  }
}
</style>
