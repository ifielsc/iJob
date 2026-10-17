import { describe, expect, it } from 'vitest'
import {
  dataCheckDraft,
  dataCheckConfig,
  dataCheckIssues,
} from '../../src/modules/tasks/data-check-model'
import { draftFor, issues, taskBody, type TaskRow } from '../../src/modules/tasks/model'
const config =
  '{"resourceId":9223372036854775807,"extra":0.123456789012345678901,"checks":[{"name":"amount","type":"NORMAL","sql":"select 1","expected":0.123456789012345678901,"extra":9007199254740993}],"handlers":[{"type":"notify","params":{"notifyConfigId":9007199254740993,"keep":true}},{"type":"VARIABLE","params":{"keep":"yes"}}]}'
describe('DATA_CHECK task contract', () => {
  it('opens malformed config as a blocked draft and preserves its raw text', () => {
    const d = draftFor({
      pluginType: 'DATA_CHECK',
      pluginConfig: '{broken',
      groups: [],
    } as unknown as TaskRow)
    expect(d.check.blocked).toBe(true)
    expect(d.check.original).toBe('{broken')
  })
  it('compares COUNT bounds without rounding large integers', () => {
    const d = dataCheckDraft(config),
      item = d.checks[0]!
    item.type = 'COUNT'
    item.compareType = 'BETWEEN'
    item.minValue = { kind: 'number', text: '9007199254740993' }
    item.maxValue = { kind: 'number', text: '9007199254740992' }
    expect(dataCheckIssues(d)['check.0.minValue']).toBeDefined()
  })
  it('preserves decimal numbers, long IDs, unknown fields and handler order', () => {
    const d = dataCheckDraft(config)
    expect(d.blocked).toBe(false)
    expect(dataCheckIssues(d)).toEqual({})
    const saved = dataCheckConfig(d, '9223372036854775807')
    expect(saved).toContain('"expected":0.123456789012345678901')
    expect(saved).toContain('"extra":9007199254740993')
    expect(saved).toContain('"notifyConfigId":9007199254740993')
    expect(saved).toContain('"extra":0.123456789012345678901')
    expect(saved.indexOf('"type":"notify"')).toBeLessThan(saved.indexOf('"type":"VARIABLE"'))
    expect(saved).toContain('"keep":true')
  })
  it.each(['UNKNOWN', 'PROBE'])(
    'blocks unsupported check type %s without discarding original',
    (type) => {
      const raw = config.replace('NORMAL', type),
        d = dataCheckDraft(raw)
      expect(d.blocked).toBe(true)
      expect(d.original).toBe(raw)
      expect(() => dataCheckConfig(d, '1')).toThrow()
    },
  )
  it('serializes four check types with exclusive comparison fields', () => {
    const d = dataCheckDraft(
      JSON.stringify({
        resourceId: 1,
        checks: [
          { name: 'value', type: 'NORMAL', sql: 'select 1', expected: true },
          {
            name: 'rows',
            type: 'COUNT',
            sql: 'select 1',
            compareType: 'BETWEEN',
            minValue: 0,
            maxValue: 3,
          },
          { name: 'exists', type: 'EXISTS', sql: 'select 1' },
          { name: 'empty', type: 'EMPTY', sql: 'select 1' },
        ],
      }),
    )
    expect(dataCheckIssues(d)).toEqual({})
    const c = JSON.parse(dataCheckConfig(d, '1'))
    expect(c.checks[1].expected).toBeUndefined()
    expect(c.checks[2].expected).toBe(true)
    expect(c.checks[3].compareType).toBeUndefined()
  })
  it('validates key collisions only when their producer is enabled', () => {
    const d = dataCheckDraft(config)
    d.checks[0]!.variable = 'data_check_result'
    expect(dataCheckIssues(d)['check.0.variable']).toBeDefined()
    d.exportVariables = false
    expect(dataCheckIssues(d)).toEqual({})
    d.exportVariables = true
    d.checks[0]!.variable = 'amount'
    expect(dataCheckIssues(d)['check.0.variable']).toBeDefined()
  })
  it('rejects empty checks, invalid timeouts and invalid notification IDs', () => {
    const d = dataCheckDraft()
    d.checks = []
    d.queryTimeoutSeconds = 3601
    d.notify = true
    d.notifyConfigId = '9223372036854775808'
    expect(Object.keys(dataCheckIssues(d))).toEqual(
      expect.arrayContaining(['check.checks', 'check.queryTimeoutSeconds', 'check.notifyConfigId']),
    )
  })
  it('retains advanced JSON and checks expression/COUNT/EXISTS types', () => {
    const d = dataCheckDraft(config)
    const item = d.checks[0]!
    item.expected = { kind: 'json', text: '{"exact":9223372036854775807}' }
    expect(dataCheckConfig(d, '1')).toContain('"expected":{"exact":9223372036854775807}')
    item.type = 'COUNT'
    expect(dataCheckIssues(d)['check.0.expected']).toBeDefined()
    item.expected = { kind: 'expression', text: '${expected!}' }
    expect(dataCheckIssues(d)).toEqual({})
    item.type = 'EXISTS'
    item.expected = { kind: 'text', text: 'true' }
    expect(dataCheckIssues(d)['check.0.expected']).toBeDefined()
  })
  it('uses DATA_CHECK task serialization rather than SQL or script configuration', () => {
    const row = {
      pluginType: 'DATA_CHECK',
      pluginConfig: config,
      name: 'Check',
      groups: [],
      retryTimes: 0,
      retryInterval: 0,
      timeout: null,
      description: '',
      enabled: true,
    } as unknown as TaskRow
    const draft = draftFor(row)
    expect(issues(draft)).toEqual({})
    expect(taskBody(draft, row).pluginConfig).toContain('"checks"')
    expect(taskBody(draft, row).pluginConfig).not.toContain('"transaction"')
  })
})
