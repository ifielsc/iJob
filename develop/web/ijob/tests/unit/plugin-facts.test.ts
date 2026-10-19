// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, test } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { parse } from 'lossless-json'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import PluginConfigDetails from '../../src/modules/tasks/PluginConfigDetails.vue'
import PluginConfigTechnical from '../../src/modules/tasks/PluginConfigTechnical.vue'
import { detailPresentation } from '../../src/modules/tasks/detail-presentation'
import PluginFacts from '../../src/modules/tasks/PluginFacts.vue'
import audit from '../plugin-detail-audit.json'
const i18n = () =>
  createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN, 'en-US': enUS } })
for (const entry of audit)
  test(
    entry.plugin + ' displays every backend field and unknown extensions without mutation',
    () => {
      const value = Object.fromEntries(
        [...entry.fields, 'futureSetting'].map((key) => [key, 'SAVED_' + key]),
      )
      const original = JSON.stringify(value)
      const wrapper = mount(PluginConfigDetails, {
        props: { plugin: entry.plugin, scope: entry.scope, value },
        global: {
          plugins: [i18n()],
          stubs: {
            SqlEditor: {
              props: ['modelValue'],
              template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
            },
          },
        },
      })
      const technical = mount(PluginConfigTechnical, {
        props: { plugin: entry.plugin, scope: entry.scope, value },
        global: {
          plugins: [i18n()],
          stubs: {
            SqlEditor: {
              props: ['modelValue'],
              template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
            },
          },
        },
      })
      for (const key of Object.keys(value))
        expect(wrapper.text() + technical.text(), key).toContain('SAVED_' + key)
      expect(wrapper.text()).not.toContain('SAVED_futureSetting')
      expect(technical.text()).toContain('SAVED_futureSetting')
      expect(JSON.stringify(value)).toBe(original)
    },
  )
test('heterogeneous record rows retain later columns, nested extensions, null, zero and exact decimals', () => {
  const wrapper = mount(PluginFacts, {
    props: {
      scope: 'apiCall',
      path: 'extractors',
      value: parse(
        '[{"name":"operationId","path":"$.id","required":false,"vendor":{"precision":0.1234567890123456789}},{"name":"count","value":0,"nullable":null,"exact":9007199254740993,"blank":""}]',
      ),
    },
    global: {
      plugins: [i18n()],
      stubs: {
        SqlEditor: {
          props: ['modelValue'],
          template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
        },
      },
    },
  })
  expect(wrapper.findAll('tbody > tr:not(.plugin-detail-row)')).toHaveLength(2)
  expect(wrapper.findAll('thead th').map((node) => node.text())).toHaveLength(5)
  for (const item of [
    'operationId',
    '$.id',
    '否',
    'precision',
    '0.1234567890123456789',
    '0',
    'null',
    '9007199254740993',
    '空字符串',
  ])
    expect(wrapper.text()).toContain(item)
  expect(wrapper.findAll('.plugin-row-toggle')).toHaveLength(2)
})
test('literal map keys and formatted JSON preserve user data without localization or rounding', () => {
  const value = parse(
    '{"type":"custom","enabled":false,"batchSize":9007199254740993,"items":[],"empty":{},"nil":null}',
  )
  const wrapper = mount(PluginFacts, {
    props: { scope: 'localJar', path: 'userConfig', literal: true, value },
    global: {
      plugins: [i18n()],
      stubs: {
        SqlEditor: {
          props: ['modelValue'],
          template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
        },
      },
    },
  })
  expect(wrapper.findAll('dt')).toHaveLength(0)
  expect(wrapper.findAll('.json-viewer')).toHaveLength(1)
  expect(wrapper.text()).toContain('batchSize')
  expect(wrapper.text()).toContain('false')
  expect(wrapper.text()).toContain('[]')
  expect(wrapper.text()).toContain('{}')
  expect(wrapper.find('.viewer-stub').text()).toContain('9007199254740993')
  expect(wrapper.find('.viewer-stub').text()).not.toContain('9007199254740992')
})
test('large tables and simple arrays search the complete list', async () => {
  for (const value of [
    Array.from({ length: 25 }, (_, i) => ({ name: 'row-' + i + '-end' })),
    Array.from({ length: 25 }, (_, i) => 'row-' + i + '-end'),
  ]) {
    const wrapper = mount(PluginFacts, {
      props: { value },
      global: {
        plugins: [i18n()],
        stubs: {
          SqlEditor: {
            props: ['modelValue'],
            template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
          },
        },
      },
    })
    await wrapper.get('input[type=search]').setValue('row-24-end')
    expect(wrapper.text()).toContain('row-24-end')
    expect(wrapper.text()).not.toContain('row-23-end')
    expect(wrapper.findAll('tbody > tr')).toHaveLength(1)
    wrapper.unmount()
  }
})
test('omitted historical fields are identified without substituting present-day defaults', () => {
  const wrapper = mount(PluginConfigTechnical, {
    props: { plugin: 'FILE_TRANSFER', scope: 'files', value: { sourcePath: '/old' } },
    global: {
      plugins: [i18n()],
      stubs: {
        SqlEditor: {
          props: ['modelValue'],
          template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
        },
      },
    },
  })
  expect(wrapper.find('.plugin-missing').text()).toContain('远端发布方式')
  expect(wrapper.text()).not.toContain('STAGED_RENAME')
  expect(wrapper.text()).not.toContain('DIRECT')
  expect(wrapper.find('.plugin-missing').text()).toContain('执行版本')
})

