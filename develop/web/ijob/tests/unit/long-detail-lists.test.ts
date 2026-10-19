// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import PluginFacts from '../../src/modules/tasks/PluginFacts.vue'
import { detailPresentation } from '../../src/modules/tasks/detail-presentation'

const plugins = () => [
  createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN, 'en-US': enUS } }),
]
it('searches all 500 records including collapsed fields while retaining original indices', async () => {
  const value = Array.from({ length: 500 }, (_, i) => ({
    name: `table_${i}`,
    type: 'NORMAL',
    compareType: 'EQ',
    expected: `secret_${i}`,
  }))
  const wrapper = mount(PluginFacts, {
    props: { value, path: 'checks' },
    global: { plugins: plugins() },
  })
  await wrapper.get('input[type=search]').setValue('secret_499')
  expect(wrapper.findAll('tbody > tr:not(.plugin-detail-row)')).toHaveLength(1)
  expect(wrapper.text()).toContain('table_499')
  expect(wrapper.text()).toContain('500')
  await wrapper.get('.plugin-row-toggle').trigger('click')
  expect(wrapper.text()).toContain('secret_499')
  await wrapper.get('input[type=search]').setValue('no-matches')
  expect(wrapper.findAll('tbody > tr')).toHaveLength(0)
  expect(value).toHaveLength(500)
  wrapper.unmount()
})
it('searches keys and values across large mappings and keeps SQL null literal', async () => {
  const value = Object.fromEntries(
    Array.from({ length: 100 }, (_, i) => [`column_${i}`, i === 99 ? null : `target_${i}`]),
  )
  const wrapper = mount(PluginFacts, {
    props: { value, path: 'columns' },
    global: { plugins: plugins() },
  })
  await wrapper.get('input[type=search]').setValue('column_99')
  expect(wrapper.findAll('tbody > tr')).toHaveLength(1)
  expect(wrapper.text()).toContain('null')
  wrapper.unmount()
})
it('projects batch source and target names without changing the stored configuration', () => {
  const config = { tables: ['orders', 'customers'], targetNaming: { prefix: 'ods_' } }
  const original = JSON.stringify(config)
  const detail = detailPresentation('BATCH_ETL', config)
  const tables = detail.sections.find((s) => 'tables' in s.value)!.value.tables
  expect(tables).toEqual([
    { sourceTable: 'orders', targetTable: 'ods_orders' },
    { sourceTable: 'customers', targetTable: 'ods_customers' },
  ])
  expect(JSON.stringify(config)).toBe(original)
})
