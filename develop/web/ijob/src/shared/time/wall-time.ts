// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

// A wall-clock timestamp has no offset. Never convert it through the browser timezone.
export function wallTime(value: string): string {
  const text = value.replace(' ', 'T')
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?$/.test(text))
    throw new Error('Invalid wall time')
  const validation = new Date(text.slice(0, 19) + 'Z')
  if (
    !Number.isFinite(validation.getTime()) ||
    validation.toISOString().slice(0, 19) !== text.slice(0, 19)
  )
    throw new Error('Invalid wall time')
  return text
}
export function nowInZone(zoneId: string, now = new Date()): string {
  const fields = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: zoneId,
      calendar: 'gregory',
      numberingSystem: 'latn',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(now)
      .map(({ type, value }) => [type, value]),
  )
  return `${fields.year}-${fields.month}-${fields.day}T${fields.hour}:${fields.minute}:${fields.second}`
}