test('unrecorded fields exclude explicitly inapplicable modes without inventing defaults', () => {
  const local = detailPresentation('SCRIPT', {
    executionMode: 'LOCAL',
    engine: 'SHELL',
    script: 'echo ok',
    env: {},
    workingDir: null,
    scriptCharset: 'UTF-8',
    outputCharset: 'UTF-8',
  })
  expect(local.missing).toEqual([])
  const ssh = detailPresentation('SCRIPT', { executionMode: 'SSH', engine: 'PYTHON' })
  expect(ssh.missing).toContain('sshResourceId')
  expect(ssh.missing).toContain('interpreter')
  expect(detailPresentation('SCRIPT', {}).missing).toContain('sshResourceId')
  expect(detailPresentation('EVENT', { action: 'WAIT_APPROVAL' }).missing).toEqual([
    'timeoutSeconds',
    'allowedOperatorIds',
  ])
  const file = detailPresentation('FILE_TRANSFER', { mode: 'READ_ONLY', compressType: 'NONE' })
  for (const key of [
    'targetResourceId',
    'targetPath',
    'remotePublishMode',
    'overwrite',
    'deleteSource',
    'archiveName',
    'compressionLevel',
  ])
    expect(file.missing).not.toContain(key)
  expect(
    detailPresentation('BATCH_ETL', { extractMode: 'FULL', clearBeforeImport: false }).missing,
  ).not.toContain('incremental')
})
test('technical metadata and extension fields are separated from business parameters without data loss', () => {
  const value = parse(
    '{"formatVersion":1,"userConfig":{"qa6Future":9007199254740993,"enabled":false},"qa6Future":{"zero":0}}',
  )
  const p = detailPresentation('LOCAL_JAR', value)
  expect(Object.keys(p.metadata)).toEqual(['formatVersion'])
  expect(Object.keys(p.extensions)).toEqual(['qa6Future'])
  const main = mount(PluginConfigDetails, {
    props: { plugin: 'LOCAL_JAR', scope: 'localJar', value },
    global: {
      plugins: [i18n()],
      stubs: {
        SqlEditor: {
          props: ['modelValue'],
          template: '<pre class="viewer-stub">{{ modelValue }}</pre>',
        },
      },
    },
  })
  expect(main.text()).toContain('9007199254740993')
  expect(main.text()).toContain('false')
  expect(main.text()).not.toContain('zero')
  expect(main.text()).not.toContain('配置版本')
})
