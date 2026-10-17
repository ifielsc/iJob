import { describe, expect, it } from 'vitest'
import {
  draftFor,
  issues,
  taskBody,
  sqlConfig,
  exactId,
  variables,
  runRow,
} from '../../src/modules/tasks/model'
describe('SQL configuration contracts', () => {
  it('preserves max Long JSON resource IDs without a Number roundtrip', () => {
    expect(sqlConfig('{"resourceId":9223372036854775807}').resourceId).toBe('9223372036854775807')
    expect(exactId('9223372036854775807')).toBe('9223372036854775807')
    expect(() => exactId(Number('9223372036854775807'))).toThrow()
    expect(() => exactId('9223372036854775808')).toThrow()
  })
  it('uses actual defaults and preserves zero and false', () => {
    const d = draftFor()
    expect(d.transaction).toBe(true)
    expect(d.queryTimeoutSeconds).toBe(600)
    expect(d.retryTimes).toBe(0)
    Object.assign(d, {
      name: 'SQL',
      resourceId: '9223372036854775807',
      script: 'SELECT 1;',
      transaction: false,
      minRows: 0,
      enabled: false,
    })
    expect(issues(d)).toEqual({})
    const b = taskBody(d)
    expect(b.enabled).toBe(false)
    expect(b.timeout).toBe(null)
    expect(JSON.parse(b.pluginConfig)).toMatchObject({
      resourceId: '9223372036854775807',
      transaction: false,
      minRows: 0,
      maxRows: null,
      resultVariable: null,
    })
  })
  it('validates interdependent limits rather than silently submitting hidden invalid values', () => {
    const d = draftFor()
    Object.assign(d, {
      name: 'SQL',
      resourceId: '1',
      script: 'SELECT 1',
      continueOnError: true,
      minRows: 4,
      maxRows: 2,
      retryTimes: 100,
      retryInterval: 3600,
    })
    expect(issues(d)).toMatchObject({
      continueOnError: 'invalid',
      minRows: 'rowRange',
      retryInterval: 'retryRange',
    })
  })
  it('distinguishes missing, invalid optional text and integer ranges', () => {
    const d = draftFor()
    Object.assign(d, { resultVariable: ' ', queryTimeoutSeconds: null, timeout: 0, groups: 'a,,b' })
    expect(issues(d)).toMatchObject({
      name: 'required',
      resourceId: 'required',
      script: 'required',
      resultVariable: 'invalid',
      queryTimeoutSeconds: 'invalid',
      timeout: 'invalid',
      groups: 'invalid',
    })
  })
  it('rejects non-object or imprecise execution variables', () => {
    expect(variables('')).toEqual({})
    expect(variables('{"enabled":false,"count":0}')).toEqual({ enabled: false, count: 0 })
    for (const value of ['[]', 'null', '{"id":9223372036854775807}'])
      expect(() => variables(value)).toThrow()
  })
  it('retains trigger failures without inventing an instance', () => {
    expect(runRow({ id: '7', status: 'FAILED', displayMessage: 'Unavailable' })).toMatchObject({
      rootInstanceId: null,
      taskDefinitionId: null,
      message: 'Unavailable',
    })
    expect(() => runRow({ id: '8', status: 'ACCEPTED' })).toThrow()
  })
})
