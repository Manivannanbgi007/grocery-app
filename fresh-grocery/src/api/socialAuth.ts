import { apiRequest, apiUrl } from './client'
import type { AuthResult, UserRole } from './types'

/*
 * Social login: Google, Microsoft, Facebook, Apple (docs/social-login.md).
 *
 * 1. The button is a full page visit to the backend (socialLoginUrl): Spring Security sends the browser on to the
 *    provider. The backend sets a cookie (social_flow) that ties the attempt to this browser.
 * 2. After the provider's login the backend sends the browser to /login/social#code=<one-time code>.
 * 3. That page exchanges the code (exchangeSocialCode): logged in (like after /login), or the sign-up form, or the
 *    password of the existing account with the same email. Provider tokens never reach the app.
 */

/** A provider the shop offers (only those with credentials on the server). */
export interface SocialProvider {
  /** google, microsoft, facebook, apple (test: the local fake provider) */
  id: string
  /** as shown on the button, e.g. "Google" */
  name: string
}

export function getSocialProviders(signal?: AbortSignal): Promise<SocialProvider[]> {
  return apiRequest<SocialProvider[]>('/api/v1/auth/social/providers', { signal })
}

/** Where the button leads: the backend's redirect to the provider. */
export function socialLoginUrl(provider: string): string {
  return apiUrl(`/api/v1/auth/oauth2/authorization/${encodeURIComponent(provider)}`)
}

/** What the exchange of the one-time code found. */
export interface SocialSessionResult {
  status: 'LOGGED_IN' | 'SIGNUP_REQUIRED' | 'PASSWORD_REQUIRED'
  provider: string
  /** only for LOGGED_IN (the refresh token is in the cookie) */
  auth: AuthResult | null
  /** SIGNUP_REQUIRED: the provider's name for the person (may be null) */
  fullName: string | null
  /** SIGNUP_REQUIRED: the provider's email (may be null); PASSWORD_REQUIRED: the existing account's email */
  email: string | null
  /** true: the provider verified the email, it cannot be changed */
  emailLocked: boolean
}

/** Fails with 400 when the code expired, was used, or belongs to another browser; 403 for admin or disabled accounts. */
export function exchangeSocialCode(code: string): Promise<SocialSessionResult> {
  return apiRequest<SocialSessionResult>('/api/v1/auth/social/session', { method: 'POST', body: { code } })
}

export interface SocialSignupRequest {
  fullName: string
  /** only when the provider gave none or did not verify it */
  email?: string
  phone: string
  /** CUSTOMER, VENDOR or DELIVERY_PARTNER */
  role: UserRole
}

/** Creates the account for the provider's login. Fails like /register (400 field errors, 409 email or phone taken). */
export function socialSignUp(request: SocialSignupRequest): Promise<AuthResult> {
  return apiRequest<AuthResult>('/api/v1/auth/social/signup', { method: 'POST', body: request })
}

/** Connects the provider's login to the existing account, with its password. 401 "Invalid email or password". */
export function socialLink(password: string): Promise<AuthResult> {
  return apiRequest<AuthResult>('/api/v1/auth/social/link', { method: 'POST', body: { password } })
}
