// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { hidden, type HiddenField } from './contracts'
export interface HeaderDraft {
  name: string
  field: HiddenField
}
export function headerDraft(names: string[] = []): HeaderDraft[] {
  return names.map((name) => ({ name, field: hidden(true, true) }))
}
export function headerBody(rows: HeaderDraft[]) {
  return {
    preservedHeaderNames: rows.filter((r) => r.field.mode === 'keep').map((r) => r.name),
    headers: Object.fromEntries(
      rows.filter((r) => r.field.mode === 'replace').map((r) => [r.name, r.field.value]),
    ),
  }
}
export function headerIssues(
  rows: HeaderDraft[],
  max = 100,
  maxName = 256,
  maxValue = 8192,
  oauth = false,
) {
  const errors: Record<string, string> = {},
    names = new Set<string>()
  if (rows.length > max) errors.headers = 'invalid'
  rows.forEach((row, i) => {
    const name = row.name.toLowerCase()
    if (
      !/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(row.name) ||
      row.name.length > maxName ||
      names.has(name) ||
      (oauth && name === 'authorization')
    )
      errors['header-name-' + i] = 'invalid'
    if (
      row.field.mode === 'replace' &&
      (row.field.value.length > maxValue ||
        [...row.field.value].some((c) => c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127))
    )
      errors['header-value-' + i] = 'invalid'
    names.add(name)
  })
  return errors
}
