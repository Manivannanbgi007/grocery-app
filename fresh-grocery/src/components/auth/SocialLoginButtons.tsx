import { useQuery } from '@tanstack/react-query'
import type { MouseEvent } from 'react'
import { useNavigate } from 'react-router'
import { queryKeys } from '../../api/queryKeys'
import { getSocialProviders, socialLoginUrl } from '../../api/socialAuth'
import type { UserRole } from '../../api/types'
import type { Panel } from '../../features/auth/authContext'
import { rememberSocialLogin, SIGNUP_ROLE } from '../../features/auth/socialLoginIntent'
import { cn } from '../../utils/cn'
import { buttonClasses } from '../ui/buttonClasses'
import { AuthDivider } from './AuthDivider'
import { SocialLogo } from './SocialIcons'

/**
 * Each provider's button in its own colours (its branding rules; the colours, also for the dark theme, are in
 * styles/index.css), so people recognise "the Google button". The local test provider keeps the shop's outline button.
 */
const BRAND_CLASSES: Record<string, string> = {
  google: 'border-google-border bg-google text-google-text hover:border-google-border hover:bg-google-hover',
  microsoft: 'border-microsoft-border bg-microsoft text-microsoft-text hover:border-microsoft-border hover:bg-microsoft-hover',
  facebook: 'border-facebook bg-facebook text-white hover:border-facebook-hover hover:bg-facebook-hover',
  apple: 'border-apple bg-apple text-apple-text hover:border-apple-hover hover:bg-apple-hover',
}

interface SocialLoginButtonsProps {
  /** the panel of the page (not the admin panel: admins keep the password only) */
  panel: Exclude<Panel, 'ADMIN'>
  /** where to go after logging in */
  next: string
  /** the account type a sign-up creates (the register page's choice); by default the panel's */
  role?: UserRole
  /** "Continue with Google" on the login pages, "Sign up with Google" on the register page */
  action?: 'Continue' | 'Sign up'
}

/**
 * "Continue with Google" (and the other providers the server offers: without credentials there, no buttons at all).
 * A button is a plain link to the backend, which sends the browser on to the provider (docs/social-login.md); it
 * remembers the panel, the next page and the account type for /login/social first.
 */
export function SocialLoginButtons({ panel, next, role, action = 'Continue' }: SocialLoginButtonsProps) {
  const navigate = useNavigate()
  const { data: providers = [] } = useQuery({
    queryKey: queryKeys.socialProviders,
    queryFn: ({ signal }) => getSocialProviders(signal),
    staleTime: 5 * 60 * 1000,
  })
  if (providers.length === 0) return null

  function start(event: MouseEvent<HTMLAnchorElement>, provider: string) {
    rememberSocialLogin({ panel, next, role: role ?? SIGNUP_ROLE[panel] })
    // The mock API cannot answer a page visit: it plays the provider inside the app
    if (import.meta.env.VITE_API_MOCKING === 'true') {
      event.preventDefault()
      navigate(`/login/social#code=mock-${provider}`)
    }
  }

  return (
    <div>
      <AuthDivider label="or" />
      {/* One column that never grows past the form: a long name wraps on a phone instead of widening the page */}
      <ul className="grid grid-cols-1 gap-3">
        {providers.map((provider) => (
          <li key={provider.id}>
            <a
              href={socialLoginUrl(provider.id)}
              onClick={(event) => start(event, provider.id)}
              className={buttonClasses('outline', 'lg', cn('w-full gap-3 rounded-xl text-center whitespace-normal', BRAND_CLASSES[provider.id]))}
            >
              <SocialLogo provider={provider.id} />
              {action} with {provider.name}
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}
