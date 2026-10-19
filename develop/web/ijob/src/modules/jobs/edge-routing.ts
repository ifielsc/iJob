// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

// Geometry only: shared by the worker and the single live connection preview.
export type Point = { x: number; y: number }
export type Side = 'left' | 'right' | 'top' | 'bottom'
export function portSide(value: unknown, fallback: Side): Side {
  return value === 'left' || value === 'right' || value === 'top' || value === 'bottom'
    ? value
    : fallback
}
export type Obstacle = Point & { id: string; width: number; height: number }
export type RouteInput = {
  id: string
  source: string
  target: string
  start: Point
  end: Point
  sourceSide: Side
  targetSide: Side
}
export type Route = {
  path: string
  points: Point[]
  label: Point
  kind: 'direct' | 'curve' | 'detour' | 'fallback'
  start: Point
  end: Point
  bounds: Box
}
export type Box = { left: number; top: number; right: number; bottom: number }
const clearance = 14
const stub = 28
const bucket = 256
const directions: Record<Side, Point> = {
  right: { x: 1, y: 0 },
  bottom: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  top: { x: 0, y: -1 },
}
const sides: Side[] = ['right', 'bottom', 'left', 'top']
export const obstacleBox = (r: Obstacle): Box => ({
  left: r.x - clearance,
  top: r.y - clearance,
  right: r.x + r.width + clearance,
  bottom: r.y + r.height + clearance,
})
export function intersects(a: Box, b: Box) {
  return a.left <= b.right && a.right >= b.left && a.top <= b.bottom && a.bottom >= b.top
}
function bounds(points: Point[], padding = 0): Box {
  return {
    left: Math.min(...points.map((p) => p.x)) - padding,
    right: Math.max(...points.map((p) => p.x)) + padding,
    top: Math.min(...points.map((p) => p.y)) - padding,
    bottom: Math.max(...points.map((p) => p.y)) + padding,
  }
}
function inside(p: Point, b: Box) {
  return p.x > b.left && p.x < b.right && p.y > b.top && p.y < b.bottom
}
function segmentHits(a: Point, b: Point, box: Box) {
  let lo = 0,
    hi = 1
  for (const [p, d, min, max] of [
    [a.x, b.x - a.x, box.left, box.right],
    [a.y, b.y - a.y, box.top, box.bottom],
  ] as [number, number, number, number][]) {
    if (Math.abs(d) < 0.00001) {
      if (p <= min || p >= max) return false
    } else {
      const t1 = (min - p) / d,
        t2 = (max - p) / d
      lo = Math.max(lo, Math.min(t1, t2))
      hi = Math.min(hi, Math.max(t1, t2))
      if (lo >= hi) return false
    }
  }
  return true
}
export class ObstacleIndex {
  readonly boxes: (Box & { id: string })[]
  private cells = new Map<string, Set<number>>()
  constructor(nodes: Obstacle[]) {
    this.boxes = nodes.map((r) => ({ ...obstacleBox(r), id: r.id }))
    this.boxes.forEach((b, i) => {
      for (let x = Math.floor(b.left / bucket); x <= Math.floor(b.right / bucket); x++)
        for (let y = Math.floor(b.top / bucket); y <= Math.floor(b.bottom / bucket); y++) {
          const key = x + ':' + y
          if (!this.cells.has(key)) this.cells.set(key, new Set())
          this.cells.get(key)!.add(i)
        }
    })
  }
  query(box: Box) {
    const x1 = Math.floor(box.left / bucket),
      x2 = Math.floor(box.right / bucket)
    const y1 = Math.floor(box.top / bucket),
      y2 = Math.floor(box.bottom / bucket)
    // Very distant nodes must not create an enormous raster or unbounded bucket scan.
    if ((x2 - x1 + 1) * (y2 - y1 + 1) > 2048) return this.boxes.filter((b) => intersects(b, box))
    const ids = new Set<number>()
    for (let x = x1; x <= x2; x++)
      for (let y = y1; y <= y2; y++) this.cells.get(x + ':' + y)?.forEach((i) => ids.add(i))
    return [...ids].map((i) => this.boxes[i]!).filter((b) => intersects(b, box))
  }
  clear(a: Point, b: Point, ignore?: string) {
    return !this.query(bounds([a, b])).some((r) => r.id !== ignore && segmentHits(a, b, r))
  }
  pathClear(points: Point[]) {
    return points.every((p, i) => !i || this.clear(points[i - 1]!, p))
  }
}
const distance = (a: Point, b: Point) => Math.hypot(b.x - a.x, b.y - a.y)
const shift = (p: Point, d: Point, n: number): Point => ({ x: p.x + d.x * n, y: p.y + d.y * n })
const midpoint = (a: Point, b: Point): Point => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 })
const coord = (p: Point) => `${+p.x.toFixed(2)} ${+p.y.toFixed(2)}`
function flatten(a: Point, b: Point, c: Point, d: Point, out: Point[], depth = 0) {
  if (depth >= 10 || distance(a, b) + distance(b, c) + distance(c, d) - distance(a, d) < 0.2) {
    out.push(d)
    return
  }
  const ab = midpoint(a, b),
    bc = midpoint(b, c),
    cd = midpoint(c, d)
  const abc = midpoint(ab, bc),
    bcd = midpoint(bc, cd),
    center = midpoint(abc, bcd)
  flatten(a, ab, abc, center, out, depth + 1)
  flatten(center, bcd, cd, d, out, depth + 1)
}
function simplify(points: Point[]) {
  const out: Point[] = []
  for (const p of points) {
    if (out.length && distance(out.at(-1)!, p) < 0.001) continue
    const a = out.at(-2),
      b = out.at(-1)
    if (
      a &&
      b &&
      ((a.x === b.x && b.x === p.x) || (a.y === b.y && b.y === p.y)) &&
      (b.x - a.x) * (p.x - b.x) + (b.y - a.y) * (p.y - b.y) >= 0
    )
      out.pop()
    out.push(p)
  }
  return out
}
function rounded(points: Point[], index: ObstacleIndex) {
  let path = 'M ' + coord(points[0]!)
  for (let i = 1; i < points.length - 1; i++) {
    const a = points[i - 1]!,
      b = points[i]!,
      c = points[i + 1]!
    let r = Math.min(12, distance(a, b) / 2, distance(b, c) / 2)
    let before: Point, after: Point
    // A rounded corner cuts inside the polyline. Keep its whole control hull clear.
    do {
      before = shift(b, { x: (a.x - b.x) / distance(a, b), y: (a.y - b.y) / distance(a, b) }, r)
      after = shift(b, { x: (c.x - b.x) / distance(b, c), y: (c.y - b.y) / distance(b, c) }, r)
      if (!index.query(bounds([before, b, after])).length) break
      r /= 2
    } while (r > 0.5)
    path += r > 0.5 ? ` L ${coord(before!)} Q ${coord(b)} ${coord(after!)}` : ` L ${coord(b)}`
  }
  return path + ' L ' + coord(points.at(-1)!)
}
function labelPoint(points: Point[]) {
  const lengths = points.slice(1).map((p, i) => distance(points[i]!, p))
  const half = lengths.reduce((a, b) => a + b, 0) / 2
  let traveled = 0
  for (let i = 0; i < lengths.length; i++) {
    const length = lengths[i]!
    if (traveled + length >= half && length > 0) {
      const a = points[i]!,
        b = points[i + 1]!,
        t = (half - traveled) / length
      return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t }
    }
    traveled += length
  }
  return points[0]!
}
type SearchItem = { state: number; cost: number; score: number }
class Heap {
  items: SearchItem[] = []
  push(item: SearchItem) {
    let i = this.items.push(item) - 1
    while (i > 0) {
      const parent = (i - 1) >> 1
      if (this.items[parent]!.score <= item.score) break
      this.items[i] = this.items[parent]!
      i = parent
    }
    this.items[i] = item
  }
  pop() {
    const first = this.items[0]!,
      last = this.items.pop()!
    if (this.items.length) {
      let i = 0
      while (i * 2 + 1 < this.items.length) {
        let child = i * 2 + 1
        if (
          child + 1 < this.items.length &&
          this.items[child + 1]!.score < this.items[child]!.score
        )
          child++
        if (this.items[child]!.score >= last.score) break
        this.items[i] = this.items[child]!
        i = child
      }
      this.items[i] = last
    }
    return first
  }
}
function search(start: Point, end: Point, input: RouteInput, index: ObstacleIndex, visits: number) {
  const nearby = index.query(bounds([start, end], 240))
  // Compressed visibility coordinates, never a pixel-sized grid of the whole canvas.
  const xs = [
    ...new Set([start.x, end.x, ...nearby.flatMap((b) => [b.left - 2, b.right + 2])]),
  ].sort((a, b) => a - b)
  const ys = [
    ...new Set([start.y, end.y, ...nearby.flatMap((b) => [b.top - 2, b.bottom + 2])]),
  ].sort((a, b) => a - b)
  const nx = xs.length,
    startDir = sides.indexOf(input.sourceSide),
    targetDir = sides.indexOf(input.targetSide)
  const state = (x: number, y: number, d: number) => (y * nx + x) * 4 + d
  const point = (s: number) => ({ x: xs[Math.floor(s / 4) % nx]!, y: ys[Math.floor(s / 4 / nx)]! })
  const origin = state(xs.indexOf(start.x), ys.indexOf(start.y), startDir)
  const best = new Map<number, number>([[origin, 0]]),
    parent = new Map<number, number>()
  const heap = new Heap()
  heap.push({ state: origin, cost: 0, score: 0 })
  const segments = new Map<string, boolean>()
  while (heap.items.length && visits-- > 0) {
    const current = heap.pop(),
      p = point(current.state),
      direction = current.state % 4
    if (current.cost !== best.get(current.state)) continue
    if (p.x === end.x && p.y === end.y && direction !== targetDir) {
      const points = [p]
      let s = current.state
      while (parent.has(s)) {
        s = parent.get(s)!
        points.push(point(s))
      }
      return points.reverse()
    }
    const x = Math.floor(current.state / 4) % nx,
      y = Math.floor(current.state / 4 / nx)
    for (let d = 0; d < 4; d++) {
      if (d === (direction + 2) % 4) continue
      const dx = directions[sides[d]!]!.x,
        dy = directions[sides[d]!]!.y
      const xx = x + dx,
        yy = y + dy
      if (xx < 0 || xx >= nx || yy < 0 || yy >= ys.length) continue
      const next = state(xx, yy, d),
        q = point(next)
      const a = Math.floor(current.state / 4),
        b = Math.floor(next / 4)
      const key = Math.min(a, b) + ':' + Math.max(a, b)
      if (!segments.has(key)) segments.set(key, index.clear(p, q))
      if (!segments.get(key)) continue
      const cost = current.cost + distance(p, q) + (d === direction ? 0 : 24)
      if (cost >= (best.get(next) ?? Infinity)) continue
      best.set(next, cost)
      parent.set(next, current.state)
      heap.push({ state: next, cost, score: cost + Math.abs(q.x - end.x) + Math.abs(q.y - end.y) })
    }
  }
}
export function routeEdge(input: RouteInput, index: ObstacleIndex, maxVisits = 6000): Route {
  const { start, end } = input,
    sd = directions[input.sourceSide],
    td = directions[input.targetSide]
  const a = shift(start, sd, stub),
    b = shift(end, td, stub)
  const bend = Math.max(24, Math.min(150, distance(a, b) * 0.4))
  const c1 = shift(a, sd, bend),
    c2 = shift(b, td, bend)
  const curve = [a]
  flatten(a, c1, c2, b, curve)
  const natural = [start, ...curve, end]
  const make = (path: string, points: Point[], kind: Route['kind']): Route => ({
    path,
    points,
    kind,
    start,
    end,
    label: labelPoint(points),
    bounds: bounds([...points, ...natural], 20),
  })
  const naturalPath = `M ${coord(start)} L ${coord(a)} C ${coord(c1)} ${coord(c2)} ${coord(b)} L ${coord(end)}`
  const clearStubs = index.clear(start, a, input.source) && index.clear(b, end, input.target)
  if (clearStubs && index.pathClear(curve)) {
    const aligned = Math.abs(start.x - end.x) < 0.01 || Math.abs(start.y - end.y) < 0.01
    return make(naturalPath, natural, aligned ? 'direct' : 'curve')
  }
  if (!clearStubs || index.query(bounds([a, b])).some((r) => inside(a, r) || inside(b, r)))
    return make(naturalPath, natural, 'fallback')
  const nearby = index.query(bounds([a, b], 80))
  const ys = [...new Set([(a.y + b.y) / 2, ...nearby.flatMap((r) => [r.top - 16, r.bottom + 16])])]
  const xs = [...new Set([(a.x + b.x) / 2, ...nearby.flatMap((r) => [r.left - 16, r.right + 16])])]
  const candidates = [
    ...ys.map((y) => [a, { x: a.x, y }, { x: b.x, y }, b]),
    ...xs.map((x) => [a, { x, y: a.y }, { x, y: b.y }, b]),
  ]
    .map(simplify)
    .sort((p, q) => {
      const cost = (v: Point[]) =>
        v.slice(1).reduce((sum, n, i) => sum + distance(v[i]!, n), 0) + v.length * 24
      return cost(p) - cost(q)
    })
  // Check port tangents as well: a detour must not immediately reverse into its own node.
  const usable = (p: Point[]) => {
    const first = p[1],
      last = p.at(-2)
    return (
      first &&
      last &&
      (first.x - a.x) * sd.x + (first.y - a.y) * sd.y >= 0 &&
      (last.x - b.x) * td.x + (last.y - b.y) * td.y >= 0 &&
      index.pathClear(p)
    )
  }
  const found = candidates.find(usable) ?? search(a, b, input, index, maxVisits)
  if (!found) return make(naturalPath, natural, 'fallback')
  const points = simplify([start, ...found, end])
  return make(rounded(points, index), points, 'detour')
}
