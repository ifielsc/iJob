// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import {
  defineComponent,
  h,
  inject,
  nextTick,
  reactive,
  ref,
  shallowRef,
  type InjectionKey,
} from 'vue'
import type { GraphEdge, GraphNode } from '@vue-flow/core'
import type { EdgeRoutingMode } from '../../src/shared/preferences/preferences'
import { routingKey, useEdgeRouting } from '../../src/modules/jobs/use-edge-routing'
import { ObstacleIndex, routeEdge } from '../../src/modules/jobs/edge-routing'
import type { RoutingReply, RoutingRequest } from '../../src/modules/jobs/edge-routing.worker'

class RoutingWorker {
  static instances: RoutingWorker[] = []
  requests: RoutingRequest[] = []
  onmessage?: (event: MessageEvent<RoutingReply>) => void
  onerror?: () => void
  terminate = vi.fn()
  constructor() {
    RoutingWorker.instances.push(this)
  }
  postMessage(request: RoutingRequest) {
    this.requests.push(structuredClone(request))
  }
  reply(index: number) {
    const request = this.requests[index]!
    this.onmessage?.({
      data: {
        revision: request.revision,
        routes: request.edges.map((e) => [e.id, routeEdge(e, new ObstacleIndex(request.nodes))]),
        done: true,
      },
    } as MessageEvent<RoutingReply>)
  }
}

function setup(initial: EdgeRoutingMode = 'avoid') {
  const nodes = shallowRef<GraphNode[]>(
    [
      {
        id: 'a',
        computedPosition: { x: 0, y: 100 },
        dimensions: { width: 180, height: 76 },
        data: {},
      },
      {
        id: 'b',
        computedPosition: { x: 330, y: 90 },
        dimensions: { width: 180, height: 100 },
        data: {},
      },
      {
        id: 'c',
        computedPosition: { x: 650, y: 100 },
        dimensions: { width: 180, height: 76 },
        data: {},
      },
    ].map((node) => reactive(node)) as GraphNode[],
  )
  const edges = shallowRef<GraphEdge[]>(
    [
      {
        id: 'ac',
        source: 'a',
        target: 'c',
        sourceX: 180,
        sourceY: 138,
        targetX: 650,
        targetY: 138,
      },
    ].map((edge) => reactive(edge)) as GraphEdge[],
  )
  const mode = ref(initial),
    dragging = ref(false)
  let context!: typeof routingKey extends InjectionKey<infer T> ? T : never
  let result!: ReturnType<typeof useEdgeRouting>
  const child = defineComponent(() => {
    context = inject(routingKey)!
    return () => null
  })
  const wrapper = mount(
    defineComponent(() => {
      result = useEdgeRouting(nodes, edges, mode, dragging)
      return () => h(child)
    }),
  )
  return { nodes, edges, mode, dragging, context, result, wrapper }
}
const mounted: ReturnType<typeof setup>[] = []
function harness(mode?: EdgeRoutingMode) {
  const value = setup(mode)
  mounted.push(value)
  return value
}
async function settle() {
  await nextTick()
  await vi.advanceTimersByTimeAsync(60)
}

describe('canvas routing scheduling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    RoutingWorker.instances = []
    vi.stubGlobal('Worker', RoutingWorker)
  })
  afterEach(() => {
    for (const value of mounted.splice(0)) value.wrapper.unmount()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })
  it('does not route in simple mode and ignores old replies when the user switches modes', async () => {
    const value = harness('simple')
    await settle()
    expect(RoutingWorker.instances).toHaveLength(0)
    expect(value.result.pending.value).toBe(false)
    value.mode.value = 'avoid'
    await settle()
    const worker = RoutingWorker.instances[0]!
    worker.reply(0)
    expect(value.context.routes.get('ac')?.kind).toBe('detour')
    value.mode.value = 'simple'
    await nextTick()
    worker.reply(0)
    expect(value.context.routes.size).toBe(0)
    expect(value.result.pending.value).toBe(false)
    value.mode.value = 'avoid'
    await settle()
    worker.reply(1)
    expect(value.context.routes.get('ac')?.kind).toBe('detour')
  })
  it('freezes settled routes and labels during a drag, then computes only the final geometry', async () => {
    const value = harness()
    value.edges.value[0]!.label = 'Branch'
    await settle()
    const worker = RoutingWorker.instances[0]!
    worker.reply(0)
    const before = value.context.routes.get('ac')
    const label = value.context.labels.value.get('ac')
    value.dragging.value = true
    await nextTick()
    for (const y of [140, 200, 350]) {
      value.nodes.value[1]!.computedPosition.y = y
      await settle()
    }
    expect(worker.requests).toHaveLength(1)
    expect(value.context.routes.get('ac')).toBe(before)
    expect(value.context.labels.value.get('ac')).toEqual(label)
    worker.reply(0)
    expect(value.context.routes.get('ac')).toBe(before)
    value.dragging.value = false
    await settle()
    expect(worker.requests).toHaveLength(2)
    expect(worker.requests[1]!.nodes[1]!.y).toBe(350)
    worker.reply(1)
    expect(value.context.routes.get('ac')?.kind).toBe('direct')
  })
  it('rejects obsolete geometry during the debounce window and does not reroute label or selection changes', async () => {
    const value = harness()
    await settle()
    const worker = RoutingWorker.instances[0]!
    value.nodes.value[1]!.computedPosition.y = 350
    await nextTick()
    worker.reply(0)
    expect(value.context.routes.size).toBe(0)
    await settle()
    worker.reply(1)
    expect(value.context.routes.get('ac')?.kind).toBe('direct')
    value.edges.value[0]!.label = 'Translated label'
    value.edges.value[0]!.selected = true
    await settle()
    expect(worker.requests).toHaveLength(2)
  })
  it('leaves simple curves available after worker failure and cleans up on unmount', async () => {
    const value = harness()
    await settle()
    const worker = RoutingWorker.instances[0]!
    worker.onerror?.()
    expect(value.context.available.value).toBe(false)
    expect(value.result.pending.value).toBe(false)
    expect(worker.terminate).toHaveBeenCalledOnce()
    worker.reply(0)
    expect(value.context.routes.size).toBe(0)
    value.mode.value = 'simple'
    await settle()
    expect(value.context.mode.value).toBe('simple')
    expect(value.context.routes.size).toBe(0)
    value.wrapper.unmount()
    worker.reply(0)
    expect(value.context.routes.size).toBe(0)
  })
  it('does not reuse a previous layout label while waiting for the new route', async () => {
    const value = harness()
    value.edges.value[0]!.label = 'Branch'
    await settle()
    RoutingWorker.instances[0]!.reply(0)
    for (const node of value.nodes.value) node.computedPosition.y += 1000
    value.edges.value[0]!.sourceY += 1000
    value.edges.value[0]!.targetY += 1000
    await nextTick()
    expect(value.context.labels.value.get('ac')!.y).toBeGreaterThan(1000)
    expect(RoutingWorker.instances[0]!.requests).toHaveLength(1)
  })
  it('cancels scheduled work on unmount and rejects late replies', async () => {
    const early = harness()
    early.wrapper.unmount()
    await settle()
    expect(RoutingWorker.instances).toHaveLength(0)
    const running = harness()
    await settle()
    const worker = RoutingWorker.instances[0]!
    running.wrapper.unmount()
    worker.reply(0)
    expect(worker.terminate).toHaveBeenCalledOnce()
    expect(running.context.routes.size).toBe(0)
  })
})
