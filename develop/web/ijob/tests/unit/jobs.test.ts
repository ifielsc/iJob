import { describe, expect, it } from 'vitest'
import {
  freshDraft,
  issues,
  jobBody,
  jobDetail,
  json,
  jsonValue,
  renameNode,
  validateCondition,
  type JobDraft,
  type JobNode,
} from '../../src/modules/jobs/model'
import { notifyBindings, bindingBody } from '../../src/modules/tasks/notify-binding-model'
function node(id: string): JobNode {
  return {
    nodeId: id,
    name: id,
    kind: 'EXECUTION',
    nodeType: 'TASK',
    refId: '9223372036854775807',
    position: { x: 0, y: 0 },
    policy: { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' },
  }
}
function graph(): JobDraft {
  return {
    ...freshDraft(),
    name: 'Job',
    nodes: [
      node('read'),
      {
        nodeId: 'gate',
        name: 'Gate',
        kind: 'GATEWAY',
        gateway: { mode: 'EXCLUSIVE' },
        position: { x: 200, y: 0 },
      },
      node('yes'),
      node('no'),
    ],
    edges: [
      { id: 'a', fromNodeId: 'read', toNodeId: 'gate', conditionText: '' },
      {
        id: 'b',
        fromNodeId: 'gate',
        toNodeId: 'yes',
        routeKey: 'yes',
        priority: 10,
        defaultRoute: false,
        conditionText:
          '{"op":"GT","left":{"var":"nodes.read.count"},"right":{"literal":9223372036854775807}}',
      },
      {
        id: 'c',
        fromNodeId: 'gate',
        toNodeId: 'no',
        routeKey: 'fallback',
        priority: 20,
        defaultRoute: true,
        conditionText: '',
      },
    ],
  }
}
describe('job editor contracts', () => {
  it('keeps long literal numbers numeric, identifiers exact and ordinary edges free of route fields', () => {
    const d = graph()
    expect(issues(d)).toEqual([])
    const body = jobBody(d)
    expect(json(body)).toContain('"literal": 9223372036854775807')
    expect('refId' in body.nodes[0]! && body.nodes[0]!.refId).toBe('9223372036854775807')
    expect(body.edges[0]).toEqual({ fromNodeId: 'read', toNodeId: 'gate' })
    expect(body.layout.positions.read).toEqual({ x: 0, y: 0 })
  })
  it('blocks cycles, isolated nodes and duplicate links independently', () => {
    const d = graph()
    d.edges.push({ id: 'x', fromNodeId: 'yes', toNodeId: 'read', conditionText: '' })
    expect(issues(d).some((i) => i.key === 'cycle')).toBe(true)
    d.edges.pop()
    d.nodes.push(node('isolated'))
    expect(issues(d).some((i) => i.key === 'isolated')).toBe(true)
    d.edges.push({ ...d.edges[0]!, id: 'duplicate' })
    expect(issues(d).some((i) => i.key === 'edgeInvalid')).toBe(true)
  })
  it('requires one fallback, unique priorities and only direct upstream outputs', () => {
    const d = graph()
    d.edges[1]!.defaultRoute = true
    expect(issues(d).some((i) => i.key === 'gatewayRoutes')).toBe(true)
    d.edges[1]!.defaultRoute = false
    d.edges[1]!.priority = 20
    expect(issues(d).some((i) => i.key === 'routeInvalid')).toBe(true)
    d.edges[1]!.priority = 10
    d.edges[1]!.conditionText = '{"op":"EXISTS","left":{"var":"nodes.no.value"}}'
    expect(issues(d).some((i) => i.key === 'conditionInvalid')).toBe(true)
  })
  it('renames structural variable references while preserving matching literal strings', () => {
    const d = graph()
    d.edges[1]!.conditionText =
      '{"op":"EQ","left":{"var":"nodes.read.count"},"right":{"literal":"nodes.read.count"}}'
    expect(renameNode(d, 'read', 'read-v2')).toBe(true)
    const condition = jsonValue(d.edges[1]!.conditionText) as {
      left: { var: string }
      right: { literal: string }
    }
    expect(condition.left.var).toBe('nodes.read-v2.count')
    expect(condition.right.literal).toBe('nodes.read.count')
    expect(d.edges[0]!.fromNodeId).toBe('read-v2')
    expect(renameNode(d, 'yes', 'no')).toBe(false)
  })
  it('rejects nested objects and oversized memberships, and allows explicit null checks', () => {
    expect(
      validateCondition(
        { op: 'EQ', left: { var: 'input.x' }, right: { literal: { secret: 1 } } },
        [],
      ),
    ).toBe(false)
    expect(
      validateCondition(
        { op: 'IN', left: { var: 'input.x' }, right: { literal: Array(101).fill(1) } },
        [],
      ),
    ).toBe(false)
    expect(validateCondition({ op: 'IS_NULL', left: { var: 'input.x' } }, [])).toBe(true)
    expect(
      validateCondition({ op: 'NOT', operand: { op: 'EXISTS', left: { var: 'input.x' } } }, []),
    ).toBe(true)
  })
  it('converts persisted numeric node IDs to stable node keys before republishing', () => {
    const detail = jobDetail({
      id: '90',
      name: 'j',
      enabled: true,
      currentDefinitionId: '900',
      currentRevision: '2',
      groups: [],
      nodes: [
        {
          id: '9223372036854775806',
          nodeKey: 'a',
          name: 'a',
          kind: 'EXECUTION',
          nodeType: 'TASK',
          refId: '80',
          refName: 'old name',
          refRevision: '1',
          refDefinitionId: '800',
          policy: { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' },
        },
        {
          id: '9223372036854775807',
          nodeKey: 'b',
          name: 'b',
          kind: 'EXECUTION',
          nodeType: 'TASK',
          refId: '80',
          policy: { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' },
        },
      ],
      edges: [{ id: '8000', fromNodeId: '9223372036854775806', toNodeId: '9223372036854775807' }],
    })
    expect(jobBody(detail.draft).edges).toEqual([{ fromNodeId: 'a', toNodeId: 'b' }])
    expect(detail.draft.nodes[0]!.refName).toBe('old name')
  })
  it('keeps JOB bindings distinct from TASK bindings', () => {
    const body = bindingBody('10', '20', 'ALWAYS', 'JOB')
    expect(body.targetType).toBe('JOB')
    const raw = [
      {
        id: '30',
        targetType: 'JOB',
        targetId: '10',
        notifyConfigId: '20',
        notifyConfigName: 'Alerts',
        notifyType: 'EMAIL',
        notifyConfigEnabled: true,
        notifyPolicy: 'ALWAYS',
      },
    ]
    expect(notifyBindings(raw, '10', 'JOB')[0]!.config.name).toBe('Alerts')
    expect(() => notifyBindings(raw, '10')).toThrow()
  })
})
