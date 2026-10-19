// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: 'parameters.spec.ts',
  workers: 2,
  reporter: [['list']],
  outputDir: 'test-results-parameters',
  use: { baseURL: 'http://127.0.0.1:5189', trace: 'retain-on-failure' },
  webServer: {
    command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5189 --strictPort',
    url: 'http://127.0.0.1:5189',
    reuseExistingServer: false,
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
})
