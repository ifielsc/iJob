// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { useLeaveGuard } from '../../src/shared/forms/use-leave-guard'
const guards = vi.hoisted(() => ({
  leave: undefined as undefined | (() => unknown),
  update: undefined as
    undefined | ((to: { query: { tab?: string } }, from: { query: { tab?: string } }) => unknown),
}))
const state = { accessIssue: ref(null as string | null), session: { token: 'token' } }
vi.mock('../../src/app/runtime', () => ({ useRuntime: () => state }))
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: typeof guards.leave) => {
    guards.leave = guard
  },
  onBeforeRouteUpdate: (guard: typeof guards.update) => {
    guards.update = guard
  },
}))
describe('editor route and unload guard', () => {
  it('protects active drafts, permits clean/details views and releases invalid sessions', async () => {
    const dirty = ref(true),
      confirm = vi.fn().mockResolvedValue(false)
    const wrapper = mount(
      defineComponent({
        setup() {
          useLeaveGuard(() => dirty.value, confirm)
          return () => null
        },
      }),
    )
    expect(await guards.leave?.()).toBe(false)
    expect(confirm).toHaveBeenCalledOnce()
    const unload = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(unload)
    expect(unload.defaultPrevented).toBe(true)
    dirty.value = false
    const clean = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(clean)
    expect(clean.defaultPrevented).toBe(false)
    state.accessIssue.value = 'unauthorized'
    expect(await guards.leave?.()).toBe(true)
    expect(confirm).toHaveBeenCalledOnce()
    state.accessIssue.value = null
    wrapper.unmount()
    dirty.value = true
    const removed = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(removed)
    expect(removed.defaultPrevented).toBe(false)
  })
})
