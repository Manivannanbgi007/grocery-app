import type { ApiErrorDetail } from '../api/types'

/**
 * Thrown by the mock services (carts, orders, addresses ...); the handlers turn it into the backend's error response
 * with this status, message and field errors.
 */
export class MockApiError extends Error {
  readonly status: number
  readonly details: ApiErrorDetail[]

  constructor(status: number, message: string, details: ApiErrorDetail[] = []) {
    super(message)
    this.status = status
    this.details = details
  }
}

/** The backend's answer to a request body that breaks a @Valid rule: 400 "Validation failed" with the field errors. */
export function validationFailed(details: ApiErrorDetail[]): MockApiError {
  return new MockApiError(400, 'Validation failed', details)
}
