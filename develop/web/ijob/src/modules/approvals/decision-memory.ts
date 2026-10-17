import type { Runtime } from '../../app/runtime'
import { effectScope, watch } from 'vue'
import type { Decision } from './model'
export interface PendingDecision {
  action: Decision
  comment: string
}
const stores = new WeakMap<Runtime, { revision: number; entries: Map<string, PendingDecision> }>()
// Uncertain writes survive closing the drawer, but never an identity change or a page reload.
export function decisionMemory(runtime: Runtime) {
  let store = stores.get(runtime)
  if (!store) {
    store = { revision: runtime.session.revision, entries: new Map() }
    stores.set(runtime, store)
    const current = store
    effectScope(true).run(() =>
      watch(
        () => runtime.session.revision,
        (revision) => {
          current.entries.clear()
          current.revision = revision
        },
        { flush: 'sync' },
      ),
    )
  }
  if (store.revision !== runtime.session.revision) {
    store.entries.clear()
    store.revision = runtime.session.revision
  }
  return store.entries
}
