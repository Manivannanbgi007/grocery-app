import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/** Answers API calls inside the browser (a service worker); started by main.tsx when VITE_API_MOCKING=true. */
export const worker = setupWorker(...handlers)
