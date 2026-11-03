/** The theme the user picked. "system" (the default) follows the device's light or dark setting. */
export type ThemeChoice = 'light' | 'dark' | 'system'

/** index.html reads the same key, to set the theme before the page is drawn. */
const STORAGE_KEY = 'fresh-grocery.theme'

/** A media query that matches while the device (computer or phone) uses a dark theme. */
export const DARK_DEVICE = '(prefers-color-scheme: dark)'

export function readThemeChoice(): ThemeChoice {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'light' || saved === 'dark' ? saved : 'system'
  } catch {
    return 'system' // the browser blocks storage (e.g. strict privacy settings)
  }
}

export function saveThemeChoice(choice: ThemeChoice) {
  try {
    if (choice === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, choice)
  } catch {
    // the browser blocks storage: the choice lasts until the page is closed
  }
}

/** Puts the theme on the page: styles/index.css gives the colours their dark values under data-theme="dark". */
export function applyTheme(dark: boolean) {
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}
