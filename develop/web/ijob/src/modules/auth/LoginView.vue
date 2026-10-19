<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NForm, NInput } from 'naive-ui'
import type { InputInst } from 'naive-ui'
import { UserRound } from '@lucide/vue'
import { useRuntime } from '../../app/runtime'
import RequestError from '../../shared/ui/RequestError.vue'
import AuthLayout from './AuthLayout.vue'
import AuthPasswordInput from '../../shared/ui/PasswordInput.vue'
import AuthFormItem from '../../shared/ui/CompactFormItem.vue'
import { useRequiredFields } from '../../shared/forms/required-fields'
const { t } = useI18n()
const runtime = useRuntime()
const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const usernameInput = ref<InputInst | null>(null)
const passwordInput = ref<InstanceType<typeof AuthPasswordInput> | null>(null)
const { missing, firstMissing } = useRequiredFields({ username, password })
const busy = ref(false)
const error = shallowRef<unknown>(null)
async function submit() {
  if (busy.value) return
  error.value = null
  const first = firstMissing()
  if (first) {
    const input = first === 'username' ? usernameInput.value : passwordInput.value
    input?.focus()
    return
  }
  busy.value = true
  try {
    const user = await runtime.login(username.value, password.value)
    password.value = ''
    await router.replace(user.mustChangePassword ? '/change-password' : '/')
  } catch (e) {
    error.value = e
    password.value = ''
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <AuthLayout
    ><h1>{{ t('auth.login') }}</h1>
    <p class="subtitle">{{ t('auth.loginHint') }}</p>
    <p v-if="route.query.reason === 'changed'" class="notice" role="status">
      {{ t('auth.changed') }}
    </p>
    <p v-if="route.query.reason === 'expired'" class="notice" role="status">
      {{ t('error.unauthorized') }}
    </p>
    <p v-if="route.query.reason === 'logoutFailed'" class="notice" role="status">
      {{ t('auth.logoutFailed') }}
    </p>
    <NForm class="auth-form" novalidate :show-feedback="false" @submit.prevent="submit">
      <AuthFormItem
        id="auth-username"
        required
        :label="t('auth.username')"
        :missing="missing('username')"
      >
        <NInput
          ref="usernameInput"
          v-model:value="username"
          :disabled="busy"
          :maxlength="64"
          :input-props="{
            id: 'auth-username',
            autocomplete: 'username',
            required: true,
            'aria-label': t('auth.username'),
            'aria-invalid': missing('username') || undefined,
            'aria-describedby': missing('username') ? 'auth-username-required' : undefined,
          }"
        >
          <template #prefix><UserRound :size="16" aria-hidden="true" /></template>
        </NInput>
      </AuthFormItem>
      <AuthFormItem
        id="auth-password"
        required
        :label="t('auth.password')"
        :missing="missing('password')"
      >
        <AuthPasswordInput
          id="auth-password"
          ref="passwordInput"
          v-model="password"
          :label="t('auth.password')"
          autocomplete="current-password"
          :disabled="busy"
          :invalid="missing('password')"
          :missing="missing('password')"
        />
      </AuthFormItem>
      <RequestError :error="error" /><NButton
        type="primary"
        attr-type="submit"
        :loading="busy"
        :disabled="busy"
        >{{ t('auth.login') }}</NButton
      >
    </NForm></AuthLayout
  >
</template>
