// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { ComputedRef, InjectionKey } from 'vue'
import { isLosslessNumber } from 'lossless-json'
export interface ReferenceName {
  kind: string
  id: string
  name: string
  type: string
  enabled?: boolean
}
export const referenceNamesKey: InjectionKey<ComputedRef<Map<string, ReferenceName>>> =
  Symbol('referenceNames')
export function referenceKind(path: string) {
  if (/(?:^|\.)(?:notifyConfigId|notifyConfigIds|notificationId)(?:\.\d+)?$/.test(path))
    return 'NOTIFICATION'
  if (/(?:^|\.)(?:[a-zA-Z]*ResourceId|resourceId|resourceIds)(?:\.\d+)?$/.test(path))
    return 'RESOURCE'
  return ''
}
export function collectReferences(value: unknown) {
  const result = new Map<string, { kind: string; id: string; path: string }>()
  function visit(v: unknown, path: string, depth: number) {
    if (depth > 30) return
    const kind = referenceKind(path)
    if (kind && (typeof v === 'string' || typeof v === 'number' || isLosslessNumber(v))) {
      const id = String(v)
      if (/^[1-9]\d*$/.test(id)) result.set(kind + ':' + id, { kind, id, path })
    } else if (v && typeof v === 'object' && !isLosslessNumber(v)) {
      for (const [key, item] of Object.entries(v)) {
        if (['userConfig', 'body', 'headers', 'variables', 'inputVariables'].includes(key)) continue
        visit(item, path ? path + '.' + key : key, depth + 1)
      }
    }
  }
  visit(value, '', 0)
  return [...result.values()]
}
