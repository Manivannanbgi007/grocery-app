/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'

/**
 * The proxy's call to the gateway is server-to-server, so the browser's Origin header does not belong in it.
 * Without this, the gateway's CORS check (it only knows http://localhost:5173) rejects POST requests with 403
 * whenever the dev server runs on another port (5174 when 5173 is busy, 5175 for the Playwright tests).
 */
const dropOriginHeader: ProxyOptions['configure'] = (proxy) => {
  proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'))
}

/**
 * The API gateway; another one only for testing, e.g. a copy that routes to new builds of some services before they
 * replace the shared ones: `$env:API_GATEWAY_URL = "http://localhost:8180"; npm run e2e`.
 */
const GATEWAY = process.env.API_GATEWAY_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // During development every "/api/..." call goes to the API gateway.
    // The browser only talks to the dev server, so the app needs no full backend URL.
    proxy: {
      '/api': { target: GATEWAY, configure: dropOriginHeader },
      '/media': { target: GATEWAY, configure: dropOriginHeader },
      // Proof-of-delivery photos: only for logged-in users, so the delivery panel fetches them with the token
      '/delivery-media': { target: GATEWAY, configure: dropOriginHeader },
      // The super admin's System page reads Prometheus (started with the monitoring tools): /prometheus/api/... -> :9090/api/...
      '/prometheus': { target: 'http://localhost:9090', rewrite: (path) => path.replace(/^\/prometheus/, '') },
    },
    fs: {
      // Files outside this folder that may be read: the backend seed files (compared with the demo data in a test)
      allow: ['.', '../product-service/src/main/resources/db/seed', '../inventory-service/src/main/resources/db/seed'],
    },
  },
  test: {
    // Only the unit and component tests; the Playwright tests in e2e/ run with `npm run e2e`
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'jsdom',
    // Page tests click through several screens; 15 s (default 5 s) leaves room on a busy machine
    testTimeout: 15_000,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
})
