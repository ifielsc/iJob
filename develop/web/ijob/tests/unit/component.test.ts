// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import ComponentLab from '../../src/app/views/ComponentLab.vue'

it('retains unsaved input and element identity across a locale change', async () => {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN, 'en-US': enUS },
  })
  const wrapper = mount(ComponentLab, { global: { plugins: [i18n] } })
  const textarea = wrapper.get('textarea')
  await textarea.setValue('SELECT 用户输入;')
  i18n.global.locale.value = 'en-US'
  await wrapper.vm.$nextTick()
  expect(wrapper.get('textarea').element).toBe(textarea.element)
  expect((textarea.element as HTMLTextAreaElement).value).toBe('SELECT 用户输入;')
  expect(wrapper.text()).toContain('Unsaved content')
  wrapper.unmount()
})
