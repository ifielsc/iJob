// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import {
  bindingBody,
  bindingId,
  notifyBindings,
  notifyCandidate,
  notifyPolicies,
} from '../../src/modules/tasks/notify-binding-model'
const target = '9007199254740993'
const row = {
  id: '9223372036854775807',
  targetType: 'TASK',
  targetId: target,
  notifyConfigId: '9223372036854775806',
  notifyConfigName: 'Ops',
  notifyType: 'EMAIL',
  notifyConfigEnabled: false,
  notifyPolicy: 'ON_FAILURE',
}
describe('task notification bindings', () => {
  it('preserves all long identifiers and disabled metadata for every policy', () => {
    for (const notifyPolicy of notifyPolicies) {
      expect(notifyBindings([{ ...row, notifyPolicy }], target)[0]).toEqual({
        id: row.id,
        targetId: target,
        config: { id: row.notifyConfigId, name: 'Ops', type: 'EMAIL', enabled: false },
        policy: notifyPolicy,
      })
      expect(bindingBody(target, row.notifyConfigId, notifyPolicy)).toEqual({
        targetType: 'TASK',
        targetId: target,
        notifyConfigId: row.notifyConfigId,
        notifyPolicy,
      })
    }
  })
  it('rejects numeric, malformed or overflowing IDs', () => {
    for (const id of [
      1,
      Number('9007199254740993'),
      '0',
      '-1',
      '01',
      '1.0',
      '9223372036854775808',
      null,
    ])
      expect(() => bindingId(id)).toThrow()
  })
  it('rejects wrong target, duplicate bindings, unknown policies and malformed metadata', () => {
    for (const mutation of [
      { targetId: '42' },
      { targetType: 'JOB' },
      { notifyPolicy: 'FAILURE' },
      { notifyConfigEnabled: null },
      { notifyType: 'SMS' },
      { notifyConfigName: null },
    ])
      expect(() => notifyBindings([{ ...row, ...mutation }], target)).toThrow()
    expect(() => notifyBindings([row, { ...row, id: '2' }], target)).toThrow()
    expect(() => notifyBindings({ list: [] }, target)).toThrow()
    expect(() => notifyBindings({ message: 'secret' }, target)).toThrow()
  })
  it('retains the full list for client pagination and projects only list metadata', () => {
    const many = Array.from({ length: 123 }, (_, i) => ({
      ...row,
      id: String(i + 1),
      notifyConfigId: String(i + 200),
    }))
    expect(notifyBindings(many, target)).toHaveLength(123)
    expect(
      notifyCandidate({
        id: '1',
        name: 'Ops',
        type: 'DINGTALK',
        enabled: true,
        webhook: 'not retained',
      }),
    ).toEqual({ id: '1', name: 'Ops', type: 'DINGTALK', enabled: true })
  })
})
