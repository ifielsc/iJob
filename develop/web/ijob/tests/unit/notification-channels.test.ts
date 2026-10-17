import { describe, expect, it } from 'vitest'
import {
  notificationTypes,
  webhookMatches,
} from '../../src/modules/configuration/notification-channels'
import { parseConfig } from '../../src/modules/configuration/contracts'

describe('notification channels', () => {
  it('accepts only the configured provider route without credential-bearing redirects', () => {
    expect(webhookMatches('SLACK', 'https://hooks.slack.com/services/T1/B1/token')).toBe(true)
    for (const url of [
      'http://hooks.slack.com/services/T1/B1/token',
      'https://hooks.slack.com.evil.invalid/services/T1/B1/token',
      'https://user@hooks.slack.com/services/T1/B1/token',
      'https://hooks.slack.com/services/T1/B1/token?redirect=x',
    ])
      expect(webhookMatches('SLACK', url)).toBe(false)
    expect(
      webhookMatches('WECOM', 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=a&key=b'),
    ).toBe(false)
    expect(webhookMatches('FEISHU', 'https://open.larksuite.com/open-apis/bot/v2/hook/token')).toBe(
      false,
    )
  })
  it('parses masked detail for each new channel and rejects malformed member IDs in the response shape', () => {
    for (const type of notificationTypes.filter((type) => !['EMAIL', 'DINGTALK'].includes(type))) {
      const row = {
        id: '123',
        name: 'bot',
        type,
        enabled: true,
        config: {
          webhookConfigured: true,
          secretConfigured: false,
          webhookDisplay: 'https://provider/••••',
          format: 'Markdown',
          members: [],
          mobiles: [],
          roles: [],
          atAll: false,
        },
      }
      expect(parseConfig(row, true).type).toBe(type)
      expect(() =>
        parseConfig({ ...row, config: { ...row.config, members: [123] } }, true),
      ).toThrow()
    }
  })
})
