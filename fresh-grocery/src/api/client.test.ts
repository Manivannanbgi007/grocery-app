import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../mocks/server'
import { apiAudio, apiDownload, apiRequest, ApiError, isApiError, refreshSession } from './client'
import { clearSession, loadSession, saveSession, SESSION_EXPIRED_EVENT, type Session } from './session'
import type { User } from './types'

const TEST_URL = '*/api/v1/test'

/** Runs a request that must fail and returns its ApiError. */
async function failedRequest(): Promise<ApiError> {
  try {
    await apiRequest('/api/v1/test')
  } catch (error) {
    if (error instanceof ApiError) return error
    throw error
  }
  throw new Error('the request did not fail')
}

const customer: User = {
  id: 4,
  fullName: 'Priya Sharma',
  email: 'customer1@grocery.com',
  phone: '9000000004',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  storeId: null,
  lastLoginAt: null,
  createdAt: '2026-01-01T00:00:00Z',
}

/** Logs in (in memory) with a token that is still good for `minutes` (renewed 3 minutes before its end). */
function loggedIn(accessToken = 'abc.def.ghi', minutes = 10, user: User = customer): Session {
  const session = { accessToken, expiresAt: Date.now() + minutes * 60_000, renewAt: Date.now() + (minutes - 3) * 60_000, user }
  saveSession(session)
  return session
}

/** POST /auth/refresh answers `status` (200: new tokens "fresh.1", "fresh.2" ... for `user`); counts the calls. */
function refreshAnswers(status = 200, user: User = customer) {
  const calls = { count: 0 }
  server.use(
    http.post('*/api/v1/auth/refresh', async () => {
      calls.count++
      await delay(20)
      if (status !== 200) return HttpResponse.json({ success: false, message: 'Your session has ended. Please log in again.', data: null }, { status })
      const data = { accessToken: `fresh.${calls.count}`, tokenType: 'Bearer', expiresInSeconds: 900, user }
      return HttpResponse.json({ success: true, message: 'Token refreshed', data })
    }),
  )
  return calls
}

/** GET /api/v1/test accepts only the renewed tokens ("fresh.…") and answers with the token it got; others get 401. */
function onlyFreshTokens() {
  server.use(
    http.get(TEST_URL, ({ request }) => {
      const authorization = request.headers.get('Authorization')
      if (authorization?.startsWith('Bearer fresh.')) return HttpResponse.json({ success: true, message: 'ok', data: authorization })
      return HttpResponse.json({ success: false, message: 'Your login has expired.', data: null }, { status: 401 })
    }),
  )
}

/** Counts SESSION_EXPIRED_EVENT (the AuthProvider logs out and says so when it comes). */
function countExpiredEvents() {
  const events = { count: 0 }
  const listener = () => events.count++
  window.addEventListener(SESSION_EXPIRED_EVENT, listener)
  return events
}

