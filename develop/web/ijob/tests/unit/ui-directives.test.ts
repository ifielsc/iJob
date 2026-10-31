// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import process from 'node:process'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick, withDirectives } from 'vue'
import type { DirectiveBinding, ObjectDirective, VNode } from 'vue'
import { mount } from '@vue/test-utils'
import { clickoutside, mousemoveoutside, zindexable } from '../../packages/ui-directives/index.cjs'

const cleanups: (() => void)[] = []
afterEach(() => {
  for (const cleanup of cleanups.splice(0).reverse()) cleanup()
  document.body.replaceChildren()
})

function fixture() {
  const element = document.createElement('div')
  const inside = document.createElement('button')
  const outside = document.createElement('button')
  element.append(inside)
  document.body.append(element, outside)
  return { element, inside, outside }
}

function use<T>(
  directive: ObjectDirective<HTMLElement, T>,
  element: HTMLElement,
  initial: NoInfer<T>,
  capture = false,
) {
  let value = initial
  let modifiers = { capture }
  const invoke = (hook: 'mounted' | 'updated' | 'unmounted', oldValue?: T) => {
    const binding = {
      value,
      oldValue,
      modifiers,
      instance: null,
      dir: directive,
    } as DirectiveBinding<T>
    const vnode = {} as VNode<HTMLElement, HTMLElement>
    if (hook === 'updated') {
      directive.updated?.(element, binding, vnode, vnode)
    } else {
      directive[hook]?.(element, binding, vnode, null)
    }
  }
  invoke('mounted')
  let mounted = true
  const unmount = () => {
    if (mounted) invoke('unmounted')
    mounted = false
  }
  cleanups.push(unmount)
  return {
    update(next: T, nextCapture = modifiers.capture) {
      const old = value
      value = next
      modifiers = { capture: nextCapture }
      invoke('updated', old)
    },
    unmount,
  }
}

function mouse(target: Element, type: string) {
  target.dispatchEvent(new MouseEvent(type, { bubbles: true, composed: true, button: 0 }))
}
function click(target: Element) {
  mouse(target, 'mousedown')
  mouse(target, 'mouseup')
  mouse(target, 'click')
}

