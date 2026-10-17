import { ApiError } from '../api/errors'
import { record } from '../api/values'
import { logRows } from './contracts'

// Replace the visible window on refresh: an ID is not a commit-order watermark.
export function logSnapshot(value: unknown) {
  const snapshot = record(value)
  if (typeof snapshot.truncated !== 'boolean') throw new ApiError('protocol')
  const rows = logRows(snapshot.rows)
  if (rows.length > 5000) throw new ApiError('protocol')
  return { rows, truncated: snapshot.truncated }
}
