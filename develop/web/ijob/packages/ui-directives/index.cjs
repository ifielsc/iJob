// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

/* global exports: readonly */

// One CommonJS implementation serves both import and require so that Naive UI
// and vueuc always share the same layer allocator. Importing does not touch DOM.
function containsEvent(element, event) {
  return (
    event.composedPath().includes(element) ||
    (event.target != null && element.contains(event.target))
  )
}

function outsideDirective(gesture) {
  const states = new WeakMap()
  function remove(element) {
    const state = states.get(element)
    if (!state) return
    for (const [type, listener] of state.listeners) {
      state.document.removeEventListener(type, listener, state.capture)
    }
    states.delete(element)
  }
  function mount(element, binding) {
    remove(element)
    const state = {
      document: element.ownerDocument,
      capture: gesture && !!binding.modifiers.capture,
      handler: binding.value,
      startedOutside: false,
      listeners: [],
    }
    const deliver = (event) => {
      if (
        (!gesture || state.startedOutside) &&
        !containsEvent(element, event) &&
        typeof state.handler === 'function'
      ) {
        state.handler(event)
      }
    }
    if (gesture) {
      state.listeners.push([
        'mousedown',
        (event) => {
          state.startedOutside = !containsEvent(element, event)
        },
      ])
    }
    state.listeners.push([gesture ? 'mouseup' : 'mousemove', deliver])
    states.set(element, state)
    for (const [type, listener] of state.listeners) {
      state.document.addEventListener(type, listener, state.capture)
    }
  }
  return {
    mounted: mount,
    updated(element, binding) {
      const state = states.get(element)
      if (!state || state.capture !== (gesture && !!binding.modifiers.capture)) {
        mount(element, binding)
      } else {
        state.handler = binding.value
      }
    },
    unmounted: remove,
  }
}

exports.clickoutside = outsideDirective(true)
exports.mousemoveoutside = outsideDirective(false)

// Explicit layers do not advance the automatic stack. Keep the assigned style
// during leave transitions; release only the reservation when disabled/unmounted.
const documents = new WeakMap()
const layers = new WeakMap()
function releaseLayer(element) {
  const state = layers.get(element)
  if (!state) return
  if (state.automatic) {
    state.pool.active -= 1
    if (state.pool.active === 0) state.pool.next = 2000
  }
  layers.delete(element)
}
function updateLayer(element, binding) {
  if (!binding.value?.enabled) {
    releaseLayer(element)
    return
  }
  // A visible layer retains its rank across unrelated component updates.
  // A newly opened layer receives a new rank, including after explicit changes.
  if (layers.has(element)) return
  let pool = documents.get(element.ownerDocument)
  if (!pool) {
    pool = { next: 2000, active: 0 }
    documents.set(element.ownerDocument, pool)
  }
  const automatic = binding.value.zIndex === undefined
  const rank = automatic ? pool.next++ : binding.value.zIndex
  if (automatic) pool.active += 1
  element.style.zIndex = String(rank)
  layers.set(element, { automatic, pool })
}
exports.zindexable = {
  mounted: updateLayer,
  updated: updateLayer,
  unmounted: releaseLayer,
}
