import { expect, test } from '@playwright/test'
import { apiLogin, CUSTOMER, emptyCart, fillCart, GATEWAY, logIn, skipSalePopup } from './helpers'

/*
 * Smoke flow 4 and 5: checkout, paying, tracking and cancelling an order, and an order that was delivered.
 * Needs order-service and payment-service too (and delivery-service for the delivery rating).
 * The order of flow 4 is cancelled again at the end, so its stock goes back and the mock payment is refunded.
 */

test.describe('checkout and my orders', () => {
  /** The order the test placed: cancelled in the clean-up too, in case the test stopped before cancelling it. */
  let placedOrderId: string | undefined

  test.beforeEach(async ({ request }) => {
    placedOrderId = undefined
    await emptyCart(request)
  })
  test.afterEach(async ({ request }) => {
    await emptyCart(request)
    if (placedOrderId) {
      // Fails with 400 when the test already cancelled it: that is fine
      await request.post(`${GATEWAY}/api/v1/orders/${placedOrderId}/cancel`, { headers: await apiLogin(request), data: { reason: 'Playwright clean-up' } })
    }
  })

  test('a customer places an order, pays online, cancels it and finds it under My orders', async ({ page, request }) => {
    // 2 × Tomato (₹36) + 2 × Apple Shimla (₹162) = ₹396, free delivery
    await fillCart(request, [
      [1, 2],
      [5, 2],
    ])
    await skipSalePopup(page)

    await page.goto('/checkout')
    await expect(page).toHaveURL(/\/login\?next=%2Fcheckout/)
    await logIn(page)
    await expect(page.getByRole('heading', { level: 1, name: 'Checkout' })).toBeVisible()
    await expect(page.getByRole('radio', { name: /^Home/ })).toBeChecked()

    // Fresh Mart does not deliver to Priya's work address (another PIN code)
    await page.getByRole('radio', { name: /^Work/ }).check()
    await page.getByRole('button', { name: 'Place order' }).click()
    await expect(page.getByText('Fresh Mart does not deliver to PIN code 560034. Please choose another address.')).toBeVisible()

    await page.getByRole('radio', { name: /^Home/ }).check()
    await page.getByRole('radio', { name: /Pay online/ }).check()
    await page.getByLabel('3. Note for the store (optional)').fill('Playwright test order')
    await page.getByRole('button', { name: 'Place order' }).click()

    await expect(page.getByRole('heading', { name: 'Thank you for your order!' })).toBeVisible()
    await expect(page).toHaveURL(/\/orders\/\d+$/)
    const orderId = page.url().split('/').pop()!
    placedOrderId = orderId
    // The backend empties the cart in the background
    await expect(page.getByRole('link', { name: 'Cart, 0 items' })).toBeVisible()

    // Once the stock is reserved, the test gateway takes the payment
    await page.getByRole('button', { name: 'Pay ₹396' }).click()
    await expect(page.getByText('Payment of ₹396 received', { exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible()
    await expect(page.getByText('Paid ₹396 online')).toBeVisible()

    await page.getByRole('button', { name: 'Cancel order' }).click()
    const dialog = page.getByRole('alertdialog', { name: `Cancel order #${orderId}?` })
    await dialog.getByLabel('Reason (optional)').fill('Playwright test: cancelled again')
    await dialog.getByRole('button', { name: 'Cancel order' }).click()
    await expect(page.getByRole('heading', { name: 'Order cancelled' })).toBeVisible()
    // payment-service refunds in the background; the page asks again until it has
    await expect(page.getByText('₹396 refunded')).toBeVisible()

    await page.getByRole('button', { name: `Account of ${CUSTOMER.firstName} Sharma` }).click()
    await page.getByRole('navigation', { name: 'Your account' }).getByRole('link', { name: 'My orders' }).click()
    const newest = page.getByRole('list', { name: 'Your orders' }).getByRole('listitem').first()
    await expect(newest.getByRole('heading')).toHaveText(`Order #${orderId}`)
    await expect(newest).toContainText('Cancelled')
  })
})

test('a delivered order shows its tracking, the delivery rating and the invoice', async ({ page, request }) => {
  // customer1's delivered orders come from the backend's own end-to-end tests
  const headers = await apiLogin(request)
  const delivered = await request.get(`${GATEWAY}/api/v1/orders?status=DELIVERED&size=1`, { headers })
  const orderId = (await delivered.json()).data.content[0]?.id as number | undefined
  test.skip(orderId === undefined, 'customer1 has no delivered order yet (run the backend end-to-end tests once)')

  await skipSalePopup(page)
  await page.goto(`/orders/${orderId}`)
  await logIn(page)

  await expect(page.getByRole('heading', { level: 1, name: `Order #${orderId}` })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Delivered', exact: true })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Order progress' }).getByRole('listitem').last()).toHaveAttribute('aria-current', 'step')
  await expect(page.getByRole('heading', { name: 'Delivery partner' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Rate the delivery' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Cancel order' })).toHaveCount(0)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download invoice' }).click()
  expect((await download).suggestedFilename()).toMatch(/^invoice-INV-\d+\.pdf$/)
})
