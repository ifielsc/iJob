// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { bundleLicenses } from './tools/bundle-licenses'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue(), bundleLicenses()],
    server: {
      port: 5173,
      strictPort: true,
      // No automatic connection to a local main database-backed service.
      proxy: env.IJOB_DEV_API_TARGET
        ? { '/api': { target: env.IJOB_DEV_API_TARGET, changeOrigin: false } }
        : undefined,
    },
    build: { sourcemap: false, manifest: true },
  }
})
