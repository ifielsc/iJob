import { describe, expect, it } from 'vitest'
import { markdownBlocks, parsePreview } from '../../src/modules/configuration/notification-preview'

describe('notification payload previews', () => {
  it('renders provider payloads without inventing fields or interpreting plain text as Markdown', () => {
    const result = parsePreview({
      type: 'SLACK',
      source: 'check',
      subject: 'Subject',
      payload: {
        blocks: [
          { type: 'header', text: { type: 'plain_text', text: 'Actual header' } },
          { type: 'section', text: { type: 'plain_text', text: '**literal** &lt;value&gt;' } },
          { type: 'section', text: { type: 'mrkdwn', text: '<!here>' } },
        ],
      },
    })
    expect(result.parts).toEqual([
      { kind: 'heading', text: 'Actual header' },
      { kind: 'plain', text: '**literal** &lt;value&gt;' },
      { kind: 'markdown', text: '<!here>' },
    ])
    expect(() =>
      parsePreview({ type: 'WECOM', source: 'task', subject: '', payload: { msgtype: 'unknown' } }),
    ).toThrow()
  })
  it('keeps escaped template data literal and returns only text tokens for hostile HTML', () => {
    const blocks = markdownBlocks(
      '### Title\n\n**name\\_one**\n\n- Value: &lt;script&gt;alert(1)&lt;/script&gt;\n\n<at id=ou_1></at>',
    )
    expect(blocks[0]!.kind).toBe('heading')
    expect(blocks[1]!.tokens[1]).toEqual({ text: 'name_one', strong: true })
    expect(blocks[2]!.tokens[0]!.text).toBe('Value: <script>alert(1)</script>')
    expect(blocks[3]!.tokens[0]!.text).toBe('@ou_1')
    expect(markdownBlocks('\\*\\*literal\\*\\*')[0]!.tokens).toEqual([
      { text: '**literal**', strong: false },
    ])
  })
  it('preserves actual email HTML and WeCom text mentions', () => {
    expect(
      parsePreview({
        type: 'EMAIL',
        source: 'job',
        subject: 'job',
        payload: { html: '<html>actual</html>' },
      }).html,
    ).toBe('<html>actual</html>')
    const wecom = parsePreview({
      type: 'WECOM',
      source: 'task',
      subject: 'task',
      payload: {
        msgtype: 'text',
        text: {
          content: 'Actual body',
          mentioned_list: ['alice', '@all'],
          mentioned_mobile_list: ['13800138000'],
        },
      },
    })
    expect(wecom.atAll).toBe(true)
    expect(wecom.mentions).toEqual(['alice', '13800138000'])
    expect(wecom.parts).toEqual([{ kind: 'plain', text: 'Actual body' }])
  })
})
