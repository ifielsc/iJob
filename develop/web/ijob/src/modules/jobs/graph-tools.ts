// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { JobEdge } from './model'

export function connectionProblem(edges: JobEdge[], from: string, to: string) {
  if (!from || !to || from === to || edges.some((e) => e.fromNodeId === from && e.toNodeId === to))
    return 'edgeInvalid'
  if (edges.length >= 5000) return 'edgeCount'
  const next = new Map<string, string[]>()
  for (const e of edges) next.set(e.fromNodeId, [...(next.get(e.fromNodeId) ?? []), e.toNodeId])
  const pending = [to],
    visited = new Set<string>()
  while (pending.length) {
    const key = pending.pop()!
    if (key === from) return 'cycle'
    if (visited.has(key)) continue
    visited.add(key)
    pending.push(...(next.get(key) ?? []))
  }
  return undefined
}

export function isTextTarget(target: EventTarget | null) {
  return (
    target instanceof Element &&
    !!target.closest(
      'input, textarea, select, [contenteditable="true"], [role="combobox"], .cm-editor, [role="dialog"]',
    )
  )
}
