import { defineConfig, devices } from '@playwright/test';

/**
 * E2E runs against the production bundle plus a backend in SEED mode, so the
 * suite never spends provider credits.
 */
const PORT = 4173;
const BASE_URL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  timeout: 30_000,
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    video: 'off',
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
  ],
  // Two servers: the backend in SEED mode, and the production bundle served by
  // `vite preview`, which proxies /api and /media to the backend.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : [
        {
          command: '../scripts/run-backend.sh',
          url: 'http://localhost:8080/actuator/health',
          reuseExistingServer: true,
          timeout: 240_000,
          stdout: 'ignore',
          stderr: 'pipe',
        },
        {
          command: `npm run preview -- --port ${PORT} --strictPort`,
          url: BASE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 60_000,
        },
      ],
});
