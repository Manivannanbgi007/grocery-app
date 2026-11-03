import { defineConfig } from '@playwright/test'

/**
 * Smoke tests in a real browser against the REAL backend (npm run e2e).
 * Before running: start the infrastructure and the services the storefront uses (see README.md "End-to-end tests").
 *
 * Playwright starts its own dev server on port 5175 (mocks off), so it never disturbs `npm run dev` on 5173.
 * The browser is the installed Microsoft Edge; to use Google Chrome set PLAYWRIGHT_CHANNEL=chrome.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // The flows change the carts of the seed customers, so they run one after the other
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5175',
    channel: process.env.PLAYWRIGHT_CHANNEL ?? 'msedge',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npx vite --port 5175 --strictPort',
    url: 'http://localhost:5175',
    reuseExistingServer: false,
    timeout: 60_000,
  },
})
