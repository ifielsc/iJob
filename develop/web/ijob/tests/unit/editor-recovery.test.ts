// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import EventFields from '../../src/modules/tasks/EventFields.vue'
import { eventDraft } from '../../src/modules/tasks/event-model'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { NSelect } from 'naive-ui'
import FileResourcePicker from '../../src/modules/tasks/FileResourcePicker.vue'
import PushResourcePicker from '../../src/modules/tasks/PushResourcePicker.vue'
import ParameterFormEditor from '../../src/modules/parameters/ParameterFormEditor.vue'
const state = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../src/app/runtime', () => ({
  useRuntime: () => ({ request: state.request, accessIssue: ref(null) }),
  useIdentity: () => ({ user: ref({ permissions: ['CONFIGURATION_WRITE'] }) }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('naive-ui', async (original) => ({
  ...(await original<object>()),
  useDialog: () => ({ warning: vi.fn() }),
  useMessage: () => ({ error: vi.fn(), success: vi.fn() }),
}))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})
beforeEach(() => state.request.mockReset())
const bottom = { target: { scrollTop: 100, clientHeight: 100, scrollHeight: 200 } }
for (const [component, type] of [
  [FileResourcePicker, 'FILE'],
  [PushResourcePicker, 'DB'],
] as const)
  describe(type + ' resource recovery', () => {
    it('retries the failed page without skipping it or replacing earlier options', async () => {
      let fail = true
      const pages: number[] = []
      state.request.mockImplementation(async (url: string) => {
        const page = Number(new URL(url, 'http://test').searchParams.get('pageNum'))
        pages.push(page)
        if (page === 2 && fail) throw Error('offline')
        return {
          list: Array.from({ length: 20 }, (_, i) => ({
            id: String((page - 1) * 20 + i + 1),
            name: 'r' + i,
            type,
            enabled: true,
          })),
          total: 60,
        }
      })
      const wrapper = mount(component, {
        props: {
          id: 'resource',
          label: 'resource',
          ...(type === 'DB' ? { type: 'DB' as const } : {}),
          value: '',
        },
        global: { stubs: { NSelect: true, RequestError: true } },
      })
      wrappers.push(wrapper)
      await flushPromises()
      const select = wrapper.findComponent(NSelect)
      select.vm.$emit('scroll', bottom)
      await flushPromises()
      select.vm.$emit('scroll', bottom)
      await flushPromises()
      expect(pages).toEqual([1, 2])
      fail = false
      await wrapper.get('button').trigger('click')
      await flushPromises()
      expect(pages).toEqual([1, 2, 2])
      expect(select.props('options')).toHaveLength(40)
      select.vm.$emit('scroll', bottom)
      await flushPromises()
      expect(pages).toEqual([1, 2, 2, 3])
      expect(select.props('options')).toHaveLength(60)
    })
  })
it('exposes incomplete JSON input for the parent leave guard and clears it on restoration', async () => {
  state.request.mockResolvedValue({ list: [], total: 0 })
  const wrapper = mount(ParameterFormEditor, {
    props: { modelValue: '{"a":1}' },
    global: { stubs: { ParameterTable: true, FieldValue: true, RequestError: true } },
  })
  wrappers.push(wrapper)
  await flushPromises()
  const toggle = wrapper
    .findAll('button')
    .find((button) => button.text() === 'parameters.advanced')!
  await toggle.trigger('click')
  await wrapper.get('textarea').setValue('{')
  expect((wrapper.vm as unknown as { hasUnpublishedInput: boolean }).hasUnpublishedInput).toBe(true)
  expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).not.toBe('{')
  await wrapper.get('textarea').setValue('{"a":1}')
  expect((wrapper.vm as unknown as { hasUnpublishedInput: boolean }).hasUnpublishedInput).toBe(
    false,
  )
})

it('approver pagination retries a failed next page without skipping candidates', async () => {
  let fail = true
  const pages: number[] = []
  state.request.mockImplementation(async (url: string) => {
    const page = Number(new URL(url, 'http://test').searchParams.get('pageNum'))
    pages.push(page)
    if (page === 2 && fail) throw Error('offline')
    return Array.from({ length: 20 }, (_, i) => ({
      loginId: String((page - 1) * 20 + i + 1),
      username: 'person' + i,
      displayName: 'Person ' + i,
    }))
  })
  const draft = eventDraft('{"action":"WAIT_APPROVAL","allowedOperatorIds":[]}')
  const wrapper = mount(EventFields, {
    props: { draft, timeout: null, disabled: false, submitted: false, errors: {} },
    global: {
      stubs: { NSelect: true, RequestError: true, ProbeFileFields: true, JsonFormEditor: true },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  const button = (text: string) => wrapper.findAll('button').find((b) => b.text() === text)!
  await button('eventTask.more').trigger('click')
  await flushPromises()
  await button('eventTask.more').trigger('click')
  await flushPromises()
  expect(pages).toEqual([1, 2])
  fail = false
  await button('eventTask.refreshPeople').trigger('click')
  await flushPromises()
  expect(pages).toEqual([1, 2, 2])
  const select = wrapper.findAllComponents(NSelect).find((s) => s.props('multiple'))!
  expect(select.props('options')).toHaveLength(40)
})
