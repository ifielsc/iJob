// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { describe, expect, it } from 'vitest'
import {
  previewParameters,
  parameterValues,
  previewResult,
  applyPreviewFields,
} from '../../src/shared/ui/sql-preview'
import { exactGlob, resourcePath } from '../../src/shared/ui/file-browser'
import { transferDraft, transferIssues } from '../../src/modules/tasks/file-transfer-model'
describe('resource preview contracts', () => {
  it('applies template constraints without exposing sensitive defaults', () => {
    const rows = previewParameters('SELECT ${id}, ${secret}', { resourceId: '1', mode: 'SQL' })
    applyPreviewFields(rows, {
      fields: [
        {
          name: 'id',
          type: 'INTEGER',
          minimum: '9007199254740993',
          hasDefault: true,
          defaultValue: '9007199254740993',
        },
        {
          name: 'secret',
          type: 'STRING',
          sensitive: true,
          hasDefault: true,
          defaultValue: 'not-returned',
        },
      ],
    })
    expect(rows[0]!.kind).toBe('number')
    expect(rows[1]!.value).toBe('')
    expect(rows[1]!.sensitive).toBe(true)
    expect(String(parameterValues(rows).variables.id)).toBe('9007199254740993')
    rows[0]!.value = '9007199254740992'
    expect(() => parameterValues(rows)).toThrow('minimum')
  })
  it('extracts only selected parameters, preserves defaults and runtime samples', () => {
    const rows = previewParameters(
      "SELECT '${date:2026-10-22}', ${count!}, ${count!}, ${ijob.instance.rootId}",
      { resourceId: '1', mode: 'SQL' },
    )
    expect(rows.map((r) => [r.name, r.value, r.source])).toEqual([
      ['date', '2026-10-22', 'default'],
      ['count', '', 'input'],
      ['ijob.instance.rootId', '', 'runtime'],
    ])
  })
  it('keeps ETL bound values separate from interpolation and SQL quoted colons', () => {
    const rows = previewParameters("SELECT :id, ':ignored', 1::int, ${min:10}", {
      resourceId: '1',
      mode: 'ETL',
      parameters: '{"id":9007199254740993}',
    })
    expect(rows.map((r) => r.name)).toEqual(['min', 'id'])
    expect(String(parameterValues(rows).parameters.id)).toBe('9007199254740993')
    expect(parameterValues(rows).variables).toEqual({ min: '10' })
  })
  it('includes variables used by named ETL parameter values', () => {
    const rows = previewParameters('SELECT :day', {
      resourceId: '1',
      mode: 'ETL',
      parameters: '{"day":"${date!}"}',
    })
    expect(rows.map((r) => r.name)).toEqual(['day', 'date'])
  })
  it('rejects invalid typed values and malformed results', () => {
    const row = previewParameters('SELECT ${count}', { resourceId: '1', mode: 'SQL' })[0]!
    row.kind = 'number'
    row.value = 'NaN'
    expect(() => parameterValues([row])).toThrow()
    expect(() =>
      previewResult({
        status: 'SUCCESS',
        columns: [],
        rows: [['unexpected']],
        elapsedMillis: 1,
        truncated: '',
        traceId: 'x',
      }),
    ).toThrow()
  })
  it('escapes literal filenames without converting a source directory into a filename', () => {
    expect(exactGlob('orders[1]*?.csv')).toBe('orders\\[1\\]\\*\\?.csv')
    expect(resourcePath('/home/files/', 'inbox')).toBe('/home/files/inbox')
    expect(resourcePath('/', '')).toBe('/')
    const draft = transferDraft()
    draft.sourceResourceId = '1'
    draft.targetResourceId = '2'
    draft.filePattern = exactGlob('orders[1].csv')
    expect(transferIssues(draft)['transfer.filePattern']).toBeUndefined()
    draft.filePattern = 'sub\\orders.csv'
    expect(transferIssues(draft)['transfer.filePattern']).toBeDefined()
  })
})
