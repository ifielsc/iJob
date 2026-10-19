// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { randomUuid } from '../../shared/runtime/uuid'
import { hidden, type ConfigRow } from './contracts'
import { headerBody, headerDraft, headerIssues } from './header-model'
export type ServiceProtocol = 'HTTP' | 'WEB_SERVICE'
export function serviceDraft(row?: ConfigRow) {
  const c = row?.config
  const branch = (protocol: ServiceProtocol) => {
    const existing = !!row && c?.protocol === protocol
    return {
      endpoint: {
        ...hidden(existing && !!c?.endpointConfigured, existing),
        mode: 'replace' as const,
        value: existing ? (c?.endpoint ?? '') : '',
      },
      headers: headerDraft(existing ? c?.headerNames : []),
      authType: existing ? (c?.authType ?? 'NONE') : 'NONE',
      oauth: {
        tokenEndpoint: {
          ...hidden(existing && !!c?.oauth2TokenEndpointConfigured, existing),
          mode: 'replace' as 'keep' | 'replace' | 'clear',
          value: existing ? (c?.oauth2TokenEndpoint ?? '') : '',
        },
        clientId: {
          ...hidden(existing && !!c?.oauth2ClientIdConfigured, existing),
          mode: 'replace' as 'keep' | 'replace' | 'clear',
          value: existing ? (c?.oauth2ClientId ?? '') : '',
        },
        clientSecret: hidden(existing && !!c?.oauth2ClientSecretConfigured, existing),
        scopes: {
          ...hidden(existing && !!c?.oauth2ScopesConfigured, existing),
          mode: 'replace' as 'keep' | 'replace' | 'clear',
          value: existing ? (c?.oauth2Scopes ?? '') : '',
        },
        audience: {
          ...hidden(existing && !!c?.oauth2AudienceConfigured, existing),
          mode: 'replace' as 'keep' | 'replace' | 'clear',
          value: existing ? (c?.oauth2Audience ?? '') : '',
        },
      },
      clientAuthenticationMethod: existing
        ? (c?.oauth2ClientAuthenticationMethod ?? 'CLIENT_SECRET_BASIC')
        : 'CLIENT_SECRET_BASIC',
      refreshSkewSeconds: (existing ? (c?.oauth2RefreshSkewSeconds ?? 30) : 30) as number | null,
      wsdl: existing ? (c?.wsdl ?? '') : '',
      namespace: existing ? (c?.namespace ?? '') : '',
      serviceName: existing ? (c?.serviceName ?? '') : '',
      portName: existing ? (c?.portName ?? '') : '',
    }
  }
  return {
    name: row?.name ?? '',
    code: row?.code ?? 'res-' + randomUuid(),
    enabled: row?.enabled ?? true,
    description: row?.description ?? '',
    protocol: (c?.protocol ?? 'HTTP') as ServiceProtocol,
    http: branch('HTTP'),
    soap: branch('WEB_SERVICE'),
    connectTimeout: (c?.connectTimeout ?? 10) as number | null,
    readTimeout: (c?.readTimeout ?? 30) as number | null,
    writeTimeout: (c?.writeTimeout ?? 30) as number | null,
    callTimeout: (c?.callTimeout ?? 60) as number | null,
  }
}
export type ServiceDraft = ReturnType<typeof serviceDraft>
export const activeService = (d: ServiceDraft) => (d.protocol === 'HTTP' ? d.http : d.soap)
export function serviceIssues(d: ServiceDraft) {
  const e: Record<string, string> = {},
    s = activeService(d)
  if (!d.name.trim()) e.name = 'required'
  else if (d.name.trim().length > 128) e.name = 'invalid'
  if (!/^[A-Za-z0-9._-]{1,128}$/.test(d.code.trim())) e.code = 'invalid'
  if (d.description.length > 500) e.description = 'invalid'
  const url = (value: string) => {
    try {
      const u = new URL(value)
      return (
        ['http:', 'https:'].includes(u.protocol) &&
        !u.username &&
        !u.password &&
        value.length <= 2048 &&
        !/[\r\n]/.test(value) &&
        !/(?:^|[&;])(token|key|secret|password|passwd|pwd)=/i.test(u.search.slice(1))
      )
    } catch {
      return false
    }
  }
  if (!s.endpoint.value.trim()) e.endpoint = 'required'
  else if (!url(s.endpoint.value.trim())) e.endpoint = 'invalid'
  for (const k of ['connectTimeout', 'readTimeout', 'writeTimeout', 'callTimeout'] as const)
    if (
      !Number.isInteger(d[k]) ||
      (d[k] ?? 0) < 1 ||
      (d[k] ?? 0) > (k === 'callTimeout' ? 900 : 300)
    )
      e[k] = 'invalid'
  if (d.protocol === 'HTTP' && s.authType === 'OAUTH2_CLIENT_CREDENTIALS') {
    for (const key of [
      'tokenEndpoint',
      'clientId',
      'clientSecret',
      'scopes',
      'audience',
    ] as const) {
      const field = s.oauth[key],
        required = !['scopes', 'audience'].includes(key)
      if (
        required &&
        ((field.mode === 'keep' && !field.configured) ||
          (field.mode === 'replace' && !field.value.trim()) ||
          field.mode === 'clear')
      )
        e[key] = 'required'
      if (
        field.mode === 'replace' &&
        field.value.length > (key === 'clientId' ? 512 : key === 'clientSecret' ? 4096 : 2048)
      )
        e[key] = 'invalid'
      if (key === 'tokenEndpoint' && field.mode === 'replace' && !url(field.value.trim()))
        e[key] = 'invalid'
    }
    if (
      !Number.isInteger(s.refreshSkewSeconds) ||
      (s.refreshSkewSeconds ?? -1) < 0 ||
      (s.refreshSkewSeconds ?? 301) > 300
    )
      e.refreshSkewSeconds = 'invalid'
  }
  if (d.protocol === 'WEB_SERVICE') {
    for (const key of ['wsdl', 'namespace', 'serviceName', 'portName'] as const)
      if (!s[key].trim()) e[key] = 'required'
    if (s.wsdl && !url(s.wsdl.trim())) e.wsdl = 'invalid'
  }
  Object.assign(
    e,
    headerIssues(
      s.headers,
      50,
      128,
      4096,
      d.protocol === 'HTTP' && s.authType === 'OAUTH2_CLIENT_CREDENTIALS',
    ),
  )
  return e
}
export function serviceBody(d: ServiceDraft, existing: boolean) {
  const s = activeService(d)
  const oauth = Object.fromEntries(
    Object.entries(s.oauth)
      .filter(([, v]) => v.mode === 'replace')
      .map(([k, v]) => [k, v.value]),
  )
  return {
    type: 'SERVICE',
    name: d.name.trim(),
    code: d.code.trim(),
    enabled: d.enabled,
    description: d.description,
    serviceConfig: {
      preserveExisting: existing,
      protocol: d.protocol,
      endpoint: s.endpoint.value,
      authType: d.protocol === 'HTTP' ? s.authType : 'NONE',
      ...headerBody(s.headers),
      connectTimeout: d.connectTimeout,
      readTimeout: d.readTimeout,
      writeTimeout: d.writeTimeout,
      callTimeout: d.callTimeout,
      oauth2:
        d.protocol === 'HTTP' && s.authType === 'OAUTH2_CLIENT_CREDENTIALS'
          ? {
              ...oauth,
              clearScopes: s.oauth.scopes.mode === 'clear',
              clearAudience: s.oauth.audience.mode === 'clear',
              clientAuthenticationMethod: s.clientAuthenticationMethod,
              refreshSkewSeconds: s.refreshSkewSeconds,
            }
          : undefined,
      webService:
        d.protocol === 'WEB_SERVICE'
          ? {
              wsdl: s.wsdl,
              namespace: s.namespace,
              serviceName: s.serviceName,
              portName: s.portName,
            }
          : undefined,
    },
  }
}
