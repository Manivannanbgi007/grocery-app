import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** The same mocks for the tests (Node.js instead of a browser); started in src/test/setup.ts. */
export const server = setupServer(...handlers)
