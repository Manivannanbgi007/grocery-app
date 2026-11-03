import { useState } from 'react'

const STORAGE_KEY = 'fresh-grocery.menu-collapsed'

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false // the browser blocks storage (e.g. strict privacy settings)
  }
}

/**
 * Whether the panels' menu is collapsed to a column of icons (big screens only), and a function that switches it.
 * The choice is saved in the browser, so the menu stays as it was on the next page and the next visit.
 */
export function useCollapsedMenu() {
  const [collapsed, setCollapsed] = useState(readCollapsed)

  function toggle() {
    const next = !collapsed
    setCollapsed(next)
    try {
      if (next) localStorage.setItem(STORAGE_KEY, 'true')
      else localStorage.removeItem(STORAGE_KEY)
    } catch {
      // the browser blocks storage: the choice lasts until the page is closed
    }
  }

  return [collapsed, toggle] as const
}
