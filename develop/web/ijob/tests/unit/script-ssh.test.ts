// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { expect, it } from 'vitest'
import { draftFor, issues, taskBody, taskRow } from '../../src/modules/tasks/model'
import {
  sshDraft,
  sshSecrets,
  sshIssues,
  sshBody,
  sshTrustValid,
} from '../../src/modules/configuration/ssh-model'

it('isolates SQL, local and SSH drafts and only publishes active script fields', () => {
  const d = draftFor()
  Object.assign(d, { pluginType: 'SCRIPT', name: 'script', sshResourceId: '9223372036854775807' })
  d.local.script = 'local code'
  d.local.engine = 'POWERSHELL'
  d.remote.script = 'remote code'
  d.remote.engine = 'PYTHON'
  d.remote.env = [{ name: 'MODE', value: 'test' }]
  expect(issues(d)).toEqual({})
  expect(JSON.parse(taskBody(d).pluginConfig)).toMatchObject({
    script: 'local code',
    engine: 'POWERSHELL',
  })
  expect(JSON.parse(taskBody(d).pluginConfig)).not.toHaveProperty('sshResourceId')
  d.executionMode = 'SSH'
  expect(issues(d)).toEqual({})
  const config = JSON.parse(taskBody(d).pluginConfig)
  expect(config).toMatchObject({
    script: 'remote code',
    engine: 'PYTHON',
    interpreter: 'python3',
    sshResourceId: '9223372036854775807',
    env: { MODE: 'test' },
  })
  expect(config).not.toHaveProperty('resourceId')
  expect(config).not.toHaveProperty('transaction')
  d.executionMode = 'LOCAL'
  expect(d.local.script).toBe('local code')
})

it('normalizes legacy charset and preserves unknown properties without retaining stale mode fields', () => {
  const row = taskRow(
    {
      id: '1',
      name: 'legacy',
      pluginType: 'SCRIPT',
      enabled: true,
      groups: [],
      currentRevision: '2',
      currentTaskDefinitionId: '3',
      retryTimes: 0,
      retryInterval: 0,
      pluginConfig: '{"engine":"SHELL","script":"echo old","charset":"GBK","custom":false}',
    },
    true,
  )
  const d = draftFor(row)
  expect(d.local.scriptCharset).toBe('GBK')
  expect(d.local.outputCharset).toBe('GBK')
  const saved = JSON.parse(taskBody(d, row).pluginConfig)
  expect(saved.custom).toBe(false)
  expect(saved).not.toHaveProperty('charset')
})

it('rejects invalid remote engine, paths, duplicate environment and UTF-8 payload overflow', () => {
  const d = draftFor()
  Object.assign(d, {
    pluginType: 'SCRIPT',
    name: 'remote',
    executionMode: 'SSH',
    sshResourceId: '1',
  })
  Object.assign(d.remote, {
    engine: 'BAT',
    script: 'echo ok',
    workingDir: 'relative',
    env: [
      { name: 'A', value: '' },
      { name: 'A', value: 'x' },
      { name: '0A', value: '\0' },
    ],
  })
  expect(issues(d)).toMatchObject({
    engine: 'invalid',
    workingDir: 'sshDirectoryInvalid',
    'env-name-1': 'envNameInvalid',
    'env-name-2': 'envNameInvalid',
    'env-value-2': 'invalid',
  })
  d.remote.script = '汉'.repeat(3 * 1024 * 1024)
  expect(issues(d).script).toBe('configTooLarge')
})

it('requires valid SSH credentials and never sends hidden fields from the other authentication mode', () => {
  const d = sshDraft(),
    s = sshSecrets()
  Object.assign(d, {
    name: 'ssh',
    host: 'localhost',
    username: 'runner',
    knownHosts: '/keys/known_hosts',
  })
  expect(sshIssues(d, s)).toMatchObject({ password: 'sshAuthRequired' })
  s.password.value = 'password'
  s.privateKey.value = '/keys/old'
  expect(sshIssues(d, s)).toEqual({})
  expect(sshBody(d, s, 'generated', false).sshConfig).not.toHaveProperty('privateKey')
  d.auth = 'KEY'
  const body = sshBody(d, s, 'generated', false)
  expect(body.code).toBe(d.code)
  expect(body.sshConfig).toHaveProperty('privateKey', '/keys/old')
  expect(body.sshConfig).not.toHaveProperty('password')
})

it('preserves both saved credential types, and enforces explicit clear dependencies', () => {
  const d = sshDraft(),
    s = sshSecrets()
  Object.assign(d, {
    name: 'ssh',
    host: 'localhost',
    username: 'runner',
    knownHosts: '/known_hosts',
    auth: 'BOTH',
  })
  for (const f of Object.values(s)) Object.assign(f, { configured: true, mode: 'keep' })
  expect(sshIssues(d, s, true)).toEqual({})
  expect(sshBody(d, s, 'generated', true).sshConfig).toMatchObject({
    preserveExisting: true,
    password: undefined,
    privateKey: undefined,
    passphrase: undefined,
  })
  s.privateKey.mode = 'clear'
  expect(sshIssues(d, s, true)).toHaveProperty('passphrase', 'sshPassphraseRequiresKey')
  s.passphrase.mode = 'clear'
  expect(sshIssues(d, s, true)).toEqual({})
  s.password.mode = 'clear'
  expect(sshIssues(d, s, true)).toHaveProperty('password', 'sshAuthRequired')
})

it('binds managed trust to host and port, and requires a file only in manual mode', () => {
  const d = sshDraft(),
    s = sshSecrets()
  Object.assign(d, { name: 'ssh', host: 'runner', username: 'user' })
  s.password.value = 'password'
  expect(d.hostKeyVerification).toBe('MANAGED')
  expect(sshIssues(d, s)).toEqual({})
  Object.assign(d, {
    hostKey: 'public-key',
    fingerprint: 'SHA256:test',
    trustedHost: 'runner',
    trustedPort: 22,
  })
  expect(sshTrustValid(d)).toBe(true)
  expect(sshBody(d, s, d.code, false).sshConfig.hostKey).toBe('public-key')
  d.port = 2222
  expect(sshTrustValid(d)).toBe(false)
  expect(sshBody(d, s, d.code, false).sshConfig.hostKey).toBeUndefined()
  d.hostKeyVerification = 'KNOWN_HOSTS'
  expect(sshIssues(d, s).knownHosts).toBe('required')
  d.knownHosts = '/etc/ijob/known_hosts'
  expect(sshIssues(d, s)).toEqual({})
  expect(sshBody(d, s, d.code, false).sshConfig.hostKey).toBeUndefined()
})
