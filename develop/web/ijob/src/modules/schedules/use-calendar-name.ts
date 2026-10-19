// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRuntime } from '../../app/runtime'
import { record } from '../../shared/api/values'
import { ApiError } from '../../shared/api/errors'
import { id } from './model'

// Resolve the selected calendar directly; it may not be on the first candidate page.
export function useCalendarName(calendarId: () => string) {
  const runtime = useRuntime()
  return useQuery(
    computed(() => ({
      queryKey: [
        'schedule-calendar-name',
        runtime.session.revision,
        runtime.preferences.locale,
        calendarId(),
      ],
      enabled: !!calendarId() && !runtime.accessIssue.value,
      retry: false,
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const selected = calendarId()
        const row = record(
          await runtime.request('/api/calendars/' + selected, { signal, lossless: true }),
        )
        if (id(row.id) !== selected || typeof row.name !== 'string' || !row.name.trim())
          throw new ApiError('protocol')
        return row.name
      },
    })),
  )
}
