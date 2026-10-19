// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, it, expect } from 'vitest'
import {
  monitorDraft,
  monitorConfig,
  monitorIssues,
  monitorApplication,
  monitorThreshold,
  monitorPath,
} from '../../src/modules/tasks/monitor-model'
import {
  monitorResourceDraft,
  monitorResourceBody,
  monitorResourceIssues,
} from '../../src/modules/configuration/monitor-resource-model'
import { parseConfig } from '../../src/modules/configuration/contracts'
const config =
  '{"resourceId":9223372036854775807,"collect":{"items":["CPU"],"future":9007199254740993},"rules":{"cpu":{"usage":{"warning":80.1234567890123456789,"critical":90,"future":0.1234567890123456789}}},"future":9007199254740993}'
describe('monitor contracts', () => {
  it('preserves disabled network thresholds across unrelated edits and repeated saves', () => {
    const original = config.replace(
      '"rules":{',
      '"rules":{"network":{"enabled":false,"receiveRate":{"warning":0,"critical":9007199254740993,"future":0.1234567890123456789}},',
    )
    for (const collected of [false, true]) {
      const d = monitorDraft(original)
      if (collected) d.items.push('NETWORK')
      expect(monitorIssues(d)).toEqual({})
      const out = monitorConfig(monitorDraft(monitorConfig(d)))
      expect(out).toContain(
        '"network":{"enabled":false,"receiveRate":{"warning":0,"critical":9007199254740993,"future":0.1234567890123456789}}',
      )
    }
  })

  it('can disable one network direction while the other remains active', () => {
    const d = monitorDraft(
      config.replace(
        '"rules":{',
        '"rules":{"network":{"enabled":true,"receiveRate":{"critical":100},"sendRate":{"critical":200}},',
      ),
    )
    d.items.push('NETWORK')
    d.metrics.receiveRate.enabled = false
    const output = JSON.parse(monitorConfig(d))
    expect(output.rules.network.enabled).toBe(true)
    expect(output.rules.network.receiveRate).toBeNull()
    expect(output.rules.network.sendRate.critical).toBe(200)
  })

  it('preserves exact IDs, decimals and unknown properties', () => {
    const d = monitorDraft(config)
    expect(d.blocked).toBe(false)
    expect(monitorIssues(d)).toEqual({})
    const out = monitorConfig(d)
    expect(out).toContain('80.1234567890123456789')
    expect(out).toContain('0.1234567890123456789')
    expect(out).toContain('"resourceId":"9223372036854775807"')
    expect(out).toContain('"future":9007199254740993')
  })
  it('validates only effective thresholds without losing dormant drafts', () => {
    const d = monitorDraft(config)
    d.items.push('MEMORY')
    d.metrics.memory.enabled = true
    d.metrics.memory.warning = 'bad'
    expect(monitorIssues(d)['monitor.metrics.memory.warning']).toBe('invalid')
    d.metrics.memory.enabled = false
    expect(monitorIssues(d)).toEqual({})
    expect(monitorConfig(d)).not.toContain('bad')
    expect(d.metrics.memory.warning).toBe('bad')
  })
  it('checks percentage limits and warning order', () => {
    const d = monitorDraft(config)
    d.metrics.cpu.warning = '101'
    expect(monitorIssues(d)['monitor.metrics.cpu.warning']).toBe('invalid')
    expect(monitorIssues(d)['monitor.metrics.cpu.critical']).toBe('thresholdOrder')
  })
  it('requires an enabled effective rule', () => {
    const d = monitorDraft(config)
    d.metrics.cpu.enabled = false
    expect(monitorIssues(d)['monitor.items']).toBe('ruleRequired')
  })
  it('normalizes mount path duplicates across Windows and UNC', () => {
    expect(monitorPath('c:\\Data\\')).toBe('C:/data')
    expect(monitorPath('///Host//Share///')).toBe('//host/share')
    expect(monitorPath('//')).toBe('//')
    const d = monitorDraft(config)
    d.items.push('DISK')
    d.metrics.disk.enabled = true
    d.metrics.disk.warning = '80'
    d.paths = [
      { path: 'c:\\Data', threshold: { ...monitorThreshold(), warning: '80' } },
      { path: 'C:/data/', threshold: { ...monitorThreshold(), warning: '80' } },
    ]
    expect(monitorIssues(d)['monitor.paths.1.path']).toBe('invalid')
  })
  it('requires unique application names and matching checks', () => {
    const d = monitorDraft(config)
    d.items.push('APPLICATION')
    const a = monitorApplication()
    Object.assign(a, { name: 'svc', matchValue: 'svc', required: false })
    d.applications = [a, { ...a }]
    const e = monitorIssues(d)
    expect(e['monitor.applications.0.enabled']).toBe('effectiveCheck')
    expect(e['monitor.applications.1.name']).toBe('invalid')
  })
  it('keeps applications in the draft while excluding uncollected applications', () => {
    const d = monitorDraft(config)
    const a = monitorApplication()
    a.name = 'svc'
    a.matchValue = 'svc'
    d.applications = [a]
    expect(JSON.parse(monitorConfig(d)).collect.applications).toEqual([])
    expect(d.applications).toHaveLength(1)
  })
  it.each([
    '{"resourceId":1,"collect":{"items":["FUTURE"]},"rules":{}}',
    config.replace('"rules":', '"notify":{"recovery":true},"rules":'),
    config.replace('"warning":80.1234567890123456789', '"warning":"80"'),
  ])('blocks unsupported shape without resaving it', (json) => {
    const d = monitorDraft(json)
    expect(d.blocked).toBe(true)
    expect(() => monitorConfig(d)).toThrow()
  })
  it('validates PID and enabled notification references', () => {
    const d = monitorDraft(config)
    d.items.push('APPLICATION')
    const a = monitorApplication()
    Object.assign(a, { name: 'svc', matchType: 'PID', matchValue: '2147483648' })
    d.applications = [a]
    d.notify = true
    d.notifyConfigId = '9223372036854775808'
    expect(monitorIssues(d)).toHaveProperty('monitor.applications.0.matchValue')
    expect(monitorIssues(d)).toHaveProperty('monitor.notifyConfigId')
  })
})
describe('monitor resource secrets', () => {
  const row = () =>
    parseConfig(
      {
        id: '1',
        name: 'Agent',
        code: 'agent',
        type: 'MONITOR_SERVER',
        enabled: true,
        config: {
          protocol: 'HTTPS',
          endpoint: 'https://agent.invalid/base',
          connectTimeout: 5000,
          readTimeout: 10000,
          tokenConfigured: true,
          headerNames: ['X-Key'],
          osType: 'LINUX',
          agentVersion: '1',
        },
      },
      true,
    )
  it('keeps hidden token and headers while clearing optional metadata', () => {
    const d = monitorResourceDraft(row())
    d.osType = null
    d.agentVersion = ''
    const c = monitorResourceBody(d).monitorConfig
    expect(c.token).toBeUndefined()
    expect(c.clearToken).toBe(false)
    expect(c.preservedHeaderNames).toEqual(['X-Key'])
    expect(c.osType).toBeNull()
    expect(c.agentVersion).toBeNull()
    expect(monitorResourceIssues(d)).toEqual({})
  })
  it('serializes explicit replacement and clearing', () => {
    const d = monitorResourceDraft(row())
    d.token.mode = 'clear'
    d.headers = []
    expect(monitorResourceBody(d).monitorConfig).toMatchObject({
      clearToken: true,
      preservedHeaderNames: [],
      headers: {},
    })
    d.token.mode = 'replace'
    d.token.value = 'replacement'
    expect(monitorResourceBody(d).monitorConfig.token).toBe('replacement')
  })
  it.each([
    'https://user:pass@agent.invalid',
    'https://agent.invalid?key=x',
    'https://agent.invalid#x',
    'http://agent.invalid',
  ])('rejects unsupported endpoints', (endpoint) => {
    const d = monitorResourceDraft(row())
    d.endpoint = endpoint
    expect(monitorResourceIssues(d).endpoint).toBe('invalid')
  })
  it('rejects missing safe detail fields', () => {
    expect(() => parseConfig({ ...row(), config: {} }, true)).toThrow()
  })
})
