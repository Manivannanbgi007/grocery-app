import type { DemoUser } from './data/users'
import { allUsers } from './mockAuth'

/*
 * Social login for the mock API (npm run dev:mock and the tests). A real login is a page visit to Google and back, which
 * MSW cannot answer (it only sees fetch calls): in the mock, a button goes straight to /login/social#code=mock-google
 * (mock-microsoft, mock-facebook, mock-apple). Each mock code below stands for one person's account at that provider.
 * The account rules are the backend's
 * (SocialLoginServiceImpl, docs/social-login.md), with one simplification: the demo accounts' emails count as not
 * verified on our side, so an existing email always asks for that account's password once.
 */

export interface MockSocialAccount {
  provider: string
  providerUserId: string
  /** null: the provider gave no email (the sign-up form asks for it) */
  email: string | null
  emailVerified: boolean
  fullName: string | null
}

function google(providerUserId: string, email: string | null, fullName: string): MockSocialAccount {
  return { provider: 'google', providerUserId, email, emailVerified: email !== null, fullName }
}

/** The providers the mock offers, in the backend's order (the buttons of npm run dev:mock). */
export const MOCK_SOCIAL_PROVIDERS = [
  { id: 'google', name: 'Google' },
  { id: 'microsoft', name: 'Microsoft' },
  { id: 'facebook', name: 'Facebook' },
  { id: 'apple', name: 'Apple' },
]

/**
 * The one-time codes the mock knows. Unlike real ones they work again and again (each mock button always sends
 * mock-<provider>).
 */
export const MOCK_SOCIAL_CODES: Record<string, MockSocialAccount> = {
  // A new person: the sign-up form the first time, then logged in at once
  'mock-google': google('mock-google-1', 'asha.rao@gmail.com', 'Asha Rao'),
  'mock-microsoft': { provider: 'microsoft', providerUserId: 'mock-microsoft-1', email: 'meera.iyer@outlook.com', emailVerified: true, fullName: 'Meera Iyer' },
  // Facebook never says it checked the email: the sign-up form lets the person change it
  'mock-facebook': { provider: 'facebook', providerUserId: 'mock-facebook-1', email: 'farah.khan@example.com', emailVerified: false, fullName: 'Farah Khan' },
  // "Hide My Email": Apple's relay address
  'mock-apple': { provider: 'apple', providerUserId: 'mock-apple-1', email: 'x7k2qp9d@privaterelay.appleid.com', emailVerified: true, fullName: 'Arjun Mehta' },
  // The emails of demo accounts: their password connects Google (the admin's is refused)
  'mock-google-customer': google('mock-google-2', 'customer1@grocery.com', 'Priya Sharma'),
  'mock-google-vendor': google('mock-google-3', 'vendor1@grocery.com', 'Ravi Kumar'),
  'mock-google-admin': google('mock-google-4', 'admin@grocery.com', 'Platform Admin'),
  // No email from the provider: the sign-up form asks for it
  'mock-google-no-email': google('mock-google-5', null, 'Vikram Rao'),
}

/** Connected provider accounts: "google:mock-google-1" -> user id. */
let links = new Map<string, number>()
/** The provider account waiting for the sign-up form or the password (the real one is tied to the browser's cookie). */
let waiting: MockSocialAccount | null = null

function keyOf(account: MockSocialAccount): string {
  return `${account.provider}:${account.providerUserId}`
}

/** The user this provider account is connected to, or null. */
export function linkedSocialUser(account: MockSocialAccount): DemoUser | null {
  const userId = links.get(keyOf(account))
  return allUsers().find((user) => user.id === userId) ?? null
}

/** After the sign-up or the password: the provider account logs this user in from now on. */
export function linkSocialAccount(account: MockSocialAccount, userId: number): void {
  links.set(keyOf(account), userId)
  waiting = null
}

export function waitForSocialAccount(account: MockSocialAccount): void {
  waiting = account
}

export function waitingSocialAccount(): MockSocialAccount | null {
  return waiting
}

/** For tests: no connected accounts, nothing waiting. */
export function resetMockSocial(): void {
  links = new Map()
  waiting = null
}
