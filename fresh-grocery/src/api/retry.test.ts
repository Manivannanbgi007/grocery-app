import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { retryDelay, retryOnce, retryWhileProfileIsCreated } from './retry'

const error = (status: number) => new ApiError(status, `HTTP ${status}`)

describe('retry rules', () => {
  it('tries once more after a server error or "too many requests", never after another 4xx', () => {
    expect(retryOnce(0, error(500))).toBe(true)
    expect(retryOnce(1, error(500))).toBe(false)
    expect(retryOnce(0, error(429))).toBe(true)
    expect(retryOnce(1, error(429))).toBe(false)
    expect(retryOnce(0, error(404))).toBe(false)
    expect(retryOnce(0, error(403))).toBe(false)
    expect(retryOnce(0, new Error('network down'))).toBe(true)
  })

  it('waits past the gateway\'s Retry-After (1 s) after a 429', () => {
    expect(retryDelay(0, error(429))).toBeGreaterThan(1000)
    expect(retryDelay(0, error(500))).toBe(1000)
    expect(retryDelay(1, error(500))).toBe(2000)
  })

  it('keeps asking for a profile that is still being created, and also retries a 429', () => {
    expect(retryWhileProfileIsCreated.retry(7, error(404))).toBe(true)
    expect(retryWhileProfileIsCreated.retry(8, error(404))).toBe(false)
    expect(retryWhileProfileIsCreated.retry(0, error(429))).toBe(true)
    expect(retryWhileProfileIsCreated.retryDelay(0, error(429))).toBeGreaterThan(1000)
    expect(retryWhileProfileIsCreated.retry(0, error(403))).toBe(false)
  })
})
