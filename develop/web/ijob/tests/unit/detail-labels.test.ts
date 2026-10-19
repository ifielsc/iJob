// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from 'vitest'
import { createI18n } from 'vue-i18n'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import { detailLabel, literalChildren } from '../../src/shared/ui/detail-labels'
import fields from '../detail-fields.json'
import { mount } from '@vue/test-utils'
import ConfigFacts from '../../src/shared/ui/ConfigFacts.vue'
import { parse } from 'lossless-json'
import { detailSections, pluginDetailSections } from '../../src/modules/tasks/detail-sections'
import PluginConfigDetails from '../../src/modules/tasks/PluginConfigDetails.vue'

test('known task and resource detail fields have Chinese labels', () => {
  const { t, te } = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN, 'en-US': enUS },
  }).global
  const missing = fields.filter(
    ([scope, path, key]) => !/[\u3400-\u9fff]/.test(detailLabel(scope!, path!, key!, t, te)),
  )
  expect(missing).toEqual([])
})
test('detail domains and user-defined keys stay separate', () => {
  const { t, te } = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN },
  }).global
  expect(detailLabel('monitor', '', 'rules', t, te)).toBe('阈值规则')
  expect(detailLabel('monitor', 'rules.applications.0', 'memory', t, te)).toContain('MiB')
  expect(detailLabel('monitor', 'rules.disk.paths', '/data', t, te)).toBe('/data')
  expect(literalChildren('apiCall', '', 'body')).toBe(true)
  expect(literalChildren('localJar', '', 'parameters')).toBe(true)
  expect(literalChildren('localJar', '', 'userConfig')).toBe(true)
  expect(literalChildren('eventTask', '', 'payload')).toBe(true)
  expect(literalChildren('etl', 'source', 'parameters')).toBe(true)
  expect(literalChildren('tasks', '', 'env')).toBe(true)
  expect(detailLabel('tasks', '', 'futureCustomSetting', t, te)).toBe('futureCustomSetting')
  expect(detailLabel('etl', '', 'source', t, te)).toBe('数据来源')
  expect(detailLabel('etl', '', 'target', t, te)).toBe('写入目标')
  expect(detailLabel('etl', 'transforms.0.properties', 'source', t, te)).toBe('原日期格式')
})

test('plugin sections preserve every configuration field exactly once and do not mutate input', () => {
  for (const [plugin, definitions] of Object.entries(pluginDetailSections)) {
    const keys = definitions.flatMap((section) => section.keys)
    expect(new Set(keys).size).toBe(keys.length)
    const value = Object.fromEntries(
      [...keys, 'futureExtension'].map((key) => [
        key,
        parse('{"exact":9007199254740993,"enabled":false}'),
      ]),
    )
    const sections = detailSections(plugin, value)
    expect(sections.flatMap((section) => Object.keys(section.value)).sort()).toEqual(
      Object.keys(value).sort(),
    )
    for (const section of sections)
      for (const [key, item] of Object.entries(section.value)) expect(item).toBe(value[key])
    expect(sections.at(-1)?.title).toBe('details.otherConfiguration')
  }
  expect(detailSections('ETL', 'legacy text')).toEqual([])
})

test('ETL sections retain source, rule and custom map contexts in both languages', () => {
  for (const locale of ['zh-CN', 'en-US']) {
    const i18n = createI18n({ legacy: false, locale, messages: { 'zh-CN': zhCN, 'en-US': enUS } })
    const value = parse(
      '{"source":{"type":"FILE","parameters":{"source":9007199254740993}},"transforms":[{"type":"DATE_FORMAT","properties":{"source":"yyyyMMdd","target":"yyyy-MM-dd"}}],"target":{"type":"DATABASE","columns":{"enabled":"source"}},"incremental":{"enabled":false},"futureExtension":{"value":"preserved"}}',
    )
    const wrapper = mount(PluginConfigDetails, {
      props: { plugin: 'ETL', scope: 'etl', value },
      global: { plugins: [i18n] },
    })
    expect(wrapper.findAll('h4').map((node) => node.text())).toEqual(
      ['etl.tab_source', 'etl.tab_rules', 'etl.tab_target', 'etl.tab_incremental'].map(
        i18n.global.t,
      ),
    )
    expect(wrapper.find('section').text()).not.toContain(i18n.global.t('etl.form_source'))
    expect(wrapper.findAll('section')[1]!.text()).toContain(i18n.global.t('etl.form_source'))
    expect(wrapper.findAll('section')[1]!.text()).toContain(i18n.global.t('etl.transforms'))
    expect(wrapper.text()).toContain('9007199254740993')
    expect(wrapper.text()).not.toContain('futureExtension')
    expect(wrapper.findAll('dt, th').map((node) => node.text())).toContain('enabled')
    wrapper.unmount()
  }
})
test('custom business parameter keys and exact values are not localized', () => {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  const wrapper = mount(ConfigFacts, {
    props: {
      scope: 'localJar',
      value: parse(
        '{"formatVersion":1,"userConfig":{"type":"custom","enabled":true,"batchSize":9007199254740993}}',
      ),
    },
    global: { plugins: [i18n] },
  })
  expect(wrapper.text()).toContain('业务参数')
  expect(wrapper.text()).toContain('配置版本')
  expect(wrapper.findAll('dt, th').map((node) => node.text())).toContain('enabled')
  expect(wrapper.text()).toContain('true')
  expect(wrapper.text()).toContain('9007199254740993')
  wrapper.unmount()
})
