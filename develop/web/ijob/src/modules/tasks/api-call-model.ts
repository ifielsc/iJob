// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { parseExact as parse } from '../../shared/ui/json-form'
import { ApiError } from '../../shared/api/errors'
import { stringify } from 'lossless-json'
import { headerBody, headerDraft, headerIssues } from '../configuration/public/contracts'
export function apiDraft(text?: string, names: string[] = [], stopNames: string[] = []) {
  const object = (value: unknown): Record<string, unknown> => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError('protocol')
    return value as Record<string, unknown>
  }
  let c: Record<string, unknown>
  try {
    c = text ? object(parse(text)) : {}
  } catch {
    throw new ApiError('protocol')
  }
  const ws = object(c.webService ?? {})
  const condition = object(c.successCondition ?? {})
  object(ws.parameters ?? {})
  if (
    c.extractors != null &&
    (!Array.isArray(c.extractors) ||
      c.extractors.some(
        (v) => !v || typeof v.variable !== 'string' || typeof v.expression !== 'string',
      ))
  )
    throw new ApiError('protocol')
  const remote = object(c.remoteStop ?? {})
  const remoteWs = object(remote.webService ?? {})
  object(remoteWs.parameters ?? {})
  const soap = !!c.webService
  return {
    resourceId: c.resourceId == null ? '' : String(c.resourceId),
    protocol: soap ? 'WEB_SERVICE' : 'HTTP',
    authType: 'NONE',
    remoteStop: {
      enabled: c.remoteStop != null,
      timeoutSeconds: Number(remote.timeoutSeconds ?? 5) as number | null,
      http: {
        method: String(remote.method ?? 'POST').toUpperCase(),
        path: typeof remote.path === 'string' ? remote.path : '',
        body: remote.body === undefined ? '' : stringify(remote.body, null, 2)!,
        headers: headerDraft(soap ? [] : stopNames),
      },
      soap: {
        operation: typeof remoteWs.operation === 'string' ? remoteWs.operation : '',
        action: typeof remoteWs.action === 'string' ? remoteWs.action : '',
        headers: headerDraft(soap ? stopNames : []),
        parameters: Object.entries((remoteWs.parameters ?? {}) as Record<string, unknown>).map(
          ([name, value]) => ({
            name,
            value: typeof value === 'string' ? value : stringify(value)!,
            original: typeof value === 'string' ? undefined : stringify(value),
            initial: typeof value === 'string' ? value : stringify(value)!,
          }),
        ),
      },
    },
    http: {
      method: String(c.method ?? 'POST').toUpperCase(),
      path: typeof c.path === 'string' ? c.path : '',
      body: c.body === undefined ? '' : stringify(c.body, null, 2)!,
      headers: headerDraft(soap ? [] : names),
    },
    soap: {
      operation: typeof ws.operation === 'string' ? ws.operation : '',
      action: typeof ws.action === 'string' ? ws.action : '',
      headers: headerDraft(soap ? names : []),
      parameters: Object.entries((ws.parameters ?? {}) as Record<string, unknown>).map(
        ([name, value]) => ({
          name,
          value: typeof value === 'string' ? value : stringify(value)!,
          original: typeof value === 'string' ? undefined : stringify(value),
          initial: typeof value === 'string' ? value : stringify(value)!,
        }),
      ),
    },
    maxResponseBytes: Number(c.maxResponseBytes ?? 1048576) as number | null,
    useCondition: !!c.successCondition,
    expression: String(condition.expression ?? ''),
    operator: String(condition.operator ?? 'EQ'),
    expected: String(condition.expected ?? ''),
    extractors: ((c.extractors ?? []) as { variable: string; expression: string }[]).map((v) => ({
      variable: v.variable,
      expression: v.expression,
    })),
  }
}
export type ApiDraft = ReturnType<typeof apiDraft>
export const apiHeaders = (d: ApiDraft) =>
  d.protocol === 'WEB_SERVICE' ? d.soap.headers : d.http.headers
export const apiStopHeaders = (d: ApiDraft) =>
  d.protocol === 'WEB_SERVICE' ? d.remoteStop.soap.headers : d.remoteStop.http.headers
