// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { defineConfig } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
process.env.IJOB_TEST_PASSWORD ??= 'Test-' + randomUUID()
process.env.IJOB_TEST_BACKEND_PORT = '18087'
process.env.IJOB_TEST_SQL_FIXTURE = 'false'
process.env.IJOB_TEST_BATCH_ETL = 'true'
process.env.IJOB_TEST_DIRECTORY ??= mkdtempSync(join(tmpdir(), 'ijob-frontend-batch-etl-'))
export default defineConfig({
  testDir: './tests/integration',
  testMatch: 'batch-etl.spec.ts',
  workers: 1,
  timeout: 90000,
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
      url: 'http://127.0.0.1:18087/api/auth/me',
      timeout: 90000,
      reuseExistingServer: false,
    },
    {
      command: 'pnpm dev --port 5176',
      url: 'http://127.0.0.1:5176',
      reuseExistingServer: false,
      env: { IJOB_DEV_API_TARGET: 'http://127.0.0.1:18087' },
    },
  ],
  projects: [{ name: 'batch-etl-isolated', use: { browserName: 'chromium' } }],
})
