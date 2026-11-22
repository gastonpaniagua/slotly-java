import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  // Tests de componentes (doc 06): simulan un navegador con jsdom.
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Los end-to-end (e2e/) los corre Playwright, no Vitest.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
