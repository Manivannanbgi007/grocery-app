import { HttpResponse } from 'msw'
import type { ApiErrorDetail, ApiResponse, PageResponse } from '../../api/types'

/* Helpers that build responses in the backend's exact JSON shape (ApiResponse / PageResponse). */

export function ok<T>(data: T, message: string, status = 200) {
  const body: ApiResponse<T> = { success: true, message, data, errors: null, timestamp: new Date().toISOString(), path: null }
  return HttpResponse.json(body, { status })
}

export function fail(status: number, message: string, request: Request, errors: ApiErrorDetail[] = []) {
  const body: ApiResponse<null> = {
    success: false,
    message,
    data: null,
    errors,
    timestamp: new Date().toISOString(),
    path: new URL(request.url).pathname,
  }
  return HttpResponse.json(body, { status })
}

/** Cuts one page out of a list. `page` starts at 0, like Spring Data. */
export function paginate<T>(items: T[], page: number, size: number): PageResponse<T> {
  const totalPages = Math.ceil(items.length / size)
  return {
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages,
    last: page >= totalPages - 1,
  }
}

/** A whole-number query parameter, or the fallback when it is missing or not a number. */
export function intParam(params: URLSearchParams, name: string, fallback: number): number {
  const value = Number(params.get(name))
  return params.has(name) && Number.isInteger(value) ? value : fallback
}
