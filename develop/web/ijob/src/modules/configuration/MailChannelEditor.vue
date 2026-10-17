<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { NButton, NForm, NSwitch, NDrawer, NDrawerContent, NInput, NInputNumber } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { useEditor } from '../../shared/forms/use-editor'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import { isWriteOutcomeUnknown } from '../../shared/api/errors'
import type { MailChannel } from './mail-channel-model'
const props = defineProps<{ row?: MailChannel; readonly?: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { t } = useI18n(),
  runtime = useRuntime()
const draft = reactive({
  name: props.row?.name ?? '',
  host: props.row?.host ?? '',
  port: props.row?.port ?? 465,
  security: props.row?.security ?? 'TLS',
  authentication: props.row?.authentication ?? true,
  username: props.row?.username ?? '',
  password: '',
  from: props.row?.from ?? '',
  enabled: props.row?.enabled ?? true,
})
const { changed, busy, error, submitted, close, focusError, confirm } = useEditor(
  () => draft,
  () => emit('close'),
)
const form = ref<HTMLElement | null>(null),
  uncertain = ref(false)
const disabled = computed(() => !!props.readonly || busy.value || uncertain.value)
const requiresPassword = computed(
  () =>
    draft.authentication &&
    (!props.row?.passwordConfigured ||
      draft.host.trim() !== props.row.host ||
      draft.username.trim() !== props.row.username),
)
const invalid = computed(() => ({
  name: !draft.name.trim(),
  host: !/^[A-Za-z0-9.:-]+$/.test(draft.host.trim()) || draft.host.includes('..'),
  port: !Number.isInteger(draft.port) || draft.port < 1 || draft.port > 65535,
  username: draft.authentication && !draft.username.trim(),
  password: (requiresPassword.value && !draft.password.trim()) || /[\r\n]/.test(draft.password),
  from: !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.from.trim()),
}))
async function save() {
  if (disabled.value || (props.row && !changed.value)) return
  submitted.value = true
  if (Object.values(invalid.value).some(Boolean)) {
    await nextTick()
    focusError(form.value)
    return
  }
  if (
    props.row?.enabled &&
    !draft.enabled &&
    !(await confirm(t('mailChannels.disabled'), t('mailChannels.retireHint')))
  )
    return
  busy.value = true
  error.value = null
  try {
    await runtime.request('/api/mail-channels' + (props.row ? '/' + props.row.id : ''), {
      method: props.row ? 'PUT' : 'POST',
      body: {
        ...draft,
        name: draft.name.trim(),
        host: draft.host.trim(),
        username: draft.authentication ? draft.username.trim() : null,
        password: draft.authentication && draft.password ? draft.password : undefined,
        from: draft.from.trim(),
        revision: props.row?.revision,
      },
    })
    emit('saved')
  } catch (e) {
    error.value = e
    uncertain.value = isWriteOutcomeUnknown(e)
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(() => {
  draft.password = ''
})
</script>
<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="notification-drawer configuration-drawer mail-channel-drawer editor-drawer"
    :mask-closable="false"
    :close-on-esc="!busy"
    @update:show="close"
  >
    <NDrawerContent
      :title="t(readonly ? 'common.details' : row ? 'mailChannels.edit' : 'mailChannels.create')"
      :native-scrollbar="true"
    >
      <template #header
        ><div class="notification-editor-title">
          <h2>{{ t(row ? 'mailChannels.edit' : 'mailChannels.create') }}</h2>
          <DrawerCloseButton
            class="notification-editor-close"
            :disabled="busy"
            @close="close"
          /></div
      ></template>
      <div ref="form" class="configuration-editor">
        <NForm :disabled="disabled" :show-feedback="false" novalidate @submit.prevent="save">
          <RequestError :error="error" />
          <p
            v-if="submitted && Object.values(invalid).some(Boolean)"
            role="alert"
            class="status-error"
          >
            {{ t('mailChannels.invalid') }}
          </p>
          <h3>{{ t('unified.basic') }}</h3>
          <div class="unified-basic-row">
            <FormItem
              id="mail-name"
              :label="t('mailChannels.name')"
              required
              :missing="submitted && invalid.name"
            >
              <NInput
                v-model:value="draft.name"
                :maxlength="128"
                :input-props="{ id: 'mail-name', 'aria-invalid': submitted && invalid.name }"
              />
            </FormItem>
            <div class="notification-enabled">
              <label for="mail-enabled">{{ t('config.enable') }}</label
              ><NSwitch
                id="mail-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('config.enable')"
              />
            </div>
          </div>
          <h3>{{ t('mailChannels.connection') }}</h3>
          <FormItem
            id="mail-host"
            :label="t('mailChannels.host')"
            required
            :missing="submitted && invalid.host"
          >
            <NInput
              v-model:value="draft.host"
              :maxlength="253"
              :input-props="{ id: 'mail-host', 'aria-invalid': submitted && invalid.host }"
            />
          </FormItem>
          <div class="mail-channel-columns">
            <FormItem
              id="mail-port"
              :label="t('mailChannels.port')"
              required
              :missing="submitted && invalid.port"
            >
              <NInputNumber
                :value="draft.port"
                :input-props="{ id: 'mail-port' }"
                :min="1"
                :max="65535"
                :precision="0"
                :show-button="false"
                :disabled="disabled"
                @update:value="draft.port = $event ?? 0"
              />
            </FormItem>
            <FormItem id="mail-security" :label="t('mailChannels.security')" required>
              <CompactSelect
                id="mail-security"
                :value="draft.security"
                :label="t('mailChannels.security')"
                :options="
                  (['TLS', 'STARTTLS', 'NONE'] as const).map((value) => ({
                    value,
                    label: t('mailChannels.' + value),
                  }))
                "
                :disabled="disabled"
                @update:value="draft.security = $event as typeof draft.security"
              />
            </FormItem>
          </div>
          <div class="notification-at-all">
            <label for="mail-authentication">{{ t('mailChannels.authentication') }}</label
            ><NSwitch
              id="mail-authentication"
              v-model:value="draft.authentication"
              :aria-label="t('mailChannels.authentication')"
            />
          </div>
          <template v-if="draft.authentication">
            <FormItem
              id="mail-username"
              :label="t('mailChannels.username')"
              required
              :missing="submitted && invalid.username"
            >
              <NInput
                v-model:value="draft.username"
                :maxlength="320"
                :disabled="disabled"
                :input-props="{
                  id: 'mail-username',
                  autocomplete: 'off',
                  'aria-invalid': submitted && invalid.username,
                }"
              />
            </FormItem>
            <FormItem
              id="mail-password"
              :label="t('mailChannels.password')"
              :required="requiresPassword"
              :missing="submitted && invalid.password"
            >
              <NInput
                v-model:value="draft.password"
                type="password"
                show-password-on="click"
                :maxlength="4096"
                :disabled="disabled"
                :placeholder="
                  t(
                    row?.passwordConfigured
                      ? 'mailChannels.passwordKeep'
                      : 'mailChannels.passwordNew',
                  )
                "
                :input-props="{
                  id: 'mail-password',
                  autocomplete: 'new-password',
                  'aria-invalid': submitted && invalid.password,
                }"
              />

              <p class="configuration-note">{{ t('mailChannels.credentialChange') }}</p>
            </FormItem>
          </template>
          <FormItem
            id="mail-from"
            :label="t('mailChannels.from')"
            required
            :missing="submitted && invalid.from"
          >
            <NInput
              v-model:value="draft.from"
              :maxlength="320"
              :disabled="disabled"
              :input-props="{ id: 'mail-from', 'aria-invalid': submitted && invalid.from }"
            />
          </FormItem>
          <p class="configuration-note">{{ t('mailChannels.retireHint') }}</p>
        </NForm>
      </div>
      <template #footer
        ><div class="configuration-footer">
          <NButton :disabled="busy" @click="close">{{ t('common.cancel') }}</NButton
          ><NButton
            v-if="!readonly"
            type="primary"
            :loading="busy"
            :disabled="disabled || (!!row && !changed)"
            @click="save"
            >{{ t('common.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
<style scoped>
.mail-channel-columns {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 16px;
}
</style>
