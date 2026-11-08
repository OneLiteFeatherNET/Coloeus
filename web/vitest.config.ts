import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// Unit tests only: no Nuxt runtime, no network, no backend, no Dragonfly, no system clock. The BFF core runs in the
// plain Node environment; the few component tests opt into happy-dom with a file comment.
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '~': fileURLToPath(new URL('./app', import.meta.url)),
      '#shared': fileURLToPath(new URL('./shared', import.meta.url))
    }
  },
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.spec.ts'],
    // Random order and parallel files must not change results (F.I.R.S.T.).
    sequence: { shuffle: true },
    restoreMocks: true,
    clearMocks: true
  }
})
