import type { ApiErrorDetail, User, UserRole } from '../api/types'
import type { DemoUser } from './data/users'
import { demoUsers } from './data/users'
import { MockApiError, validationFailed } from './mockApiError'

/*
 * Login for the mock API. The real backend issues a signed JWT; the mock issues "mock-token.<userId>.<expires at>"
 * and reads the user id back from it. The app never looks inside the token, so it cannot tell the difference.
 *
 * The real refresh token is an HttpOnly cookie. The mock remembers instead whose login the "cookie" holds, in
 * localStorage (so `npm run dev:mock` keeps the login after a reload, like the real cookie). The app itself never
 * reads that key.
 */

/** The access token lasts 15 minutes, like the real one. */
export const MOCK_TOKEN_SECONDS = 15 * 60
const RESET_TOKEN_MINUTES = 30
const MOCK_REFRESH_KEY = 'fresh-grocery.mock-refresh-login'

/** Users registered while the mock API runs (they disappear on reload). */
let registeredUsers: DemoUser[] = []
/** Passwords changed while the mock API runs ("change password" or a reset), per user id. */
let changedPasswords = new Map<number, string>()
/** Password reset codes: code -> whose, until when, and whether it was used already. */
let resetTokens = new Map<string, { userId: number; expiresAt: number; used: boolean }>()
/**
 * Changes an admin or the super admin made while the mock API runs: a vendor's approved store (null: the link was
 * removed by a role change), accounts switched on or off, and new roles.
 */
let linkedStores = new Map<number, number | null>()
let changedStatuses = new Map<number, User['status']>()
let changedRoles = new Map<number, UserRole>()

/** Every user, with the admins' changes (the seed objects themselves never change). */
export function allUsers(): DemoUser[] {
  return [...demoUsers, ...registeredUsers].map((user) => ({
    ...user,
    storeId: linkedStores.has(user.id) ? linkedStores.get(user.id)! : user.storeId,
    status: changedStatuses.get(user.id) ?? user.status,
    role: changedRoles.get(user.id) ?? user.role,
  }))
}

/** StoreApproved: auth-service links the store to its vendor, so the vendor's next login token has the store id. */
export function linkStoreToVendor(userId: number, storeId: number): void {
  linkedStores.set(userId, storeId)
}

/** An admin switches an account on or off (a disabled account cannot log in). */
export function setUserStatus(userId: number, status: User['status']): void {
  changedStatuses.set(userId, status)
}

// ---------- Admin (UserAdminServiceImpl) ----------

/** A user without the password, as UserResponse shows it. */
function publicUser({ password: _password, ...user }: DemoUser): User {
  return user
}

/** Accounts by role, status and part of the name or email, oldest first. */
export function searchUsers(filter: { role: string | null; status: string | null; keyword: string | null }): User[] {
  const keyword = filter.keyword?.trim().toLowerCase()
  return allUsers()
    .filter((user) => (!filter.role || user.role === filter.role) && (!filter.status || user.status === filter.status))
    .filter((user) => !keyword || user.fullName.toLowerCase().includes(keyword) || user.email.toLowerCase().includes(keyword))
    .sort((a, b) => a.id - b.id)
    .map(publicUser)
}

export function adminGetUser(userId: number): User {
  const user = allUsers().find((candidate) => candidate.id === userId)
  if (!user) throw new MockApiError(404, `User not found with id: ${userId}`)
  return publicUser(user)
}

/** The status of a SUPER_ADMIN account cannot be changed (the only rule of the backend). */
export function changeUserStatus(userId: number, status: unknown): User {
  if (status !== 'ACTIVE' && status !== 'DISABLED') throw validationFailed([{ field: 'status', message: 'must not be null' }])
  const user = adminGetUser(userId)
  if (user.role === 'SUPER_ADMIN') throw new MockApiError(400, 'The status of a SUPER_ADMIN account cannot be changed')
  setUserStatus(userId, status)
  return adminGetUser(userId)
}

// ---------- Super admin (SuperAdminController) ----------

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** The role an account signed up with: customer-service and delivery-service only make their profile for that one. */
export function signUpRoleOf(userId: number): UserRole | undefined {
  return [...demoUsers, ...registeredUsers].find((user) => user.id === userId)?.role
}

/** The CreateAdminRequest rules, then a free email and mobile number. */
export function createAdmin(body: { fullName?: string; email?: string; phone?: string; password?: string }): User {
  const errors: ApiErrorDetail[] = []
  if (!body.fullName?.trim()) errors.push({ field: 'fullName', message: 'must not be blank' })
  else if (body.fullName.length > 100) errors.push({ field: 'fullName', message: 'size must be between 0 and 100' })
  if (!body.email?.trim()) errors.push({ field: 'email', message: 'must not be blank' })
  else if (!EMAIL.test(body.email)) errors.push({ field: 'email', message: 'must be a well-formed email address' })
  if (!/^[6-9]\d{9}$/.test(body.phone ?? '')) errors.push({ field: 'phone', message: 'must be a valid 10-digit Indian mobile number' })
  const password = body.password ?? ''
  if (password.length < 8 || password.length > 64) errors.push({ field: 'password', message: 'size must be between 8 and 64' })
  else if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(password)) errors.push({ field: 'password', message: 'must contain at least one letter and one digit' })
  if (errors.length > 0) throw validationFailed(errors)

  const email = body.email!.trim()
  if (allUsers().some((user) => user.email.toLowerCase() === email.toLowerCase())) throw new MockApiError(409, `Email is already registered: ${email}`)
  if (allUsers().some((user) => user.phone === body.phone)) throw new MockApiError(409, `Phone number is already registered: ${body.phone}`)
  const admin: DemoUser = {
    id: 1000 + allUsers().length,
    fullName: body.fullName!.trim(),
    email,
    phone: body.phone!,
    role: 'ADMIN',
    status: 'ACTIVE',
    storeId: null,
    lastLoginAt: null,
    createdAt: new Date().toISOString(),
    password,
  }
  registeredUsers.push(admin)
  return publicUser(admin)
}

