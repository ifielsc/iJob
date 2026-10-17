<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NForm } from 'naive-ui'
import { useIdentity, useRuntime } from '../../app/runtime'
import { ApiError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import AuthLayout from './AuthLayout.vue'
import AuthPasswordInput from '../../shared/ui/PasswordInput.vue'
import AuthFormItem from '../../shared/ui/CompactFormItem.vue'
import { useRequiredFields } from '../../shared/forms/required-fields'
import { validPassword } from '../../shared/auth/account-rules'
const { t } = useI18n()
const runtime = useRuntime()
const { passwordRequired } = useIdentity()
const router = useRouter()
const currentPassword = ref(''),
  newPassword = ref(''),
  confirmation = ref('')
const currentInput = ref<InstanceType<typeof AuthPasswordInput> | null>(null)
const newInput = ref<InstanceType<typeof AuthPasswordInput> | null>(null)
const confirmationInput = ref<InstanceType<typeof AuthPasswordInput> | null>(null)
const { missing, firstMissing } = useRequiredFields({ currentPassword, newPassword, confirmation })
const busy = ref(false),
  mismatch = ref(false)
const error = shallowRef<unknown>(null)
async function submit() {
  if (busy.value) return
  error.value = null
  mismatch.value = false
  const first = firstMissing()
  if (first) {
    const inputs = {
      currentPassword: currentInput,
      newPassword: newInput,
      confirmation: confirmationInput,
    }
    inputs[first].value?.focus()
    return
  }
  mismatch.value = newPassword.value !== confirmation.value
  if (mismatch.value) return
  if (!validPassword(newPassword.value) || newPassword.value === currentPassword.value) {
    error.value = new ApiError('generic', 400, { messageKey: 'error.security.password' })
    return
  }
  busy.value = true
  try {
    await runtime.request<void>('/api/auth/change-password', {
      method: 'POST',
      body: { currentPassword: currentPassword.value, newPassword: newPassword.value },
    })
    runtime.clearSession()
    await router.replace({ name: 'login', query: { reason: 'changed' } })
  } catch (e) {
    error.value = e
  } finally {
    currentPassword.value = ''
    newPassword.value = ''
    confirmation.value = ''
    busy.value = false
  }
}
async function exit() {
  busy.value = true
  let reason: string | undefined
  try {
    await runtime.logout()
  } catch {
    reason = 'logoutFailed'
  }
  await router.replace({ name: 'login', query: { reason } })
}
</script>
<template>
  <AuthLayout
    ><h1>{{ t(passwordRequired ? 'auth.firstPassword' : 'auth.changePassword') }}</h1>
    <p class="notice">{{ t('auth.passwordHint') }}</p>
    <NForm class="auth-form" novalidate :show-feedback="false" @submit.prevent="submit">
      <AuthFormItem
        id="auth-current-password"
        required
        :label="t('auth.currentPassword')"
        :missing="missing('currentPassword')"
      >
        <AuthPasswordInput
          id="auth-current-password"
          ref="currentInput"
          v-model="currentPassword"
          :label="t('auth.currentPassword')"
          autocomplete="current-password"
          :disabled="busy"
          :invalid="missing('currentPassword')"
          :missing="missing('currentPassword')"
        />
      </AuthFormItem>
      <AuthFormItem
        id="auth-new-password"
        required
        :label="t('auth.newPassword')"
        :missing="missing('newPassword')"
      >
        <AuthPasswordInput
          id="auth-new-password"
          ref="newInput"
          v-model="newPassword"
          :label="t('auth.newPassword')"
          autocomplete="new-password"
          :disabled="busy"
          :invalid="missing('newPassword')"
          :missing="missing('newPassword')"
          describedby="auth-password-policy"
        />
      </AuthFormItem>
      <AuthFormItem
        id="auth-confirm-password"
        required
        :label="t('auth.confirmPassword')"
        :missing="missing('confirmation')"
      >
        <AuthPasswordInput
          id="auth-confirm-password"
          ref="confirmationInput"
          v-model="confirmation"
          :label="t('auth.confirmPassword')"
          autocomplete="new-password"
          :disabled="busy"
          :invalid="missing('confirmation')"
          :missing="missing('confirmation')"
          describedby="auth-password-policy"
        />
      </AuthFormItem>
      <p id="auth-password-policy" class="auth-policy">{{ t('auth.passwordPolicy') }}</p>
      <p v-if="mismatch" class="request-error" role="alert">{{ t('auth.mismatch') }}</p>
      <RequestError :error="error" />
      <div class="auth-actions">
        <NButton type="primary" attr-type="submit" :loading="busy" :disabled="busy">{{
          t('auth.savePassword')
        }}</NButton>
        <div class="auth-secondary">
          <NButton :disabled="busy" @click="exit">{{ t('auth.logout') }}</NButton
          ><NButton
            v-if="!passwordRequired"
            tag="a"
            href="/"
            :disabled="busy"
            @click.prevent="router.push('/')"
            >{{ t('common.cancel') }}</NButton
          >
        </div>
      </div>
    </NForm></AuthLayout
  >
</template>
