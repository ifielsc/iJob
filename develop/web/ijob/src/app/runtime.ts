// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { computed, inject, shallowRef } from 'vue'
import type { InjectionKey } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { createApiClient } from '../shared/api/client'
import { ApiError } from '../shared/api/errors'
import { parseCurrentUser } from '../shared/api/contracts'
import type { Token } from '../shared/api/contracts'
import { queryClient } from '../shared/api/query'
import { useSession } from '../shared/auth/session'
import { usePreferences } from '../shared/preferences/store'

export function createRuntime() {
  const session = useSession()
  const preferences = usePreferences()
  const accessIssue = shallowRef<ApiError | null>(null)
  const request = createApiClient({
    token: () => session.token,
    revision: () => session.revision,
    locale: () => preferences.locale,
    onAccessError(error) {
      accessIssue.value = error
      void queryClient.cancelQueries({ predicate: (q) => q.queryKey[0] !== 'identity' })
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'identity' })
      if (error.kind === 'unauthorized') {
        if (session.token) session.replace(null)
        preferences.switchIdentity(null)
      }
    },
  })
  const identityOptions = () => ({
    queryKey: ['identity', session.revision],
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const data = await request<unknown>('/api/auth/me', { signal })
      try {
        return parseCurrentUser(data)
      } catch {
        throw new ApiError('protocol')
      }
    },
    retry: false as const,
  })
  async function refreshIdentity() {
    accessIssue.value = null
    await queryClient.invalidateQueries({ queryKey: ['identity'] })
  }
  function clearSession() {
    session.replace(null)
    preferences.setBusinessZone(null)
    queryClient.clear()
    accessIssue.value = null
    preferences.switchIdentity(null)
  }
  async function login(username: string, password: string) {
    const token = await request<Token>('/api/auth/login', {
      method: 'POST',
      body: { username, password },
      anonymous: true,
    })
    if (!token || typeof token.tokenName !== 'string' || typeof token.tokenValue !== 'string')
      throw new ApiError('protocol')
    queryClient.clear()
    session.replace(token)
    accessIssue.value = null
    try {
      const user = await queryClient.fetchQuery(identityOptions())
      preferences.switchIdentity(user.loginId, { preserveAppearance: true })
      return user
    } catch (error) {
      clearSession()
      throw error
    }
  }
  async function logout() {
    try {
      await request<void>('/api/auth/logout', { method: 'POST' })
    } finally {
      clearSession()
    }
  }
  return {
    session,
    preferences,
    request,
    accessIssue,
    identityOptions,
    refreshIdentity,
    clearSession,
    login,
    logout,
  }
}
export type Runtime = ReturnType<typeof createRuntime>
export const runtimeKey: InjectionKey<Runtime> = Symbol('runtime')
export function useRuntime() {
  const runtime = inject(runtimeKey)
  if (!runtime) throw new Error('Runtime provider is missing')
  return runtime
}
export function useIdentity() {
  const runtime = useRuntime()
  const query = useQuery(
    computed(() => ({
      ...runtime.identityOptions(),
      enabled: runtime.accessIssue.value?.kind !== 'unauthorized',
    })),
  )
  const user = computed(() =>
    runtime.accessIssue.value?.kind === 'unauthorized' || query.isError.value
      ? undefined
      : query.data.value,
  )
  const passwordRequired = computed(
    () => user.value?.mustChangePassword || runtime.accessIssue.value?.kind === 'passwordRequired',
  )
  return { ...query, user, passwordRequired }
}
