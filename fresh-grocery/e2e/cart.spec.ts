import { expect, test } from '@playwright/test'
import { CUSTOMER, emptyCart, logIn, skipSalePopup } from './helpers'

/**
 * Smoke flow 2: a customer fills the cart (customer1's real cart is emptied before and after).
 * Tomato (₹36) and 2 × Apple Shimla (₹162) = ₹360: enough for SAVE10 (₹199) and free delivery (₹299).
 */
test.beforeEach(async ({ request }) => emptyCart(request))
test.afterEach(async ({ request }) => emptyCart(request))

test('a customer logs in, fills the cart, uses a coupon and checks out', async ({ page }) => {
  await skipSalePopup(page)

  // A guest who clicks "Add to cart" logs in first and comes back to the product
  await page.goto('/products/1')
  await page.getByRole('button', { name: 'Add Tomato to cart' }).click()
  await expect(page).toHaveURL(/\/login\?next=%2Fproducts%2F1/)
  await logIn(page)
  await expect(page.getByRole('heading', { level: 1, name: 'Tomato' })).toBeVisible()
  await expect(page.getByRole('button', { name: `Account of ${CUSTOMER.firstName} Sharma` })).toBeVisible()

  await page.getByRole('button', { name: 'Add Tomato to cart' }).click()
  await expect(page.getByText('Tomato added to your cart')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Cart, 1 item' })).toBeVisible()

  await page.goto('/products/5')
  await page.getByRole('button', { name: 'Increase quantity' }).click()
  await page.getByRole('button', { name: 'Add Apple Shimla to cart' }).click()
  await expect(page.getByText('2 × Apple Shimla added to your cart')).toBeVisible()

  // A product of another store: the one-store rule asks first ("Keep my cart" keeps it)
  await page.goto('/products/11')
  await page.getByRole('button', { name: 'Add Organic Spinach to cart' }).click()
  const dialog = page.getByRole('alertdialog', { name: 'Start a new cart?' })
  await expect(dialog).toContainText('Your cart has products from Fresh Mart')
  await dialog.getByRole('button', { name: 'Keep my cart' }).click()

  // The cart page: the backend's totals, a coupon, the checkout check
  await page.getByRole('link', { name: 'Cart, 2 items' }).click()
  await expect(page.getByRole('list', { name: 'Products in your cart' }).getByRole('listitem')).toHaveCount(2)
  const totals = page.getByRole('heading', { name: 'Cart totals' }).locator('..')
  await expect(totals.getByText('Items total').locator('..')).toContainText('₹360')
  await expect(totals.getByText('Delivery').locator('..')).toContainText('Free')

  await page.getByRole('button', { name: /Apply coupon SAVE10/ }).click()
  await expect(totals.getByText('Coupon SAVE10').locator('..')).toContainText('₹36')
  await expect(totals.getByText('Total to pay').locator('..')).toContainText('₹324')

  // The checkout page checks the cart against today's catalog and shows the same total
  await page.getByRole('link', { name: 'Proceed to checkout' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Checkout' })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Products in your order' }).getByRole('listitem')).toHaveCount(2)
  const orderTotal = page.getByRole('heading', { name: 'Order total' }).locator('..')
  await expect(orderTotal.getByText('Total to pay').locator('..')).toContainText('₹324')
})
