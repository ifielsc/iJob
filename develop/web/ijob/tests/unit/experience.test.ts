// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { draftFingerprint } from '../../src/shared/forms/draft-fingerprint'
import { collectReferences } from '../../src/shared/runtime/reference-names'
import { parse } from 'lossless-json'
describe('effective edit detection', () => {
  it('ignores trimmed names and retained secret presentation but preserves exact code', () => {
    expect(
      draftFingerprint({
        name: ' A ',
        password: { mode: 'keep', configured: true, value: 'mask' },
      }),
    ).toBe(draftFingerprint({ name: 'A', password: { mode: 'keep', configured: true, value: '' } }))
    expect(draftFingerprint({ script: 'x ' })).not.toBe(draftFingerprint({ script: 'x' }))
    expect(draftFingerprint({ password: { mode: 'clear', configured: true } })).not.toBe(
      draftFingerprint({ password: { mode: 'keep', configured: true } }),
    )
  })
  it('preserves business payload text and treats an empty secret replacement as retention', () => {
    expect(draftFingerprint({ userConfig: { name: ' A ' } })).not.toBe(
      draftFingerprint({ userConfig: { name: 'A' } }),
    )
    expect(draftFingerprint({ password: { mode: 'replace', configured: true, value: '' } })).toBe(
      draftFingerprint({ password: { mode: 'keep', configured: true, value: '' } }),
    )
  })
  it('collects nested references without rounding IDs or interpreting arbitrary business JSON', () => {
    expect(
      collectReferences(
        parse(
          '{"source":{"resourceId":9223372036854775807},"target":{"resourceId":9223372036854775807},"notifyConfigId":"12","userConfig":{"resourceId":"13"}}',
        ),
      ).map((r) => [r.kind, r.id]),
    ).toEqual([
      ['RESOURCE', '9223372036854775807'],
      ['NOTIFICATION', '12'],
    ])
  })
})
