import { describe, expect, it } from 'vitest'
import {
  pushDraft,
  pushConfig,
  pushIssues,
  pushLimits,
  type PushLimit,
} from '../../src/modules/tasks/push-model'
import { mqDraft, mqIssues, mqBody } from '../../src/modules/configuration/mq-model'
import { parseConfig } from '../../src/modules/configuration/contracts'
import { draftFor, issues, taskBody } from '../../src/modules/tasks/model'
const config =
  '{"sourceResourceId":9007199254740993,"sql":"SELECT id FROM orders","future":0.1234567890123456789,"target":{"resourceId":9223372036854775807,"type":"KAFKA","future":9007199254740993,"properties":{"topic":"orders","future":9223372036854775807}}}'
describe('PUSH configuration', () => {
  it('preserves exact identifiers and unknown nested values', () => {
    const d = pushDraft(config)
    expect(pushIssues(d)).toEqual({})
    const saved = pushConfig(d)
    for (const value of ['9007199254740993', '9223372036854775807', '0.1234567890123456789'])
      expect(saved).toContain(value)
    expect(saved).toContain('"future":9007199254740993')
  })
  it('switches targets without leaking inactive routing or losing drafts', () => {
    const d = pushDraft(config)
    d.type = 'RABBITMQ'
    Object.assign(d.targets.RABBITMQ, { resourceId: '23', routingKey: 'orders', exchange: '' })
    expect(pushIssues(d)).toEqual({})
    expect(pushConfig(d)).toContain('"exchange":""')
    expect(pushConfig(d)).not.toContain('"topic"')
    d.type = 'KAFKA'
    expect(d.targets.KAFKA.topic).toBe('orders')
    expect(pushConfig(d)).not.toContain('routingKey')
  })
  it.each(['1.2', '1e2', 'null', 'true', '"1e2"', '9223372036854775808'])(
    'blocks invalid source %s',
    (value) => {
      expect(pushDraft(config.replace('9007199254740993', value)).blocked).toBe(true)
    },
  )
  it.each(['.', '..', 'bad topic'])('rejects invalid topic %s', (topic) => {
    const d = pushDraft(config)
    d.targets.KAFKA.topic = topic
    expect(pushIssues(d)).toHaveProperty('push.topic')
  })
  it('keeps runtime placeholders for server resolution', () => {
    const d = pushDraft(config)
    d.targets.KAFKA.topic = '${topic}'
    d.sql = '${query}'
    expect(pushIssues(d)).toEqual({})
  })
  it('rejects unknown enums and structures without destructive saving', () => {
    for (const text of [
      '[]',
      '{',
      'null',
      config.replace('"KAFKA"', '"FUTURE"'),
      config.replace('"sql":', '"serializer":"xml","sql":'),
    ]) {
      const d = pushDraft(text)
      expect(d.blocked).toBe(true)
      expect(() => pushConfig(d)).toThrow()
    }
  })
  it('checks every quota boundary and total versus single limit', () => {
    for (const k of Object.keys(pushLimits) as PushLimit[]) {
      const d = pushDraft(config)
      d.limits[k] = pushLimits[k][1] - 1
      expect(pushIssues(d)).toHaveProperty('push.' + k)
      d.limits[k] = pushLimits[k][2] + 1
      expect(pushIssues(d)).toHaveProperty('push.' + k)
    }
    const d = pushDraft(config)
    d.limits.maxTotalPayloadBytes = 1024
    expect(pushIssues(d)).toHaveProperty('push.maxTotalPayloadBytes')
  })
  it('publishes only PUSH configuration through common task flow', () => {
    const d = draftFor()
    d.name = 'push'
    d.pluginType = 'PUSH'
    d.push = pushDraft(config)
    expect(issues(d)).toEqual({})
    expect(taskBody(d).pluginConfig).toContain('9223372036854775807')
  })
})
describe('MQ resources', () => {
  const row = () =>
    parseConfig(
      {
        id: '9007199254740993',
        code: 'rabbit',
        name: 'Rabbit',
        type: 'RABBITMQ',
        enabled: true,
        config: {
          host: 'rabbit.invalid',
          port: 5672,
          username: 'publisher',
          virtualHost: '/',
          publisherConfirm: true,
          passwordConfigured: true,
          exchange: 'old',
          routingKey: 'legacy',
        },
      },
      true,
    )
  it('keeps password and legacy routing out of update requests', () => {
    const d = mqDraft('RABBITMQ', row())
    expect(mqIssues(d)).toEqual({})
    const result = mqBody(d, true)
    if (!('rabbitConfig' in result)) throw Error()
    const body = result.rabbitConfig
    expect(body.password).toBeUndefined()
    expect(body.preserveExisting).toBe(true)
    expect(body).not.toHaveProperty('exchange')
  })
  it('requires new password and validates replacement', () => {
    const d = mqDraft('RABBITMQ')
    Object.assign(d, { name: 'New', host: 'rabbit.invalid', username: 'p' })
    expect(mqIssues(d)).toHaveProperty('password')
    d.password.value = 'new-secret'
    expect(mqIssues(d)).toEqual({})
    const result = mqBody(d, false)
    if (!('rabbitConfig' in result)) throw Error()
    expect(result.rabbitConfig.password).toBe('new-secret')
    d.password.mode = 'clear'
    expect(mqIssues(d)).toHaveProperty('password')
  })
  it('validates Kafka fields without inventing credentials', () => {
    const d = mqDraft('KAFKA')
    d.name = 'Kafka'
    d.bootstrapServers = 'kafka.invalid:9092'
    expect(mqIssues(d)).toEqual({})
    expect(mqBody(d, false)).not.toHaveProperty('rabbitConfig')
    d.acks = 'future'
    expect(mqIssues(d)).toHaveProperty('acks')
  })
})
