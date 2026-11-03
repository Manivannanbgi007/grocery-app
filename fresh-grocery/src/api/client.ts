import { clearSession, loadSession, saveSession, SESSION_EXPIRED_EVENT, sessionFrom, sessionGeneration, type Session } from './session'
import type { ApiErrorDetail, ApiResponse, AuthResult } from './types'

/*
 * The one place that calls the backend with fetch() (only monitoring.ts asks Prometheus directly).
 *
 * Every backend response is wrapped:  { success, message, data, errors, ... }
 * apiRequest() returns only `data` when the call worked, and throws an ApiError when it did not,
 * so the rest of the app never looks at the wrapper or at HTTP status codes directly.
 *
 * It also keeps the login going: the access token lasts 15 minutes, and a new one comes from POST /auth/refresh
 * (the browser sends the HttpOnly refresh cookie with it). See send() and refreshSession().
 */

/** Empty in development (the Vite proxy forwards /api to the gateway); see .env.example. */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

const REFRESH_PATH = '/api/v1/auth/refresh'
/**
 * The auth calls that work without a login. They never get the access token (an old one must not get in the way of
 * logging in again), and their 401 is an answer ("Invalid email or password"), not an expired token.
 */
const PUBLIC_AUTH_PATHS = new Set([
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  REFRESH_PATH,
  '/api/v1/auth/logout',
  '/api/v1/auth/forgot-password',
  '/api/v1/auth/reset-password',
  // Social login (api/socialAuth.ts)
  '/api/v1/auth/social/providers',
  '/api/v1/auth/social/session',
  '/api/v1/auth/social/signup',
  '/api/v1/auth/social/link',
])

const NETWORK_ERROR = 'Cannot reach the server. Check your internet connection and try again.'
const TOO_MANY_REQUESTS = 'Too many requests. Please wait a moment and try again.'
const SERVICE_UNAVAILABLE = 'This service is not available right now. Please try again shortly.'
const UNKNOWN_ERROR = 'Something went wrong. Please try again.'

/** A failed API call. `status` is the HTTP status, or 0 when the server could not be reached. */
export class ApiError extends Error {
  readonly status: number
  /** field errors from the backend, e.g. { field: "phone", message: "..." } */
  readonly details: ApiErrorDetail[]

