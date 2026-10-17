import { QueryClient } from '@tanstack/vue-query'
import type { AppLocale } from '../preferences/preferences'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: true },
    mutations: { retry: false },
  },
})

export function localizedKey(loginId: string, locale: AppLocale, ...parts: unknown[]) {
  return ['localized', loginId, locale, ...parts] as const
}
