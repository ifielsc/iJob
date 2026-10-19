// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, it } from 'vitest'
import { stringify } from 'lossless-json'
import { apiDraft, apiConfig, apiIssues } from '../../src/modules/tasks/api-call-model'
import { draftFor, taskBody, taskRow } from '../../src/modules/tasks/model'
import {
  serviceDraft,
  serviceBody,
  serviceIssues,
} from '../../src/modules/configuration/service-model'
import { headerBody, headerDraft, headerIssues } from '../../src/modules/configuration/header-model'
import type { ConfigRow } from '../../src/modules/configuration/contracts'

it.each(['false', '0', '"text"', '[]', '{"id":9223372036854775807,"ok":false}', 'null'])(
  'preserves JSON body type and number precision: %s',
  (body) => {
    const row = taskRow(
      {
        id: '1',
        name: 'API',
        pluginType: 'API_CALL',
        enabled: true,
        groups: [],
        currentRevision: '2',
        currentTaskDefinitionId: '3',
        retryTimes: 0,
        retryInterval: 0,
        headerNames: ['X-Key'],
        pluginConfig: `{"resourceId":9223372036854775807,"body":${body},"custom":false}`,
      },
      true,
    )
    const draft = draftFor(row)
    const saved = taskBody(draft, row)
    expect(saved.pluginConfig).toContain(`"body":${body}`)
    expect(saved.pluginConfig).toContain('"custom":false')
    expect(saved.pluginConfig).toContain('"resourceId":"9223372036854775807"')
    expect(saved).toMatchObject({
      preservedHeaderNames: ['X-Key'],
      headerSourceDefinitionId: '3',
    })
  },
)
it('keeps HTTP/SOAP drafts independent and retains typed SOAP parameters until edited', () => {
  const d = apiDraft(
    '{"resourceId":"1","webService":{"operation":"Check","parameters":{"id":9223372036854775807,"active":false}}}',
  )
  d.http.body = '[1,false]'
  expect(stringify(apiConfig(d))).toContain('"id":9223372036854775807')
  expect(stringify(apiConfig(d))).toContain('"active":false')
  d.soap.parameters[0]!.value = 'edited'
  expect(stringify(apiConfig(d))).toContain('"id":"edited"')
  d.protocol = 'HTTP'
  expect(stringify(apiConfig(d))).toContain('"body":[1,false]')
  expect(apiConfig(d)).not.toHaveProperty('webService', expect.anything())
  d.protocol = 'WEB_SERVICE'
  expect(d.soap.operation).toBe('Check')
})
it('keeps, replaces and removes hidden headers and rejects ambiguous or OAuth overrides', () => {
  const h = headerDraft(['X-Keep', 'X-Replace', 'X-Remove'])
  h[1]!.field.mode = 'replace'
  h[1]!.field.value = 'new-secret'
  h.splice(2, 1)
  expect(headerBody(h)).toEqual({
    preservedHeaderNames: ['X-Keep'],
    headers: { 'X-Replace': 'new-secret' },
  })
  h.push({ name: 'x-keep', field: { mode: 'replace', configured: false, value: 'a' } })
  expect(Object.keys(headerIssues(h, 100, 256, 8192, false)).length).toBeGreaterThan(0)
  expect(
    Object.keys(headerIssues(headerDraft(['Authorization']), 100, 256, 8192, true)).length,
  ).toBeGreaterThan(0)
})
it('returns editable SERVICE configuration, preserves secrets, and requires new protocol configuration', () => {
  const row: ConfigRow = {
    id: '1',
    name: 'Service',
    code: 'service',
    enabled: true,
    type: 'SERVICE',
    config: {
      protocol: 'HTTP',
      authType: 'OAUTH2_CLIENT_CREDENTIALS',
      endpoint: 'https://api.example.invalid',
      oauth2TokenEndpoint: 'https://auth.example.invalid/token',
      oauth2ClientId: 'client-id',
      oauth2Scopes: 'orders.read',
      endpointConfigured: true,
      headerNames: ['X-Key'],
      oauth2TokenEndpointConfigured: true,
      oauth2ClientIdConfigured: true,
      oauth2ClientSecretConfigured: true,
      oauth2ScopesConfigured: true,
      oauth2AudienceConfigured: true,
    },
  }
  const d = serviceDraft(row)
  expect(serviceIssues(d)).toEqual({})
  let c = serviceBody(d, true).serviceConfig
  expect(c.endpoint).toBe('https://api.example.invalid')
  expect(c.oauth2).toMatchObject({ clientId: 'client-id', scopes: 'orders.read' })
  expect(c.oauth2).not.toHaveProperty('clientSecret')
  expect(c).toMatchObject({ preserveExisting: true, preservedHeaderNames: ['X-Key'] })
  d.http.oauth.scopes.mode = 'clear'
  c = serviceBody(d, true).serviceConfig
  expect(c.oauth2?.clearScopes).toBe(true)
  d.protocol = 'WEB_SERVICE'
  expect(serviceIssues(d)).toMatchObject({ endpoint: 'required', wsdl: 'required' })
  expect(serviceBody(d, true).serviceConfig.oauth2).toBeUndefined()
  d.protocol = 'HTTP'
  expect(serviceIssues(d)).toEqual({})
  d.http.authType = 'NONE'
  expect(serviceBody(d, true).serviceConfig.oauth2).toBeUndefined()
})
it('rejects malformed details and invalid API fields without silently dropping configuration', () => {
  expect(() => apiDraft('{"extractors":{}}')).toThrow()
  expect(() => apiDraft('{"webService":[]}')).toThrow()
  const d = apiDraft()
  d.resourceId = '9223372036854775808'
  d.http.body = '{'
  d.http.path = 'https://external'
  d.maxResponseBytes = 1
  expect(apiIssues(d)).toMatchObject({
    resourceId: 'required',
    body: 'invalid',
    path: 'invalid',
    maxResponseBytes: 'invalid',
  })
  d.http.body = 'false'
  d.resourceId = '1'
  d.http.path = '/ok'
  d.maxResponseBytes = 1024
  expect(apiIssues(d)).toEqual({})
})

it('round-trips editable SOAP fields and saves changed endpoint', () => {
  const d = serviceDraft({
    id: '2',
    name: 'SOAP',
    code: 'soap',
    type: 'SERVICE',
    enabled: true,
    config: {
      protocol: 'WEB_SERVICE',
      authType: 'NONE',
      endpoint: 'https://soap.example.invalid',
      wsdl: 'https://soap.example.invalid?wsdl',
      namespace: 'urn:orders',
      serviceName: 'Orders',
      portName: 'OrdersPort',
    },
  })
  expect(serviceIssues(d)).toEqual({})
  d.soap.endpoint.value = 'https://soap.example.invalid/v2'
  d.soap.namespace = 'urn:orders:v2'
  expect(serviceBody(d, true).serviceConfig).toMatchObject({
    endpoint: 'https://soap.example.invalid/v2',
    webService: {
      wsdl: 'https://soap.example.invalid?wsdl',
      namespace: 'urn:orders:v2',
      serviceName: 'Orders',
      portName: 'OrdersPort',
    },
  })
})
