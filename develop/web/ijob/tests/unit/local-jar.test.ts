// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import {
  localJarConfig,
  localJarDraft,
  localJarIssues,
  parameterNode,
} from '../../src/modules/tasks/local-jar-model'
import { draftFor, issues, taskBody } from '../../src/modules/tasks/model'
const prefix =
  '"formatVersion":1,"jarFile":"orders.jar","sha256":"' +
  'a'.repeat(64) +
  '","entryClass":"sample.OrderTask"'
const config = (value: string) => '{' + prefix + ',"userConfig":' + value + '}'
describe('Local JAR typed configuration', () => {
  it.each([
    'null',
    'false',
    'true',
    '""',
    '"${keep.literal}"',
    String.raw`{"constructor":1,"toString":false,"value":"a:\"b\""}`,
    '[]',
    '{}',
    '9007199254740993123456789',
    '0.12345678901234567890123456789',
    '-1.2e-50',
    '{"regions":["cn",null,{"a.b":true,"":42,"__proto__":{"safe":true}}]}',
  ])('round trips %s without numeric loss', (value) => {
    const d = localJarDraft(config(value))
    expect(d.blocked).toBe(false)
    expect(localJarIssues(d)).toEqual({})
    expect(localJarConfig(d)).toBe(config(value))
  })
  it.each([
    '{"a":1,"a":2}',
    '{"a":1,"a":1}',
    '{"__proto__":0,"__proto__":0}',
    '{"a":{"b":0,"b":1}}',
    '[01]',
    'NaN',
    'Infinity',
    '1 true',
  ])('blocks ambiguous input %s', (value) =>
    expect(localJarDraft(config(value)).blocked).toBe(true),
  )
  it('blocks unsupported top level fields and versions', () => {
    expect(
      localJarDraft(config('{}').replace('"formatVersion":1', '"formatVersion":2')).blocked,
    ).toBe(true)
    expect(localJarDraft(config('{}').replace('"formatVersion":1', '"extra":1')).blocked).toBe(true)
  })
  it('keeps snapshot metadata for display but excludes it from publish input', () => {
    const d = localJarDraft(
      config('{}').replace(
        '"userConfig"',
        '"jarSize":9007199254740993,"implementationVersion":"2.0","spiVersion":"1","userConfig"',
      ),
    )
    expect(d.snapshot?.jarSize).toBe('9007199254740993')
    expect(localJarConfig(d)).toBe(config('{}'))
  })
  it('rejects legacy SPI snapshots and both platform namespaces', () => {
    const legacy = config('{}').replace(
      '"userConfig"',
      '"jarSize":4096,"implementationVersion":"1.0","spiVersion":"2","userConfig"',
    )
    expect(localJarDraft(legacy).blocked).toBe(true)
    for (const entryClass of ['com.ifeel.ijob.Extension', 'com.ifielsc.ijob.Extension']) {
      const d = localJarDraft(config('{}'))
      d.entryClass = entryClass
      expect(localJarIssues(d)['localJar.entryClass']).toBe('entryInvalid')
    }
  })
  it('validates names literally and numbers without coercion', () => {
    const d = localJarDraft(config('{"a.b":1,"a":{"b":2},"":3}'))
    expect(localJarIssues(d)).toEqual({})
    const node = parameterNode('number', 'a.b')
    node.text = '01'
    d.root.children.push(node)
    expect(localJarIssues(d)).toMatchObject({
      ['localJar.nodes.' + node.key + '.name']: 'duplicateKey',
      ['localJar.nodes.' + node.key + '.value']: 'numberInvalid',
    })
  })
  it('enforces full UTF-8 configuration bytes and bounded trees', () => {
    const d = localJarDraft(config('{}'))
    d.root = parameterNode('string')
    d.root.text = '中'.repeat(88000)
    expect(localJarIssues(d)['localJar.size']).toBe('tooLarge')
    expect(localJarDraft(localJarConfig(d)).blocked).toBe(true)
    expect(localJarDraft(config('['.repeat(65) + '0' + ']'.repeat(65))).blocked).toBe(true)
    expect(localJarDraft(config('[' + Array(10001).fill('null').join(',') + ']')).blocked).toBe(
      true,
    )
  })
  it('publishes only selected plugin draft and retains the other plugin drafts', () => {
    const d = draftFor()
    d.name = 'Local'
    d.pluginType = 'LOCAL_JAR'
    d.localJar = localJarDraft(config('false'))
    d.script = 'SELECT 1'
    expect(issues(d)).toEqual({})
    expect(taskBody(d).pluginConfig).toBe(config('false'))
    d.pluginType = 'SQL'
    expect(taskBody(d).pluginConfig).toContain('SELECT 1')
    expect(d.localJar.root.kind).toBe('boolean')
  })
})
