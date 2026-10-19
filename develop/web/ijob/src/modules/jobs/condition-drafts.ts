// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

/** Dormant drafts stay in history so undo can recover incomplete input. */
export function hasActiveConditionDraft(
  edges: readonly { id: string; defaultRoute?: boolean }[],
  drafts: Record<string, Record<string, string>>,
) {
  return edges.some((edge) => !edge.defaultRoute && Object.keys(drafts[edge.id] ?? {}).length > 0)
}
