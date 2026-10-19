// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../shared/api/errors'
import { permissions } from '../shared/api/contracts'
import { parseUtc } from '../shared/time/format'
export type RoleCode = 'ADMIN' | 'OPERATOR' | 'VIEWER'
export const roles: RoleCode[] = ['ADMIN', 'OPERATOR', 'VIEWER']
export interface Account {
  id: string
  loginId: string
  username: string
  displayName: string
  roleCode: RoleCode
  enabled: boolean
  mustChangePassword: boolean
  revision: string
  createTime?: string
  updateTime?: string
}
export interface Role {
  roleCode: RoleCode
  permissions: string[]
}
export interface Audit {
  id: string
  occurredAt: string
  actorLoginId: string
  actorName?: string
  actionCode: string
  targetType: string
  targetId?: string
  resultCode: string
  traceId?: string
  requestId?: string
  changedFields?: string
}
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError('protocol')
  return value as Record<string, unknown>
}
function strings(row: Record<string, unknown>, keys: string[]) {
  if (keys.some((key) => typeof row[key] !== 'string')) throw new ApiError('protocol')
}
function decimal(value: unknown) {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) throw new ApiError('protocol')
}
export function parseAccount(value: unknown): Account {
  const r = record(value)
  strings(r, ['id', 'loginId', 'username', 'displayName', 'roleCode', 'revision'])
  decimal(r.id)
  decimal(r.revision)
  if (
    !roles.includes(r.roleCode as RoleCode) ||
    typeof r.enabled !== 'boolean' ||
    typeof r.mustChangePassword !== 'boolean'
  )
    throw new ApiError('protocol')
  return r as unknown as Account
}
export function parseRoles(value: unknown): Role[] {
  if (!Array.isArray(value)) throw new ApiError('protocol')
  return value.map((v) => {
    const r = record(v)
    if (
      !roles.includes(r.roleCode as RoleCode) ||
      !Array.isArray(r.permissions) ||
      r.permissions.some((p) => typeof p !== 'string')
    )
      throw new ApiError('protocol')
    return {
      roleCode: r.roleCode as RoleCode,
      permissions: r.permissions.filter((p) => (permissions as readonly string[]).includes(p)),
    }
  })
}
export function parseAudit(value: unknown): Audit {
  const r = record(value)
  strings(r, ['id', 'occurredAt', 'actorLoginId', 'actionCode', 'targetType', 'resultCode'])
  decimal(r.id)
  try {
    parseUtc(r.occurredAt as string)
  } catch {
    throw new ApiError('protocol')
  }
  for (const key of ['actorName', 'targetId', 'traceId', 'requestId', 'changedFields'])
    if (r[key] != null && typeof r[key] !== 'string') throw new ApiError('protocol')
  return r as unknown as Audit
}
export function parsePage<T>(value: unknown, parse: (item: unknown) => T): PageResult<T> {
  const r = record(value)
  if (
    !Array.isArray(r.list) ||
    ['total', 'pageNum', 'pageSize', 'pages'].some(
      (k) => !Number.isSafeInteger(r[k]) || Number(r[k]) < 0,
    ) ||
    Number(r.pageNum) < 1 ||
    Number(r.pageSize) < 1 ||
    Number(r.pageSize) > 500
  )
    throw new ApiError('protocol')
  return {
    list: r.list.map(parse),
    total: Number(r.total),
    pageNum: Number(r.pageNum),
    pageSize: Number(r.pageSize),
    pages: Number(r.pages),
  }
}
