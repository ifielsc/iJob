import { describe, expect, it } from 'vitest'
import { parse, stringify } from 'lossless-json'
import { fromTemplate, readRows, rowsOf, type Template } from '../../src/modules/parameters/model'
const template: Template = {
  id: '1',
  revision: '1',
  name: 'Daily',
  fields: [
    { name: 'size', type: 'INTEGER', hasDefault: true, defaultValue: 1000 },
    { name: 'note', type: 'STRING' },
    { name: 'dryRun', type: 'BOOLEAN', hasDefault: true, defaultValue: false },
  ],
}
describe('parameter forms', () => {
  it('distinguishes omitted values, false, zero and empty text', () => {
    const rows = fromTemplate(template)
    expect(readRows(rows)).toEqual({ size: 1000, dryRun: false })
    rows[0]!.text = '0'
    rows[1]!.included = true
    expect(readRows(rows)).toEqual({ size: 0, note: '', dryRun: false })
  })
  it('freezes saved schedule values independently of template changes', () => {
    const rows = fromTemplate(template)
    const values = stringify(readRows(rows))!
    template.fields[0]!.defaultValue = 10000
    expect(
      readRows(rowsOf(values, { templateId: '1', fields: rows.map((r) => r.field) })).size,
    ).toBe(1000)
  })
  it('preserves exact large numbers and nested legacy JSON', () => {
    const text = '{"id":9223372036854775806,"nested":{"values":[1,true]}}'
    expect(stringify(readRows(rowsOf(text)))).toBe(stringify(parse(text)))
  })
  it('rejects duplicate, reserved, invalid types and missing required values', () => {
    expect(() =>
      readRows([
        { field: { name: 'ijob.instance.id', type: 'INTEGER' }, included: true, text: '1' },
      ]),
    ).toThrow()
    expect(() =>
      readRows([
        { field: { name: 'date', type: 'DATE', required: true }, included: false, text: '' },
      ]),
    ).toThrow()
    expect(() =>
      readRows([{ field: { name: 'n', type: 'INTEGER' }, included: true, text: '1.2' }]),
    ).toThrow()
    const rows = rowsOf('{"a":1}')
    expect(() => readRows([...rows, ...rows])).toThrow()
  })
})