/** Any role but SUPER_ADMIN, and never for the super admin's account; only a vendor keeps a store id. */
export function changeRole(userId: number, role: unknown): User {
  const roles: UserRole[] = ['SUPER_ADMIN', 'ADMIN', 'VENDOR', 'DELIVERY_PARTNER', 'CUSTOMER']
  if (role === undefined || role === null) throw validationFailed([{ field: 'role', message: 'must not be null' }])
  if (!roles.includes(role as UserRole)) throw new MockApiError(400, 'Request body is missing or invalid (check the JSON syntax and the allowed values)')
  if (role === 'SUPER_ADMIN') throw new MockApiError(400, 'The SUPER_ADMIN role cannot be assigned')
  const user = adminGetUser(userId)
  if (user.role === 'SUPER_ADMIN') throw new MockApiError(400, 'The role of a SUPER_ADMIN account cannot be changed')
  changedRoles.set(userId, role as UserRole)
  if (role !== 'VENDOR') linkedStores.set(userId, null)
  // auth-service ends the user's sessions: the new role needs a new login
  if (mockLoginUser()?.id === userId) endMockLogin()
  return adminGetUser(userId)
}

export function addRegisteredUser(user: DemoUser): void {
  registeredUsers.push(user)
}

/** The user's current password (a changed one, or the one they registered with). */
export function passwordOf(user: DemoUser): string {
  return changedPasswords.get(user.id) ?? user.password
}

/** A password change or a reset: like auth-service, the reset codes the user still had stop working. */
export function setPassword(userId: number, password: string): void {
  changedPasswords.set(userId, password)
  endResetTokens(userId)
}

/** A new single-use reset code for the user, valid for 30 minutes (like auth-service); the user's earlier codes stop working. */
export function createResetToken(user: DemoUser): { resetToken: string; expiresAt: string } {
  endResetTokens(user.id)
  const resetToken = Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join('')
  const expiresAt = Date.now() + RESET_TOKEN_MINUTES * 60 * 1000
  resetTokens.set(resetToken, { userId: user.id, expiresAt, used: false })
  return { resetToken, expiresAt: new Date(expiresAt).toISOString() }
}

function endResetTokens(userId: number) {
  resetTokens.forEach((token) => {
    if (token.userId === userId) token.used = true
  })
}

/** Uses a reset code: the user it belongs to, or null when it is unknown, expired or used already. */
export function redeemResetToken(token: string): DemoUser | null {
  const found = resetTokens.get(token)
  if (!found || found.used || found.expiresAt <= Date.now()) return null
  found.used = true
  return allUsers().find((user) => user.id === found.userId) ?? null
}

export function issueToken(user: DemoUser): string {
  return `mock-token.${user.id}.${Date.now() + MOCK_TOKEN_SECONDS * 1000}`
}

/** The logged-in user of a request (from "Authorization: Bearer mock-token.4.<expires at>"), or null (also when expired). */
export function userFromRequest(request: Request): DemoUser | null {
  const match = /^Bearer mock-token\.(\d+)\.(\d+)$/.exec(request.headers.get('Authorization') ?? '')
  if (!match || Number(match[2]) <= Date.now()) return null
  return allUsers().find((user) => user.id === Number(match[1])) ?? null
}

// ---------- The refresh "cookie" ----------

/** Login and register: the "cookie" now holds this user's login. */
export function startMockLogin(userId: number): void {
  try {
    localStorage.setItem(MOCK_REFRESH_KEY, String(userId))
  } catch {
    // storage blocked: the mock login lasts until the token expires
  }
}

/** Logout, a password reset, a role change: the "cookie" no longer works. */
export function endMockLogin(): void {
  try {
    localStorage.removeItem(MOCK_REFRESH_KEY)
  } catch {
    // nothing to remove
  }
}

/** The user whose login the "cookie" holds, or null. */
export function mockLoginUser(): DemoUser | null {
  let userId: string | null = null
  try {
    userId = localStorage.getItem(MOCK_REFRESH_KEY)
  } catch {
    return null
  }
  return allUsers().find((user) => String(user.id) === userId) ?? null
}

/** For tests: forget the users, passwords and reset codes of a test. */
export function resetMockUsers(): void {
  endMockLogin()
  registeredUsers = []
  changedPasswords = new Map()
  resetTokens = new Map()
  linkedStores = new Map()
  changedStatuses = new Map()
  changedRoles = new Map()
}
