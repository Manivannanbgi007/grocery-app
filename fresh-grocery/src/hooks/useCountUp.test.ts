import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useCountUp } from './useCountUp'

/** Like a browser where "reduce motion" is off (allowed = true) or on (false). */
function motion(allowed: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: allowed && query === '(prefers-reduced-motion: no-preference)' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useCountUp', () => {
  it('counts up from 0 to the number when the system allows motion', async () => {
    motion(true)
    const { result } = renderHook(() => useCountUp(120))
    expect(result.current).toBe(0)
    await waitFor(() => expect(result.current).toBe(120))
  })

  it('counts from the old number to a new one', async () => {
    motion(true)
    const { result, rerender } = renderHook(({ target }) => useCountUp(target), { initialProps: { target: 10 } })
    await waitFor(() => expect(result.current).toBe(10))
    rerender({ target: 4 })
    await waitFor(() => expect(result.current).toBe(4))
  })

  it('shows the number at once with "reduce motion" (and in browsers without matchMedia)', () => {
    motion(false)
    expect(renderHook(() => useCountUp(120)).result.current).toBe(120)
    vi.unstubAllGlobals()
    expect(renderHook(() => useCountUp(7)).result.current).toBe(7)
  })
})
