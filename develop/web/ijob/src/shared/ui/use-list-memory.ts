import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { useRuntime } from '../../app/runtime'
type Snapshot = { values: Record<string, string>; page: number; size: number; top: number }
const memory = new WeakMap<object, Map<string, Snapshot>>()
// In-memory, session-scoped list state only: never persist editor drafts or secrets.
export function useListMemory<T extends Record<string, string>>(
  draft: T,
  applied: Ref<T>,
  page: Ref<number>,
  size: Ref<number>,
  fetching: Ref<boolean>,
) {
  const runtime = useRuntime(),
    route = useRoute(),
    path = String(route.name ?? route.path),
    revision = runtime.session.revision
  let states = memory.get(runtime)
  if (!states) {
    states = new Map()
    memory.set(runtime, states)
  }
  const key = String(revision) + ':' + path,
    saved = states.get(key)
  const explicit = Object.keys(route.query).length > 0
  if (saved && !explicit) {
    Object.assign(draft, saved.values)
    applied.value = { ...draft }
    page.value = saved.page
    size.value = saved.size
  }
  let pending = saved?.top ?? 0
  const scroll = () => document.querySelector<HTMLElement>('#main')
  const restore = async () => {
    if (!pending || fetching.value) return
    await nextTick()
    const top = pending
    pending = 0
    scroll()?.scrollTo({ top })
    window.scrollTo({ top })
  }
  onMounted(restore)
  watch(fetching, restore)
  watch([applied, page, size], () => {
    pending = 0
  })
  watch(
    () => runtime.session.revision,
    () => {
      states?.clear()
      pending = 0
    },
  )
  onBeforeUnmount(() => {
    if (runtime.session.revision === revision && !runtime.accessIssue.value)
      states?.set(key, {
        values: { ...applied.value },
        page: page.value,
        size: size.value,
        top: Math.max(scroll()?.scrollTop ?? 0, window.scrollY),
      })
  })
  return {
    removeFilter: (key: string) => {
      if (key in draft) {
        Object.assign(draft, { [key]: '' })
        applied.value = { ...applied.value, [key]: '' }
        page.value = 1
      }
    },
  }
}
