// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, it } from 'vitest'
import { apiDraft, apiConfig, apiIssues } from '../../src/modules/tasks/api-call-model'
import { transferDraft, transferConfig } from '../../src/modules/tasks/file-transfer-model'
import { stringify } from 'lossless-json'
it('defaults new transfers to staging and preserves missing legacy mode', () => {
  expect(transferDraft().remotePublishMode).toBe('TEMP_RENAME')
  expect(transferDraft('{}').remotePublishMode).toBe('DIRECT')
  const draft = transferDraft(
    '{"sourceResourceId":1,"targetResourceId":2,"remotePublishMode":"TEMP_RENAME"}',
  )
  expect(transferConfig(draft)).toContain('"remotePublishMode":"TEMP_RENAME"')
  expect(transferDraft('{"remotePublishMode":"unknown"}').blocked).toBe(true)
})
it('preserves disabled legacy calls and remote request precision and hidden header names', () => {
  expect(apiConfig(apiDraft('{"resourceId":1}')).remoteStop).toBeNull()
  const draft = apiDraft(
    '{"resourceId":1,"remoteStop":{"path":"/stop","body":{"id":9007199254740993,"empty":null}}}',
    [],
    ['X-Stop'],
  )
  expect(draft.remoteStop.http.headers[0]!.name).toBe('X-Stop')
  expect(stringify(apiConfig(draft))).toContain('9007199254740993')
  expect(stringify(apiConfig(draft))).toContain('"empty":null')
  expect(apiIssues(draft)).toEqual({})
  draft.remoteStop.enabled = false
  expect(apiConfig(draft).remoteStop).toBeNull()
  draft.remoteStop.enabled = true
  expect(draft.remoteStop.http.path).toBe('/stop')
})
it('keeps stop HTTP and SOAP drafts independent and validates active configuration', () => {
  const draft = apiDraft('{"resourceId":1}')
  draft.remoteStop.enabled = true
  expect(apiIssues(draft)['remoteStop.path']).toBe('required')
  draft.remoteStop.http.path = '/stop'
  draft.remoteStop.http.body = '{"id":1}'
  draft.protocol = 'WEB_SERVICE'
  draft.soap.operation = 'Work'
  draft.remoteStop.soap.operation = 'Cancel'
  expect(apiConfig(draft).remoteStop?.webService?.operation).toBe('Cancel')
  expect(apiConfig(draft).remoteStop?.body).toBeUndefined()
  draft.protocol = 'HTTP'
  expect(stringify(apiConfig(draft).remoteStop?.body)).toBe('{"id":1}')
  draft.remoteStop.timeoutSeconds = 31
  expect(apiIssues(draft)['remoteStop.timeoutSeconds']).toBe('invalid')
})

it('reports malformed stop JSON against the stop editor only', () => {
  const draft = apiDraft('{"resourceId":"1"}')
  draft.remoteStop.enabled = true
  draft.remoteStop.http.path = '/stop'
  draft.remoteStop.http.body = '{'
  expect(apiIssues(draft)['remoteStop.body']).toBe('invalid')
  expect(apiIssues(draft).body).toBeUndefined()
})
