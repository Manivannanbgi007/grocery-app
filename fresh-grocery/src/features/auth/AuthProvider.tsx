import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import { toast } from 'sonner'
import { login, logout, register, type LoginRequest, type RegisterRequest } from '../../api/auth'
import { refreshSession } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import { clearSession, loadSession, saveSession, SESSION_EXPIRED_EVENT, sessionFrom, subscribeSession } from '../../api/session'
import type { AuthResult, User } from '../../api/types'
import { AuthContext, PANELS, WrongPanelError, type AuthState, type Panel } from './authContext'

/** The tabs of this app tell each other about logins and logouts on this channel. */
const TABS_CHANNEL = 'fresh-grocery-auth'
type TabMessage = { type: 'login'; userId: number } | { type: 'logout' }

/**
 * Set while a logout has not reached the server yet (it is on its way, or the server could not be reached). The
 * refresh cookie would still work then, so after a reload the app sends the logout again instead of restoring the login.
 */
const LOGOUT_PENDING_KEY = 'fresh-grocery.logout-pending'

/**
 * Keeps the login (see api/session.ts and docs/authentication.md):
 * - the access token lives in memory and lasts 15 minutes; a timer renews it shortly before it expires (api/client.ts
 *   also renews it before a call, and once after a 401);
 * - after a reload the memory is empty: while `restoring`, the app asks POST /auth/refresh whether the browser's
 *   refresh cookie still holds a login; pages that need a login show a spinner until then;
 * - the login ends when the user logs out (the server is told), when the server refuses the refresh (the API client
 *   sends SESSION_EXPIRED_EVENT), or when another tab logs out or logs in with another account.
 * Pages that need a login (the cart) then send the user to the login page.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const session = useSyncExternalStore(subscribeSession, loadSession)
  const [restoring, setRestoring] = useState(() => loadSession() === null)
  const tabs = useRef<BroadcastChannel | null>(null)

  /** A cart, orders, addresses and a store belong to one person: drop them from the cache when someone logs in or out. */
  function forgetPersonalData() {
    queryClient.removeQueries({ queryKey: queryKeys.allCarts })
    queryClient.removeQueries({ queryKey: queryKeys.allMine })
    queryClient.removeQueries({ queryKey: queryKeys.allVendor })
    queryClient.removeQueries({ queryKey: queryKeys.allAdmin })
    queryClient.removeQueries({ queryKey: queryKeys.allDelivery })
  }

  function startSession(result: AuthResult) {
    setLogoutPending(false)
    saveSession(sessionFrom(result))
    forgetPersonalData()
    tabs.current?.postMessage({ type: 'login', userId: result.user.id } satisfies TabMessage)
  }

  function endSession() {
    clearSession()
    forgetPersonalData()
  }

  function logOut() {
    endSession()
    tabs.current?.postMessage({ type: 'logout' } satisfies TabMessage)
    void endServerSession()
  }

  // After a reload: get the login back with the refresh cookie (or finish a logout that did not reach the server)
  useEffect(() => {
    if (!restoring) return
    const restore = isLogoutPending() ? endServerSession() : refreshSession().catch(() => null)
    void restore.finally(() => setRestoring(false))
  }, [restoring])

  // Renew the token a little before it expires, so calls never wait for it (a failure is retried by the next call)
  useEffect(() => {
    if (!session) return
    const timer = setTimeout(() => void refreshSession().catch(() => null), session.renewAt - Date.now())
    return () => clearTimeout(timer)
  }, [session])

  // useEffectEvent: the listeners always see the current state
  const expireSession = useEffectEvent(() => {
    endSession() // api/client.ts has cleared it already; this also drops the cached personal data
    toast.info('Your session has expired. Please log in again.')
  })

  useEffect(() => {
    const handleExpired = () => expireSession()
    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpired)
  }, [])

  const handleTabMessage = useEffectEvent((message: TabMessage) => {
    const mine = loadSession()
    if (message.type === 'logout') {
      if (!mine) return
      endSession()
      toast.info('You logged out in another tab.')
    } else if (!mine) {
      // This tab is a guest: take over the login of the other tab
      void refreshSession().catch(() => null)
    } else if (mine.user.id !== message.userId) {
      // The browser's cookie now belongs to the other account: this tab must not go on as the old one
      endSession()
      toast.info('You logged in with another account in another tab. Please log in again.')
    }
  })

  useEffect(() => {
    // Every current browser has it; without it, each tab notices the change at its next refresh
    if (typeof BroadcastChannel === 'undefined') return
    const channel = new BroadcastChannel(TABS_CHANNEL)
    channel.onmessage = (event: MessageEvent<TabMessage>) => handleTabMessage(event.data)
    tabs.current = channel
    return () => {
      channel.close()
      tabs.current = null
    }
  }, [])

  const user = session?.user ?? null
  const auth: AuthState = {
    user,
    customer: user?.role === 'CUSTOMER' ? user : null,
    vendor: user?.role === 'VENDOR' ? user : null,
    partner: user?.role === 'DELIVERY_PARTNER' ? user : null,
    admin: user && PANELS.ADMIN.roles.includes(user.role) ? user : null,
    restoring,

    async logIn(request: LoginRequest, panel: Panel = 'CUSTOMER'): Promise<User> {
      const result = await login(request)
      if (!PANELS[panel].roles.includes(result.user.role)) {
        // The server started a session (and set its cookie) for the wrong panel: end it again
        void logout().catch(() => null)
        throw new WrongPanelError(result.user.role)
      }
      startSession(result)
      return result.user
    },

    async register(request: RegisterRequest): Promise<User> {
      const result = await register(request)
      startSession(result)
      return result.user
    },

    finishSocialLogin(result: AuthResult, panel: Panel): User {
      // The same rule as logIn: an account of another panel is logged out again on the server
      if (!PANELS[panel].roles.includes(result.user.role)) {
        void logout().catch(() => null)
        throw new WrongPanelError(result.user.role)
      }
      startSession(result)
      return result.user
    },

    logOut,
  }

  return <AuthContext value={auth}>{children}</AuthContext>
}

/** Tells the server about the logout; until it has answered, a reload does not restore the login (see LOGOUT_PENDING_KEY). */
async function endServerSession(): Promise<void> {
  setLogoutPending(true)
  try {
    await logout()
    setLogoutPending(false)
  } catch {
    // The server could not be reached: the flag stays, the next start of the app tries again
  }
}

function isLogoutPending(): boolean {
  try {
    return localStorage.getItem(LOGOUT_PENDING_KEY) !== null
  } catch {
    return false // storage blocked (private mode)
  }
}

function setLogoutPending(pending: boolean) {
  try {
    if (pending) localStorage.setItem(LOGOUT_PENDING_KEY, 'true')
    else localStorage.removeItem(LOGOUT_PENDING_KEY)
  } catch {
    // storage blocked: the logout still works, only a reload while it is on its way could restore the login
  }
}
