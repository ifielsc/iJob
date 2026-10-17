import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
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
