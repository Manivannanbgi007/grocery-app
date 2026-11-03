import { apiRequest } from './client'
import type { AuthResult, ForgotPasswordResult, UserRole } from './types'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  fullName: string
  email: string
  phone: string
  password: string
  /** CUSTOMER, VENDOR or DELIVERY_PARTNER */
  role: UserRole
}

/** Fails with 401 "Invalid email or password", or 403 for a disabled account. */
export function login(request: LoginRequest): Promise<AuthResult> {
  return apiRequest<AuthResult>('/api/v1/auth/login', { method: 'POST', body: request })
}

/** Creates the account and logs it in. Fails with 400 (field errors) or 409 (email or phone already registered). */
export function register(request: RegisterRequest): Promise<AuthResult> {
  return apiRequest<AuthResult>('/api/v1/auth/register', { method: 'POST', body: request })
}

/**
 * Ends the login on the server: the refresh token in the cookie stops working and the cookie is deleted (204, also when
 * there was no login). `keepalive`: the browser finishes the call even when the page is reloaded or closed right after.
 * The access token in memory is forgotten by the AuthProvider; it would expire within 15 minutes anyway.
 */
export function logout(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/logout', { method: 'POST', keepalive: true })
}

/** For the logged-in user. Fails with 400 "Current password is incorrect" or "New password must be different ...". */
export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return apiRequest<void>('/api/v1/auth/me/password', { method: 'PUT', body: { currentPassword, newPassword } })
}

/**
 * Asks for a reset code by email. The answer is the same whether or not the email is registered (so nobody can find
 * out who has an account); only the local backend also returns the code (see ForgotPasswordResult).
 */
export function forgotPassword(email: string): Promise<ForgotPasswordResult | null> {
  return apiRequest<ForgotPasswordResult | null>('/api/v1/auth/forgot-password', { method: 'POST', body: { email } })
}

/** Sets a new password with the emailed code. Fails with 400 "Reset token is invalid or has expired". */
export function resetPassword(token: string, newPassword: string): Promise<void> {
  return apiRequest<void>('/api/v1/auth/reset-password', { method: 'POST', body: { token, newPassword } })
}
