// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { Page } from '@playwright/test'
export async function setupFiles(page: Page, permission = true, failure = '') {
  const base = {
    enabled: true,
    currentRevision: '2',
    currentTaskDefinitionId: '9007199254740994',
    groups: [],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
  }
  const tasks = [
    {
      ...base,
      id: '9007199254740993',
      name: 'Transfer fixture',
      pluginType: 'FILE_TRANSFER',
      pluginConfig:
        '{"sourceResourceId":9007199254740993,"targetResourceId":9223372036854775807,"sourcePath":"/","filePattern":"*.csv","extra":0.1234567890123456789}',
    },
    {
      ...base,
      id: '9007199254740995',
      name: 'Probe fixture',
      pluginType: 'EVENT',
      pluginConfig:
        '{"action":"PROBE_FILE","fileResourceId":9007199254740993,"filePath":"/","eventCode":"file.ready","extra":9223372036854775807}',
    },
  ]
  const configs = {
    LOCAL: { protocol: 'LOCAL', basePath: 'E:/fixture', timeout: 30000, pathStyleAccess: false },
    FTP: {
      protocol: 'FTP',
      basePath: '/',
      host: 'files.example.invalid',
      port: 21,
      username: 'fixture',
      timeout: 30000,
      pathStyleAccess: false,
    },
    SFTP: {
      protocol: 'SFTP',
      basePath: '/',
      host: 'files.example.invalid',
      port: 22,
      username: 'fixture',
      timeout: 30000,
      pathStyleAccess: false,
      hostKeyVerification: 'MANAGED',
      hostKeyFingerprint: 'SHA256:' + 'A'.repeat(43),
      hostKeyType: 'ssh-ed25519',
    },
    S3_COMPATIBLE: {
      protocol: 'S3_COMPATIBLE',
      basePath: '/',
      endpoint: 'http://localhost:9000',
      region: 'us-east-1',
      bucket: 'fixture-bucket',
      timeout: 30000,
      pathStyleAccess: true,
    },
  }
  const resources = Object.entries(configs).map(([protocol, c], index) => ({
    id: index === 0 ? '9007199254740993' : index === 1 ? '9223372036854775807' : String(index + 10),
    name: protocol + ' fixture',
    code: 'file-' + index,
    type: 'FILE',
    enabled: true,
    config: {
      hostConfigured: false,
      usernameConfigured: false,
      knownHostsConfigured: false,
      basePathConfigured: true,
      endpointConfigured: false,
      bucketConfigured: false,
      passwordConfigured: protocol === 'FTP' || protocol === 'SFTP',
      privateKeyConfigured: false,
      passphraseConfigured: false,
      accessKeyConfigured: protocol === 'S3_COMPATIBLE',
      secretKeyConfigured: protocol === 'S3_COMPATIBLE',
      sessionTokenConfigured: false,
      ...c,
    },
  }))
  const writes: { path: string; body: Record<string, unknown> }[] = [],
    probes: unknown[] = []
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/auth/me')
        return ok({
          loginId: 'file-admin',
          username: 'admin',
          displayName: 'Admin',
          roleCode: permission ? 'ADMIN' : 'VIEWER',
          permissions: permission
            ? [
                'SUMMARY_READ',
                'DEFINITION_READ',
                'SENSITIVE_READ',
                'CONFIGURATION_WRITE',
                'EXECUTION_CONTROL',
              ]
            : ['SUMMARY_READ', 'DEFINITION_READ', 'SENSITIVE_READ'],
          mustChangePassword: false,
        })
      if (path === '/api/resources/ssh/host-key') {
        probes.push(req.postDataJSON())
        return ok({
          host: req.postDataJSON().host,
          port: req.postDataJSON().port,
          keyType: 'ssh-ed25519',
          fingerprint: 'SHA256:' + 'B'.repeat(43),
          publicKey: 'fixture-public-key',
        })
      }
      if (req.method() !== 'GET') {
        writes.push({ path, body: req.postDataJSON() })
        if (failure === 'network') return route.abort()
        if (failure === 'conflict')
          return route.fulfill({ status: 409, json: { success: false, code: 409 } })
        return ok(
          path.startsWith('/api/tasks')
            ? { taskId: tasks[0]!.id, definitionId: '9007199254740997', revision: '3' }
            : '1',
        )
      }
      if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
      if (path === '/api/tasks') return ok({ list: tasks, total: tasks.length })
      const task = tasks.find((t) => path === '/api/tasks/' + t.id)
      if (task) return ok(task)
      if (path === '/api/resources')
        return ok({
          list: resources.filter((r) =>
            r.name.toLowerCase().includes((url.searchParams.get('name') ?? '').toLowerCase()),
          ),
          total: resources.length,
          pageNum: 1,
          pageSize: 20,
          pages: 1,
        })
      const resource = resources.find((r) => path === '/api/resources/' + r.id)
      if (resource) return ok(resource)
      return route.fulfill({ status: 404, json: { success: false, code: 404 } })
    },
  )
  return { writes, probes, tasks, resources }
}
