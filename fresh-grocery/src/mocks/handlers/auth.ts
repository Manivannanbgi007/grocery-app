import { delay, http, HttpResponse } from 'msw'
import type { ApiErrorDetail, AuthResult, User, UserRole } from '../../api/types'
import type { DemoUser } from '../data/users'
import {
  addRegisteredUser,
  allUsers,
  createResetToken,
  endMockLogin,
  issueToken,
  MOCK_TOKEN_SECONDS,
  mockLoginUser,
  passwordOf,
  redeemResetToken,
  setPassword,
  startMockLogin,
  userFromRequest,
} from '../mockAuth'
import { registerPartner } from '../mockDelivery'
import { fail, ok } from './respond'

/*
 * Mock of auth-service: register, login, refresh, logout and the password changes, with the backend's validation rules
 * and messages. The refresh "cookie" is kept by mockAuth.ts (startMockLogin).
 */

const SELF_REGISTRATION_ROLES: UserRole[] = ['CUSTOMER', 'VENDOR', 'DELIVERY_PARTNER']
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function authResult(user: DemoUser): AuthResult {
  const { password: _password, ...publicUser } = user
  const userResponse: User = { ...publicUser, lastLoginAt: new Date().toISOString() }
  return { accessToken: issueToken(user), tokenType: 'Bearer', expiresInSeconds: MOCK_TOKEN_SECONDS, user: userResponse }
}

interface NewAccount {
  fullName: string
  email: string
  phone: string
  password: string
  role: UserRole
}

/**
 * Creates the account of a registration (also of a social sign-up) and logs it in. The role must be one a person may
 * choose, the email and the phone must be free. Returns the backend's error answer otherwise.
 */
export function createAccount(request: Request, account: NewAccount): DemoUser | Response {
  if (!SELF_REGISTRATION_ROLES.includes(account.role)) return fail(400, `Role ${account.role} cannot be chosen at registration`, request)
  if (allUsers().some((user) => user.email.toLowerCase() === account.email.toLowerCase())) {
    return fail(409, `Email is already registered: ${account.email}`, request)
  }
  if (allUsers().some((user) => user.phone === account.phone)) return fail(409, `Phone number is already registered: ${account.phone}`, request)

  const user: DemoUser = {
    id: 1000 + allUsers().length,
    ...account,
    status: 'ACTIVE',
    storeId: null,
    lastLoginAt: null,
    createdAt: new Date().toISOString(),
  }
  addRegisteredUser(user)
  // UserRegistered: delivery-service makes the partner's profile (in the real backend a moment later)
  if (user.role === 'DELIVERY_PARTNER') registerPartner(user.id, user.fullName, user.phone)
  startMockLogin(user.id)
  return user
}

interface RegisterBody {
  fullName?: string
  email?: string
  phone?: string
  password?: string
  role?: UserRole
}

/** The password rules of auth-service (@Size(min = 8, max = 64) and ValidationPatterns.PASSWORD). */
function passwordErrors(field: string, password: string | undefined): ApiErrorDetail[] {
  const value = password ?? ''
  if (value.length < 8 || value.length > 64) return [{ field, message: 'size must be between 8 and 64' }]
  if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(value)) return [{ field, message: 'must contain at least one letter and one digit' }]
  return []
}

/** The same field checks as RegisterRequest in auth-service. */
function validateRegistration(body: RegisterBody): ApiErrorDetail[] {
  const errors: ApiErrorDetail[] = []
  if (!body.fullName?.trim()) errors.push({ field: 'fullName', message: 'must not be blank' })
  if (!body.email?.trim()) errors.push({ field: 'email', message: 'must not be blank' })
  else if (!EMAIL.test(body.email)) errors.push({ field: 'email', message: 'must be a well-formed email address' })
  if (!/^[6-9]\d{9}$/.test(body.phone ?? '')) errors.push({ field: 'phone', message: 'must be a valid 10-digit Indian mobile number' })
  errors.push(...passwordErrors('password', body.password))
  if (!body.role) errors.push({ field: 'role', message: 'must not be null' })
  return errors
}