  constructor(status: number, message: string, details: ApiErrorDetail[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

/** True when `error` is an ApiError (with this HTTP status, when one is given). */
export function isApiError(error: unknown, status?: number): error is ApiError {
  return error instanceof ApiError && (status === undefined || error.status === status)
}

type QueryValue = string | number | boolean | null | undefined

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** added to the URL; null/undefined/empty values are left out */
  query?: Record<string, QueryValue>
  /** sent as JSON; a FormData (file upload) is sent as it is */
  body?: unknown
  /** lets React Query cancel a request that is no longer needed */
  signal?: AbortSignal
  /** the browser finishes the request even when the page is closed or reloaded (used by the logout) */
  keepalive?: boolean
  /** extra headers, e.g. the Idempotency-Key of an order (Accept, Content-Type and Authorization are set here) */
  headers?: Record<string, string>
}

/** Calls the backend and returns the `data` of its response. Throws ApiError when the call fails. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, 'application/json')
  // 204 No Content: done, nothing to return (the logout answers like this)
  if (response.status === 204) return undefined as T
  const json = await readJson<T>(response)
  if (!response.ok || !json?.success) {
    throw new ApiError(response.status, errorMessage(response.status, json), json?.errors ?? [])
  }
  return json.data
}

/** A downloaded file, e.g. an invoice PDF. `fileName` comes from the server ("invoice-INV-000009.pdf"). */
export interface DownloadedFile {
  blob: Blob
  fileName: string | null
}

/** Downloads a file that needs the login (a plain link cannot send the token). Errors arrive as JSON: ApiError. */
export async function apiDownload(path: string, signal?: AbortSignal): Promise<DownloadedFile> {
  const response = await send(path, { signal }, 'application/pdf, application/json')
  if (!response.ok) {
    const json = await readJson<unknown>(response)
    throw new ApiError(response.status, errorMessage(response.status, json), json?.errors ?? [])
  }
  return { blob: await response.blob(), fileName: fileNameOf(response.headers.get('Content-Disposition')) }
}

/** Sends a JSON body and gets audio back (the assistant's answer read aloud, audio/mpeg). Errors arrive as JSON: ApiError. */
export async function apiAudio(path: string, body: unknown, signal?: AbortSignal): Promise<Blob> {
  const response = await send(path, { method: 'POST', body, signal }, 'audio/mpeg, application/json')
  if (!response.ok) {
    const json = await readJson<unknown>(response)
    throw new ApiError(response.status, errorMessage(response.status, json), json?.errors ?? [])
  }
  return response.blob()
}

/**
 * Opens a response that stays open and streams events (Server-Sent Events, text/event-stream), with the login token.
 * The browser's own EventSource cannot send the token, so the stream is read with fetch (see api/eventStream.ts).
 * Returns the open response; an error answer (404, 401 ...) throws an ApiError like apiRequest.
 * It accepts JSON too: errors come as JSON, and a server asked for text/event-stream alone cannot send one (Spring
 * then answers 500 instead of the real 404 or 403).
 */
export async function apiStream(path: string, signal: AbortSignal): Promise<Response> {
  const response = await send(path, { signal }, 'text/event-stream, application/json')
  if (!response.ok || !response.body) {
    const json = await readJson<unknown>(response)
    throw new ApiError(response.status, errorMessage(response.status, json), json?.errors ?? [])
  }
  return response
}

/** The refresh on its way, shared by every call that needs one: never more than one POST /auth/refresh at a time. */
let refreshing: Promise<Session | null> | null = null

/**
 * Gets a new access token with the refresh cookie and saves it (the server also sends a new cookie).
 * Resolves with the new session, or with null when the server ended the login (401: logged out, expired, revoked,
 * or the old cookie was used again). Throws an ApiError when the server cannot answer (network, 429, 5xx): the login is
 * kept then, and the next call tries again.
 */
export function refreshSession(): Promise<Session | null> {
  refreshing ??= askForNewToken().finally(() => {
    refreshing = null
  })
  return refreshing
}

async function askForNewToken(): Promise<Session | null> {
  const startedAt = sessionGeneration()
  let result: AuthResult | null = null
  try {
    result = await apiRequest<AuthResult>(REFRESH_PATH, { method: 'POST' })
  } catch (error) {
    if (!isApiError(error, 401)) throw error
  }
  // Logged out (or in) while the refresh was on its way: that newer state wins, the answer is thrown away
  if (sessionGeneration() !== startedAt) return loadSession()

  const previous = loadSession()
  // The login ended, or the cookie now belongs to another account (logged in as someone else in another tab)
  if (!result || (previous && previous.user.id !== result.user.id)) {
    clearSession()
    // Only a user who was logged in hears about it; a guest's check after a reload simply stays a guest
    if (previous) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    return null
  }
  const session = sessionFrom(result, previous)
  saveSession(session)
  return session
}

/** The login to send with a call: waits for a refresh on its way, and renews a token that is about to expire. */
async function currentSession(): Promise<Session | null> {
  if (refreshing) await refreshing.catch(() => null)
  const session = loadSession()
  if (!session || Date.now() < session.renewAt) return session
  try {
    return await refreshSession()
  } catch {
    // The server could not answer: try the current token, it may still work (a 401 tries the refresh once more)
    return loadSession()
  }
}

/**
 * Sends a request with the login token. When the server refuses the token (401), it gets a new one and sends the
 * request once more; only when the refresh is refused too is the user logged out (see askForNewToken).
 */
async function send(path: string, options: RequestOptions, accept: string): Promise<Response> {
  if (PUBLIC_AUTH_PATHS.has(path)) return sendOnce(path, options, accept, null)

  const session = await currentSession()
  const response = await sendOnce(path, options, accept, session)
  if (response.status !== 401 || !session) return response

  // Another call may have renewed the token already; otherwise ask for a new one (shared with the other calls)
  const latest = loadSession()
  const renewed = latest && latest.accessToken !== session.accessToken ? latest : await refreshSession()
  // null: the login has ended (the app logs out); the 401 answer is passed on to the caller
  if (!renewed) return response
  return sendOnce(path, options, accept, renewed)
}

/** One fetch() with the token of `session` (none for a guest). A server that cannot be reached becomes ApiError(0). */
async function sendOnce(path: string, options: RequestOptions, accept: string, session: Session | null): Promise<Response> {
  const { method = 'GET', query, body, signal, keepalive, headers: extraHeaders } = options

  const headers: Record<string, string> = { ...extraHeaders, Accept: accept }
  const isForm = body instanceof FormData
  // A FormData gets its Content-Type (multipart, with the boundary) from the browser
  if (body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json'
  }
  if (session) {
    headers.Authorization = `Bearer ${session.accessToken}`
  }

  try {
    return await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
      signal,
      keepalive,
    })
  } catch (error) {
    // A cancelled request is not an error worth showing: pass it on unchanged
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, NETWORK_ERROR)
  }
}

/** 'attachment; filename="invoice-INV-000009.pdf"' -> "invoice-INV-000009.pdf" */
function fileNameOf(contentDisposition: string | null): string | null {
  return /filename="?([^";]+)"?/.exec(contentDisposition ?? '')?.[1] ?? null
}

/** The address of a backend path for a full page visit (not fetch), e.g. the social login redirect: "/api/v1/..." locally. */
export function apiUrl(path: string): string {
  return API_BASE_URL + path
}

/** "/api/v1/products" + { q: "milk", page: 0 } -> "http://localhost:5173/api/v1/products?q=milk&page=0" */
function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(API_BASE_URL + path, window.location.origin)
  for (const [name, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(name, String(value))
    }
  }
  return url.toString()
}

/** The parsed body, or null when it is empty or not JSON (the gateway sends 429 with an empty body). */
async function readJson<T>(response: Response): Promise<ApiResponse<T> | null> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text) as ApiResponse<T>
  } catch {
    return null
  }
}

function errorMessage(status: number, json: ApiResponse<unknown> | null): string {
  if (status === 429) return TOO_MANY_REQUESTS
  if (json?.message) return json.message
  if (status === 502 || status === 503 || status === 504) return SERVICE_UNAVAILABLE
  return UNKNOWN_ERROR
}