describe('independent outside directives', () => {
  it.each([false, true])('preserves inside/outside gesture boundaries (capture %s)', (capture) => {
    const { element, inside, outside } = fixture()
    const handler = vi.fn()
    use(clickoutside, element, handler, capture)
    mouse(outside, 'mouseup')
    click(inside)
    mouse(inside, 'mousedown')
    mouse(outside, 'mouseup')
    mouse(outside, 'mousedown')
    mouse(inside, 'mouseup')
    expect(handler).not.toHaveBeenCalled()
    click(outside)
    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler.mock.calls[0]![0].type).toBe('mouseup')
  })

  it.each([false, true])('preserves callback phase and propagation (capture %s)', (capture) => {
    const { element, outside } = fixture()
    const order: string[] = []
    use(clickoutside, element, () => order.push('outside'), capture)
    outside.addEventListener('mouseup', () => order.push('target'))
    click(outside)
    expect(order).toEqual(capture ? ['outside', 'target'] : ['target', 'outside'])
    order.length = 0
    outside.addEventListener('mouseup', (event) => event.stopPropagation())
    click(outside)
    expect(order).toEqual(capture ? ['outside', 'target'] : ['target'])
  })

  it.each([clickoutside, mousemoveoutside])(
    'updates, disables and removes a callback',
    (directive) => {
      const { element, outside } = fixture()
      const first = vi.fn(),
        second = vi.fn()
      const binding = use(directive, element, first)
      const send = () => {
        click(outside)
        mouse(outside, 'mousemove')
      }
      send()
      binding.update(second)
      send()
      binding.update(undefined)
      send()
      binding.update(second)
      binding.unmount()
      send()
      expect(first).toHaveBeenCalledTimes(1)
      expect(second).toHaveBeenCalledTimes(1)
    },
  )

  it('removes exact listeners and handles a changed capture modifier', () => {
    const { element, outside } = fixture()
    const add = vi.spyOn(document, 'addEventListener')
    const remove = vi.spyOn(document, 'removeEventListener')
    const handler = vi.fn()
    const binding = use(clickoutside, element, handler)
    binding.update(handler, true)
    click(outside)
    binding.unmount()
    expect(handler).toHaveBeenCalledTimes(1)
    const registrations = add.mock.calls.filter(([type]) => ['mousedown', 'mouseup'].includes(type))
    expect(registrations).toHaveLength(4)
    for (const [type, listener, capture] of registrations) {
      expect(remove).toHaveBeenCalledWith(type, listener, capture)
    }
  })

  it('treats composed events from a shadow child as inside', () => {
    const host = document.createElement('div')
    document.body.append(host)
    const shadow = host.attachShadow({ mode: 'open' })
    const element = document.createElement('div')
    const child = document.createElement('button')
    element.append(child)
    shadow.append(element)
    const handler = vi.fn()
    use(clickoutside, element, handler, true)
    click(child)
    expect(handler).not.toHaveBeenCalled()
    click(document.body)
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('ignores inside moves and bubbles outside moves even with capture modifier', () => {
    const { element, inside, outside } = fixture()
    const order: string[] = []
    use(mousemoveoutside, element, () => order.push('move'), true)
    outside.addEventListener('mousemove', () => order.push('target'))
    mouse(inside, 'mousemove')
    expect(order).toEqual([])
    mouse(outside, 'mousemove')
    expect(order).toEqual(['target', 'move'])
  })

  it('keeps parent and nested handlers independent', () => {
    const { element, inside, outside } = fixture()
    const parent = vi.fn(),
      child = vi.fn()
    use(clickoutside, element, parent, true)
    use(clickoutside, inside, child, true)
    click(inside)
    expect(parent).not.toHaveBeenCalled()
    expect(child).not.toHaveBeenCalled()
    click(element)
    expect(child).toHaveBeenCalledTimes(1)
    expect(parent).not.toHaveBeenCalled()
    click(outside)
    expect(parent).toHaveBeenCalledTimes(1)
    expect(child).toHaveBeenCalledTimes(2)
  })

  it('uses the owner document and cleans up through the real Vue lifecycle', async () => {
    const other = document.implementation.createHTMLDocument()
    const target = other.createElement('div')
    other.body.append(target)
    const handler = vi.fn()
    const wrapper = mount(
      { render: () => withDirectives(h('div'), [[clickoutside, handler]]) },
      { attachTo: target },
    )
    cleanups.push(() => wrapper.unmount())
    click(document.body)
    expect(handler).not.toHaveBeenCalled()
    click(other.body)
    await nextTick()
    expect(handler).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    click(other.body)
    expect(handler).toHaveBeenCalledTimes(1)
  })
})

describe('shared automatic and explicit layers', () => {
  it('retains visible ranks, releases on disable, and raises a reopened layer', () => {
    const { element: a, outside: b } = fixture()
    const first = use(zindexable, a, { enabled: true })
    use(zindexable, b, { enabled: true })
    expect([a.style.zIndex, b.style.zIndex]).toEqual(['2000', '2001'])
    first.update({ enabled: true })
    expect(a.style.zIndex).toBe('2000')
    first.update({ enabled: false })
    expect(a.style.zIndex).toBe('2000')
    first.update({ enabled: true })
    expect(a.style.zIndex).toBe('2002')
  })

  it('resets only when all automatic reservations have closed', () => {
    const { element: a, outside: b } = fixture()
    const first = use(zindexable, a, { enabled: true })
    const last = use(zindexable, b, { enabled: true })
    last.update({ enabled: false })
    last.update({ enabled: true })
    expect(b.style.zIndex).toBe('2002')
    first.unmount()
    last.unmount()
    use(zindexable, a, { enabled: true })
    expect(a.style.zIndex).toBe('2000')
  })

  it.each([0, 5000])('respects explicit %s without changing the automatic stack', (rank) => {
    const { element: a, outside: b } = fixture()
    const fixed = use(zindexable, a, { enabled: true, zIndex: rank })
    use(zindexable, b, { enabled: true })
    expect(a.style.zIndex).toBe(String(rank))
    expect(b.style.zIndex).toBe('2000')
    fixed.update({ enabled: false, zIndex: rank })
    fixed.update({ enabled: true, zIndex: rank + 1 })
    expect(a.style.zIndex).toBe(String(rank + 1))
  })

  it('leaves missing/disabled values untouched and releases without oldValue', () => {
    const { element } = fixture()
    element.style.zIndex = '17'
    const binding = use(zindexable, element, undefined)
    binding.update({})
    expect(element.style.zIndex).toBe('17')
    binding.update({ enabled: true })
    binding.update(undefined)
    binding.unmount()
    use(zindexable, element, { enabled: true })
    expect(element.style.zIndex).toBe('2000')
  })
})

it('resolves both consumers to the same first-party implementation and MIT text', () => {
  const require = createRequire(import.meta.url)
  const naive = createRequire(require.resolve('naive-ui'))
  const vueuc = createRequire(naive.resolve('vueuc'))
  const paths = [naive.resolve('vdirs'), vueuc.resolve('vdirs')]
  expect(paths[0]).toBe(paths[1])
  for (const path of paths) {
    const metadata = JSON.parse(readFileSync(join(dirname(path), 'package.json'), 'utf8'))
    expect(metadata.name).toBe('@ijob/ui-directives')
    expect(metadata.version).toBe('1.0.0')
    expect(readFileSync(join(dirname(path), 'LICENSE'), 'utf8')).toContain(
      'Copyright (c) 2026 ifielsc',
    )
  }
  expect(naive('vdirs').zindexable).toBe(vueuc('vdirs').zindexable)
})

it('can load the installed package without a browser or Vue runtime initialization', () => {
  const require = createRequire(import.meta.url)
  const naive = createRequire(require.resolve('naive-ui'))
  const result = spawnSync(
    process.execPath,
    [
      '-e',
      'console.log(Object.keys(require(process.argv[1])).sort().join(","))',
      naive.resolve('vdirs'),
    ],
    { encoding: 'utf8' },
  )
  expect(result.status, result.stderr).toBe(0)
  expect(result.stdout.trim()).toBe('clickoutside,mousemoveoutside,zindexable')
})
