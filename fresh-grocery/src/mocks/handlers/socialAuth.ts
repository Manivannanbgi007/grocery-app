import { delay, http } from 'msw'
import type { SocialSessionResult } from '../../api/socialAuth'
import type { ApiErrorDetail, UserRole } from '../../api/types'
import type { DemoUser } from '../data/users'
import { allUsers, passwordOf, startMockLogin } from '../mockAuth'
import {
  linkedSocialUser,
  linkSocialAccount,
  MOCK_SOCIAL_CODES,
  MOCK_SOCIAL_PROVIDERS,
  waitForSocialAccount,
  waitingSocialAccount,
  type MockSocialAccount,
} from '../mockSocial'
import { authResult, createAccount } from './auth'
import { fail, ok } from './respond'

/*
 * Mock of auth-service's social login endpoints (SocialLoginController), with the backend's messages. The mock codes
 * and the connected accounts are in mockSocial.ts.
 */

const EXPIRED = 'This sign-in has expired or was already used. Please try again.'
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function userWithEmail(email: string | null): DemoUser | undefined {
  return email ? allUsers().find((user) => user.email.toLowerCase() === email.toLowerCase()) : undefined
}

/** Admin accounts keep the password only; a disabled account gets the login page's message. */
function refusal(user: DemoUser, request: Request): Response | null {
  if (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN') {
    return fail(403, 'Admin accounts cannot use social login. Please log in with your password.', request)
  }
  if (user.status !== 'ACTIVE') return fail(403, 'Your account is disabled. Please contact support.', request)
  return null
}

function sessionResult(account: MockSocialAccount, result: Partial<SocialSessionResult>): SocialSessionResult {
  return { status: 'LOGGED_IN', provider: account.provider, auth: null, fullName: null, email: null, emailLocked: false, ...result }
}

interface SignupBody {
  fullName?: string
  email?: string
  phone?: string
  role?: UserRole
}

export const socialAuthHandlers = [
  // All four, as when the server has every provider's credentials
  http.get('*/api/v1/auth/social/providers', async () => {
    await delay()
    return ok(MOCK_SOCIAL_PROVIDERS, 'Login providers')
  }),

  http.post('*/api/v1/auth/social/session', async ({ request }) => {
    await delay()
    const { code } = (await request.json()) as { code?: string }
    const account = code ? MOCK_SOCIAL_CODES[code] : undefined
    if (!account) return fail(400, EXPIRED, request)

    // 1. Connected already: logged in
    const linked = linkedSocialUser(account)
    if (linked) {
      const refused = refusal(linked, request)
      if (refused) return refused
      startMockLogin(linked.id)
      return ok(sessionResult(account, { auth: authResult(linked) }), 'Login successful')
    }

    // 3. No account with this email: the sign-up form
    const sameEmail = userWithEmail(account.email)
    if (!sameEmail) {
      waitForSocialAccount(account)
      const { fullName, email, emailVerified } = account
      return ok(sessionResult(account, { status: 'SIGNUP_REQUIRED', fullName, email, emailLocked: emailVerified }), 'Please finish creating your account')
    }

    // 2. An account with the same email: its password connects the provider account once
    const refused = refusal(sameEmail, request)
    if (refused) return refused
    waitForSocialAccount(account)
    return ok(
      sessionResult(account, { status: 'PASSWORD_REQUIRED', email: sameEmail.email }),
      'An account with this email exists: enter its password to connect',
    )
  }),

  http.post('*/api/v1/auth/social/signup', async ({ request }) => {
    await delay()
    const account = waitingSocialAccount()
    if (!account) return fail(400, EXPIRED, request)
    const body = (await request.json()) as SignupBody
    // A verified email from the provider is used as it is; otherwise the one typed into the form
    const email = account.emailVerified ? account.email! : (body.email?.trim() ?? '')

    const errors: ApiErrorDetail[] = []
    if (!body.fullName?.trim()) errors.push({ field: 'fullName', message: 'must not be blank' })
    if (!account.emailVerified && email && !EMAIL.test(email)) errors.push({ field: 'email', message: 'must be a well-formed email address' })
    if (!/^[6-9]\d{9}$/.test(body.phone ?? '')) errors.push({ field: 'phone', message: 'must be a valid 10-digit Indian mobile number' })
    if (!body.role) errors.push({ field: 'role', message: 'must not be null' })
    if (errors.length > 0) return fail(400, 'Validation failed', request, errors)
    if (!email) return fail(400, 'Please enter your email address', request)

    // A random password nobody knows: logging in with a password works after "Forgot password" sets one
    const user = createAccount(request, { fullName: body.fullName!.trim(), email, phone: body.phone!, password: crypto.randomUUID(), role: body.role! })
    if (user instanceof Response) return user
    linkSocialAccount(account, user.id)
    return ok(authResult(user), 'Registration successful', 201)
  }),

  http.post('*/api/v1/auth/social/link', async ({ request }) => {
    await delay()
    const account = waitingSocialAccount()
    const user = account && userWithEmail(account.email)
    if (!account || !user) return fail(400, EXPIRED, request)
    const body = (await request.json()) as { password?: string }
    if (!body.password) return fail(400, 'Validation failed', request, [{ field: 'password', message: 'must not be blank' }])

    const refused = refusal(user, request)
    if (refused) return refused
    // The same check and message as the login page
    if (passwordOf(user) !== body.password) return fail(401, 'Invalid email or password', request)
    linkSocialAccount(account, user.id)
    startMockLogin(user.id)
    return ok(authResult(user), 'Login successful')
  }),
]