describe('apiRequest', () => {
  it('returns only the data of the ApiResponse wrapper', async () => {
    server.use(http.get(TEST_URL, () => HttpResponse.json({ success: true, message: 'ok', data: { name: 'Tomato' } })))
    await expect(apiRequest('/api/v1/test')).resolves.toEqual({ name: 'Tomato' })
  })

  it('leaves empty query values out of the URL', async () => {
    let requestedUrl = ''
    server.use(
      http.get(TEST_URL, ({ request }) => {
        requestedUrl = request.url
        return HttpResponse.json({ success: true, message: 'ok', data: null })
      }),
    )
    await apiRequest('/api/v1/test', { query: { q: 'milk', categoryId: undefined, brand: '', page: 0, inStockOnly: false } })
    expect(new URL(requestedUrl).search).toBe('?q=milk&page=0&inStockOnly=false')
  })

  it('throws the backend message and field errors', async () => {
    server.use(
      http.get(TEST_URL, () =>
        HttpResponse.json(
          { success: false, message: 'Validation failed', data: null, errors: [{ field: 'phone', message: 'must be a valid Indian mobile number' }] },
          { status: 400 },
        ),
      ),
    )
    const error = await failedRequest()
    expect(error.status).toBe(400)
    expect(error.message).toBe('Validation failed')
    expect(error.details).toEqual([{ field: 'phone', message: 'must be a valid Indian mobile number' }])
    expect(isApiError(error, 400)).toBe(true)
    expect(isApiError(error, 404)).toBe(false)
  })

  it('explains a 429 from the gateway, which has an empty body', async () => {
    server.use(http.get(TEST_URL, () => new HttpResponse(null, { status: 429 })))
    const error = await failedRequest()
    expect(error.status).toBe(429)
    expect(error.message).toBe('Too many requests. Please wait a moment and try again.')
  })

  it('explains a 503 without a body', async () => {
    server.use(http.get(TEST_URL, () => new HttpResponse('Service Unavailable', { status: 503 })))
    const error = await failedRequest()
    expect(error.message).toBe('This service is not available right now. Please try again shortly.')
  })

  it('reports a network failure as status 0', async () => {
    server.use(http.get(TEST_URL, () => HttpResponse.error()))
    const error = await failedRequest()
    expect(error.status).toBe(0)
    expect(error.message).toMatch(/Cannot reach the server/)
  })

  it('sends the saved login token', async () => {
    let authorization: string | null = null
    server.use(
      http.get(TEST_URL, ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({ success: true, message: 'ok', data: null })
      }),
    )
    loggedIn()
    await apiRequest('/api/v1/test')
    expect(authorization).toBe('Bearer abc.def.ghi')
    // The token lives in memory only, never in the browser's storage
    expect(JSON.stringify({ ...localStorage })).not.toContain('abc.def.ghi')
  })

  it('returns nothing for 204 No Content', async () => {
    server.use(http.post(TEST_URL, () => new HttpResponse(null, { status: 204 })))
    await expect(apiRequest('/api/v1/test', { method: 'POST' })).resolves.toBeUndefined()
  })
})