export const authHandlers = [
  http.post('*/api/v1/auth/register', async ({ request }) => {
    await delay()
    const body = (await request.json()) as RegisterBody
    const errors = validateRegistration(body)
    if (errors.length > 0) return fail(400, 'Validation failed', request, errors)

    const user = createAccount(request, {
      fullName: body.fullName!.trim(),
      email: body.email!.trim(),
      phone: body.phone!,
      password: body.password!,
      role: body.role!,
    })
    if (user instanceof Response) return user
    return ok(authResult(user), 'Registration successful', 201)
  }),

  http.post('*/api/v1/auth/login', async ({ request }) => {
    await delay()
    const body = (await request.json()) as { email?: string; password?: string }
    const errors: ApiErrorDetail[] = []
    if (!body.email?.trim()) errors.push({ field: 'email', message: 'must not be blank' })
    if (!body.password) errors.push({ field: 'password', message: 'must not be blank' })
    if (errors.length > 0) return fail(400, 'Validation failed', request, errors)

    // Same message for "unknown email" and "wrong password", like the backend
    const user = allUsers().find((candidate) => candidate.email.toLowerCase() === body.email!.trim().toLowerCase() && passwordOf(candidate) === body.password)
    if (!user) return fail(401, 'Invalid email or password', request)
    if (user.status !== 'ACTIVE') return fail(403, 'Your account is disabled. Please contact support.', request)
    startMockLogin(user.id)
    return ok(authResult(user), 'Login successful')
  }),

  // A new access token for the "cookie" (the real one also replaces the cookie). A disabled account cannot go on.
  http.post('*/api/v1/auth/refresh', async ({ request }) => {
    await delay()
    const user = mockLoginUser()
    if (!user || user.status !== 'ACTIVE') {
      endMockLogin()
      return fail(401, 'Your session has ended. Please log in again.', request)
    }
    return ok(authResult(user), 'Token refreshed')
  }),

  // Always 204, also without a login (like the backend)
  http.post('*/api/v1/auth/logout', async () => {
    await delay()
    endMockLogin()
    return new HttpResponse(null, { status: 204 })
  }),

  http.put('*/api/v1/auth/me/password', async ({ request }) => {
    await delay()
    const user = userFromRequest(request)
    if (!user) return fail(401, 'Please log in to access this resource', request)
    const body = (await request.json()) as { currentPassword?: string; newPassword?: string }
    const errors: ApiErrorDetail[] = []
    if (!body.currentPassword) errors.push({ field: 'currentPassword', message: 'must not be blank' })
    errors.push(...passwordErrors('newPassword', body.newPassword))
    if (errors.length > 0) return fail(400, 'Validation failed', request, errors)

    if (body.currentPassword !== passwordOf(user)) return fail(400, 'Current password is incorrect', request)
    if (body.currentPassword === body.newPassword) return fail(400, 'New password must be different from the current password', request)
    setPassword(user.id, body.newPassword!)
    return ok(null, 'Password changed successfully')
  }),

  // The local backend also returns the code (expose-token-in-response); unknown emails get the same answer without it
  http.post('*/api/v1/auth/forgot-password', async ({ request }) => {
    await delay()
    const body = (await request.json()) as { email?: string }
    const email = body.email?.trim() ?? ''
    if (!email) return fail(400, 'Validation failed', request, [{ field: 'email', message: 'must not be blank' }])
    if (!EMAIL.test(email)) return fail(400, 'Validation failed', request, [{ field: 'email', message: 'must be a well-formed email address' }])
    const user = allUsers().find((candidate) => candidate.email.toLowerCase() === email.toLowerCase())
    return ok(user ? createResetToken(user) : null, 'If the email is registered, password reset instructions have been sent')
  }),

  http.post('*/api/v1/auth/reset-password', async ({ request }) => {
    await delay()
    const body = (await request.json()) as { token?: string; newPassword?: string }
    const errors: ApiErrorDetail[] = []
    if (!body.token?.trim()) errors.push({ field: 'token', message: 'must not be blank' })
    errors.push(...passwordErrors('newPassword', body.newPassword))
    if (errors.length > 0) return fail(400, 'Validation failed', request, errors)

    const user = redeemResetToken(body.token!.trim())
    if (!user) return fail(400, 'Reset token is invalid or has expired', request)
    setPassword(user.id, body.newPassword!)
    // auth-service ends every session of the user after a reset
    if (mockLoginUser()?.id === user.id) endMockLogin()
    return ok(null, 'Password has been reset successfully')
  }),
]
