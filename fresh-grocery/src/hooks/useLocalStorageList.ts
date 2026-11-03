import { useCallback, useSyncExternalStore } from 'react'

/*
 * A list that is saved in the browser (localStorage), e.g. the wishlist or "recently viewed".
 *
 * Every component that uses the same key sees the same list and re-renders when it changes:
 * the header's wishlist counter updates as soon as a heart button is clicked anywhere.
 * Other browser tabs are kept in sync through the browser's "storage" event.
 *
 * How it works: the lists live in `savedLists` (in memory, loaded once from localStorage).
 * React's useSyncExternalStore reads them and subscribes to changes.
 */

type Listener = () => void

const savedLists = new Map<string, unknown[]>()
const listeners = new Map<string, Set<Listener>>()
const EMPTY_LIST: unknown[] = []

function parseList(json: string | null): unknown[] {
  if (!json) return []
  try {
    const value: unknown = JSON.parse(json)
    return Array.isArray(value) ? value : []
  } catch {
    return [] // a broken value: start with an empty list
  }
}

function readList(key: string): unknown[] {
  if (!savedLists.has(key)) {
    let json: string | null = null
    try {
      json = localStorage.getItem(key)
    } catch {
      // Storage blocked: the list works in memory only
    }
    savedLists.set(key, parseList(json))
  }
  return savedLists.get(key) ?? EMPTY_LIST
}

function notify(key: string): void {
  listeners.get(key)?.forEach((listener) => listener())
}

function writeList(key: string, list: unknown[]): void {
  savedLists.set(key, list)
  try {
    localStorage.setItem(key, JSON.stringify(list))
  } catch {
    // Storage full or blocked: the list still works until the page is reloaded
  }
  notify(key)
}

function subscribe(key: string, listener: Listener): () => void {
  if (!listeners.has(key)) listeners.set(key, new Set())
  listeners.get(key)?.add(listener)
  return () => listeners.get(key)?.delete(listener)
}

// Another tab changed a list: load the new value and update this tab too
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key && savedLists.has(event.key)) {
      savedLists.set(event.key, parseList(event.newValue))
      notify(event.key)
    }
  })
}

/** For tests: forget the in-memory copies, so the next read loads localStorage again. */
export function resetLocalStorageLists(): void {
  savedLists.clear()
}

/**
 * @param key      the localStorage key, e.g. "fresh-grocery.wishlist"
 * @param maxItems the list never grows beyond this; the oldest items are dropped
 * @param getId    how to recognise an item, so it is never in the list twice
 */
export function useLocalStorageList<T>(key: string, maxItems: number, getId: (item: T) => number | string) {
  const items = useSyncExternalStore(
    useCallback((listener: Listener) => subscribe(key, listener), [key]),
    () => readList(key) as T[],
  )

  /** Puts the item first (moving it up if it is already in the list). */
  function add(item: T): void {
    const others = (readList(key) as T[]).filter((existing) => getId(existing) !== getId(item))
    writeList(key, [item, ...others].slice(0, maxItems))
  }

  function remove(id: number | string): void {
    writeList(key, (readList(key) as T[]).filter((existing) => getId(existing) !== id))
  }

  function has(id: number | string): boolean {
    return items.some((existing) => getId(existing) === id)
  }

  function clear(): void {
    writeList(key, [])
  }

  return { items, add, remove, has, clear }
}
