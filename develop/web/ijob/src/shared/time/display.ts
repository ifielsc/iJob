// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { formatUtc, parseUtc, toApiUtc } from './format'

/** Convert a wall-clock value in an explicit IANA zone. Never use the browser zone. */
export function wallToUtc(value: string, zone: string): string {
  const wall = value.replace(' ', 'T')
  const text = wall.length === 16 ? wall + ':00' : wall
  const coordinate = parseUtc(text).getTime()
  const offsets = new Set<number>()
  // Both sides of an offset transition must be considered. Ambiguous/nonexistent
  // wall times are rejected instead of silently choosing another execution window.
  for (let hour = -48; hour <= 48; hour += 6) {
    const instant = coordinate + hour * 3600000
    const wall = formatUtc(new Date(instant).toISOString(), 'en-US', zone).replace(' ', 'T')
    offsets.add(parseUtc(wall).getTime() - Math.floor(instant / 1000) * 1000)
  }
  const matches = [...offsets]
    .map((offset) => coordinate - offset)
    .filter(
      (instant) =>
        formatUtc(new Date(instant).toISOString(), 'en-US', zone).replace(' ', 'T') ===
        text.slice(0, 19),
    )
  if (matches.length !== 1) throw new Error('Wall time is ambiguous or does not exist in this zone')
  return toApiUtc(new Date(matches[0]!)).slice(0, 19) + (text.match(/\.\d+/)?.[0] ?? '')
}

export function utcToWall(value: string, zone: string): string {
  return formatUtc(value, 'en-US', zone).replace(' ', 'T') + (value.match(/\.\d+/)?.[0] ?? '')
}

export function utcEvidence(value: string): string {
  parseUtc(value)
  return value.replace(/Z$/, '') + 'Z'
}
