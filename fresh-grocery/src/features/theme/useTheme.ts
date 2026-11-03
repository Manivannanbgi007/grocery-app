import { useEffect, useState } from 'react'
import { applyTheme, DARK_DEVICE, readThemeChoice, saveThemeChoice, type ThemeChoice } from './theme'

/** The user's theme choice: saved in the browser, and put on the page whenever it changes. */
export function useTheme() {
  const [choice, setChoice] = useState<ThemeChoice>(readThemeChoice)

  useEffect(() => {
    saveThemeChoice(choice)
    const device = window.matchMedia(DARK_DEVICE)
    const apply = () => applyTheme(choice === 'dark' || (choice === 'system' && device.matches))
    apply()

    if (choice !== 'system') return
    // "Same as device" also follows a change on the device (e.g. a phone that turns dark in the evening)
    device.addEventListener('change', apply)
    return () => device.removeEventListener('change', apply)
  }, [choice])

  return [choice, setChoice] as const
}
