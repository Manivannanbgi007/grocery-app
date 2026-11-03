import type { AuthResult, User } from './types'

/**
 * The login of this browser tab, kept in memory only (never in localStorage, where any script on the page could read
 * the token). It has two parts:
 * - the access token (15 minutes), sent with every API call, and the user it belongs to;
 * - the refresh token, which the app never sees: the server keeps it in an HttpOnly cookie, and POST /auth/refresh
 *   swaps it for a new access token (api/client.ts does that). After a reload the memory is empty, so the
 *   AuthProvider asks /auth/refresh once to get the login back.
 * See docs/authentication.md.
 */
export interface Session {
  accessToken: string
  /** when the access token stops working, in milliseconds (same format as Date.now()) */
  expiresAt: number
  /** when to get a new one: a little earlier (see RENEW_EARLY_MS) */
  renewAt: number
  user: User
}

/**
 * A token is renewed this long before it expires: an assistant answer can take up to 3 minutes, and the assistant's
 * tools use the customer's token all that time. A token that lives shorter than twice this (a test setting) is renewed
 * halfway, so the app does not renew it again and again.
 */
const RENEW_EARLY_MS = 3 * 60 * 1000

/** Sent on `window` when the server ended the login (the refresh token was refused): the AuthProvider then logs out. */
export const SESSION_EXPIRED_EVENT = 'fresh-grocery:session-expired'

let current: Session | null = null
/**
 * Goes up by one on every login, refresh and logout. A refresh remembers it when it starts and throws its answer away
 * when it changed in the meantime (e.g. the user logged out while the refresh was on its way).
 */
let generation = 0
const listeners = new Set<() => void>()

/** The login in memory, or null for a guest. The token may have expired: api/client.ts refreshes it before using it. */
export function loadSession(): Session | null {
  return current
}

export function saveSession(session: Session): void {
  current = session
  changed()
}

export function clearSession(): void {
  current = null
  changed()
}

export function sessionGeneration(): number {
  return generation
}

/** Calls `listener` after every change of the session; returns the function that stops it (for useSyncExternalStore). */
export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function changed() {
  generation++
  listeners.forEach((listener) => listener())
}

/**
 * The session of a login, register or refresh answer. The user object of the old session is kept when nothing in it
 * changed, so components that depend on it do not run their effects again after every refresh.
 */
export function sessionFrom(result: AuthResult, previous: Session | null = null): Session {
  const sameUser = previous !== null && JSON.stringify(previous.user) === JSON.stringify(result.user)
  const lifetime = result.expiresInSeconds * 1000
  const now = Date.now()
  return {
    accessToken: result.accessToken,
    expiresAt: now + lifetime,
    renewAt: now + lifetime - Math.min(RENEW_EARLY_MS, lifetime / 2),
    user: sameUser ? previous.user : result.user,
  }
}
