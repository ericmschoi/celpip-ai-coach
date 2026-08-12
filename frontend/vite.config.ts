import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const BACKEND = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

const backendProxy = {
  '/api': { target: BACKEND, changeOrigin: true },
  '/media': { target: BACKEND, changeOrigin: true },
};

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Same-origin during local dev, so no CORS preflight and no absolute URLs
  // baked into the bundle. `/media` carries locally stored audio.
  server: {
    port: 5173,
    proxy: backendProxy,
  },
  preview: {
    port: 4173,
    proxy: backendProxy,
  },
  build: {
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.{test,spec}.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
    },
  },
});
