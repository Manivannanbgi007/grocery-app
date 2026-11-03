import { delay, http } from 'msw'
import type { ReviewRequest } from '../../api/reviews'
import type { Product, ProductSort } from '../../api/types'
import { activeCategories, addedAt, catalogProduct, catalogProducts } from '../mockCatalog'
import { allMockReviews, writeReview } from '../mockRatings'
import { asCustomer } from './withRole'
import { fail, intParam, ok, paginate } from './respond'

/*
 * Mock of product-read-service: categories, product search and product details; and the product reviews of product-service.
 * Only what the screens need; the real filtering and sorting rules live in the backend.
 * The "*" at the start of each URL matches any host (localhost:5173, the gateway ...).
 */

/** Products customers can see (the API never returns INACTIVE or BLOCKED ones). The vendor's changes are included (mockCatalog.ts). */
function visibleProducts(): Product[] {
  return catalogProducts().filter((product) => product.status === 'ACTIVE')
}

/** The lower-case words of a text: "Lay's Classic" -> ["lay's", "classic"] (whole words, like Elasticsearch). */
function words(text: string | null): string[] {
  return (text ?? '').toLowerCase().split(/[^a-z0-9']+/).filter(Boolean)
}

/** How well a product matches the search words: a match in the name counts most, as in the backend. */
function matchScore(product: Product, searchWords: string[]): number {
  const count = (text: string | null) => words(text).filter((word) => searchWords.includes(word)).length
  return count(product.name) * 3 + count(product.brand) * 2 + count(product.categoryName) * 2 + count(product.description)
}

function sortProducts(products: Product[], sort: ProductSort, scores: Map<number, number> | null): Product[] {
  const byId = (a: Product, b: Product) => a.id - b.id
  const sorted = [...products]
  switch (sort) {
    case 'PRICE_LOW_TO_HIGH':
      return sorted.sort((a, b) => a.sellingPrice - b.sellingPrice || byId(a, b))
    case 'PRICE_HIGH_TO_LOW':
      return sorted.sort((a, b) => b.sellingPrice - a.sellingPrice || byId(a, b))
    case 'NEWEST':
      return sorted.sort((a, b) => addedAt(b.id) - addedAt(a.id) || byId(a, b))
    case 'RATING':
      // Best rated first; products without reviews last
      return sorted.sort((a, b) => (b.ratingAverage ?? -1) - (a.ratingAverage ?? -1) || b.ratingCount - a.ratingCount || byId(a, b))
    case 'RELEVANCE':
      if (scores) return sorted.sort((a, b) => (scores.get(b.id) ?? 0) - (scores.get(a.id) ?? 0) || byId(a, b))
      return sorted.sort((a, b) => a.name.localeCompare(b.name))
    case 'NAME':
      return sorted.sort((a, b) => a.name.localeCompare(b.name))
  }
}

const SORTS: ProductSort[] = ['RELEVANCE', 'PRICE_LOW_TO_HIGH', 'PRICE_HIGH_TO_LOW', 'NAME', 'NEWEST', 'RATING']

export const catalogHandlers = [
  http.get('*/api/v1/categories', async () => {
    await delay()
    return ok(activeCategories(), 'Categories fetched successfully')
  }),

  http.get('*/api/v1/products', async ({ request }) => {
    await delay()
    const params = new URL(request.url).searchParams
    const page = intParam(params, 'page', 0)
    const size = intParam(params, 'size', 20)
    if (page < 0 || size < 1 || size > 100) {
      return fail(400, 'Validation failed', request, [{ field: 'size', message: 'must be between 1 and 100' }])
    }

    let products = visibleProducts()
    const categoryId = params.get('categoryId')
    const storeId = params.get('storeId')
    const brand = params.get('brand')?.trim()
    const minPrice = params.get('minPrice')
    const maxPrice = params.get('maxPrice')
    if (categoryId) products = products.filter((p) => p.categoryId === Number(categoryId))
    if (storeId) products = products.filter((p) => p.storeId === Number(storeId))
    if (brand) products = products.filter((p) => p.brand === brand)
    if (minPrice) products = products.filter((p) => p.sellingPrice >= Number(minPrice))
    if (maxPrice) products = products.filter((p) => p.sellingPrice <= Number(maxPrice))
    if (params.get('inStockOnly') === 'true') products = products.filter((p) => p.inStock)

    // Search text: keep products that match at least one word
    let scores: Map<number, number> | null = null
    const searchWords = words(params.get('q'))
    if (searchWords.length > 0) {
      scores = new Map(products.map((p) => [p.id, matchScore(p, searchWords)]))
      products = products.filter((p) => (scores?.get(p.id) ?? 0) > 0)
    }

    const sortParam = params.get('sort') as ProductSort | null
    const sort = sortParam && SORTS.includes(sortParam) ? sortParam : 'RELEVANCE'
    return ok(paginate(sortProducts(products, sort, scores), page, size), 'Products fetched successfully')
  }),

  // The search box's suggestions (before ":productId", which would take "suggestions" for an id). The backend asks
  // Elasticsearch (partly typed words, any order, small typos); the mock keeps it simple: every typed word must begin a
  // word of the name, brand or category, and matches in the name count most.
  http.get('*/api/v1/products/suggestions', async ({ request }) => {
    await delay()
    const params = new URL(request.url).searchParams
    const typedWords = words(params.get('q'))
    if (typedWords.length === 0) return fail(400, 'Validation failed', request, [{ field: 'q', message: 'must not be blank' }])
    const categoryId = params.get('categoryId')
    const begins = (text: string | null, word: string) => words(text).some((part) => part.startsWith(word))
    const score = (product: Product) => {
      const found = typedWords.every((word) => begins(product.name, word) || begins(product.brand, word) || begins(product.categoryName, word))
      return found ? 1 + typedWords.filter((word) => begins(product.name, word)).length * 3 : 0
    }
    const suggested = visibleProducts()
      .filter((product) => !categoryId || product.categoryId === Number(categoryId))
      .filter((product) => score(product) > 0)
      .sort((a, b) => score(b) - score(a) || a.name.length - b.name.length || a.name.localeCompare(b.name))
    return ok(suggested.slice(0, intParam(params, 'size', 6)), 'Suggestions fetched successfully')
  }),

  http.get('*/api/v1/products/:productId', async ({ params, request }) => {
    await delay()
    const product = visibleProducts().find((p) => p.id === Number(params.productId))
    if (!product) return fail(404, `Product not found with id: ${String(params.productId)}`, request)
    return ok(product, 'Product fetched successfully')
  }),

  // product-service: visible reviews, newest first (Spring's default for this endpoint: size 10, sorted by id descending)
  http.get('*/api/v1/products/:productId/reviews', async ({ params, request }) => {
    await delay()
    const productId = Number(params.productId)
    if (!catalogProduct(productId)) {
      return fail(404, `Product not found with id: ${String(params.productId)}`, request)
    }
    const search = new URL(request.url).searchParams
    const reviews = allMockReviews().filter((review) => review.productId === productId && review.status === 'VISIBLE').sort((a, b) => b.id - a.id)
    return ok(paginate(reviews, intParam(search, 'page', 0), intParam(search, 'size', 10)), 'Reviews fetched successfully')
  }),

  // product-service: a customer reviews a product of a delivered order (rules in mockRatings.ts)
  http.post(
    '*/api/v1/products/:productId/reviews',
    asCustomer(async (customer, request, params) => {
      const body = (await request.json()) as Partial<ReviewRequest>
      return { data: writeReview(customer.id, Number(params.productId), body), message: 'Thank you for your review', status: 201 }
    }),
  ),
]
