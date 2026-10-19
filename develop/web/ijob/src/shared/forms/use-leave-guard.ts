// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useRuntime } from '../../app/runtime'

/** Shares route/unload protection without owning the editor's draft or close dialog. */
export function useLeaveGuard(dirty: () => boolean, allowClose: () => Promise<boolean>) {
  const runtime = useRuntime()
  const revoked = () => !!runtime.accessIssue.value || !runtime.session.token
  onBeforeRouteLeave(() => (revoked() ? true : allowClose()))
  onBeforeRouteUpdate((to, from) =>
    to.query.tab === from.query.tab || revoked() ? true : allowClose(),
  )
  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (!revoked() && dirty()) event.preventDefault()
  }
  window.addEventListener('beforeunload', beforeUnload)
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
}