export function apiConfig(d: ApiDraft, previous: Record<string, unknown> = {}) {
  const soap = d.protocol === 'WEB_SERVICE'
  return {
    ...previous,
    remoteStop: d.remoteStop.enabled
      ? {
          method: soap ? 'POST' : d.remoteStop.http.method,
          path: soap ? undefined : d.remoteStop.http.path,
          body: soap
            ? undefined
            : d.remoteStop.http.body.trim()
              ? parse(d.remoteStop.http.body)
              : undefined,
          headers: headerBody(apiStopHeaders(d)).headers,
          timeoutSeconds: d.remoteStop.timeoutSeconds,
          webService: soap
            ? {
                operation: d.remoteStop.soap.operation,
                action: d.remoteStop.soap.action || null,
                parameters: Object.fromEntries(
                  d.remoteStop.soap.parameters.map((p) => [
                    p.name,
                    p.original !== undefined && p.value === p.initial ? parse(p.original) : p.value,
                  ]),
                ),
              }
            : undefined,
        }
      : null,
    resourceId: d.resourceId,
    method: soap ? 'POST' : d.http.method,
    path: soap ? undefined : d.http.path || null,
    body: soap ? undefined : d.http.body.trim() ? parse(d.http.body) : undefined,
    webService: soap
      ? {
          operation: d.soap.operation,
          action: d.soap.action || null,
          parameters: Object.fromEntries(
            d.soap.parameters.map((p) => [
              p.name,
              p.original !== undefined && p.value === p.initial ? parse(p.original) : p.value,
            ]),
          ),
        }
      : undefined,
    headers: headerBody(apiHeaders(d)).headers,
    maxResponseBytes: d.maxResponseBytes,
    successCondition: d.useCondition
      ? { expression: d.expression, operator: d.operator, expected: d.expected }
      : null,
    extractors: d.extractors,
  }
}
export function apiIssues(d: ApiDraft) {
  const e: Record<string, string> = {}
  if (!/^[1-9]\d*$/.test(d.resourceId) || BigInt(d.resourceId) > 9223372036854775807n)
    e.resourceId = 'required'
  if (
    !Number.isInteger(d.maxResponseBytes) ||
    (d.maxResponseBytes ?? 0) < 1024 ||
    (d.maxResponseBytes ?? 0) > 16777216
  )
    e.maxResponseBytes = 'invalid'
  if (d.protocol === 'WEB_SERVICE') {
    if (!/^[A-Za-z_][A-Za-z0-9_.-]{0,127}$/.test(d.soap.operation)) e.operation = 'invalid'
    if (d.soap.action.length > 2048 || /[\r\n]/.test(d.soap.action)) e.action = 'invalid'
    const seen = new Set<string>()
    d.soap.parameters.forEach((p, i) => {
      if (!/^[A-Za-z_][A-Za-z0-9_.-]*$/.test(p.name) || seen.has(p.name))
        e['parameter-' + i] = 'invalid'
      seen.add(p.name)
    })
    if (d.soap.parameters.length > 100) e.parameters = 'invalid'
  } else {
    if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].includes(d.http.method))
      e.method = 'invalid'
    if (
      d.http.path.length > 2048 ||
      /[\r\n]/.test(d.http.path) ||
      d.http.path.startsWith('//') ||
      d.http.path.includes('://')
    )
      e.path = 'invalid'
    try {
      if (d.http.body.trim()) parse(d.http.body)
    } catch {
      e.body = 'invalid'
    }
  }
  if (
    d.useCondition &&
    (!d.expression.trim() ||
      d.expression.length > 1024 ||
      !['EQ', 'NE', 'GT', 'LT'].includes(d.operator) ||
      d.expected.length > 8192)
  )
    e.expression = 'invalid'
  const seen = new Set<string>()
  d.extractors.forEach((x, i) => {
    if (
      !x.variable.trim() ||
      x.variable === 'response' ||
      x.variable.length > 128 ||
      seen.has(x.variable) ||
      !x.expression.trim() ||
      x.expression.length > 1024
    )
      e['extractor-' + i] = 'invalid'
    seen.add(x.variable)
  })
  if (d.extractors.length > 64) e.extractors = 'invalid'
  Object.assign(
    e,
    headerIssues(apiHeaders(d), 100, 256, 8192, d.authType === 'OAUTH2_CLIENT_CREDENTIALS'),
  )
  if (d.remoteStop.enabled) {
    const stop = d.remoteStop
    if (
      !Number.isInteger(stop.timeoutSeconds) ||
      stop.timeoutSeconds! < 1 ||
      stop.timeoutSeconds! > 30
    )
      e['remoteStop.timeoutSeconds'] = 'invalid'
    if (d.protocol !== 'WEB_SERVICE' && !stop.http.path.trim()) e['remoteStop.path'] = 'required'
    const requestErrors = apiIssues({
      ...d,
      http: stop.http,
      soap: stop.soap,
      remoteStop: { ...stop, enabled: false },
      useCondition: false,
      extractors: [],
    })
    for (const [key, value] of Object.entries(requestErrors)) e['remoteStop.' + key] = value
  }
  if (!e.body && !e['remoteStop.body']) {
    try {
      if (new TextEncoder().encode(stringify(apiConfig(d))!).length > 8 * 1024 * 1024)
        e.body = 'invalid'
    } catch {
      e.body = 'invalid'
    }
  }
  return e
}
