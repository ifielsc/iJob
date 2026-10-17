import { describe, expect, it } from 'vitest'
import { readDocument, writeDocument, valueText, newValue } from '../../src/shared/ui/json-form'

describe('typed JSON form', () => {
  it('preserves integer-looking field order for paired sort fields', () => {
    const source = '{"20":"first","1":"second"}'
    expect(writeDocument(readDocument(source))).toBe(source)
  })
  it('round-trips every root type without confusing null with an absent value', () => {
    for (const text of ['"hello"', '""', 'false', '0', 'null', '[]', '{}', '1e30'])
      expect(writeDocument(readDocument(text))).toBe(text)
  })
  it('preserves exact numbers, nested null, false, empty strings and property names', () => {
    const text =
      '{"id":9007199254740993,"amount":0.1234567890123456789,"empty":"","nil":null,"enabled":false,"__proto__":{"x":1},"nested":[null,0,false]}'
    const draft = readDocument(text)
    expect(writeDocument(draft)).toBe(text)
    draft.rows[0]!.included = false
    expect(writeDocument(draft)).not.toContain('"id"')
    expect(writeDocument(draft)).toContain('"__proto__":{"x":1}')
  })
  it('keeps partial drafts invalid until they can be submitted without coercion', () => {
    const draft = readDocument('{"n":1}')
    draft.rows[0]!.text = '-'
    expect(() => writeDocument(draft)).toThrow()
    draft.rows[0]!.kind = 'decimal'
    draft.rows[0]!.text = '0.000000000000000001'
    expect(writeDocument(draft)).toBe('{"n":0.000000000000000001}')
  })
  it('rejects duplicate keys and supports empty names without prototype mutation', () => {
    expect(() => readDocument('{"a":1,"a":2}')).toThrow()
    const draft = readDocument('{"":1,"other":2}')
    draft.rows[1]!.name = ''
    expect(() => writeDocument(draft)).toThrow()
  })
  it('retains SQL strings, array order and placeholders as data', () => {
    const draft = readDocument('["DELETE FROM t WHERE id=${id!};","SELECT 1"]')
    draft.rows.reverse()
    expect(writeDocument(draft)).toBe('["SELECT 1","DELETE FROM t WHERE id=${id!};"]')
    expect(valueText(newValue('null'))).toBe('null')
    expect(writeDocument(readDocument(''))).toBe('')
  })
})
