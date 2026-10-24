// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import {
  ObstacleIndex,
  portSide,
  routeEdge,
  placeEdgeLabels,
  nearestPathPoint,
} from '../../src/modules/jobs/edge-routing'
import type { Obstacle, RouteInput, Side } from '../../src/modules/jobs/edge-routing'

const source: Obstacle = { id: 'a', x: 0, y: 100, width: 180, height: 76 }
const target: Obstacle = { id: 'c', x: 650, y: 100, width: 180, height: 76 }
const input: RouteInput = {
  id: 'ac',
  source: 'a',
  target: 'c',
  start: { x: 180, y: 138 },
  end: { x: 650, y: 138 },
  sourceSide: 'right',
  targetSide: 'left',
}
describe('automatic edge routing', () => {
  it('keeps branch labels on a free part of the edge when its midpoint is crowded', () => {
    const points = [
      { x: 0, y: 0 },
      { x: 400, y: 0 },
    ]
    const labels = placeEdgeLabels(
      [{ id: 'block', x: 150, y: -40, width: 100, height: 80 }],
      [{ id: 'branch', text: '默认', anchor: { x: 200, y: 0 }, points }],
    )
    const p = labels.get('branch')!
    expect(p.y).toBe(0)
    expect(p.x < 110 || p.x > 290).toBe(true)
    expect(nearestPathPoint(p, points).x).toBeCloseTo(p.x, 8)
    expect(nearestPathPoint(p, points).y).toBeCloseTo(p.y, 8)
  })
  it('connects an offset label to its closest segment, including degenerate points', () => {
    expect(
      nearestPathPoint({ x: 350, y: 20 }, [
        { x: 0, y: 0 },
        { x: 0, y: 0 },
        { x: 400, y: 0 },
      ]),
    ).toEqual({ x: 350, y: 0 })
  })
  it('separates coincident branch labels from each other and the gateway', () => {
    const node = { id: 'gate', x: 0, y: 0, width: 110, height: 110 }
    const labels = placeEdgeLabels(
      [node],
      ['默认分支', 'risk · 优先级 10'].map((text, i) => ({
        id: String(i),
        text,
        anchor: { x: 55, y: 55 },
      })),
    )
    expect(labels.size).toBe(2)
    const points = [...labels.values()]
    expect(
      Math.abs(points[0]!.y - points[1]!.y) >= 40 || Math.abs(points[0]!.x - points[1]!.x) >= 160,
    ).toBe(true)
    for (const p of points) expect(p.y < -20 || p.y > 130 || p.x < -100 || p.x > 210).toBe(true)
  })
  it('keeps all 16 input/output port combinations and endpoint tangents', () => {
    const vectors = {
      left: { x: -1, y: 0 },
      right: { x: 1, y: 0 },
      top: { x: 0, y: -1 },
      bottom: { x: 0, y: 1 },
    }
    const anchor = (n: Obstacle, side: Side) => ({
      x: n.x + (side === 'left' ? 0 : side === 'right' ? n.width : n.width / 2),
      y: n.y + (side === 'top' ? 0 : side === 'bottom' ? n.height : n.height / 2),
    })
    for (const s of Object.keys(vectors) as Side[])
      for (const t of Object.keys(vectors) as Side[]) {
        const sourceSide = portSide(s, 'right'),
          targetSide = portSide(t, 'left')
        expect(sourceSide).toBe(s)
        expect(targetSide).toBe(t)
        const start = anchor(source, s),
          end = anchor(target, t)
        const route = routeEdge(
          { ...input, start, end, sourceSide, targetSide },
          new ObstacleIndex([source, target]),
        )
        expect(route.kind, s + ' → ' + t).not.toBe('fallback')
        expect(route.points[0]).toEqual(start)
        expect(route.points.at(-1)).toEqual(end)
        const first = route.points[1]!,
          last = route.points.at(-2)!
        expect(
          (first.x - start.x) * vectors[s].x + (first.y - start.y) * vectors[s].y,
        ).toBeGreaterThan(0)
        expect((last.x - end.x) * vectors[t].x + (last.y - end.y) * vectors[t].y).toBeGreaterThan(0)
      }
    expect(portSide(undefined, 'right')).toBe('right')
    expect(portSide('center', 'left')).toBe('left')
  })
  it('uses a direct path for aligned ports and a curve for staggered nodes', () => {
    expect(routeEdge(input, new ObstacleIndex([source, target])).kind).toBe('direct')
    const offsetTarget = { ...target, y: 260 }
    const curve = routeEdge(
      { ...input, end: { x: 650, y: 298 } },
      new ObstacleIndex([source, offsetTarget]),
    )
    expect(curve.kind).toBe('curve')
    expect(curve.path).toContain('C')
  })
  it('routes around an unrelated node with clearance, and restores the curve when it moves away', () => {
    const blocker = { id: 'b', x: 330, y: 90, width: 180, height: 100 }
    const route = routeEdge(input, new ObstacleIndex([source, target, blocker]))
    expect(route.kind).toBe('detour')
    expect(new ObstacleIndex([blocker]).pathClear(route.points)).toBe(true)
    expect(route.path).toContain('Q')
    expect(route.points[0]).toEqual(input.start)
    expect(route.points.at(-1)).toEqual(input.end)
    expect(routeEdge(input, new ObstacleIndex([source, target, { ...blocker, y: 350 }])).kind).toBe(
      'direct',
    )
  })
  it('respects bottom/top ports and their incoming/outgoing directions', () => {
    const bottom = { ...target, x: 0, y: 650 }
    const blocker = { id: 'b', x: -10, y: 320, width: 200, height: 90 }
    const vertical: RouteInput = {
      ...input,
      start: { x: 90, y: 176 },
      end: { x: 90, y: 650 },
      sourceSide: 'bottom',
      targetSide: 'top',
    }
    const route = routeEdge(vertical, new ObstacleIndex([source, bottom, blocker]))
    expect(route.kind).toBe('detour')
    expect(new ObstacleIndex([blocker]).pathClear(route.points)).toBe(true)
    expect(route.points[1]!.y).toBeGreaterThan(route.points[0]!.y)
    expect(route.points.at(-2)!.y).toBeLessThan(route.points.at(-1)!.y)
  })
  it('handles reverse-facing geometry without crossing the endpoint nodes', () => {
    const reverse = { ...target, x: -400, y: 100 }
    const route = routeEdge(
      { ...input, end: { x: -400, y: 138 } },
      new ObstacleIndex([source, reverse]),
    )
    expect(route.kind).toBe('detour')
    const middle = route.points.slice(1, -1)
    expect(new ObstacleIndex([source, reverse]).pathClear(middle)).toBe(true)
  })
  it('finds a multi-turn route through staggered obstacles', () => {
    const blockers = [
      { id: 'b1', x: 250, y: -130, width: 120, height: 260 },
      { id: 'b2', x: 420, y: 180, width: 120, height: 260 },
      { id: 'b3', x: 220, y: 340, width: 160, height: 70 },
    ]
    const route = routeEdge(
      { ...input, end: { x: 650, y: 298 } },
      new ObstacleIndex([source, { ...target, y: 260 }, ...blockers]),
    )
    expect(route.kind).not.toBe('fallback')
    expect(new ObstacleIndex(blockers).pathClear(route.points)).toBe(true)
  })
  it('searches for multiple turns when both simple horizontal detours are blocked', () => {
    const blockers = [
      { id: 'b1', x: 300, y: 40, width: 100, height: 230 },
      { id: 'b2', x: 190, y: 180, width: 70, height: 250 },
      { id: 'b3', x: 520, y: -130, width: 70, height: 235 },
    ]
    const route = routeEdge(input, new ObstacleIndex([source, target, ...blockers]))
    expect(route.kind).toBe('detour')
    expect(route.points.length).toBeGreaterThan(5)
    expect(new ObstacleIndex(blockers).pathClear(route.points)).toBe(true)
  })
  it('returns a finite fallback when overlapping nodes block an endpoint', () => {
    const covering = { id: 'b', x: 160, y: 90, width: 180, height: 100 }
    const route = routeEdge(input, new ObstacleIndex([source, target, covering]), 10)
    expect(route.kind).toBe('fallback')
    expect(route.path).not.toMatch(/NaN|Infinity/)
  })
  it('supports distant and negative coordinates without allocating a pixel grid', () => {
    const route = routeEdge(
      { ...input, end: { x: 1000000, y: -1000000 } },
      new ObstacleIndex([source]),
    )
    expect(route.kind).toBe('curve')
    expect(route.path).not.toMatch(/NaN|Infinity/)
  })
  it('keeps hundreds of independent routes clear in a large grid', () => {
    const nodes = Array.from({ length: 500 }, (_, i) => ({
      id: String(i),
      x: (i % 10) * 270,
      y: Math.floor(i / 10) * 150,
      width: 180,
      height: 76,
    }))
    const index = new ObstacleIndex(nodes)
    for (let i = 0; i < 499; i++) {
      const a = nodes[i]!,
        b = nodes[i + 1]!
      const route = routeEdge(
        {
          ...input,
          source: a.id,
          target: b.id,
          start: { x: a.x + 180, y: a.y + 38 },
          end: { x: b.x, y: b.y + 38 },
        },
        index,
      )
      expect(route.kind).not.toBe('fallback')
      expect(route.path).not.toMatch(/NaN|Infinity/)
    }
  })
})
