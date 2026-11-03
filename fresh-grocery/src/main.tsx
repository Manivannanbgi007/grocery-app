// Fraunces: one variable file with every weight and the optical size axis (titles, see styles/index.css)
import '@fontsource-variable/fraunces/opsz.css'
import '@fontsource/lato/400.css'
import '@fontsource/lato/700.css'
import '@fontsource/quicksand/600.css'
import '@fontsource/quicksand/700.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles/index.css'

/**
 * With VITE_API_MOCKING=true (see .env.development) API calls are answered in the browser with the demo
 * catalog, so the app works without the backend. The mock code is only downloaded in that case.
 */
async function startMockApi() {
  if (import.meta.env.VITE_API_MOCKING !== 'true') return
  const { worker } = await import('./mocks/browser')
  // 'bypass': requests that are not mocked (fonts, images ...) go to the network as usual
  await worker.start({ onUnhandledRequest: 'bypass' })
}

void startMockApi().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
