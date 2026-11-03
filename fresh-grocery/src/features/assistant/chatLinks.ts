import { matchPath } from 'react-router'
import type { AssistantKind } from './assistantTexts'

/*
 * Links in the assistant's answers. The links under an answer come from the backend (made from the tool results),
 * and an answer's text may hold a Markdown link "[label](target)" or an address "https://...". Neither is trusted:
 * a path is opened only when it is one of the pages below, an address only when it is http(s).
 */

/**
 * The pages each chat may open (React Router patterns, the same as in routes.tsx: chatLinks.test.ts checks that each
 * one exists). The shop's chat opens the shop's pages, a panel's chat only its own panel's pages.
 */
export const CHAT_PAGES: Record<AssistantKind, string[]> = {
  shop: [
    '/',
    '/shop',
    '/products/:productId',
    '/cart',
    '/checkout',
    '/orders',
    '/orders/track',
    '/orders/:orderId',
    '/orders/:orderId/track',
    '/stores',
    '/stores/:storeId',
    '/wishlist',
    '/compare',
    '/account',
    '/account/addresses',
    '/account/notifications',
  ],
  vendor: [
    '/vendor',
    '/vendor/orders',
    '/vendor/orders/:orderId',
    '/vendor/products',
    '/vendor/products/new',
    '/vendor/products/:productId',
    '/vendor/coupons',
    '/vendor/reviews',
    '/vendor/store',
    '/vendor/notifications',
  ],
  delivery: ['/delivery', '/delivery/deliveries/:deliveryId', '/delivery/history', '/delivery/earnings', '/delivery/profile', '/delivery/notifications'],
  admin: [
    '/admin',
    '/admin/stores',
    '/admin/stores/:storeId',
    '/admin/orders',
    '/admin/orders/:orderId',
    '/admin/payments',
    '/admin/deliveries',
    '/admin/partners',
    '/admin/zones',
    '/admin/accounts',
    '/admin/accounts/:userId',
    '/admin/products',
    '/admin/categories',
    '/admin/reviews',
    '/admin/coupons',
    '/admin/banners',
    '/admin/reports',
    '/admin/notifications',
  ],
}

/**
 * The path to open in the app ("/orders/902", "/#offers", "/shop?q=milk"), or null when this chat must not open it:
 * not a path of this site ("//other.site", "https://...", "javascript:..."), a backslash or a space or control
 * character in it (browsers read "/\other.site" as another site), or not one of this chat's pages.
 */
export function safeInternalPath(target: string, kind: AssistantKind): string | null {
  const hasControlCharacter = [...target].some((character) => character.charCodeAt(0) < 32)
  if (!target.startsWith('/') || target.startsWith('//') || /[\\\s]/.test(target) || hasControlCharacter) return null
  let url: URL
  try {
    url = new URL(target, window.location.origin)
  } catch {
    return null
  }
  if (url.origin !== window.location.origin) return null
  const allowed = CHAT_PAGES[kind].some((pattern) => matchPath(pattern, url.pathname) !== null)
  return allowed ? url.pathname + url.search + url.hash : null
}

/** An address of another site to open in a new tab ("https://..."), or null when it is not http(s). */
export function safeExternalUrl(target: string): string | null {
  try {
    const url = new URL(target)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch {
    return null
  }
}

/** One piece of an answer's text: plain text, or a link with its label and its (not yet checked) target. */
export type ChatTextPart = { kind: 'text'; text: string } | { kind: 'link'; label: string; target: string }

/**
 * A Markdown link "[label](target)", or an address "http(s)://..." (its last character is not a full stop, comma,
 * bracket or quote, so "See https://example.com." links without the full stop).
 */
const LINK = /\[([^\]\n]+)\]\(([^)\s]+)\)|(https?:\/\/[^\s<>()[\]]*[^\s<>()[\].,;:!?'"])/g

/** Splits an answer's text into plain text and links, in order. */
export function parseChatText(text: string): ChatTextPart[] {
  const parts: ChatTextPart[] = []
  let last = 0
  for (const match of text.matchAll(LINK)) {
    if (match.index > last) parts.push({ kind: 'text', text: text.slice(last, match.index) })
    const [whole, label, target, address] = match
    parts.push(address ? { kind: 'link', label: address, target: address } : { kind: 'link', label, target })
    last = match.index + whole.length
  }
  if (last < text.length) parts.push({ kind: 'text', text: text.slice(last) })
  return parts
}

/** The text to read aloud: a Markdown link becomes its label ("[your cart](/cart)" -> "your cart"). */
export function speakableText(text: string): string {
  return text.replace(LINK, (whole, label: string | undefined) => label ?? whole)
}
