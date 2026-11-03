import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement, ReactNode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { createQueryClient } from '../api/queryClient'
import { saveSession, sessionFrom } from '../api/session'
import { AuthProvider } from '../features/auth/AuthProvider'
import { allUsers, issueToken, MOCK_TOKEN_SECONDS, startMockLogin } from '../mocks/mockAuth'
import { routes } from '../routes'

/** A fresh cache per test, and no retries, so a failing request fails the test straight away. */
function testQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

/**
 * A fresh cache with the app's own settings (api/queryClient.ts: data fresh for 60 s, nothing asked again when the
 * browser tab is shown again …), still without retries. For tests of WHEN data is asked again: testQueryClient() uses
 * React Query's defaults (stale at once, asked again on focus), which would hide a page that forgets to refresh.
 */
export function appQueryClient(): QueryClient {
  const client = createQueryClient()
  client.setDefaultOptions({ queries: { ...client.getDefaultOptions().queries, retry: false } })
  return client
}

function Providers({ children, client = testQueryClient() }: { children: ReactNode; client?: QueryClient }) {
  return (
    <QueryClientProvider client={client}>
      <AuthProvider>{children}</AuthProvider>
      <Toaster />
    </QueryClientProvider>
  )
}

/**
 * Renders the whole app (layout + pages) at `path`, as if the browser opened that address.
 * `queryClient`: e.g. appQueryClient(), to test with the app's own React Query settings.
 */
export function renderApp(path = '/', queryClient?: QueryClient) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const user = userEvent.setup()
  render(
    <Providers client={queryClient}>
      <RouterProvider router={router} />
    </Providers>,
  )
  return { user, router }
}

/**
 * Renders one component with what it needs (router, React Query, login).
 * `router.state.location` tells where a click or form navigated to.
 */
export function renderWithProviders(ui: ReactElement, path = '/') {
  const router = createMemoryRouter([{ path: '*', element: ui }], { initialEntries: [path] })
  const user = userEvent.setup()
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return { user, router }
}

/**
 * Starts the test logged in, as if the user had logged in earlier: the session is in memory, and the mock's refresh
 * "cookie" holds the login (so a refresh works, like in the browser).
 * Call it before rendering. customer1 = Priya Sharma (id 4), who has the seed cart; vendor1 = Ravi Kumar (id 3, Fresh Mart).
 * Users registered in the mock during the test (addRegisteredUser) work too.
 */
export function logInAs(email = 'customer1@grocery.com') {
  const user = allUsers().find((candidate) => candidate.email === email)
  if (!user) throw new Error(`No demo user ${email}`)
  const { password: _password, ...publicUser } = user
  startMockLogin(user.id)
  saveSession(sessionFrom({ accessToken: issueToken(user), tokenType: 'Bearer', expiresInSeconds: MOCK_TOKEN_SECONDS, user: publicUser }))
  return publicUser
}
