// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect } from 'vitest'
import {
  fieldValue,
  hidden,
  id,
  parseConfig,
  parseCalendar,
  parsePreview,
  validDate,
  splitValues,
} from '../../src/modules/configuration/contracts'
describe('configuration contracts', () => {
  it('keeps opaque values absent and distinguishes explicit empty collections', () => {
    const field = hidden(true, true, 2)
    expect(fieldValue(field, true)).toBeUndefined()
    expect(fieldValue({ ...field, mode: 'clear' }, true)).toEqual([])
    expect(
      fieldValue({ ...field, mode: 'replace', value: 'a@example.test\nb@example.test' }, true),
    ).toEqual(['a@example.test', 'b@example.test'])
    expect(splitValues(' a, b\n ')).toEqual(['a', 'b'])
  })
  it('rejects numeric identifiers rather than recovering rounded values', () => {
    expect(id('9223372036854775807')).toBe('9223372036854775807')
    expect(() => id(Number('9223372036854775807'))).toThrow()
    expect(() =>
      parseConfig(
        {
          id: '1',
          name: 'x',
          type: 'EMAIL',
          enabled: true,
          config: { fromConfigured: true, toCount: '2', ccCount: 0 },
        },
        true,
      ),
    ).toThrow()
  })
  it('validates real calendar dates and bounded saved-rule previews', () => {
    expect(parsePreview([{ date: '2026-09-21', allowed: true }])).toEqual([
      { date: '2026-09-21', allowed: true, exceptionType: null },
    ])
    expect(validDate('2026-02-29')).toBe(false)
    expect(validDate('2024-02-29')).toBe(true)
    expect(() =>
      parseCalendar({ id: '1', name: 'x', workdays: [], exceptions: [] }, true),
    ).toThrow()
    expect(() =>
      parsePreview([{ date: '2026-09-25', allowed: true, exceptionType: 'OTHER' }]),
    ).toThrow()
    expect(
      parsePreview([{ date: '2026-09-25', allowed: false, exceptionType: 'HOLIDAY' }]),
    ).toHaveLength(1)
  })
})