describe('keeping the login (access token + refresh cookie)', () => {
  it('renews a token that is about to expire before sending it', async () => {
    const refreshes = refreshAnswers()
    onlyFreshTokens()
    loggedIn('old.token', 2)
    await expect(apiRequest('/api/v1/test')).resolves.toBe('Bearer fresh.1')
    expect(refreshes.count).toBe(1)
    expect(loadSession()?.accessToken).toBe('fresh.1')
  })

  it('never sends the token to login, refresh and the other public auth calls', async () => {
    let authorization: string | null = 'not called'
    server.use(
      http.post('*/api/v1/auth/login', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({ success: false, message: 'Invalid email or password', data: null }, { status: 401 })
      }),
    )
    const refreshes = refreshAnswers()
    loggedIn()
    // A 401 from the login is an answer, not an expired token: no refresh, and the login in memory stays
    await expect(apiRequest('/api/v1/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 401 })
    expect(authorization).toBeNull()
    expect(refreshes.count).toBe(0)
    expect(loadSession()).not.toBeNull()
  })

  it('lets 15 calls refused at the same time share one refresh, then sends each of them again', async () => {
    const refreshes = refreshAnswers()
    onlyFreshTokens()
    loggedIn('refused.token')
    const answers = await Promise.all(Array.from({ length: 15 }, () => apiRequest('/api/v1/test')))
    expect(answers).toEqual(Array(15).fill('Bearer fresh.1'))
    expect(refreshes.count).toBe(1)
  })

  it('sends a late 401 again with the token another call has renewed already, without a second refresh', async () => {
    const refreshes = refreshAnswers()
    server.use(
      http.get(TEST_URL, async ({ request }) => {
        const authorization = request.headers.get('Authorization')
        if (authorization?.startsWith('Bearer fresh.')) return HttpResponse.json({ success: true, message: 'ok', data: authorization })
        // While this call was on its way, another one renewed the token
        await refreshSession()
        return HttpResponse.json({ success: false, message: 'Your login has expired.', data: null }, { status: 401 })
      }),
    )
    loggedIn('old.token')
    await expect(apiRequest('/api/v1/test')).resolves.toBe('Bearer fresh.1')
    expect(refreshes.count).toBe(1)
  })

  it('keeps the login when the refresh fails for another reason (503, no network)', async () => {
    refreshAnswers(503)
    onlyFreshTokens()
    const events = countExpiredEvents()
    loggedIn('refused.token')
    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({ status: 503 })
    expect(loadSession()?.accessToken).toBe('refused.token')
    expect(events.count).toBe(0)
  })

  it('logs out when the server refuses the refresh (401)', async () => {
    refreshAnswers(401)
    onlyFreshTokens()
    const events = countExpiredEvents()
    loggedIn('refused.token')
    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({ status: 401 })
    expect(loadSession()).toBeNull()
    expect(events.count).toBe(1)
  })

  it('does not refresh for a guest: a 401 is just the answer', async () => {
    const refreshes = refreshAnswers()
    onlyFreshTokens()
    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({ status: 401 })
    expect(refreshes.count).toBe(0)
  })

  it('throws a refresh away when the user logged out while it was on its way', async () => {
    refreshAnswers()
    loggedIn()
    const refreshing = refreshSession()
    clearSession() // the logout
    await expect(refreshing).resolves.toBeNull()
    expect(loadSession()).toBeNull()
  })

  it('ends the login when the cookie now belongs to another account (logged in as someone else in another tab)', async () => {
    refreshAnswers(200, { ...customer, id: 5, email: 'customer2@grocery.com' })
    const events = countExpiredEvents()
    loggedIn()
    await expect(refreshSession()).resolves.toBeNull()
    expect(loadSession()).toBeNull()
    expect(events.count).toBe(1)
  })

  it('keeps the same user object when the refresh brings the same user (no effects run again)', async () => {
    refreshAnswers()
    const before = loggedIn()
    const after = await refreshSession()
    expect(after?.accessToken).toBe('fresh.1')
    expect(after?.user).toBe(before.user)
  })

  it('renews halfway a token that lives shorter than 6 minutes (no refresh again and again)', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1_000_000)
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json({ success: true, message: 'Token refreshed', data: { accessToken: 'short', tokenType: 'Bearer', expiresInSeconds: 60, user: customer } }),
      ),
    )
    const session = await refreshSession()
    expect(session).toMatchObject({ expiresAt: 1_060_000, renewAt: 1_030_000 })
  })

  it('sends a file upload (FormData) again after the refresh', async () => {
    refreshAnswers()
    server.use(
      http.post(TEST_URL, async ({ request }) => {
        if (request.headers.get('Authorization') !== 'Bearer fresh.1') return HttpResponse.json({ success: false, message: 'expired', data: null }, { status: 401 })
        const form = await request.formData()
        return HttpResponse.json({ success: true, message: 'ok', data: form.get('note') })
      }),
    )
    loggedIn('refused.token')
    const form = new FormData()
    form.append('note', 'proof of delivery')
    await expect(apiRequest('/api/v1/test', { method: 'POST', body: form })).resolves.toBe('proof of delivery')
  })

  it('renews the token for a spoken answer (apiAudio) too', async () => {
    refreshAnswers()
    server.use(
      http.post('*/api/v1/test/speech', ({ request }) => {
        if (request.headers.get('Authorization') !== 'Bearer fresh.1') return HttpResponse.json({ success: false, message: 'expired', data: null }, { status: 401 })
        return new HttpResponse('mp3-bytes', { headers: { 'Content-Type': 'audio/mpeg' } })
      }),
    )
    loggedIn('refused.token')
    const audio = await apiAudio('/api/v1/test/speech', { text: 'Hello' })
    expect(await audio.text()).toBe('mp3-bytes')
  })
})

describe('apiDownload', () => {
  it('returns the file and the name the server gives it, with the login token sent', async () => {
    let authorization: string | null = null
    server.use(
      http.get('*/api/v1/test.pdf', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return new HttpResponse('%PDF-1.4', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="invoice-INV-000009.pdf"' } })
      }),
    )
    loggedIn()
    const file = await apiDownload('/api/v1/test.pdf')
    expect(file.fileName).toBe('invoice-INV-000009.pdf')
    expect(await file.blob.text()).toBe('%PDF-1.4')
    expect(authorization).toBe('Bearer abc.def.ghi')
  })

  it('throws the backend message when there is no file', async () => {
    server.use(http.get('*/api/v1/test.pdf', () => HttpResponse.json({ success: false, message: 'Order 7 has no invoice', data: null }, { status: 400 })))
    await expect(apiDownload('/api/v1/test.pdf')).rejects.toMatchObject({ status: 400, message: 'Order 7 has no invoice' })
  })
})
