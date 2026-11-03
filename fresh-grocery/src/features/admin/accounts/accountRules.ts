import type { User, UserRole } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'

export const ROLE_TONES: Record<UserRole, BadgeTone> = {
  SUPER_ADMIN: 'hot',
  ADMIN: 'hot',
  VENDOR: 'sale',
  DELIVERY_PARTNER: 'new',
  CUSTOMER: 'brand',
}

/** The tabs of the account list, one per role (the super admin is under "All"). */
export const ACCOUNT_TABS: { key: string; label: string; role?: UserRole }[] = [
  { key: 'all', label: 'All' },
  { key: 'customers', label: 'Customers', role: 'CUSTOMER' },
  { key: 'vendors', label: 'Vendors', role: 'VENDOR' },
  { key: 'partners', label: 'Delivery partners', role: 'DELIVERY_PARTNER' },
  { key: 'admins', label: 'Admins', role: 'ADMIN' },
]

/** A role the super admin can give (nobody can be made SUPER_ADMIN). */
export type AssignableRole = Exclude<UserRole, 'SUPER_ADMIN'>

const ASSIGNABLE_ROLES: AssignableRole[] = ['CUSTOMER', 'VENDOR', 'DELIVERY_PARTNER', 'ADMIN']

/** Which profiles the account has in the other services (they make one only when someone signs up with that role). */
export interface AccountProfiles {
  customer: boolean
  partner: boolean
}

/**
 * The roles the account can get instead of its current one. Customer and delivery partner need a profile that
 * customer-service and delivery-service only make at sign-up, so they are offered only when it exists; an admin needs
 * none, and a vendor registers a store after logging in.
 */
export function roleChoices(account: User, profiles: AccountProfiles): { role: AssignableRole; unavailable?: string }[] {
  return ASSIGNABLE_ROLES.filter((role) => role !== account.role).map((role) => {
    if (role === 'CUSTOMER' && !profiles.customer) return { role, unavailable: 'No customer profile: it is only made when someone signs up as a customer.' }
    if (role === 'DELIVERY_PARTNER' && !profiles.partner) return { role, unavailable: 'No delivery profile: it is only made when someone signs up as a delivery partner.' }
    return { role }
  })
}

/**
 * Whether the logged-in admin may switch this account on or off, and why not. The backend only refuses the super
 * admin's account; the panel also leaves admin accounts to the super admin and protects your own account.
 */
export function statusChangeRule(account: User, me: User): { allowed: true } | { allowed: false; reason: string } {
  if (account.role === 'SUPER_ADMIN') return { allowed: false, reason: "The super admin's account cannot be switched off." }
  if (account.id === me.id) return { allowed: false, reason: 'This is your own account.' }
  if (account.role === 'ADMIN' && me.role !== 'SUPER_ADMIN') return { allowed: false, reason: 'Admin accounts are managed by the super admin.' }
  return { allowed: true }
}
