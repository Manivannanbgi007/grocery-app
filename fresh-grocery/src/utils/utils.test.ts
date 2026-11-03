import { describe, expect, it } from 'vitest'
import { addressLines } from './address'
import { cn } from './cn'
import { categoryEmoji, productEmoji, tileBackground } from './emoji'
import { formatDateTime, formatPrice, formatUnit, phoneHref } from './format'
import { appLink, shopLink } from './links'
import { pageItems, pageOfList } from './pagination'

describe('formatPrice', () => {
  it('shows rupees with Indian digit grouping, and paise only when there are some', () => {
    expect(formatPrice(36)).toBe('₹36')
    expect(formatPrice(28.5)).toBe('₹28.50')
    expect(formatPrice(195.5)).toBe('₹195.50')
    expect(formatPrice(125000)).toBe('₹1,25,000')
    expect(formatPrice(0)).toBe('₹0')
  })
})

describe('formatUnit', () => {
  it('turns the backend unit into a short pack size', () => {
    expect(formatUnit('KG', 1)).toBe('1 kg')
    expect(formatUnit('G', 500)).toBe('500 g')
    expect(formatUnit('L', 1)).toBe('1 L')
    expect(formatUnit('ML', 500)).toBe('500 ml')
    expect(formatUnit('PIECE', 1)).toBe('1 pc')
    expect(formatUnit('PIECE', 6)).toBe('6 pcs')
    expect(formatUnit('PACK', 1)).toBe('1 pack')
    expect(formatUnit('DOZEN', 1)).toBe('1 dozen')
    expect(formatUnit('KG', 0.25)).toBe('0.25 kg')
  })
})

describe('formatDateTime', () => {
  it('shows order times in India time, whatever the computer’s time zone', () => {
    expect(formatDateTime('2026-10-26T10:15:00Z')).toBe('26 Oct 2026, 3:45 pm')
  })
})

describe('addressLines', () => {
  it('lists the address parts that are filled in', () => {
    const address = { addressLine1: '18, Cambridge Layout', addressLine2: 'Ulsoor', landmark: null, city: 'Bengaluru', state: 'Karnataka', pincode: '560008' }
    expect(addressLines(address)).toEqual(['18, Cambridge Layout', 'Ulsoor', 'Bengaluru, Karnataka 560008'])
    expect(addressLines({ ...address, addressLine2: ' ', landmark: 'Near the lake' })).toEqual(['18, Cambridge Layout', 'Near the lake', 'Bengaluru, Karnataka 560008'])
  })
})

describe('phoneHref', () => {
  it('keeps only digits and +', () => {
    expect(phoneHref('1800-123-4567')).toBe('tel:18001234567')
    expect(phoneHref('+91 90000 00003')).toBe('tel:+919000000003')
  })
})

describe('emoji placeholders', () => {
  it('finds an emoji from the product name', () => {
    expect(productEmoji('Tomato')).toBe('🍅')
    expect(productEmoji('Amul Taaza Toned Milk')).toBe('🥛')
    expect(productEmoji("Lay's Classic Salted")).toBe('🍟')
    expect(productEmoji('Coca-Cola Soft Drink')).toBe('🥤')
  })

  it('prefers the more specific word', () => {
    expect(productEmoji('Biotique Green Apple Shampoo')).toBe('🧴')
    expect(productEmoji('Amul Vanilla Ice Cream')).toBe('🍨')
    // not milk, egg, tomato or chicken
    expect(productEmoji('Cadbury Dairy Milk Silk')).toBe('🍫')
    expect(productEmoji('Veeba Eggless Mayonnaise')).toBe('🫙')
    expect(productEmoji('Kissan Fresh Tomato Ketchup')).toBe('🥫')
    expect(productEmoji('Pedigree Adult Dog Food Chicken & Vegetables')).toBe('🐶')
  })

  it('only matches whole words from their start', () => {
    // "cola" is inside "chocolate" but does not start a word there
    expect(productEmoji('Dark Chocolate', 'snacks-beverages')).toBe('🍿')
  })

  it('falls back to the category emoji, then to a basket', () => {
    expect(productEmoji('Pomegranate', 'fruits')).toBe('🍎')
    expect(productEmoji('MDH Garam Masala', 'masala-spices')).toBe('🌶️')
    expect(productEmoji('Something new')).toBe('🛒')
    expect(categoryEmoji('unknown-slug')).toBe('🛒')
  })

  it('gives the same id the same pastel tile', () => {
    expect(tileBackground(3)).toBe(tileBackground(3))
    expect(tileBackground(1)).not.toBe(tileBackground(2))
  })
})

