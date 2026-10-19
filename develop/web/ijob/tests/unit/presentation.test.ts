// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import { formatUtc, parseUtc, stringId, toApiUtc } from '../../src/shared/time/format'
import { themes } from '../../src/shared/theme/tokens'
import { compactThemes as authThemes } from '../../src/shared/theme/compact'

function luminance(hex: string) {
  const rgb = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((n) => (n <= 0.04045 ? n / 12.92 : ((n + 0.055) / 1.055) ** 2.4))
  return rgb[0]! * 0.2126 + rgb[1]! * 0.7152 + rgb[2]! * 0.0722
}
function ratio(a: string, b: string) {
  const l = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (l[0]! + 0.05) / (l[1]! + 0.05)
}

describe('language, time, and visual contracts', () => {
  it.each(['light', 'dark'] as const)('auth %s compact theme remains legible', (mode) => {
    const t = authThemes[mode]
    for (const bg of [t.background, t.panel, t.overlay]) {
      for (const fg of [t.text, t.muted, t.primary, t.error])
        expect(ratio(fg, bg), `${fg} on ${bg}`).toBeGreaterThanOrEqual(4.5)
      expect(ratio(t.controlBorder, bg)).toBeGreaterThanOrEqual(3)
    }
    expect(ratio(t.onPrimary, t.primary)).toBeGreaterThanOrEqual(4.5)
  })
  it('has matching translation keys and interpolation parameters', () => {
    for (const section of Object.keys(zhCN) as (keyof typeof zhCN)[]) {
      expect(Object.keys(enUS[section]).sort()).toEqual(Object.keys(zhCN[section]).sort())
      for (const key of Object.keys(zhCN[section])) {
        const chinese = (zhCN[section] as Record<string, string>)[key]!
        const english = (enUS[section] as Record<string, string>)[key]!
        expect(english.match(/\{[^}]+\}/g) ?? []).toEqual(chinese.match(/\{[^}]+\}/g) ?? [])
        expect(english.length).toBeGreaterThan(0)
      }
    }
  })
  it('never shifts UTC source timestamps with language', () => {
    const value = '2026-09-20T00:42:10'
    expect(parseUtc(value).toISOString()).toBe('2026-09-20T00:42:10.000Z')
    for (const locale of ['zh-CN', 'en-US'] as const)
      expect(formatUtc(value, locale, 'Asia/Shanghai')).toBe('2026-09-20 08:42:10')
    for (const locale of ['zh-CN', 'en-US'] as const)
      expect(formatUtc('2026-09-22T13:00:09.999', locale, 'UTC')).toBe('2026-09-22 13:00:09')
    expect(toApiUtc(new Date('2026-09-20T00:42:10Z'))).toBe('2026-09-20T00:42:10.000')
    expect(() => parseUtc('2026-09-20')).toThrow()
    expect(() => parseUtc('2026-02-31T00:00:00')).toThrow()
  })
  it('keeps large IDs as strings and rejects unsafe numeric IDs', () => {
    expect(stringId('9223372036854775807')).toBe('9223372036854775807')
    expect(() => stringId(Number('9223372036854775807'))).toThrow()
  })
  it.each(['light', 'dark'] as const)(
    '%s text and focus colors meet contrast targets on opaque surfaces',
    (mode) => {
      const t = themes[mode]
      for (const bg of [t.background, t.panel, t.overlay]) {
        for (const fg of [t.text, t.muted, t.primary, t.success, t.warning, t.error])
          expect(ratio(fg, bg), `${fg} on ${bg}`).toBeGreaterThanOrEqual(4.5)
        expect(ratio(t.focus, bg)).toBeGreaterThanOrEqual(3)
        expect(ratio(t.controlBorder, bg)).toBeGreaterThanOrEqual(3)
      }
      expect(ratio(t.onPrimary, t.primary)).toBeGreaterThanOrEqual(4.5)
    },
  )
})
