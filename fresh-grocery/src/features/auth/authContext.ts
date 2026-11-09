import { createContext, useContext } from 'react'
import type { LoginRequest, RegisterRequest } from '../../api/auth'
import type { AuthResult, User, UserRole } from '../../api/types'

/**
 * The panels of this app. The shop is for customers, /vendor for vendors, /delivery for delivery partners, /admin for
 * admins and the super admin.
 */
export type Panel = 'CUSTOMER' | 'VENDOR' | 'DELIVERY' | 'ADMIN'

interface PanelInfo {
  /** the name of a link to the panel, e.g. in the shop's account menu */
  label: string
  /** how the panel is named in messages ("Vendors log in on the vendor panel.") */
  name: string
  /** the start page */
  home: string
  login: string
  /** the roles that may use it */
  roles: UserRole[]
}

export const PANELS: Record<Panel, PanelInfo> = {
  CUSTOMER: { label: 'Shop', name: 'the shop', home: '/', login: '/login', roles: ['CUSTOMER'] },
  VENDOR: { label: 'Vendor panel', name: 'the vendor panel', home: '/vendor', login: '/vendor/login', roles: ['VENDOR'] },
  DELIVERY: { label: 'Delivery panel', name: 'the delivery panel', home: '/delivery', login: '/delivery/login', roles: ['DELIVERY_PARTNER'] },
  ADMIN: { label: 'Admin panel', name: 'the admin panel', home: '/admin', login: '/admin/login', roles: ['ADMIN', 'SUPER_ADMIN'] },
}

/**
 * A logged-in customer's home page: the shop's home page at its own address (routes.tsx). The shop's login goes there
 * when no other page is waiting (?next=), '/' sends a logged-in customer on to it (features/home/ShopHomePage.tsx), and
 * the shop's own links (logo, Home, Hot Deals, breadcrumbs) point a customer straight to it (hooks/useShopHome.ts).
 * PANELS.CUSTOMER.home stays '/': it is the shop for everyone.
 */
export const CUSTOMER_HOME = '/customer/home'

/**
 * Navigation state of a logout: it shows '/' while the customer is still logged in (and logs out right after), so '/'
 * must not send them on to CUSTOMER_HOME, whose guard would then send the logged-out guest to the login page.
 */
export const LOGGING_OUT_STATE = { loggingOut: true } as const

/** The link to the right login when an account belongs to another panel (WrongPanelError). */
export const LOGIN_LINKS: Record<Panel, string> = {
  CUSTOMER: "Go to the shop's login",
  VENDOR: 'Go to the vendor login',
  DELIVERY: 'Go to the delivery login',
  ADMIN: 'Go to the admin login',
}

/** The panel an account belongs to (null would be a role without a panel). */
export function panelOf(role: UserRole): Panel | null {
  const panels = Object.keys(PANELS) as Panel[]
  return panels.find((panel) => PANELS[panel].roles.includes(role)) ?? null
}

/** What every component can ask about the login (through useAuth()). */
export interface AuthState {
  /** the logged-in user (any role), or null for a guest */
  user: User | null
  /** the same user when it is a customer, else null: the shop's cart, orders and account use this one */
  customer: User | null
  /** the same user when it is a vendor, else null: the vendor panel uses this one */
  vendor: User | null
  /** the same user when it is a delivery partner, else null: the delivery panel uses this one */
  partner: User | null
  /** the same user when it is an admin or the super admin, else null: the admin panel uses this one */
  admin: User | null
  /**
   * True for a moment after the app starts: it asks the server whether the browser still has a login (the refresh
   * cookie). Pages that need a login wait for it (RestoringLogin) instead of sending the user to the login page.
   */
  restoring: boolean
  /**
   * Logs in to a panel (the shop by default). Throws an ApiError (wrong password, disabled account) or a
   * WrongPanelError when the account belongs to another panel.
   */
  logIn: (request: LoginRequest, panel?: Panel) => Promise<User>
  /** Creates the account (customer, vendor or delivery partner) and logs it in straight away. */
  register: (request: RegisterRequest) => Promise<User>
  /**
   * Starts the session of a social login (Google ...: the answer of /auth/social/session, /signup or /link). Throws a
   * WrongPanelError when the account belongs to another panel (and logs it out on the server again).
   */
  finishSocialLogin: (result: AuthResult, panel: Panel) => User
  /** Logs out at once (this tab and the others); the server is told in the background. */
  logOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

/** The login state. Only works inside <AuthProvider> (see App.tsx). */
export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth() must be used inside <AuthProvider>')
  return auth
}

export const ROLE_NAMES: Record<UserRole, string> = {
  SUPER_ADMIN: 'Super admin',
  ADMIN: 'Admin',
  VENDOR: 'Vendor',
  DELIVERY_PARTNER: 'Delivery partner',
  CUSTOMER: 'Customer',
}

/** "a vendor", "an admin": the role in a sentence. */
export function aRole(role: UserRole): string {
  const name = ROLE_NAMES[role].toLowerCase()
  return `${/^[aeiou]/.test(name) ? 'an' : 'a'} ${name}`
}

/** "This is a vendor account. Vendors log in on the vendor panel." */
function wrongPanelMessage(role: UserRole): string {
  const account = `This is ${aRole(role)} account.`
  const own = panelOf(role)
  if (own) return `${account} ${ROLE_NAMES[role]}s log in on ${PANELS[own].name}.`
  return `${account} ${ROLE_NAMES[role]}s will get their own panel in a later phase.`
}

/** The account is fine, but it belongs to another panel (e.g. a vendor on the shop's login page). */
export class WrongPanelError extends Error {
  readonly role: UserRole
  constructor(role: UserRole) {
    super(wrongPanelMessage(role))
    this.name = 'WrongPanelError'
    this.role = role
  }
}
