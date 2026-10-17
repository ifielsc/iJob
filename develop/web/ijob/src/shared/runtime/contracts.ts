import { ApiError } from '../api/errors'
import { exactId, record } from '../api/values'
export const terminal = new Set(['SUCCESS', 'FAILED', 'STOPPED', 'TIMED_OUT', 'SKIPPED'])
export interface Run {
  jobDefinitionId?: string | null
  id: string
  status: string
  rootInstanceId: string | null
  taskDefinitionId: string | null
  createTime: string
  startTime: string
  endTime: string
  message: string
}
export function runRow(v: unknown): Run {
  const r = record(v)
  if (
    typeof r.status !== 'string' ||
    ![
      'WAITING',
      'RUNNING',
      'SUCCESS',
      'FAILED',
      'STOPPED',
      'TIMED_OUT',
      'SKIPPED',
      'PAUSED',
      'WAITING_EVENT',
    ].includes(r.status)
  )
    throw new ApiError('protocol')
  return {
    id: exactId(r.id),
    status: r.status,
    rootInstanceId: r.rootInstanceId == null ? null : exactId(r.rootInstanceId),
    taskDefinitionId: r.taskDefinitionId == null ? null : exactId(r.taskDefinitionId),
    jobDefinitionId: r.jobDefinitionId == null ? null : exactId(r.jobDefinitionId),
    createTime: typeof r.createTime === 'string' ? r.createTime : '',
    startTime: typeof r.startTime === 'string' ? r.startTime : '',
    endTime: typeof r.endTime === 'string' ? r.endTime : '',
    message:
      typeof r.displayMessage === 'string'
        ? r.displayMessage
        : typeof r.message === 'string'
          ? r.message
          : '',
  }
}
export interface Log {
  instanceId?: string
  id: string
  logTime: string
  level: string
  content: string
}
export function logRows(v: unknown): Log[] {
  if (!Array.isArray(v)) throw new ApiError('protocol')
  return v.map((x) => {
    const r = record(x)
    if (
      typeof r.logTime !== 'string' ||
      typeof r.level !== 'string' ||
      typeof r.content !== 'string'
    )
      throw new ApiError('protocol')
    return {
      id: exactId(r.id),
      ...(r.instanceId == null ? {} : { instanceId: exactId(r.instanceId) }),
      logTime: r.logTime,
      level: r.level,
      content: r.content,
    }
  })
}
