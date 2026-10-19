// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { afterEach, describe, expect, it, vi } from 'vitest'
import { randomUuid } from '../../src/shared/runtime/uuid'

afterEach(() => vi.unstubAllGlobals())

describe('randomUuid', () => {
  it('uses the browser UUID when available', () => {
    const value = '8f80ee90-0862-46e0-a79a-9090077abfaa'
    vi.stubGlobal('crypto', { randomUUID: () => value })
    expect(randomUuid()).toBe(value)
  })

  it('generates RFC 4122 version 4 IDs on HTTP origins', () => {
    const source = globalThis.crypto
    vi.stubGlobal('crypto', { getRandomValues: source.getRandomValues.bind(source) })
    const ids = new Set(Array.from({ length: 1000 }, () => randomUuid()))
    expect(ids.size).toBe(1000)
    for (const id of ids) {
      expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    }
  })
})
