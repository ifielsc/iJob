// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { inject, type InjectionKey } from 'vue'
import type { createApiClient } from './client'
export const requestKey: InjectionKey<ReturnType<typeof createApiClient>> = Symbol('request')
export function useRequest() {
  const request = inject(requestKey)
  if (!request) throw new Error('Request provider is missing')
  return request
}
