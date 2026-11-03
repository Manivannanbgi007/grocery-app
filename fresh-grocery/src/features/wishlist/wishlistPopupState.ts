import { useSyncExternalStore } from 'react'

/*
 * Whether the wishlist pop-up is open. Any heart button can open it, and MainLayout shows it, so the state lives here
 * (outside React) and both read it with useSyncExternalStore, like the lists in hooks/useLocalStorageList.
 */

let open = false
// The button that opened the pop-up: the focus goes back to it when the pop-up closes
let opener: HTMLElement | null = null
const listeners = new Set<() => void>()

function setOpen(value: boolean): void {
  open = value
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Opens the wishlist pop-up (called by the heart buttons after they saved a product). */
export function openWishlistPopup(): void {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  setOpen(true)
}

/** Closes the pop-up; the focus goes back to the button that opened it (if it is still on the page). */
export function closeWishlistPopup(returnFocus = true): void {
  setOpen(false)
  if (returnFocus && opener?.isConnected) opener.focus()
  opener = null
}

export function useWishlistPopupOpen(): boolean {
  return useSyncExternalStore(subscribe, () => open)
}
