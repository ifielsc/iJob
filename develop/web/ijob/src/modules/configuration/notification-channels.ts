// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export const notificationTypes = [
  'EMAIL',
  'DINGTALK',
  'WECOM',
  'FEISHU',
  'SLACK',
  'DISCORD',
] as const
export const robotTypes = notificationTypes.filter((type) => type !== 'EMAIL')
export const newWebhookTypes = ['WECOM', 'FEISHU', 'SLACK', 'DISCORD']
export function notificationLabel(type: string) {
  return (
    'config.' +
    (
      {
        EMAIL: 'email',
        DINGTALK: 'ding',
        WECOM: 'wecom',
        FEISHU: 'feishu',
        SLACK: 'slack',
        DISCORD: 'discord',
      } as Record<string, string>
    )[type]
  )
}
export function webhookMatches(type: string, value: string) {
  try {
    const url = new URL(value)
    if (
      url.protocol !== 'https:' ||
      url.username ||
      url.password ||
      url.hash ||
      (url.port && url.port !== '443')
    )
      return false
    const patterns: Record<string, [string, RegExp]> = {
      WECOM: ['qyapi.weixin.qq.com', /^\/cgi-bin\/webhook\/send$/],
      FEISHU: ['open.feishu.cn', /^\/open-apis\/bot\/v2\/hook\/[A-Za-z0-9_-]+$/],
      SLACK: ['hooks.slack.com', /^\/services\/[A-Za-z0-9]+\/[A-Za-z0-9]+\/[A-Za-z0-9_-]+$/],
      DISCORD: ['discord.com', /^\/api(?:\/v10)?\/webhooks\/[0-9]+\/[A-Za-z0-9._-]+$/],
    }
    const pattern = patterns[type]
    return (
      !!pattern &&
      url.hostname === pattern[0] &&
      pattern[1].test(url.pathname) &&
      (type === 'WECOM'
        ? [...url.searchParams].length === 1 &&
          /^[A-Za-z0-9_-]+$/.test(url.searchParams.get('key') ?? '')
        : !url.search)
    )
  } catch {
    return false
  }
}
