import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { ThemeSwitcher } from './ThemeSwitcher'
import { useTheme } from './useTheme'

/** jsdom has no matchMedia: a fake device whose dark setting the test can change. */
const device = {
  dark: false,
  listeners: new Set<() => void>(),
  switchTo(dark: boolean) {
    device.dark = dark
    device.listeners.forEach((listener) => listener())
  },
}

beforeEach(() => {
  device.dark = false
  window.matchMedia = () =>
    ({
      get matches() {
        return device.dark
      },
      addEventListener: (_type: string, listener: () => void) => device.listeners.add(listener),
      removeEventListener: (_type: string, listener: () => void) => device.listeners.delete(listener),
    }) as unknown as MediaQueryList
})

afterEach(() => {
  delete document.documentElement.dataset.theme
})

/** The switch as App.tsx uses it. */
function ThemeSwitcherWithState() {
  const [theme, setTheme] = useTheme()
  return <ThemeSwitcher choice={theme} onChange={setTheme} />
}

const pageTheme = () => document.documentElement.dataset.theme

describe('ThemeSwitcher', () => {
  it('switches to the dark theme from the tab and remembers it after a reload', async () => {
    const user = userEvent.setup()
    const { unmount } = render(<ThemeSwitcherWithState />)
    expect(pageTheme()).toBe('light')

    await user.click(screen.getByRole('button', { name: 'Theme: Same as device' }))
    await user.click(screen.getByRole('radio', { name: 'Dark' }))
    expect(pageTheme()).toBe('dark')
    expect(screen.getByRole('button', { name: 'Theme: Dark' })).toHaveAttribute('aria-expanded', 'true')

    // Like closing the page and coming back: the saved choice is used again
    unmount()
    delete document.documentElement.dataset.theme
    render(<ThemeSwitcherWithState />)
    expect(pageTheme()).toBe('dark')
    expect(screen.getByRole('button', { name: 'Theme: Dark' })).toBeInTheDocument()
  })

  it('follows the device by default, also when the device changes', () => {
    device.dark = true
    render(<ThemeSwitcherWithState />)
    expect(pageTheme()).toBe('dark')

    act(() => device.switchTo(false))
    expect(pageTheme()).toBe('light')
  })

  it('stops following the device once a theme is picked, moves with the arrow keys and closes with Escape', async () => {
    const user = userEvent.setup()
    render(<ThemeSwitcherWithState />)
    const tab = screen.getByRole('button', { name: /^Theme/ })

    await user.click(tab)
    await user.click(screen.getByRole('radio', { name: 'Light' }))
    act(() => device.switchTo(true))
    expect(pageTheme()).toBe('light')

    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked()
    expect(pageTheme()).toBe('dark')

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('radio', { name: 'Light' })).not.toBeInTheDocument()
    expect(tab).toHaveFocus()
  })
})
