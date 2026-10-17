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
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { describeError, isWriteOutcomeUnknown } from '../../shared/api/errors'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import ConfiguredField from './ConfiguredField.vue'
import HeaderEditor from './HeaderEditor.vue'
import { useEditor } from '../../shared/forms/use-editor'
import { serviceDraft, activeService, serviceIssues, serviceBody } from './service-model'
import type { ConfigRow } from './contracts'
const props = defineProps<{ row?: ConfigRow }>(),
  emit = defineEmits<{ close: []; saved: [] }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  message = useMessage()
const draft = reactive(serviceDraft(props.row)),
  active = computed(() => activeService(draft))
const { changed, busy, submitted, close, focusError } = useEditor(
  () => draft,
  () => emit('close'),
)
const root = ref<HTMLElement | null>(null),
  uncertain = ref(false),
  selectOpen = ref(false),
  closing = ref(false),
  advanced = ref<string[]>([]),
  oauthOptions = ref<string[]>([])
const controller = new AbortController(),
  errors = computed(() => serviceIssues(draft))
const error = (k: string) =>
  submitted.value && errors.value[k] ? t('apiCall.' + errors.value[k]) : undefined
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
  if (Object.keys(errors.value).length) {
    advanced.value = ['advanced']
    oauthOptions.value = ['oauth']
    await nextTick()
    focusError(root.value)
    return
  }
  busy.value = true
  try {
    await runtime.request('/api/resources' + (props.row ? '/' + props.row.id : ''), {
      method: props.row ? 'PUT' : 'POST',
      body: serviceBody(draft, !!props.row),
      signal: controller.signal,
    })
    if (!controller.signal.aborted && !runtime.accessIssue.value) emit('saved')
  } catch (e) {
    if (!controller.signal.aborted && isWriteOutcomeUnknown(e)) uncertain.value = true
    if (!controller.signal.aborted && !runtime.accessIssue.value)
      message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(() => {
  controller.abort()
  for (const branch of [draft.http, draft.soap]) {
    branch.endpoint.value = ''
    for (const field of Object.values(branch.oauth)) field.value = ''
    for (const row of branch.headers) row.field.value = ''
  }
})
</script>
<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="resource-drawer service-drawer editor-drawer"
    :mask-closable="false"
    :close-on-esc="!busy && !selectOpen"
    @update:show="requestClose"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="resource-editor-title">
          <h2>{{ t(row ? 'apiCall.edit' : 'apiCall.create') }}</h2>
          <DrawerCloseButton :disabled="busy" @close="requestClose" /></div
      ></template>
      <div ref="root" class="configuration-editor">
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
              :id="'service-' + 'name'"
              :label="t('config.' + 'name')"
              required
              :invalid="!!error('name')"
              :message="error('name')"
              ><NInput
                v-model:value="draft['name']"
                :maxlength="128"
                :input-props="{ id: 'service-' + 'name', 'aria-invalid': !!error('name') }"
              />
            </FormItem>
            <div class="resource-enabled">
              <label for="service-enabled">{{ t('config.enable') }}</label
              ><NSwitch
                id="service-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('config.enable')"
              />
            </div>
          </div>
          <FormItem
            :id="'service-' + 'code'"
            :label="t('config.' + 'code')"
            required
            :invalid="!!error('code')"
            :message="error('code')"
            ><NInput
              v-model:value="draft['code']"
              :maxlength="128"
              :input-props="{ id: 'service-' + 'code', 'aria-invalid': !!error('code') }"
            />
            <p class="configuration-note field-hint">
              {{ t('config.codeHint') }}
            </p></FormItem
          >
          <h3>{{ t('unified.connection') }}</h3>
          <FormItem id="service-protocol" :label="t('apiCall.protocol')" required
            ><NSelect
              v-model:value="draft.protocol"
              :options="[
                { label: 'HTTP', value: 'HTTP' },
                { label: 'SOAP', value: 'WEB_SERVICE' },
              ]"
              :input-props="{ id: 'service-protocol', 'aria-label': t('apiCall.protocol') }"
              @update:show="selectOpen = $event"
          /></FormItem>
          <FormItem
            id="service-endpoint"
            :label="t('apiCall.endpoint')"
            required
            :invalid="!!error('endpoint')"
            :message="error('endpoint')"
          >
            <NInput
              v-model:value="active.endpoint.value"
              :input-props="{ id: 'service-endpoint', 'aria-invalid': !!error('endpoint') }"
            />
          </FormItem>
          <template v-if="draft.protocol === 'HTTP'">
            <FormItem id="service-auth" :label="t('apiCall.authType')"
              ><NSelect
                v-model:value="active.authType"
                :options="[
                  { label: t('apiCall.none'), value: 'NONE' },
                  { label: t('apiCall.oauth'), value: 'OAUTH2_CLIENT_CREDENTIALS' },
                ]"
                :input-props="{ id: 'service-auth', 'aria-label': t('apiCall.authType') }"
                @update:show="selectOpen = $event"
            /></FormItem>
            <template v-if="active.authType === 'OAUTH2_CLIENT_CREDENTIALS'">
              <FormItem
                v-for="key in ['tokenEndpoint', 'clientId'] as const"
                :id="'service-' + key"
                :key="key"
                :label="t('apiCall.' + key)"
                required
                :invalid="!!error(key)"
                :message="error(key)"
              >
                <NInput
                  v-model:value="active.oauth[key].value"
                  :input-props="{ id: 'service-' + key, 'aria-invalid': !!error(key) }"
                />
              </FormItem>
              <ConfiguredField
                id="service-clientSecret"
                :label="t('apiCall.clientSecret')"
                :field="active.oauth.clientSecret"
                required
                secret
                password-toggle
                :disabled="busy"
                :message="error('clientSecret')"
                @change="active.oauth.clientSecret = $event"
              />
              <NCollapse v-model:expanded-names="oauthOptions">
                <NCollapseItem name="oauth" :title="t('apiCall.oauthOptions')">
                  <div class="ssh-advanced-fields">
                    <FormItem
                      v-for="key in ['scopes', 'audience'] as const"
                      :id="'service-' + key"
                      :key="key"
                      :label="t('apiCall.' + key)"
                      :invalid="!!error(key)"
                      :message="error(key)"
                    >
                      <NInput
                        v-model:value="active.oauth[key].value"
                        :input-props="{ id: 'service-' + key, 'aria-invalid': !!error(key) }"
                      />
                    </FormItem>
                    <div class="service-fields-grid">
                      <FormItem
                        id="service-auth-method"
                        :label="t('apiCall.clientAuthenticationMethod')"
                        ><NSelect
                          v-model:value="active.clientAuthenticationMethod"
                          :options="
                            ['CLIENT_SECRET_BASIC', 'CLIENT_SECRET_POST'].map((value) => ({
                              value,
                              label: value,
                            }))
                          "
                          :input-props="{
                            id: 'service-auth-method',
                            'aria-label': t('apiCall.clientAuthenticationMethod'),
                          }"
                          @update:show="selectOpen = $event" /></FormItem
                      ><FormItem
                        id="service-refreshSkewSeconds"
                        :label="t('apiCall.refreshSkewSeconds')"
                        :invalid="!!error('refreshSkewSeconds')"
                        :message="error('refreshSkewSeconds')"
                        ><NInputNumber
                          v-model:value="active.refreshSkewSeconds"
                          :precision="0"
                          :input-props="{
                            id: 'service-refreshSkewSeconds',
                            'aria-invalid': !!error('refreshSkewSeconds'),
                          }"
                      /></FormItem>
                    </div>
                  </div> </NCollapseItem
              ></NCollapse>
            </template>
          </template>
          <template v-else>
            <FormItem
              v-for="key in ['wsdl', 'namespace'] as const"
              :id="'service-' + key"
              :key="key"
              :label="t('apiCall.' + key)"
              required
              :invalid="!!error(key)"
              :message="error(key)"
            >
              <NInput
                v-model:value="active[key]"
                :input-props="{ id: 'service-' + key, 'aria-invalid': !!error(key) }"
              />
            </FormItem>
            <div class="service-fields-grid">
              <FormItem
                v-for="key in ['serviceName', 'portName'] as const"
                :id="'service-' + key"
                :key="key"
                :label="t('apiCall.' + key)"
                required
                :invalid="!!error(key)"
                :message="error(key)"
              >
                <NInput
                  v-model:value="active[key]"
                  :input-props="{ id: 'service-' + key, 'aria-invalid': !!error(key) }"
                />
              </FormItem>
            </div>
          </template>
          <HeaderEditor
            v-model:rows="active.headers"
            :title="t('apiCall.defaultHeaders')"
            :errors="errors"
            :submitted="submitted"
            :disabled="busy"
            prefix="service"
          />

          <NCollapse v-model:expanded-names="advanced">
            <NCollapseItem name="advanced" :title="t('tasks.advanced')">
              <div class="ssh-advanced-fields">
                <div class="service-fields-grid">
                  <FormItem
                    v-for="key in [
                      'connectTimeout',
                      'readTimeout',
                      'writeTimeout',
                      'callTimeout',
                    ] as const"
                    :id="'service-' + key"
                    :key="key"
                    :label="t('apiCall.' + key)"
                    :invalid="!!error(key)"
                    :message="error(key)"
                    ><NInputNumber
                      v-model:value="draft[key]"
                      :precision="0"
                      :input-props="{ id: 'service-' + key, 'aria-invalid': !!error(key) }"
                  /></FormItem>
                </div>
                <FormItem id="service-description" :label="t('config.description')"
                  ><NInput
                    v-model:value="draft.description"
                    type="textarea"
                    :maxlength="500"
                    :input-props="{ id: 'service-description' }"
                /></FormItem>
              </div> </NCollapseItem
          ></NCollapse>
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
