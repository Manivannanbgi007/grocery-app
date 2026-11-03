import type { RouteObject } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../../routes'
import { CHAT_PAGES, parseChatText, safeExternalUrl, safeInternalPath, speakableText } from './chatLinks'

/** Every page path of routes.tsx ("/", "/shop", "/orders/:orderId", "/vendor" for the panel's start page ...). */
function routePaths(children: RouteObject[], parent = ''): string[] {
  return children.flatMap((route) => {
    const path = route.path === undefined ? parent : route.path.startsWith('/') ? route.path : `${parent.replace(/\/$/, '')}/${route.path}`
    const own = route.path !== undefined || route.index ? [path || '/'] : []
    return [...own, ...routePaths(route.children ?? [], path)]
  })
}

describe('The pages a chat may open', () => {
  it('are all pages of the app (the list follows routes.tsx)', () => {
    const paths = routePaths(routes)
    for (const pattern of Object.values(CHAT_PAGES).flat()) expect(paths).toContain(pattern)
  })

  it("opens the shop's pages from the shop's chat, with a query or an anchor", () => {
    expect(safeInternalPath('/wishlist', 'shop')).toBe('/wishlist')
    expect(safeInternalPath('/orders/902', 'shop')).toBe('/orders/902')
    expect(safeInternalPath('/shop?q=milk', 'shop')).toBe('/shop?q=milk')
    expect(safeInternalPath('/#offers', 'shop')).toBe('/#offers')
  })

  it("opens a panel's own pages only from that panel's chat", () => {
    expect(safeInternalPath('/vendor/orders', 'vendor')).toBe('/vendor/orders')
    expect(safeInternalPath('/vendor/orders', 'shop')).toBeNull()
    expect(safeInternalPath('/admin/reports', 'vendor')).toBeNull()
    expect(safeInternalPath('/cart', 'admin')).toBeNull()
  })

  it('refuses other sites, scripts, tricks and unknown pages', () => {
    for (const target of ['//evil.example/cart', '/\\evil.example', 'https://evil.example/cart', 'javascript:alert(1)', '/nope', '/cart\n', ' /cart', '/ca rt', 'cart']) {
      expect(safeInternalPath(target, 'shop'), target).toBeNull()
    }
  })

  it('opens only http(s) addresses of other sites', () => {
    expect(safeExternalUrl('https://example.com/help')).toBe('https://example.com/help')
    expect(safeExternalUrl('http://example.com')).toBe('http://example.com/')
    for (const target of ['javascript:alert(1)', 'data:text/html,<b>hi</b>', 'mailto:help@example.com', '/cart', 'example.com']) {
      expect(safeExternalUrl(target), target).toBeNull()
    }
  })
})

describe('The links in an answer', () => {
  it('finds Markdown links and addresses (without a full stop at the end)', () => {
    expect(parseChatText('See [your cart](/cart) or https://example.com/help. Thanks!')).toEqual([
      { kind: 'text', text: 'See ' },
      { kind: 'link', label: 'your cart', target: '/cart' },
      { kind: 'text', text: ' or ' },
      { kind: 'link', label: 'https://example.com/help', target: 'https://example.com/help' },
      { kind: 'text', text: '. Thanks!' },
    ])
    expect(parseChatText('No links here: ₹56 [sic].')).toEqual([{ kind: 'text', text: 'No links here: ₹56 [sic].' }])
  })

  it('reads a Markdown link as its label', () => {
    expect(speakableText('Open [your cart](/cart) to pay.')).toBe('Open your cart to pay.')
  })
})
