import { defineConfig } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
// Generated per invocation, inherited by workers and child servers, never persisted in evidence.
process.env.IJOB_TEST_PASSWORD ??= `Test-${randomUUID()}`
// Keep destructive integration fixtures away from the user's live preview on 18086.
process.env.IJOB_TEST_BACKEND_PORT ??= '18087'
process.env.IJOB_TEST_SQL_FIXTURE = 'true'
process.env.IJOB_TEST_DIRECTORY ??= mkdtempSync(join(tmpdir(), 'ijob-frontend-security-'))
const backendUrl = `http://127.0.0.1:${process.env.IJOB_TEST_BACKEND_PORT}`
export default defineConfig({
  globalTeardown: './tools/integration-teardown.mjs',
  testDir: './tests/integration',
  testMatch: 'security.spec.ts',
  outputDir: '../../../test/reports/frontend-security-integration-dev',
  workers: 1,
  fullyParallel: false,
  timeout: 60000,
  reporter: [['list']],
  use: {
    actionTimeout: 15000,
    baseURL: 'http://127.0.0.1:5176',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: [
    {
      command: 'node tools/isolated-backend.mjs',
      stdout: 'pipe',
      url: `${backendUrl}/api/auth/me`,
      timeout: 90000,
      reuseExistingServer: false,
    },
    {
      command: 'pnpm dev --port 5176',
      url: 'http://127.0.0.1:5176',
      reuseExistingServer: false,
      env: { IJOB_DEV_API_TARGET: backendUrl },
    },
  ],
  projects: [{ name: 'isolated-chromium', use: { browserName: 'chromium' } }],
})
