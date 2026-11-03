import { expect, test } from '@playwright/test'
import { choose, skipSalePopup } from './helpers'

/** Smoke flow 1: a guest browses the shop. Reads data only. */
test('a guest browses: home -> shop filters -> product -> recently viewed survives a reload', async ({ page }) => {
  await skipSalePopup(page)

  // Home: the banners and categories come from the real API
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: 'Fresh vegetables, delivered today' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Featured Categories' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Deals Of The Day' })).toBeVisible()

  // Shop: category from the sidebar, then "in stock only" and the cheapest first
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Shop', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'All products' })).toBeVisible()
  const sidebar = page.getByRole('complementary', { name: 'Browse the shop' })
  await sidebar.getByRole('link', { name: /^Fruits/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Fruits' })).toBeVisible()
  // click + an assertion that waits: the box is ticked once the shop has moved to the new address
  const inStockOnly = page.getByRole('region', { name: 'Filters' }).getByRole('checkbox', { name: 'In stock only' })
  await inStockOnly.click()
  await expect(inStockOnly).toBeChecked()
  await choose(page.getByLabel('Sort by:'), 'Price: low to high')
  await expect(page).toHaveURL(/category=2&inStock=true&sort=PRICE_LOW_TO_HIGH/)
  await expect(page.getByText(/We found \d+ items? for you!/)).toBeVisible()
  // Out-of-stock products are gone
  await expect(page.getByRole('button', { name: 'Sold out' })).toHaveCount(0)

  // The first (cheapest) product, once the sorted list replaced the previous one (busy while it loads)
  await expect(page.locator('ul[aria-busy="true"]')).toHaveCount(0)
  const firstCard = page.getByRole('article').first()
  const productName = (await firstCard.getByRole('heading').textContent())!.trim()
  await firstCard.getByRole('heading').getByRole('link').click()
  await expect(page.getByRole('heading', { level: 1, name: productName })).toBeVisible()
  await expect(page.getByRole('tab', { name: /^Reviews \(\d+\)$/ })).toBeVisible()

  // Browser storage: "recently viewed" is on the wishlist page, also after a reload
  await page.goto('/wishlist')
  await page.reload()
  const recentlyViewed = page.getByRole('region', { name: 'Recently viewed products' })
  await expect(recentlyViewed.getByRole('link', { name: productName })).toBeVisible()
})