describe('links', () => {
  it('builds shop links with only the filters that are set', () => {
    expect(shopLink()).toBe('/shop')
    expect(shopLink({ q: '  milk ' })).toBe('/shop?q=milk')
    expect(shopLink({ q: 'amul butter', category: 3 })).toBe('/shop?q=amul+butter&category=3')
    expect(shopLink({ q: '   ' })).toBe('/shop')
  })

  it('turns backend banner links into pages of this app', () => {
    expect(appLink('/categories/1')).toBe('/shop?category=1')
    expect(appLink('/offers')).toBe('/#offers')
    expect(appLink('/products/5')).toBe('/products/5')
    expect(appLink('https://example.com/sale')).toBe('https://example.com/sale')
    expect(appLink(null)).toBe('/shop')
  })
})

describe('pageItems', () => {
  it('shows every page when there are few', () => {
    expect(pageItems(1, 1)).toEqual([1])
    expect(pageItems(2, 4)).toEqual([1, 2, 3, 4])
  })

  it('skips pages far from the current one', () => {
    expect(pageItems(6, 12)).toEqual([1, 'gap', 5, 6, 7, 'gap', 12])
    expect(pageItems(1, 12)).toEqual([1, 2, 'gap', 12])
    expect(pageItems(12, 12)).toEqual([1, 'gap', 11, 12])
  })

  it('shows a single skipped page instead of a gap', () => {
    expect(pageItems(4, 12)).toEqual([1, 2, 3, 4, 5, 'gap', 12])
  })
})

describe('pageOfList', () => {
  const letters = ['a', 'b', 'c', 'd', 'e']

  it('cuts a list that is in the browser into pages, with the totals of an API page', () => {
    expect(pageOfList(letters, 1, 2)).toEqual({ rows: ['a', 'b'], page: 1, totalElements: 5, totalPages: 3 })
    expect(pageOfList(letters, 3, 2)).toEqual({ rows: ['e'], page: 3, totalElements: 5, totalPages: 3 })
    expect(pageOfList(letters, 1, 10)).toEqual({ rows: letters, page: 1, totalElements: 5, totalPages: 1 })
  })

  it('shows the last page for a page after it, and the first for 0', () => {
    expect(pageOfList(letters, 9, 2)).toEqual({ rows: ['e'], page: 3, totalElements: 5, totalPages: 3 })
    expect(pageOfList(letters, 0, 2).page).toBe(1)
  })

  it('has no pages for an empty list', () => {
    expect(pageOfList([], 2, 10)).toEqual({ rows: [], page: 1, totalElements: 0, totalPages: 0 })
  })
})

describe('cn', () => {
  it('joins the classes that are set', () => {
    const active = false
    expect(cn('a', active && 'b', null, undefined, 'c')).toBe('a c')
  })

  it('lets a later class override an earlier one of the same kind', () => {
    expect(cn('h-10 rounded-lg px-5', 'rounded-full')).toBe('h-10 px-5 rounded-full')
    expect(cn('inline-flex', 'hidden xl:inline-flex')).toBe('hidden xl:inline-flex')
    // Our own theme colours are recognised as colours, so they do not remove a text size
    expect(cn('text-sm text-body', 'text-brand-text')).toBe('text-sm text-brand-text')
  })
})
