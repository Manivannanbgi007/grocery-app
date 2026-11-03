import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter } from 'react-router'
// The DOM version of RouterProvider: it can put a page on screen at once (navigate's flushSync, used by the logout)
import { RouterProvider } from 'react-router/dom'
import { Toaster } from 'sonner'
import { createQueryClient } from './api/queryClient'
import { LoadingIndicator } from './components/layout/LoadingIndicator'
import { AuthProvider } from './features/auth/AuthProvider'
import { ThemeSwitcher } from './features/theme/ThemeSwitcher'
import { useTheme } from './features/theme/useTheme'
import { routes } from './routes'

const queryClient = createQueryClient()
const router = createBrowserRouter(routes)

/**
 * The providers every page needs: server data (React Query), the login (AuthProvider, which uses React Query to
 * forget the cart on logout), the pages (router) and pop-up messages (toasts). The theme switch and the "Loading…" sign
 * are on every page too.
 */
export default function App() {
  const [theme, setTheme] = useTheme()

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
      {/* Always dark (black with white text), in both themes, so a toast stands out from the page;
          the icon shows the kind of message (green, red, yellow, blue: "Toasts" in index.css) */}
      <Toaster position="top-right" theme="dark" closeButton />
      <ThemeSwitcher choice={theme} onChange={setTheme} />
      <LoadingIndicator router={router} />
    </QueryClientProvider>
  )
}
