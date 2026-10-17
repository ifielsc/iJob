import type { Draft, Resource, Target } from './model'
export interface Snapshot {
  name: string
  code: string
  max: number | null
  enabled: boolean
  description: string
  version: string
  resources: Resource[]
  pluginType: string
  active: number
  references: number
}
interface Pending {
  draft: Draft
  snapshot: Snapshot
  deleting: boolean
}
const sessions = new WeakMap<object, { revision: number; entries: Map<string, Pending> }>()
export function pendingWrites(runtime: { session: { revision: number } }) {
  let session = sessions.get(runtime)
  if (!session || session.revision !== runtime.session.revision) {
    session = { revision: runtime.session.revision, entries: new Map() }
    sessions.set(runtime, session)
  }
  return session.entries
}
export function targetKey(target: Target) {
  return target.kind + ':' + (target.kind === 'plugin' ? target.type : (target.id ?? 'new'))
}
