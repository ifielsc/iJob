// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { describe, expect, it } from 'vitest'
import { parseConfig, type ConfigRow } from '../../src/modules/configuration/contracts'
import {
  fileDraft,
  fileSecrets,
  fileBody,
  fileIssues,
  fileTrustValid,
} from '../../src/modules/configuration/file-model'
import {
  transferDraft,
  transferConfig,
  transferIssues,
} from '../../src/modules/tasks/file-transfer-model'
import { draftFor, issues, taskBody } from '../../src/modules/tasks/model'
import { eventDraft, eventConfig, eventIssues } from '../../src/modules/tasks/event-model'

const file = (): ConfigRow =>
  parseConfig(
    {
      id: '9007199254740993',
      name: 'Files',
      code: 'files',
      type: 'FILE',
      enabled: true,
      config: {
        protocol: 'SFTP',
        host: 'files.example.invalid',
        username: 'fixture',
        port: 22,
        basePath: '/',
        timeout: 30000,
        pathStyleAccess: false,
        hostKeyVerification: 'MANAGED',
        hostKeyFingerprint: 'SHA256:' + 'a'.repeat(43),
        hostKeyType: 'ssh-ed25519',
        passwordConfigured: true,
        privateKeyConfigured: false,
        passphraseConfigured: false,
        accessKeyConfigured: false,
        secretKeyConfigured: false,
        sessionTokenConfigured: false,
      },
    },
    true,
  )
