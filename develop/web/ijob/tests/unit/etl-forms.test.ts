import { describe, it, expect } from 'vitest'
import {
  formText,
  prepareForm,
  ruleFormText,
  ruleSchemas,
  targetSchemas,
  formNode,
} from '../../src/modules/tasks/etl-form-model'
import {
  etlDraft,
  etlIssues,
  etlConfig,
  prepareEtlForms,
  transformExamples,
  validationExamples,
} from '../../src/modules/tasks/etl-model'
import { fileConfig } from '../etl-fixture'

describe('ETL typed forms', () => {
  it.each(Object.entries({ ...transformExamples, ...validationExamples }))(
    'loads and saves %s without changing its parameters',
    (type, properties) => {
      const raw = JSON.stringify(properties)
      expect(JSON.parse(ruleFormText(type, raw, prepareForm(raw, ruleSchemas[type]!)))).toEqual(
        properties,
      )
    },
  )
  it('preserves exact numbers, null, booleans, empty text and nested extension values', () => {
    const raw =
      '{"a":9223372036854775807,"b":0.1234567890123456789,"c":null,"d":false,"e":"","f":{"items":[1,"1"]}}'
    expect(ruleFormText('DEFAULT_VALUE', raw, prepareForm(raw, ruleSchemas.DEFAULT_VALUE!))).toBe(
      raw,
    )
  })
  it('keeps incomplete numeric edits and rejects them without losing the draft', () => {
    const d = etlDraft(fileConfig)
    prepareEtlForms(d)
    const value = d.transforms[0]!.form!.entries.find((e) => e.key === 'multiply')!.node
    value.value = '-'
    expect(etlIssues(d)['etl.transforms.0']).toBe('ruleInvalid')
    expect(value.value).toBe('-')
    value.value = '0.9999999999999999999'
    expect(etlIssues(d)).toEqual({})
    expect(etlConfig(d)).toContain('0.9999999999999999999')
  })
  it('blocks blank and duplicate map keys without silently overwriting rows', () => {
    const node = prepareForm('{"id":"order_id"}', ruleSchemas.FIELD_MAPPING!)
    node.entries.push({ key: '', node: formNode('other') })
    expect(() => formText(node, ruleSchemas.FIELD_MAPPING)).toThrow()
    node.entries[1]!.key = 'id'
    expect(() => formText(node, ruleSchemas.FIELD_MAPPING)).toThrow()
    expect(node.entries).toHaveLength(2)
  })
  it('preserves server defaults and explicit empty current values for SCD2', () => {
    const raw =
      '{"businessKeys":["id"],"compareFields":["amount"],"effectiveValueField":"at","validFromColumn":"start_at","validToColumn":"end_at","currentValue":""}'
    expect(formText(prepareForm(raw, targetSchemas.scd2!), targetSchemas.scd2)).toBe(raw)
  })
  it('validates target form scripts against file incremental restrictions', () => {
    const d = etlDraft(fileConfig)
    prepareEtlForms(d)
    d.targetForms.beforeSql!.entries.push({ key: '', node: formNode('DELETE FROM orders') })
    expect(etlIssues(d)['etl.target.beforeSql']).toBe('noScripts')
  })
  it.each([
    ['NUMBER_SCALE', '{"field":"n","divide":0}'],
    ['NUMBER_SCALE', '{"field":"n","scale":1.5}'],
    ['RANGE', '{"field":"n"}'],
    ['SQL', '{"resourceId":1,"mode":"FIELD","sql":"select 1"}'],
    ['LOOKUP', '{"resourceId":9223372036854775808}'],
    ['REQUIRED', '{"fields":[""]}'],
  ])('rejects invalid %s parameters', (type, raw) => {
    expect(() => ruleFormText(type, raw, prepareForm(raw, ruleSchemas[type]!))).toThrow()
  })
  it('accepts legacy numeric strings and very small nonzero decimals losslessly', () => {
    const raw = '{"field":"n","multiply":"0.1234567890123456789","divide":1e-1000}'
    expect(ruleFormText('NUMBER_SCALE', raw, prepareForm(raw, ruleSchemas.NUMBER_SCALE!))).toBe(raw)
  })
})
