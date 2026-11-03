import { isApiError } from './client'

/*
 * When React Query asks the server again after a failed request (queries only; a mutation is never repeated by itself).
 */

/**
 * The gateway's rate limit answers 429 with "Retry-After: 1" (second). A burst of requests (a page with many sections,
 * just after logging in) can reach it; a moment later the limit has refilled, so one more try then usually works.
 */
const AFTER_RATE_LIMIT_MS = 1500

/**
 * The app's usual rule: try once more, but not after a 4xx error (not found, not allowed …: the answer would be the same),
 * except 429 (too many requests), which is worth one more try after the wait.
 */
export function retryOnce(failureCount: number, error: Error): boolean {
  if (isApiError(error, 429)) return failureCount < 1
  if (isApiError(error) && error.status >= 400 && error.status < 500) return false
  return failureCount < 1
}

/** How long to wait before asking again: past the gateway's Retry-After after a 429, else React Query's usual back-off. */
export function retryDelay(failureCount: number, error: Error): number {
  return isApiError(error, 429) ? AFTER_RATE_LIMIT_MS : Math.min(1000 * 2 ** failureCount, 30_000)
}

/**
 * customer-service and delivery-service create a new user's profile from the registration in the background, so right
 * after registering the profile answers 404 for a moment. These React Query options ask again (every second, up to 8
 * times) instead of showing an error; other errors keep the app's usual rule (retryOnce).
 */
export const retryWhileProfileIsCreated = {
  retry: (failureCount: number, error: Error) => (isApiError(error, 404) ? failureCount < 8 : retryOnce(failureCount, error)),
  retryDelay: (_failureCount: number, error: Error) => (isApiError(error, 429) ? AFTER_RATE_LIMIT_MS : 1000),
}
