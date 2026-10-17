<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { draftFingerprint } from '../../shared/forms/draft-fingerprint'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NSwitch,
  useDialog,
  useMessage,
} from 'naive-ui'
import type { InputInst } from 'naive-ui'
import { useRuntime, useIdentity } from '../../app/runtime'
import { ApiError, describeError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import PasswordInput from '../../shared/ui/PasswordInput.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import { useRequiredFields } from '../../shared/forms/required-fields'
import './users.css'
import { parseAccount, parsePage, roles } from '../security-contracts'
import type { Account, RoleCode } from '../security-contracts'
import { validDisplayName, validPassword, validUsername } from '../../shared/auth/account-rules'
const props = defineProps<{ account?: Account; page: number; size: number; queryPath?: string }>()
const emit = defineEmits<{ close: []; saved: [string] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  router = useRouter()
const { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage(),
  confirming = ref(false)
const account = shallowRef(props.account)
const section = ref<'profile' | 'access' | 'reset'>('profile')
const draft = reactive({
  username: '',
  password: '',
  displayName: props.account?.displayName ?? '',
  roleCode: props.account?.roleCode ?? ('VIEWER' as RoleCode),
  enabled: props.account?.enabled ?? true,
})
const original = () => draftFingerprint({ ...draft, password: '' })
const usernameInput = ref<InputInst | null>(null)
const passwordInput = ref<InstanceType<typeof PasswordInput> | null>(null)
const {
  missing,
  firstMissing,
  reset: resetRequired,
} = useRequiredFields({
  username: computed(() => (props.account ? 'existing' : draft.username)),
  password: computed(() =>
    !props.account || section.value === 'reset' ? draft.password : 'unchanged',
  ),
})
const roleOptions = computed(() => roles.map((value) => ({ value, label: t(`users.${value}`) })))
const enableDisabled = computed(
  () => busy.value || (!!account.value && account.value.loginId === user.value?.loginId),
)
const usernameInvalid = computed(
  () => invalid.value && !account.value && !validUsername(draft.username),
)
const passwordInvalid = computed(
  () =>
    invalid.value &&
    (!account.value || section.value === 'reset') &&
    !validPassword(draft.password),
)
const displayInvalid = computed(() => invalid.value && !validDisplayName(draft.displayName))
let baseline = original()
const dirty = computed(() => original() !== baseline || !!draft.password)
const busy = ref(false),
  invalid = ref(false),
  compared = ref(false)
const error = shallowRef<unknown>(null),
  latest = shallowRef<Account>()
const conflict = computed(
  () => !!account.value && error.value instanceof ApiError && error.value.kind === 'conflict',
)
function confirm(title: string, content: string) {
  if (confirming.value) return Promise.resolve(false)
  confirming.value = true
  const done = (resolve: (value: boolean) => void, value: boolean) => {
    confirming.value = false
    resolve(value)
  }
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title,
      content,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => done(resolve, true),
      onNegativeClick: () => done(resolve, false),
      onClose: () => done(resolve, false),
      onMaskClick: () => done(resolve, false),
      onEsc: () => done(resolve, false),
    }),
  )
}
async function close() {
  if (
    !busy.value &&
    (!dirty.value || (await confirm(t('common.discard'), t('common.discardHint'))))
  )
    emit('close')
}
onBeforeRouteLeave(async () => {
  // Session revocation must not be blocked by an unsaved draft.
  if (runtime.accessIssue.value || !runtime.session.token) return true
  return (
    !busy.value && (!dirty.value || (await confirm(t('common.discard'), t('common.discardHint'))))
  )
})
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) event.preventDefault()
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  draft.password = ''
  window.removeEventListener('beforeunload', beforeUnload)
})
function resetDraft() {
  resetRequired()
  draft.displayName = account.value?.displayName ?? ''
  draft.roleCode = account.value?.roleCode ?? 'VIEWER'
  draft.enabled = account.value?.enabled ?? true
  draft.password = ''
  baseline = original()
  error.value = null
  invalid.value = false
  latest.value = undefined
  compared.value = false
}
async function changeSection(next: typeof section.value) {
  if (next === section.value || busy.value) return
  if (dirty.value && !(await confirm(t('common.discard'), t('common.discardHint')))) return
  resetDraft()
  section.value = next
}
async function compare() {
  busy.value = true
  try {
    const data = parsePage(
      await runtime.request(
        props.queryPath ?? `/api/users?pageNum=${props.page}&pageSize=${props.size}`,
      ),
      parseAccount,
    )
    latest.value = data.list.find((row) => row.id === account.value?.id)
    compared.value = true
  } catch (e) {
    error.value = e
    message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
  } finally {
    busy.value = false
  }
}
function adoptLatest() {
  if (latest.value) {
    account.value = latest.value
    resetDraft()
  }
}
async function submit() {
  if (account.value && !dirty.value) return
  if (busy.value || conflict.value) return
  error.value = null
  const first = firstMissing()
  if (first) {
    const input = first === 'username' ? usernameInput.value : passwordInput.value
    input?.focus()
    return
  }
  invalid.value =
    !validDisplayName(draft.displayName) ||
    (!account.value && (!validUsername(draft.username) || !validPassword(draft.password))) ||
    (!!account.value && section.value === 'reset' && !validPassword(draft.password))
  if (invalid.value) {
    await nextTick()
    const input = document.querySelector<HTMLElement>('#user-editor [aria-invalid="true"]')
    input?.focus()
    input?.scrollIntoView({ block: 'nearest' })
    return
  }
  busy.value = true
  error.value = null
  try {
    const current = account.value
    if (
      current &&
      section.value !== 'profile' &&
      !(await confirm(
        t(section.value === 'reset' ? 'users.confirmReset' : 'users.confirmAccess'),
        t(section.value === 'reset' ? 'users.resetHint' : 'users.accessHint'),
      ))
    )
      return
    if (!current) {
      parseAccount(
        await runtime.request('/api/users', {
          method: 'POST',
          body: {
            username: draft.username,
            password: draft.password,
            displayName: draft.displayName,
            roleCode: draft.roleCode,
            enabled: draft.enabled,
          },
        }),
      )
    } else {
      const path = `/api/users/${current.id}/${section.value === 'reset' ? 'reset-password' : section.value}`
      const body =
        section.value === 'profile'
          ? { displayName: draft.displayName }
          : section.value === 'access'
            ? { roleCode: draft.roleCode, enabled: draft.enabled }
            : { newPassword: draft.password }
      await runtime.request(path, {
        method: section.value === 'reset' ? 'POST' : 'PUT',
        body: { expectedRevision: current.revision, ...body },
      })
      if (section.value !== 'profile' && current.loginId === user.value?.loginId) {
        draft.password = ''
        baseline = original()
        runtime.clearSession()
        await router.replace('/login')
        return
      }
    }
    draft.password = ''
    baseline = original()
    emit('saved', section.value === 'reset' ? 'users.resetDone' : 'common.saved')
  } catch (e) {
    error.value = e
    message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
  } finally {
    busy.value = false
    draft.password = ''
  }
}
</script>
<template>
  <NDrawer
    class="users-drawer editor-drawer"
    :show="true"
    width="min(520px, 100vw)"
    :mask-closable="false"
    :close-on-esc="true"
    :auto-focus="true"
    @update:show="
      (value) => {
        if (!value) close()
      }
    "
    ><NDrawerContent
      :title="t(account ? 'users.editTitle' : 'users.create')"
      :native-scrollbar="true"
      ><template #header
        ><div class="users-drawer-title">
          <h2>{{ t(account ? 'users.editTitle' : 'users.create') }}</h2>
          <DrawerCloseButton
            class="users-close"
            :disabled="busy"
            :aria-label="t('users.closeEditor')"
            @close="close"
          /></div
      ></template>
      <div class="users-editor">
        <div v-if="account" class="editor-heading">
          <strong>{{ account.username }}</strong
          ><small>{{ t('users.revision') }} {{ account.revision }}</small>
        </div>
        <LineTabs
          v-if="account"
          id="user-tabs"
          panel-id="user-editor"
          :label="t('common.edit')"
          :value="section"
          :disabled="busy"
          :tabs="
            ['profile', 'access', 'reset'].map((value) => ({ value, label: t('users.' + value) }))
          "
          @change="changeSection($event as typeof section)"
        />
        <p v-if="account" class="notice">{{ t('users.independent') }}</p>
        <NForm
          id="user-editor"
          :role="account ? 'tabpanel' : undefined"
          :aria-labelledby="account ? 'user-tabs-' + section : undefined"
          class="users-form"
          novalidate
          :show-feedback="false"
          @submit.prevent="submit"
        >
          <div v-if="!account">
            <FormItem
              id="user-username"
              required
              :label="t('auth.username')"
              :missing="missing('username')"
              :invalid="usernameInvalid"
              :message="usernameInvalid ? t('users.usernameHint') : undefined"
            >
              <NInput
                ref="usernameInput"
                v-model:value="draft.username"
                :maxlength="64"
                :disabled="busy"
                :input-props="{
                  id: 'user-username',
                  'aria-label': t('auth.username'),
                  required: true,
                  autocomplete: 'off',
                  'aria-invalid': missing('username') || usernameInvalid || undefined,
                  'aria-describedby': `username-hint${missing('username') ? ' user-username-required' : ''}`,
                }"
              />
            </FormItem>
            <p id="username-hint" class="users-field-hint">{{ t('users.usernameHint') }}</p>
          </div>
          <div v-if="!account || section === 'reset'">
            <FormItem
              id="user-password"
              required
              :label="t(account ? 'auth.newPassword' : 'users.initialPassword')"
              :missing="missing('password')"
              :invalid="passwordInvalid"
              :message="passwordInvalid ? t('auth.passwordPolicy') : undefined"
            >
              <PasswordInput
                id="user-password"
                ref="passwordInput"
                v-model="draft.password"
                :label="t(account ? 'auth.newPassword' : 'users.initialPassword')"
                autocomplete="new-password"
                :disabled="busy"
                :invalid="missing('password') || passwordInvalid"
                :missing="missing('password')"
                describedby="password-hint"
              />
            </FormItem>
            <p id="password-hint" class="users-field-hint">{{ t('auth.passwordPolicy') }}</p>
          </div>
          <div v-if="!account || section === 'profile'">
            <FormItem
              id="user-display-name"
              :label="`${t('users.displayName')} (${t('common.optional')})`"
              :invalid="displayInvalid"
              :message="displayInvalid ? t('users.displayHint') : undefined"
            >
              <NInput
                v-model:value="draft.displayName"
                :maxlength="128"
                :disabled="busy"
                :input-props="{
                  id: 'user-display-name',
                  'aria-label': t('users.displayName'),
                  'aria-describedby': 'display-hint',
                  'aria-invalid': displayInvalid || undefined,
                }"
              />
            </FormItem>
            <p id="display-hint" class="users-field-hint">{{ t('users.displayHint') }}</p>
          </div>
          <template v-if="!account || section === 'access'">
            <FormItem id="user-role" :label="t('users.role')">
              <CompactSelect
                id="user-role"
                :value="draft.roleCode"
                :label="t('users.role')"
                :options="roleOptions"
                :disabled="busy"
                @update:value="draft.roleCode = $event as RoleCode"
              />
            </FormItem>
            <div class="users-enable">
              <span>{{ t('users.enable') }}</span>
              <NSwitch
                v-model:value="draft.enabled"
                size="small"
                :aria-label="t('users.enable')"
                :disabled="enableDisabled"
                :aria-disabled="enableDisabled"
              />
              <span class="users-field-hint">{{
                t(draft.enabled ? 'users.enabled' : 'users.disabled')
              }}</span>
            </div>
          </template>
          <p v-if="!account || section !== 'profile'" class="notice">
            {{
              t(
                !account
                  ? 'users.firstChange'
                  : section === 'reset'
                    ? 'users.resetHint'
                    : section === 'access'
                      ? 'users.accessHint'
                      : 'users.displayHint',
              )
            }}
          </p>
          <RequestError v-if="conflict" :error="error" />
          <section v-if="conflict" class="conflict-panel">
            <p>{{ t('users.conflict') }}</p>
            <NButton :loading="busy" :disabled="busy" @click="compare">{{
              t('users.compare')
            }}</NButton
            ><template v-if="latest"
              ><h3>{{ t('users.latest') }}</h3>
              <dl class="detail-list">
                <dt>{{ t('users.displayName') }}</dt>
                <dd>{{ latest.displayName }}</dd>
                <dt>{{ t('users.role') }}</dt>
                <dd>{{ t(`users.${latest.roleCode}`) }}</dd>
                <dt>{{ t('users.status') }}</dt>
                <dd>{{ t(latest.enabled ? 'users.enabled' : 'users.disabled') }}</dd>
                <dt>{{ t('users.revision') }}</dt>
                <dd>{{ latest.revision }}</dd>
              </dl>
              <NButton @click="adoptLatest">{{ t('users.useLatest') }}</NButton></template
            >
            <p v-else-if="compared">{{ t('users.missingAccount') }}</p>
          </section>
        </NForm>
      </div>
      <template #footer
        ><div class="users-editor-footer">
          <NButton :disabled="busy" @click="close">{{ t('common.cancel') }}</NButton
          ><NButton
            type="primary"
            attr-type="submit"
            form="user-editor"
            :loading="busy"
            :disabled="busy || conflict || (!!account && !dirty)"
            >{{
              t(account ? (section === 'reset' ? 'users.reset' : 'common.save') : 'common.save')
            }}</NButton
          >
        </div></template
      ></NDrawerContent
    ></NDrawer
  >
</template>
