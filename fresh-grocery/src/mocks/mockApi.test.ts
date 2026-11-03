import { describe, expect, it } from 'vitest'
import { getCategories } from '../api/categories'
import { isApiError } from '../api/client'
import { getCoupons } from '../api/coupons'
import { getProduct, searchProducts } from '../api/products'
import { getProductReviews } from '../api/reviews'
import { getStores } from '../api/stores'
import { demoCategories } from './data/categories'
import { demoProductRows, toProduct } from './data/products'
import { demoReviews } from './data/reviews'

/* The demo catalog and the mock API: the data must match the backend seed, the mocks the backend's rules. */

describe('demo catalog', () => {
  it('has unique product ids and SKUs, and only known categories and stores', () => {
    const ids = demoProductRows.map((row) => row.id)
    const skus = demoProductRows.map((row) => row.sku)
    expect(new Set(ids).size).toBe(ids.length)
    expect(new Set(skus).size).toBe(skus.length)
    for (const row of demoProductRows) {
      expect(demoCategories.some((category) => category.id === row.categoryId)).toBe(true)
      expect([1, 2]).toContain(row.storeId)
    }
  })

  it('keeps the backend seed prices (ids 1-17)', () => {
    const product = (id: number) => toProduct(demoProductRows.find((row) => row.id === id)!)
    expect(product(1).sellingPrice).toBe(36)
    expect(product(3).sellingPrice).toBe(28.5)
    expect(product(10).sellingPrice).toBe(195.5)
    expect(product(12).sellingPrice).toBe(42.75)
    expect(product(13)).toMatchObject({ sellingPrice: 160, inStock: false })
    expect(product(7)).toMatchObject({ availableQuantity: 3, lowStock: true })
  })

  it('has reviews only from the store that sells the product', () => {
    for (const review of demoReviews) {
      const row = demoProductRows.find((product) => product.id === review.productId)
      expect(row?.storeId, `review ${review.id}`).toBe(review.storeId)
    }
  })
})

describe('mock product API', () => {
  it('lists the categories in display order', async () => {
    const categories = await getCategories()
    expect(categories.map((category) => category.name).slice(0, 3)).toEqual(['Vegetables', 'Fruits', 'Dairy & Eggs'])
  })

  it('never returns inactive products', async () => {
    const all = await searchProducts({ size: 100 })
    expect(all.content.some((product) => product.id === 17)).toBe(false)
    await expect(getProduct(17)).rejects.toSatisfy((error) => isApiError(error, 404))
  })

  it('searches whole words in name, brand, category and description', async () => {
    const amul = await searchProducts({ q: 'amul' })
    expect(amul.content.length).toBeGreaterThan(0)
    expect(amul.content.every((product) => product.brand === 'Amul')).toBe(true)

    const chips = await searchProducts({ q: 'chips' })
    expect(chips.content.map((product) => product.name)).toContain("Lay's Classic Salted")
  })

  it('filters by category, price and stock', async () => {
    const fruits = await searchProducts({ categoryId: 2, size: 100 })
    expect(fruits.content.every((product) => product.categoryId === 2)).toBe(true)

    const cheap = await searchProducts({ maxPrice: 30, size: 100 })
    expect(cheap.content.every((product) => product.sellingPrice <= 30)).toBe(true)

    const inStock = await searchProducts({ inStockOnly: true, size: 100 })
    expect(inStock.content.every((product) => product.inStock)).toBe(true)
  })

  it('sorts and pages like Spring Data (page starts at 0)', async () => {
    const firstPage = await searchProducts({ sort: 'PRICE_LOW_TO_HIGH', size: 5 })
    const prices = firstPage.content.map((product) => product.sellingPrice)
    expect(prices).toEqual([...prices].sort((a, b) => a - b))
    expect(firstPage).toMatchObject({ page: 0, size: 5, last: false })

    const lastPage = await searchProducts({ size: 5, page: firstPage.totalPages - 1 })
    expect(lastPage.last).toBe(true)

    const rated = await searchProducts({ sort: 'RATING', size: 3 })
    expect(rated.content.every((product) => product.ratingAverage !== null)).toBe(true)
  })

  it('rejects a page size above 100', async () => {
    await expect(searchProducts({ size: 101 })).rejects.toSatisfy((error) => isApiError(error, 400))
  })

  it('returns the visible reviews of a product, newest first', async () => {
    const reviews = await getProductReviews(1, 0, 10)
    expect(reviews.content.map((review) => review.id)).toEqual([102, 101])
    expect((await getProductReviews(2, 0, 10)).totalElements).toBe(0) // Onion has none
    await expect(getProductReviews(999, 0, 10)).rejects.toSatisfy((error) => isApiError(error, 404))
  })
})

describe('mock store and coupon API', () => {
  it('lists the stores', async () => {
    const stores = await getStores()
    expect(stores.content.map((store) => store.name)).toEqual(['Fresh Mart', 'Green Basket'])
  })

  it('returns platform coupons, plus a store’s own coupons when asked', async () => {
    expect((await getCoupons()).map((coupon) => coupon.code)).toEqual(['WELCOME50', 'SAVE10'])
    expect((await getCoupons(1)).map((coupon) => coupon.code)).toEqual(['WELCOME50', 'SAVE10', 'FRESH20'])
  })
})
