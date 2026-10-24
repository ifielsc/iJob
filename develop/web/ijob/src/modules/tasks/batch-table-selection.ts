// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export const tableIdentity = (name: string) => name.trim().toUpperCase()
export function newTableNames(existing: { name: string }[], selected: string[]) {
  const seen = new Set(existing.map((row) => tableIdentity(row.name)))
  return selected.filter((name) => {
    const key = tableIdentity(name)
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}
