import { describe, expect, it } from 'vitest'
import { shopLink } from '../../utils/links'
import { hasActiveFilters, parseShopFilters, priceRangeLabel, SHOP_PAGE_SIZE, toProductSearch } from './shopFilters'

const parse = (query: string) => parseShopFilters(new URLSearchParams(query))

describe('parseShopFilters', () => {
  it('uses the defaults for an empty URL', () => {
    expect(parse('')).toEqual({ inStock: false, sort: 'RELEVANCE', page: 1 })
    expect(hasActiveFilters(parse(''))).toBe(false)
  })

  it('reads every filter', () => {
    expect(parse('q=milk&category=3&brand=Amul&minPrice=20&maxPrice=99.5&inStock=true&sort=NEWEST&page=2')).toEqual({
      q: 'milk',
      category: 3,
      brand: 'Amul',
      minPrice: 20,
      maxPrice: 99.5,
      inStock: true,
      sort: 'NEWEST',
      page: 2,
    })
  })

  it('ignores wrong values instead of failing', () => {
    expect(parse('category=abc&minPrice=-5&maxPrice=lots&sort=CHEAPEST&page=0&q=%20%20')).toEqual({ inStock: false, sort: 'RELEVANCE', page: 1 })
    expect(parse('page=2.5').page).toBe(1)
  })
})

describe('toProductSearch', () => {
  it('turns the 1-based page into the API’s 0-based page and adds the page size', () => {
    expect(toProductSearch(parse('category=2&inStock=true&page=3'))).toEqual({
      q: undefined,
      categoryId: 2,
      brand: undefined,
      minPrice: undefined,
      maxPrice: undefined,
      inStockOnly: true,
      sort: 'RELEVANCE',
      page: 2,
      size: SHOP_PAGE_SIZE,
    })
  })

  it('leaves "in stock only" out when it is not chosen', () => {
    expect(toProductSearch(parse('')).inStockOnly).toBeUndefined()
  })
})

describe('priceRangeLabel', () => {
  it('shows the range, an open end, or nothing', () => {
    expect(priceRangeLabel(parse('minPrice=100&maxPrice=150'))).toBe('₹100 – ₹150')
    expect(priceRangeLabel(parse('maxPrice=150'))).toBe('₹0 – ₹150')
    expect(priceRangeLabel(parse('minPrice=100'))).toBe('₹100 – any')
    expect(priceRangeLabel(parse(''))).toBeUndefined()
  })
})

describe('shopLink with every filter', () => {
  it('writes the filters back in the same form, leaving the defaults out', () => {
    const query = 'q=milk&category=3&brand=Amul&minPrice=0&maxPrice=99.5&inStock=true&sort=NEWEST&page=2'
    expect(shopLink(parse(query))).toBe(`/shop?${query}`)
    expect(shopLink(parse('sort=RELEVANCE&page=1&inStock=false'))).toBe('/shop')
  })
})
