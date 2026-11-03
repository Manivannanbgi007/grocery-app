import type { UserRole } from '../../api/types'
import { CUSTOMER_HOME, PANELS, type Panel } from './authContext'
import { safeNextPath } from './validation'

/*
 * What the user was doing when they clicked "Continue with Google": the panel, the page to go to afterwards, and the
 * account type a sign-up creates. The provider's login is a full page visit, so it is kept in sessionStorage (this tab
 * only) and read again on /login/social. The backend never gets it: it always sends the browser to /login/social.
 */

const KEY = 'fresh-grocery.social-login'

export interface SocialLoginIntent {
  panel: Panel
  /** a page of this app (safeNextPath) */
  next: string
  /** the account type of a sign-up: CUSTOMER, VENDOR or DELIVERY_PARTNER */
  role: UserRole
}

/** The account type a sign-up from a panel's login page creates. */
export const SIGNUP_ROLE: Record<Exclude<Panel, 'ADMIN'>, UserRole> = {
  CUSTOMER: 'CUSTOMER',
  VENDOR: 'VENDOR',
  DELIVERY: 'DELIVERY_PARTNER',
}

const SIGNUP_ROLES: UserRole[] = Object.values(SIGNUP_ROLE)

export function rememberSocialLogin(intent: SocialLoginIntent): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(intent))
  } catch {
    // storage blocked: the login still works, it ends on the customer's home page
  }
}

/**
 * What was remembered, checked again (the page may be opened directly). By default the shop's login: the customer's
 * home page afterwards, and a customer sign-up.
 */
export function readSocialLogin(): SocialLoginIntent {
  let saved: Partial<SocialLoginIntent> = {}
  try {
    saved = JSON.parse(sessionStorage.getItem(KEY) ?? '{}') as Partial<SocialLoginIntent>
  } catch {
    // nothing remembered
  }
  const panel = saved.panel && saved.panel in PANELS && saved.panel !== 'ADMIN' ? saved.panel : 'CUSTOMER'
  return {
    panel,
    next: typeof saved.next === 'string' ? safeNextPath(saved.next) : CUSTOMER_HOME,
    role: saved.role && SIGNUP_ROLES.includes(saved.role) ? saved.role : SIGNUP_ROLE[panel],
  }
}
