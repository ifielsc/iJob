// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Runtime } from '../../app/runtime'
const sessions = new WeakMap<Runtime, { revision: number; requests: Map<string, string> }>()
export function commandMemory(runtime: Runtime) {
  let session = sessions.get(runtime)
  if (!session || session.revision !== runtime.session.revision) {
    session = { revision: runtime.session.revision, requests: new Map() }
    sessions.set(runtime, session)
  }
  return session.requests
}
