import type { Page } from '@playwright/test'
export async function setupPush(page: Page, permission = true, failure = '') {
  const resources = [
    {
      id: '9007199254740993',
      name: 'Orders DB',
      code: 'db',
      type: 'DB',
      enabled: true,
      config: {
        dbType: 'MYSQL',
        jdbcUrl: 'jdbc:mysql://db.invalid/orders',
        username: 'reader',
        jdbcUrlConfigured: true,
        usernameConfigured: true,
        passwordConfigured: true,
      },
    },
    {
      id: '9223372036854775807',
      name: 'Orders Kafka',
      code: 'kafka',
      type: 'KAFKA',
      enabled: true,
      config: {
        bootstrapServers: 'kafka.invalid:9092',
        acks: 'all',
        bootstrapServersConfigured: true,
      },
    },
    {
      id: '23',
      name: 'Orders Rabbit',
      code: 'rabbit',
      type: 'RABBITMQ',
      enabled: true,
      config: {
        host: 'rabbit.invalid',
        port: 5672,
        username: 'publisher',
        virtualHost: '/',
        publisherConfirm: true,
        passwordConfigured: true,
        exchange: 'legacy',
        routingKey: 'legacy.route',
      },
    },
  ]
  const tasks = ['KAFKA', 'RABBITMQ'].map((type, i) => ({
    id: i ? '9007199254740995' : '9007199254740993',
    name: type + ' push',
    pluginType: 'PUSH',
    enabled: true,
    currentRevision: '2',
    currentTaskDefinitionId: '9007199254740994',
    groups: [],
    retryTimes: 0,
    retryInterval: 0,
    timeout: null,
    description: '',
    pluginConfig:
      '{"sourceResourceId":9007199254740993,"sql":"SELECT order_id, status FROM orders","extra":0.1234567890123456789,"target":{"type":"' +
      type +
      '","resourceId":' +
      (i ? '23' : '9223372036854775807') +
      ',"extra":9007199254740993,"properties":' +
      (i ? '{"exchange":"","routingKey":"orders.ready"}' : '{"topic":"orders.ready"}') +
      '}}',
  }))
  const writes: { path: string; body: Record<string, unknown> }[] = [],
    tests: string[] = []
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname
      const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 200, data } })
      if (path === '/api/reference-summaries') return ok([])
      if (path === '/api/auth/me')
        return ok({
          loginId: 'push-admin',
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
      if (path.endsWith('/connection-test')) {
        tests.push(path)
        return route.fulfill({
          status: 409,
          json: { success: false, code: 409, message: 'Connection failed' },
        })
      }
      if (req.method() !== 'GET') {
        writes.push({ path, body: req.postDataJSON() })
        if (failure === 'network') return route.abort()
        if (failure === 'invalid')
          return route.fulfill({ status: 400, json: { success: false, code: 400 } })
        return ok(
          path.startsWith('/api/tasks')
            ? { taskId: tasks[0]!.id, definitionId: '9007199254740997', revision: '3' }
            : '24',
        )
      }
      if (path === '/api/tasks/groups') return ok({ list: [], total: 0 })
      if (path === '/api/tasks') return ok({ list: tasks, total: tasks.length })
      const task = tasks.find((t) => path === '/api/tasks/' + t.id)
      if (task) return ok(task)
      if (path === '/api/resources') {
        const rows = resources.filter(
          (r) =>
            r.type === url.searchParams.get('type') &&
            r.name.toLowerCase().includes((url.searchParams.get('name') ?? '').toLowerCase()),
        )
        return ok({ list: rows, total: rows.length, pageNum: 1, pageSize: 20, pages: 1 })
      }
      const resource = resources.find((r) => path === '/api/resources/' + r.id)
      if (resource) return ok(resource)
      return route.fulfill({ status: 404, json: { success: false, code: 404 } })
    },
  )
  return { writes, tests, tasks, resources }
}
