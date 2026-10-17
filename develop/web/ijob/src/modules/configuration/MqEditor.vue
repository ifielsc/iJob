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
import { mqDraft, mqIssues, mqBody, type MqType } from './mq-model'
import type { ConfigRow } from './contracts'
const props = defineProps<{ row?: ConfigRow; resourceType: MqType; readonly?: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  message = useMessage()
const draft = reactive(mqDraft(props.resourceType, props.row)),
  root = ref<HTMLElement | null>(null),
  selectOpen = ref(false),
  uncertain = ref(false),
  closing = ref(false)
const { changed, busy, error, submitted, close, focusError } = useEditor(
  () => draft,
  () => emit('close'),
)
const issues = computed(() => mqIssues(draft)),
  fieldError = (k: string) =>
    submitted.value && issues.value[k] ? t('config.' + issues.value[k]) : undefined
const disabled = computed(() => busy.value || uncertain.value || props.readonly)
const controller = new AbortController()
watch(
  () => runtime.session.revision,
  () => {
    controller.abort()
    draft.password.value = ''
    emit('close')
  },
)
onBeforeUnmount(() => {
  controller.abort()
  draft.password.value = ''
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
        body: mqBody(draft, !!props.row),
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
</script>
<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="resource-drawer mq-drawer editor-drawer"
    :mask-closable="!!readonly"
    :close-on-esc="!busy && !selectOpen"
    @update:show="requestClose"
    ><NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="resource-editor-title">
          <span>{{
            t(readonly ? 'mq.detail' : row ? 'mq.edit' : 'mq.create', {
              type: resourceType === 'KAFKA' ? 'Kafka' : 'RabbitMQ',
            })
          }}</span
          ><DrawerCloseButton :disabled="busy" @close="requestClose" /></div
      ></template>
      <div ref="root" class="configuration-editor mq-form">
        <RequestError :error="error" />
        <p v-if="uncertain" role="alert">{{ t('eventTask.unknown') }}</p>
        <NForm :disabled="disabled" :show-feedback="false" novalidate @submit.prevent="submit">
          <h3>{{ t('unified.basic') }}</h3>
          <div class="unified-basic-row">
            <FormItem
              :id="'mq-' + 'name'"
              :label="t('config.' + 'name')"
              required
              :invalid="!!fieldError('name')"
              :message="fieldError('name')"
              ><NInput
                v-model:value="draft['name']"
                :maxlength="128"
                :input-props="{ id: 'mq-' + 'name', 'aria-invalid': !!fieldError('name') }"
            /></FormItem>
            <div class="resource-enabled">
              <label for="mq-enabled">{{ t('config.enable') }}</label
              ><NSwitch
                id="mq-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('config.enable')"
              />
            </div>
          </div>
          <FormItem
            :id="'mq-' + 'code'"
            :label="t('config.' + 'code')"
            required
            :invalid="!!fieldError('code')"
            :message="fieldError('code')"
            ><NInput
              v-model:value="draft['code']"
              :maxlength="128"
              :input-props="{ id: 'mq-' + 'code', 'aria-invalid': !!fieldError('code') }"
          /></FormItem>

          <h3>{{ t('mq.connection') }}</h3>
          <template v-if="resourceType === 'KAFKA'">
            <FormItem
              id="mq-bootstrapServers"
              :label="t('mq.bootstrapServers')"
              required
              :invalid="!!fieldError('bootstrapServers')"
              :message="fieldError('bootstrapServers')"
              ><NInput
                v-model:value="draft.bootstrapServers"
                type="textarea"
                :rows="2"
                :input-props="{
                  id: 'mq-bootstrapServers',
                  'aria-invalid': !!fieldError('bootstrapServers'),
                }"
              />
              <p class="configuration-note">{{ t('mq.brokersHelp') }}</p></FormItem
            >
            <FormItem
              id="mq-acks"
              :label="t('mq.acks')"
              :invalid="!!fieldError('acks')"
              :message="fieldError('acks')"
              ><NSelect
                v-model:value="draft.acks"
                filterable
                :options="
                  ['all', '1', '0'].map((value) => ({ value, label: t('mq.acks' + value) }))
                "
                :input-props="{ id: 'mq-acks', 'aria-label': t('mq.acks') }"
                @update:show="selectOpen = $event"
              />
              <p class="configuration-note">
                {{ t(draft.acks === '0' ? 'mq.noAck' : 'mq.acksHelp') }}
              </p></FormItem
            >
          </template>
          <template v-else>
            <div class="mq-grid mq-host">
              <FormItem
                id="mq-host"
                :label="t('config.host')"
                required
                :invalid="!!fieldError('host')"
                :message="fieldError('host')"
                ><NInput
                  v-model:value="draft.host"
                  :input-props="{
                    id: 'mq-host',
                    'aria-invalid': !!fieldError('host'),
                  }" /></FormItem
              ><FormItem
                id="mq-port"
                :label="t('config.port')"
                :invalid="!!fieldError('port')"
                :message="fieldError('port')"
                ><IntegerInput
                  id="mq-port"
                  v-model:value="draft.port"
                  :disabled="disabled"
                  :invalid="!!fieldError('port')"
              /></FormItem>
            </div>
            <div class="mq-grid">
              <FormItem
                v-for="key in ['virtualHost', 'username'] as const"
                :id="'mq-' + key"
                :key="key"
                :label="t(key === 'username' ? 'config.username' : 'mq.virtualHost')"
                :required="key === 'username'"
                :invalid="!!fieldError(key)"
                :message="fieldError(key)"
                ><NInput
                  v-model:value="draft[key]"
                  :input-props="{ id: 'mq-' + key, 'aria-invalid': !!fieldError(key) }"
              /></FormItem>
            </div>
            <h3>{{ t('mq.authentication') }}</h3>
            <ConfiguredField
              id="mq-password"
              :label="t('config.password')"
              :field="draft.password"
              required
              secret
              password-toggle
              :disabled="disabled"
              :maxlength="4096"
              :message="fieldError('password')"
              :hint="t('mq.passwordHelp')"
              @change="draft.password = $event"
            />
            <h3>{{ t('mq.delivery') }}</h3>
            <div class="resource-enabled">
              <label for="mq-publisherConfirm">{{ t('mq.publisherConfirm') }}</label
              ><NSwitch
                id="mq-publisherConfirm"
                v-model:value="draft.publisherConfirm"
                :aria-label="t('mq.publisherConfirm')"
              />
            </div>
            <p class="configuration-note">
              {{ t(draft.publisherConfirm ? 'mq.confirmHelp' : 'mq.noConfirm') }}
            </p>
          </template>
          <FormItem
            id="mq-description"
            :label="t('config.description')"
            :invalid="!!fieldError('description')"
            :message="fieldError('description')"
            ><NInput
              v-model:value="draft.description"
              type="textarea"
              :rows="2"
              :maxlength="500"
              :input-props="{ id: 'mq-description' }"
          /></FormItem>
          <p class="configuration-note">
            {{ t(resourceType === 'KAFKA' ? 'mq.kafkaHelp' : 'mq.rabbitHelp') }}
          </p>
          <p class="configuration-note">
            {{ t(resourceType === 'KAFKA' ? 'mq.kafkaTest' : 'mq.rabbitTest') }}
          </p>
        </NForm>
      </div>
      <template #footer
        ><div class="configuration-editor-actions">
          <NButton :disabled="busy" @click="requestClose">{{
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
.mq-form :deep(form) {
  display: grid;
  gap: 16px;
}
.mq-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.mq-host {
  grid-template-columns: 2fr 1fr;
}
h3 {
  margin: 0;
  font-size: 13px;
}
p {
  margin: 0;
}
dd {
  overflow-wrap: anywhere;
}
@media (max-width: 480px) {
  .mq-grid {
    grid-template-columns: 1fr;
  }
}
</style>
