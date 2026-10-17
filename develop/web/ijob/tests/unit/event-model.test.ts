import { describe, expect, it } from 'vitest'
import {
  eventDraft,
  eventConfig,
  eventIssues,
  removedEventFields,
} from '../../src/modules/tasks/event-model'
import { draftFor, issues, taskBody } from '../../src/modules/tasks/model'
describe('EVENT configuration', () => {
  it('preserves arbitrary JSON numbers, false and unknown fields without rounding', () => {
    const draft = eventDraft(
      '{"action":"PUBLISH","eventCode":"a.b","payload":{"id":9223372036854775807,"ok":false},"extension":{"id":9223372036854775806}}',
    )
    expect(eventIssues(draft)).toEqual({})
    expect(eventConfig(draft)).toContain('9223372036854775807')
    expect(eventConfig(draft)).toContain('9223372036854775806')
    expect(JSON.parse(eventConfig(draft)).payload.ok).toBe(false)
  })
  it.each(['[]', 'null', '{', '{"action":"FUTURE"}'])(
    'blocks unsupported configuration %s',
    (text) => {
      const draft = eventDraft(text)
      expect(draft.blocked).toBe(true)
      expect(draft.original).toBe(text)
      expect(() => eventConfig(draft)).toThrow()
    },
  )
  it('keeps drafts across switches while serializing only applicable fields', () => {
    const d = eventDraft('{"action":"PUBLISH","eventCode":"a.b","extension":true}')
    expect(removedEventFields(d, 'WAIT_APPROVAL')).toEqual(['eventCode', 'extension'])
    d.action = 'WAIT_APPROVAL'
    d.drafts.WAIT_APPROVAL.allowedOperatorIds = ['user:exact']
    expect(JSON.parse(eventConfig(d))).toEqual({
      action: 'WAIT_APPROVAL',
      allowedOperatorIds: ['user:exact'],
      timeoutSeconds: null,
    })
    d.action = 'PUBLISH'
    expect(JSON.parse(eventConfig(d)).extension).toBe(true)
  })
  it('checks integer limits and exact event codes', () => {
    const d = eventDraft(
      '{"action":"WAIT_EVENT","eventCode":"a.*","lookbackSeconds":604801,"timeoutSeconds":0}',
    )
    expect(Object.keys(eventIssues(d))).toEqual([
      'event.eventCode',
      'event.timeoutSeconds',
      'event.lookbackSeconds',
    ])
    Object.assign(d.drafts.WAIT_EVENT, {
      eventCode: 'a.b:-_1',
      lookbackSeconds: 604800,
      timeoutSeconds: 2147483647,
    })
    expect(eventIssues(d)).toEqual({})
  })
  it('validates UTF-8 payload bytes and real local UTC dates', () => {
    const d = eventDraft('{"eventCode":"a.b"}')
    d.drafts.PUBLISH.payload = JSON.stringify('中'.repeat(5461))
    expect(eventIssues(d)['event.payload']).toBe('payloadLarge')
    d.drafts.PUBLISH.payload = 'false'
    d.drafts.PUBLISH.occurredAt = '2026-02-30T12:00:00'
    expect(eventIssues(d)['event.occurredAt']).toBe('timeInvalid')
    d.drafts.PUBLISH.occurredAt = '2026-09-27T12:00:00'
    expect(eventIssues(d)).toEqual({})
    d.drafts.PUBLISH.occurredAt += 'Z'
    expect(eventIssues(d)['event.occurredAt']).toBe('timeInvalid')
  })
  it('integrates with task publication without SQL requirements and rejects task timeout while waiting', () => {
    const d = draftFor()
    d.pluginType = 'EVENT'
    d.name = 'Approval'
    d.event.action = 'WAIT_APPROVAL'
    d.event.drafts.WAIT_APPROVAL.allowedOperatorIds = ['user:abc']
    expect(issues(d)).toEqual({})
    expect(JSON.parse(taskBody(d).pluginConfig).allowedOperatorIds).toEqual(['user:abc'])
    d.timeout = 1
    expect(issues(d).timeout).toBe('invalid')
  })
})
