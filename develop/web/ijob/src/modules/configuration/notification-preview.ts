// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { ApiError } from '../../shared/api/errors'
import { record } from './contracts'
import { notificationTypes } from './notification-channels'

export interface PreviewOptions {
  type: string
  format: string
  members: string[]
  mobiles: string[]
  roles: string[]
  atAll: boolean
  mentionMode: string
  keyword?: string
}
export interface PreviewPart {
  kind: 'heading' | 'markdown' | 'plain'
  text: string
}
export interface PreviewContent {
  type: string
  source: string
  subject: string
  html?: string
  parts: PreviewPart[]
  mentions: string[]
  atAll: boolean
}
function text(value: unknown): string {
  if (typeof value !== 'string') throw new ApiError('protocol')
  return value
}
function array(value: unknown): unknown[] {
  if (!Array.isArray(value)) throw new ApiError('protocol')
  return value
}
/** Render provider payload fields, never a second hand-written notification template. */
export function parsePreview(value: unknown): PreviewContent {
  const r = record(value),
    p = record(r.payload)
  const type = text(r.type),
    source = text(r.source)
  if (
    !notificationTypes.some((value) => value === type) ||
    !['task', 'job', 'check', 'monitor'].includes(source)
  )
    throw new ApiError('protocol')
  const result: PreviewContent = {
    type,
    source,
    subject: text(r.subject),
    parts: [],
    mentions: [],
    atAll: false,
  }
  const add = (kind: PreviewPart['kind'], value: unknown) =>
    result.parts.push({ kind, text: text(value) })
  if (type === 'EMAIL') result.html = text(p.html)
  else if (type === 'DINGTALK') {
    add('markdown', record(p.markdown).text)
    const at = record(p.at)
    result.atAll = at.isAtAll === true
    result.mentions = array(at.atMobiles ?? []).map(text)
  } else if (type === 'WECOM') {
    if (p.msgtype === 'text') {
      const body = record(p.text)
      add('plain', body.content)
      result.mentions = [...array(body.mentioned_list), ...array(body.mentioned_mobile_list)].map(
        text,
      )
      result.atAll = result.mentions.includes('@all')
      result.mentions = result.mentions.filter((v) => v !== '@all')
    } else if (p.msgtype === 'markdown') add('markdown', record(p.markdown).content)
    else throw new ApiError('protocol')
  } else if (type === 'FEISHU') {
    const card = record(p.card)
    add('heading', record(record(card.header).title).content)
    for (const element of array(card.elements))
      add('markdown', record(record(element).text).content)
  } else if (type === 'SLACK') {
    for (const block of array(p.blocks)) {
      const b = record(block),
        content = record(b.text)
      add(
        b.type === 'header' ? 'heading' : content.type === 'mrkdwn' ? 'markdown' : 'plain',
        content.text,
      )
    }
  } else if (type === 'DISCORD') {
    if (text(p.content)) add('markdown', p.content)
    for (const embed of array(p.embeds)) {
      add('heading', record(embed).title)
      add('markdown', record(embed).description)
    }
  }
  return result
}

function literal(value: string) {
  return value
    .replace(/\\([\\`*_{}[\]()#!|>])/g, '$1')
    .replace(/<@&?([^>]+)>/g, '@$1')
    .replace(/<at id=([^>]+)><\/at>/g, '@$1')
    .replace(/<!(here|channel)>/g, '@$1')
    .replace(
      /&(amp|lt|gt|quot|#39);/g,
      (_, entity: string) => ({ amp: '&', lt: '<', gt: '>', quot: '"', '#39': "'" })[entity]!,
    )
}
/** Only the bounded Markdown emitted by our templates; Vue renders every token as text. */
export function markdownBlocks(value: string) {
  return value
    .trim()
    .split(/\n\s*\n/)
    .filter(Boolean)
    .map((block) => {
      const heading = /^(#{1,6}) +/.exec(block)
      const kind =
        block === '---' ? 'hr' : heading ? 'heading' : block.startsWith('- ') ? 'list' : 'paragraph'
      const content = heading
        ? block.slice(heading[0].length)
        : kind === 'list'
          ? block.slice(2)
          : block
      const tokens = content
        .split(/(?<!\\)\*\*/)
        .map((part, i) => ({ text: literal(part), strong: i % 2 === 1 }))
      return { kind, tokens }
    })
}
