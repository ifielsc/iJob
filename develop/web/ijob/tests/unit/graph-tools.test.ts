// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { connectionProblem, isTextTarget } from '../../src/modules/jobs/graph-tools'
import { freshDraft, jobBody, jobDetail } from '../../src/modules/jobs/model'

describe('graph operations', () => {
  it('detects cycles and duplicates in a 500-node chain without rejecting valid forks', () => {
    const edges = Array.from({ length: 499 }, (_, i) => ({
      id: String(i),
      fromNodeId: String(i),
      toNodeId: String(i + 1),
      conditionText: '',
    }))
    expect(connectionProblem(edges, '499', '0')).toBe('cycle')
    expect(connectionProblem(edges, '1', '1')).toBe('edgeInvalid')
    expect(connectionProblem(edges, '0', '1')).toBe('edgeInvalid')
    expect(connectionProblem(edges, '0', '499')).toBeUndefined()
    expect(
      connectionProblem(
        Array.from({ length: 5000 }, (_, i) => ({
          id: String(i),
          fromNodeId: 'a' + i,
          toNodeId: 'b' + i,
          conditionText: '',
        })),
        'x',
        'y',
      ),
    ).toBe('edgeCount')
  })
  it('leaves Delete and undo to text editors and dialogs', () => {
    expect(isTextTarget(document.createElement('input'))).toBe(true)
    const div = document.createElement('div')
    div.contentEditable = 'true'
    div.setAttribute('contenteditable', 'true')
    expect(isTextTarget(div)).toBe(true)
    expect(isTextTarget(document.createElement('button'))).toBe(false)
  })
  it('round-trips layout ports using business keys while supporting old versions', () => {
    const detail = {
      id: '10',
      name: 'DAG',
      enabled: true,
      currentDefinitionId: '11',
      currentRevision: '1',
      groups: [],
      nodes: ['a', 'b'].map((key, i) => ({
        id: String(i + 1),
        nodeKey: key,
        name: key,
        kind: 'EXECUTION',
        nodeType: 'TASK',
        refId: '20',
        policy: { executeCondition: 'ALL_SUCCESS', failStrategy: 'STOP' },
      })),
      edges: [{ id: '3', fromNodeId: '1', toNodeId: '2' }],
      layout: {
        version: 1,
        positions: {},
        connections: [
          { fromNodeId: 'a', toNodeId: 'b', sourceHandle: 'bottom', targetHandle: 'top' },
        ],
      },
    }
    const parsed = jobDetail(detail)
    expect(parsed.draft.edges[0]?.sourceHandle).toBe('bottom')
    expect(jobBody(parsed.draft).layout.connections).toEqual(detail.layout.connections)
    const old = jobDetail({ ...detail, layout: { version: 1, positions: {} } })
    expect(jobBody(old.draft).layout.connections).toBeUndefined()
    expect(jobBody(freshDraft()).layout.version).toBe(1)
  })
})
