// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect } from 'vitest'
import { runGraph } from '../../src/modules/runs/graph'
const sample = () => ({
  instanceId: '9007199254740993',
  rootInstanceId: '9007199254740993',
  definitionId: '2',
  name: 'Snapshot',
  status: 'RUNNING',
  nodes: [
    {
      id: '9007199254740995',
      nodeKey: 'one',
      name: 'First',
      kind: 'EXECUTION',
      nodeType: 'TASK',
      refId: '3',
      instanceId: '9007199254740997',
      status: 'SUCCESS',
    },
    {
      id: '9007199254740996',
      nodeKey: 'two',
      name: 'Later',
      kind: 'EXECUTION',
      nodeType: 'TASK',
      refId: '3',
      status: 'NOT_STARTED',
    },
  ],
  edges: [
    {
      id: '9223372036854775807',
      fromNodeId: '9007199254740995',
      toNodeId: '9007199254740996',
      selected: true,
    },
  ],
})
describe('runtime graph snapshots', () => {
  it('keeps separate definition nodes sharing a target and exact identifiers', () => {
    const graph = runGraph(sample())
    expect(graph.nodes).toHaveLength(2)
    expect(graph.nodes[0]?.instanceId).toBe('9007199254740997')
    expect(graph.nodes[1]?.instanceId).toBe('')
    expect(graph.edges[0]?.fromNodeId).toBe('one')
    expect(graph.highlightedEdges).toEqual(['9223372036854775807'])
    expect(graph.nodes[0]?.position).not.toEqual(graph.nodes[1]?.position)
  })
  it('uses immutable layout and saved ports', () => {
    const graph = runGraph({
      ...sample(),
      layout: {
        positions: { one: { x: 20, y: 40 }, two: { x: 400, y: 40 } },
        connections: [
          { fromNodeId: 'one', toNodeId: 'two', sourceHandle: 'bottom', targetHandle: 'top' },
        ],
      },
    })
    expect(graph.nodes[0]?.position).toEqual({ x: 20, y: 40 })
    expect(graph.edges[0]?.sourceHandle).toBe('bottom')
  })
  it('rejects rounded IDs and dangling edges instead of drawing false topology', () => {
    expect(() => runGraph({ ...sample(), instanceId: Number('9007199254740993') })).toThrow()
    const graph = sample()
    graph.edges[0]!.toNodeId = '7'
    expect(() => runGraph(graph)).toThrow()
  })
})
