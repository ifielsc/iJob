// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: { environment: 'happy-dom', include: ['tests/unit/**/*.test.ts'], restoreMocks: true },
})
