import { expect, test, type Page } from '@playwright/test'
import { choose, pickDate, skipSalePopup } from './helpers'

/*
 * Smoke flows 3 and 6: new customers (every run registers new users in the local database, with a unique email and
 * mobile number), so the seed customers' passwords and profiles are never changed.
 */

/** Registers a new customer through the form and returns the email. */
async function registerNewCustomer(page: Page): Promise<string> {
  // Date.now() plus a random part: two registrations in the same millisecond still differ
  const run = `${Date.now()}${Math.floor(Math.random() * 10)}`
  const email = `e2e.storefront.${run}@example.com`
  // 10 digits starting with 9; the last 9 digits change every run
  const phone = `9${run.slice(-9)}`

  await page.goto('/register')
  await page.getByLabel('Full name').fill('Smoke Tester')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mobile number').fill(phone)
  await page.getByLabel('Password', { exact: true }).fill('Fresh1234')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByText('Welcome to Fresh Grocery, Smoke!')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Account of Smoke Tester' })).toBeVisible()
  return email
}

test('a new customer registers, keeps a wishlist across a reload and logs out', async ({ page }) => {
  await skipSalePopup(page)
  await registerNewCustomer(page)

  // The wishlist lives in this browser: it is still there after a reload
  await page.goto('/shop?q=tomato')
  await page.getByRole('button', { name: 'Save Tomato to wishlist' }).click()
  // The heart opens the wishlist pop-up
  await expect(page.getByRole('dialog', { name: 'Wishlist (1)' }).getByRole('link', { name: 'Tomato' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('link', { name: 'Wishlist, 1 item' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('link', { name: 'Wishlist, 1 item' })).toBeVisible()
  await page.getByRole('link', { name: 'Wishlist, 1 item' }).click()
  await expect(page.getByRole('list', { name: 'Products in your wishlist' }).getByRole('link', { name: 'Tomato' })).toBeVisible()

  // Log out
  await page.getByRole('button', { name: 'Account of Smoke Tester' }).click()
  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page.getByRole('link', { name: 'Account', exact: true })).toBeVisible()
})

test('a new customer fills in the profile and address book, changes the password and resets a forgotten one', async ({ page }) => {
  await skipSalePopup(page)
  const email = await registerNewCustomer(page)
  await expect(page.getByRole('button', { name: /^Notifications, \d+ unread$/ })).toBeVisible()

  // Profile (created by customer-service a moment after the registration)
  await page.getByRole('button', { name: 'Account of Smoke Tester' }).click()
  await page.getByRole('link', { name: 'My account' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'My account' })).toBeVisible()
  await expect(page.getByLabel('Full name')).toHaveValue('Smoke Tester')
  await pickDate(page.getByLabel('Date of birth (optional)'), '1995-08-15')
  await choose(page.getByLabel('Gender (optional)'), 'Prefer not to say')
  await page.getByRole('button', { name: 'Save profile' }).click()
  await expect(page.getByText('Your profile is saved')).toBeVisible()

  // Address book: the first address becomes the default
  await page.getByRole('navigation', { name: 'My account' }).getByRole('link', { name: 'Addresses' }).click()
  await expect(page.getByRole('heading', { name: 'No addresses yet' })).toBeVisible()
  await page.getByRole('button', { name: 'Add a new address' }).click()
  await page.getByLabel('Flat, house no., building').fill('12, Rose Villa')
  await page.getByLabel('City').fill('Bengaluru')
  await page.getByLabel('State').fill('Karnataka')
  await page.getByLabel('PIN code').fill('560038')
  await page.getByRole('button', { name: 'Save address' }).click()
  const addresses = page.getByRole('list', { name: 'Your addresses' })
  await expect(addresses.getByText('12, Rose Villa')).toBeVisible()
  await expect(addresses.getByText('Default')).toBeVisible()

  // Change the password
  await page.getByRole('navigation', { name: 'My account' }).getByRole('link', { name: 'Profile and password' }).click()
  await page.getByLabel('Current password').fill('Fresh1234')
  await page.getByLabel('New password').fill('Fresh5678')
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page.getByText('Your password is changed')).toBeVisible()
  await page.getByRole('navigation', { name: 'My account' }).getByRole('button', { name: 'Log out' }).click()

  // Forgot it: the local backend returns the reset code, which the page carries to the next step.
  // Wait for each page's heading before typing: the login page has an "Email" field too, and pages load on first use.
  await page.goto('/login')
  await page.getByRole('link', { name: 'Forgot your password?' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Forgot your password?' })).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByRole('button', { name: 'Send me a code' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()
  await page.getByRole('link', { name: 'Enter the code' }).click()
  await expect(page.getByLabel('Code from the email')).not.toHaveValue('')
  await page.getByLabel('New password').fill('Fresh9012')
  await page.getByRole('button', { name: 'Save new password' }).click()
  const changed = page.getByRole('heading', { name: 'Your password is changed' })
  await expect(changed).toBeVisible()

  await changed.locator('..').getByRole('link', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Log in' })).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('Fresh9012')
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByText('Welcome back, Smoke!')).toBeVisible()
})
