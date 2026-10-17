import type { AppLocale } from '../preferences/preferences'

// The backend uses UTC LocalDateTime without a suffix; never parse it as browser local time.
export function parseUtc(value: string): Date {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z)?$/.test(value))
    throw new Error('Invalid UTC timestamp')
  const date = new Date(value.endsWith('Z') ? value : `${value}Z`)
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 19) !== value.slice(0, 19))
    throw new Error('Invalid UTC timestamp')
  return date
}
export function formatUtc(value: string, locale: AppLocale, businessTimezone: string) {
  const parts = new Intl.DateTimeFormat(locale, {
    calendar: 'gregory',
    numberingSystem: 'latn',
    timeZone: businessTimezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(parseUtc(value))
  const fields = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
  return `${fields.year}-${fields.month}-${fields.day} ${fields.hour}:${fields.minute}:${fields.second}`
}
export function toApiUtc(date: Date): string {
  return date.toISOString().replace(/Z$/, '')
}
export function stringId(value: unknown): string {
  if (typeof value !== 'string' || !/^\d+$/.test(value))
    throw new Error('ID must be a decimal string')
  return value
}
