// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/production',
  outputDir: './test-results-production',
  use: { baseURL: 'http://127.0.0.1:5175', trace: 'retain-on-failure' },
  webServer: {
    command: 'pnpm preview --port 5175',
    url: 'http://127.0.0.1:5175',
    reuseExistingServer: false,
  },
  projects: [{ name: 'chromium-production', use: { browserName: 'chromium' } }],
})
