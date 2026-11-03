import { expect, test } from '@playwright/test'
import { apiLogin, CUSTOMER, GATEWAY, logIn, skipSalePopup } from './helpers'

/*
 * Smoke flows 26 and 27: the stores in the shop (store-service, product-read-service) and the PRODUCT_REVIEWS feature flag
 * (platform-service) on a delivered order of customer1. The flag is switched on again afterwards (only when it is
 * still off: every switch is an audit log entry).
 */

const SUPER_ADMIN = { email: 'superadmin@grocery.com', password: 'Password@123' }

test('a guest finds the stores, shops at one, and gets from a product back to its store', async ({ page }) => {
  await skipSalePopup(page)
  await page.goto('/')
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Stores', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Our stores' })).toBeVisible()
  const freshMart = page.getByRole('list', { name: 'Stores' }).getByRole('listitem').filter({ hasText: 'Fresh Mart' })
  await expect(freshMart).toContainText(/Open now|Closed now/)
  await freshMart.getByRole('link', { name: 'Shop at Fresh Mart' }).click()

  await expect(page).toHaveURL(/\/shop\?store=1$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Fresh Mart' })).toBeVisible()
  await expect(page.locator('ul[aria-busy="true"]')).toHaveCount(0)
  const firstCard = page.getByRole('article').first()
  await expect(firstCard).toContainText('By Fresh Mart')
  await firstCard.getByRole('heading').getByRole('link').click()

  await page.getByText('Sold by').getByRole('link', { name: 'Fresh Mart' }).click()
  await expect(page).toHaveURL(/\/stores\/1$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Fresh Mart' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Opening hours' })).toContainText('(today)')
  await expect(page.getByRole('article').first()).toBeVisible()
})

test('with PRODUCT_REVIEWS off, a delivered order offers no product reviews', async ({ page, request }) => {
  const customer = await apiLogin(request)
  const delivered = (await (await request.get(`${GATEWAY}/api/v1/orders?status=DELIVERED&size=1`, { headers: customer })).json()).data.content as { id: number }[]
  test.skip(delivered.length === 0, 'customer1 has no delivered order (the delivery flow makes one)')
  const orderId = delivered[0].id

  const headers = await apiLogin(request, SUPER_ADMIN)
  await request.patch(`${GATEWAY}/api/v1/super-admin/feature-flags/PRODUCT_REVIEWS`, { headers, data: { enabled: false } })
  try {
    await skipSalePopup(page)
    await page.goto(`/login?next=/orders/${orderId}`)
    await logIn(page, CUSTOMER)
    await expect(page.getByRole('heading', { level: 1, name: `Order #${orderId}` })).toBeVisible()
    await expect(page.getByRole('list', { name: 'Products in this order' })).toBeVisible()
    await expect(page.getByRole('button', { name: /^Review / })).toHaveCount(0)
  } finally {
    const flags = (await (await request.get(`${GATEWAY}/api/v1/super-admin/feature-flags`, { headers })).json()).data as { key: string; enabled: boolean }[]
    if (!flags.find((flag) => flag.key === 'PRODUCT_REVIEWS')?.enabled) {
      await request.patch(`${GATEWAY}/api/v1/super-admin/feature-flags/PRODUCT_REVIEWS`, { headers, data: { enabled: true } })
    }
  }
})
