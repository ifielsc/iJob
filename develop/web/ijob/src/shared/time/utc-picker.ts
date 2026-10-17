import { parseUtc } from './format'

// UI-only local calendar coordinates; never send this timestamp to the API.
export function calendarCoordinate(day: string) {
  const utc = parseUtc(`${day}T12:00:00`)
  const local = new Date(2000, 0, 15, 12)
  local.setFullYear(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate())
  return local.getTime()
}
export function calendarDay(timestamp: number) {
  const date = new Date(timestamp)
  return `${String(date.getFullYear()).padStart(4, '0')}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
// Decouple clock selection from the selected date's DST transitions.
export function clockCoordinate(time: string) {
  const utc = parseUtc(`2000-01-15T${time}`)
  return new Date(
    2000,
    0,
    15,
    utc.getUTCHours(),
    utc.getUTCMinutes(),
    utc.getUTCSeconds(),
  ).getTime()
}
export function clockText(timestamp: number) {
  const date = new Date(timestamp)
  return [date.getHours(), date.getMinutes(), date.getSeconds()]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')
}
