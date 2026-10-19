// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../../shared/api/errors'
import { exactId, record } from './model'

export const notifyPolicies = ['NEVER', 'ON_SUCCESS', 'ON_FAILURE', 'ALWAYS'] as const
export type NotifyPolicy = (typeof notifyPolicies)[number]
export interface NotifyCandidate {
  id: string
  name: string
  type: 'EMAIL' | 'DINGTALK'
  enabled: boolean
}
export interface NotifyBinding {
  id: string
  targetId: string
  config: NotifyCandidate
  policy: NotifyPolicy
}
export function bindingId(value: unknown): string {
  if (typeof value !== 'string') throw new ApiError('protocol')
  return exactId(value)
}
export function notifyCandidate(value: unknown): NotifyCandidate {
  const r = record(value)
  if (
    typeof r.name !== 'string' ||
    !['EMAIL', 'DINGTALK'].includes(String(r.type)) ||
    typeof r.enabled !== 'boolean'
  )
    throw new ApiError('protocol')
  return {
    id: bindingId(r.id),
    name: r.name,
    type: r.type as NotifyCandidate['type'],
    enabled: r.enabled,
  }
}
export function notifyBindings(
  value: unknown,
  targetId: string,
  targetType: 'TASK' | 'JOB' = 'TASK',
): NotifyBinding[] {
  if (!Array.isArray(value)) throw new ApiError('protocol')
  const ids = new Set<string>(),
    configs = new Set<string>()
  return value.map((item) => {
    const r = record(item)
    const id = bindingId(r.id)
    const config = notifyCandidate({
      id: r.notifyConfigId,
      name: r.notifyConfigName,
      type: r.notifyType,
      enabled: r.notifyConfigEnabled,
    })
    if (
      r.targetType !== targetType ||
      bindingId(r.targetId) !== targetId ||
      !notifyPolicies.includes(r.notifyPolicy as NotifyPolicy) ||
      ids.has(id) ||
      configs.has(config.id)
    )
      throw new ApiError('protocol')
    ids.add(id)
    configs.add(config.id)
    return { id, targetId, config, policy: r.notifyPolicy as NotifyPolicy }
  })
}
export function bindingBody(
  targetId: string,
  configId: string,
  policy: NotifyPolicy,
  targetType: 'TASK' | 'JOB' = 'TASK',
) {
  if (!notifyPolicies.includes(policy)) throw new ApiError('protocol')
  return {
    targetType,
    targetId: bindingId(targetId),
    notifyConfigId: bindingId(configId),
    notifyPolicy: policy,
  }
}