describe('FILE resources and tasks', () => {
  it('validates and saves only the selected new SFTP authentication while keeping drafts', () => {
    const d = fileDraft(),
      s = fileSecrets()
    Object.assign(d, {
      protocol: 'SFTP',
      name: 'New',
      host: 'example.invalid',
      username: 'test',
      basePath: '/',
    })
    s.password.value = 'password-draft'
    d.auth = 'KEY'
    expect(fileIssues(d, s, false).privateKey).toBe('sshAuthRequired')
    s.privateKey.value = '/keys/id_ed25519'
    s.passphrase.value = 'key-passphrase'
    expect(fileIssues(d, s, false)).toEqual({})
    const keyConfig = fileBody(d, s, d.code, false).fileConfig
    expect(keyConfig.privateKey).toBe('/keys/id_ed25519')
    expect(keyConfig).not.toHaveProperty('password')
    d.auth = 'PASSWORD'
    s.privateKey.value = 'x'.repeat(65536)
    expect(fileIssues(d, s, false)).toEqual({})
    const passwordConfig = fileBody(d, s, d.code, false).fileConfig
    expect(passwordConfig.password).toBe('password-draft')
    expect(passwordConfig).not.toHaveProperty('privateKey')
    expect(passwordConfig).not.toHaveProperty('passphrase')
  })
  it('preserves authentication and managed identity on an ordinary edit', () => {
    const row = file(),
      d = fileDraft(row),
      s = fileSecrets(row)
    expect(fileTrustValid(d)).toBe(true)
    expect(fileIssues(d, s)).toEqual({})
    d.name = 'Renamed'
    const c = fileBody(d, s, d.code, true).fileConfig
    expect(c.password).toBeUndefined()
    expect(c.hostKey).toBeUndefined()
    expect(c.basePath).toBe('/')
    expect(c.preserveExisting).toBe(true)
    d.port = 2222
    expect(fileTrustValid(d)).toBe(false)
  })
  it('requires a known_hosts file only in file verification mode', () => {
    const row = file(),
      d = fileDraft(row),
      s = fileSecrets(row)
    expect(fileIssues(d, s).knownHosts).toBeUndefined()
    d.hostKeyVerification = 'KNOWN_HOSTS'
    expect(fileIssues(d, s).knownHosts).toBe('required')
    d.knownHosts = '/etc/known_hosts'
    expect(fileIssues(d, s)).toEqual({})
  })
  it('serializes only protocol fields and validates credential removal', () => {
    const row = file(),
      d = fileDraft(row),
      s = fileSecrets(row)
    s.password.mode = 'clear'
    expect(fileIssues(d, s).password).toBe('sshAuthRequired')
    d.protocol = 'LOCAL'
    expect(fileIssues(d, s)).toEqual({})
    const c = fileBody(d, s, d.code, true).fileConfig
    expect(c).not.toHaveProperty('host')
    expect(c).not.toHaveProperty('password')
    expect(c).not.toHaveProperty('hostKeyVerification')
    d.protocol = 'S3_COMPATIBLE'
    d.region = 'us-east-1'
    d.bucket = 'fixture-bucket'
    s.accessKey = { mode: 'replace', value: 'a', configured: false }
    s.secretKey = { mode: 'replace', value: 'b', configured: false }
    expect(fileIssues(d, s)).toEqual({})
    expect(fileBody(d, s, d.code, false).fileConfig.accessKey).toBe('a')
  })
  it('preserves long IDs and unknown precision while applying active transfer options', () => {
    const d = transferDraft(
      '{"sourceResourceId":9223372036854775807,"targetResourceId":9007199254740993,"sourcePath":"/","extra":0.1234567890123456789}',
    )
    expect(d.blocked).toBe(false)
    expect(transferIssues(d)).toEqual({})
    expect(transferConfig(d)).toContain('"sourceResourceId":9223372036854775807')
    expect(transferConfig(d)).toContain('"extra":0.1234567890123456789')
    d.mode = 'READ_ONLY'
    d.deleteSource = true
    d.archiveName = 'draft.zip'
    d.compressionLevel = 'BEST'
    const c = JSON.parse(transferConfig(d))
    expect(c.targetResourceId).toBeUndefined()
    expect(c.deleteSource).toBe(false)
    expect(c.archiveName).toBeUndefined()
    expect(c.compressionLevel).toBe('DEFAULT')
    expect(d.archiveName).toBe('draft.zip')
  })
  it.each([
    '{"mode":"WRITE_ONLY"}',
    '{"compressType":"UNKNOWN"}',
    '{"sourceResourceId":1.5}',
    '{"sourceResourceId":9223372036854775808}',
    '[]',
  ])('retains unsupported transfer config %s', (raw) => {
    const d = transferDraft(raw)
    expect(d.blocked).toBe(true)
    expect(d.original).toBe(raw)
    expect(() => transferConfig(d)).toThrow()
  })
  it('validates limits, filename-only glob and delete conditions', () => {
    const d = transferDraft('{"sourceResourceId":1,"targetResourceId":1}')
    d.deleteSource = true
    d.limits.maxSourceTotalBytes = 1
    d.filePattern = 'path/*.csv'
    const e = transferIssues(d)
    expect(e['transfer.deleteSource']).toBeDefined()
    expect(e['transfer.maxSourceTotalBytes']).toBeDefined()
    expect(e['transfer.filePattern']).toBeDefined()
    d.mode = 'READ_ONLY'
    d.sourcePath = ''
    expect(transferIssues(d)['transfer.sourcePath']).toBeUndefined()
  })
  it('publishes file transfer with no SQL requirements', () => {
    const d = draftFor()
    d.pluginType = 'FILE_TRANSFER'
    d.name = 'Transfer'
    Object.assign(d.transfer, { sourceResourceId: '1', targetResourceId: '2' })
    expect(issues(d)).toEqual({})
    expect(JSON.parse(taskBody(d).pluginConfig).sourcePath).toBeNull()
  })
  it('probe keeps exact references and generated metadata semantics', () => {
    const d = eventDraft(
      '{"action":"PROBE_FILE","fileResourceId":9223372036854775807,"filePath":"/","eventCode":"file.ready","extension":0.1234567890123456789}',
    )
    expect(d.blocked).toBe(false)
    expect(eventIssues(d)).toEqual({})
    const saved = eventConfig(d)
    expect(saved).toContain('"fileResourceId":9223372036854775807')
    expect(saved).toContain('"extension":0.1234567890123456789')
    expect(saved).not.toContain('"payload"')
    d.drafts.PROBE_FILE.filePath = ''
    expect(eventIssues(d)['event.filePath']).toBeDefined()
    d.drafts.PROBE_FILE.filePath = '/'
    d.drafts.PROBE_FILE.maxMatches = 1001
    expect(eventIssues(d)['event.maxMatches']).toBeDefined()
  })
  it('probe supports task execution timeout while waits reject it', () => {
    const d = draftFor()
    d.name = 'Probe'
    d.pluginType = 'EVENT'
    d.event.action = 'PROBE_FILE'
    d.timeout = 60
    Object.assign(d.event.drafts.PROBE_FILE, { fileResourceId: '1', eventCode: 'file.ready' })
    expect(issues(d)).toEqual({})
  })
})
