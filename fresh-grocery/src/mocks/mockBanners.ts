import type { BannerRequest } from '../api/adminMarketing'
import type { ApiErrorDetail, Banner } from '../api/types'
import { demoBanners } from './data/platform'
import { MockApiError, validationFailed } from './mockApiError'
import { recordChange } from './mockPlatform'

/*
 * The home page banners for the mock API (platform-service), kept in memory: the two live seed banners and the seed's
 * Diwali banner that ended last year. The shop shows the live ones; admins see and change all of them.
 */

function seedBanners(): Banner[] {
  return [
    ...demoBanners.map((banner) => ({ ...banner })),
    {
      id: 3,
      title: 'Diwali sale',
      subtitle: 'Ended last year',
      imageUrl: 'http://localhost:8080/media/banners/diwali.jpg',
      linkUrl: '/offers',
      position: 3,
      startsAt: '2025-10-14T18:30:00Z',
      endsAt: '2025-11-04T18:30:00Z',
      active: true,
      live: false,
    },
  ]
}

let banners = seedBanners()
let nextBannerId = 100

/** For tests: back to the seed banners. */
export function resetMockBanners(): void {
  banners = seedBanners()
  nextBannerId = 100
}

/** Banner.isLiveAt: switched on, started and not ended. */
function withLive(banner: Banner, now = Date.now()): Banner {
  const started = banner.startsAt === null || Date.parse(banner.startsAt) <= now
  const notEnded = banner.endsAt === null || Date.parse(banner.endsAt) > now
  return { ...banner, live: banner.active && started && notEnded }
}

const byPosition = (a: Banner, b: Banner) => a.position - b.position || a.id - b.id

/** What the home page shows. */
export function liveBanners(): Banner[] {
  return banners.map((banner) => withLive(banner)).filter((banner) => banner.live).sort(byPosition)
}

/** Every banner, also the switched-off and the ended ones. */
export function allBanners(): Banner[] {
  return banners.map((banner) => withLive(banner)).sort(byPosition)
}

/** The @Valid rules of BannerRequest and Banner.update's date check. */
function checkBanner(body: Partial<BannerRequest>): BannerRequest {
  const errors: ApiErrorDetail[] = []
  if (!body.title?.trim()) errors.push({ field: 'title', message: 'must not be blank' })
  else if (body.title.length > 100) errors.push({ field: 'title', message: 'size must be between 0 and 100' })
  if (body.subtitle && body.subtitle.length > 200) errors.push({ field: 'subtitle', message: 'size must be between 0 and 200' })
  if (!body.imageUrl?.trim()) errors.push({ field: 'imageUrl', message: 'must not be blank' })
  if (typeof body.position !== 'number') errors.push({ field: 'position', message: 'must not be null' })
  else if (body.position < 1 || body.position > 100) errors.push({ field: 'position', message: 'must be between 1 and 100' })
  if (typeof body.active !== 'boolean') errors.push({ field: 'active', message: 'must not be null' })
  if (errors.length > 0) throw validationFailed(errors)
  if (body.startsAt && body.endsAt && Date.parse(body.endsAt) <= Date.parse(body.startsAt)) throw new MockApiError(400, 'endsAt must be after startsAt')
  return body as BannerRequest
}

function fields(request: BannerRequest) {
  return {
    title: request.title.trim(),
    subtitle: request.subtitle?.trim() || null,
    imageUrl: request.imageUrl.trim(),
    linkUrl: request.linkUrl?.trim() || null,
    position: request.position,
    startsAt: request.startsAt,
    endsAt: request.endsAt,
    active: request.active,
  }
}

function findBanner(bannerId: number): Banner {
  const banner = banners.find((candidate) => candidate.id === bannerId)
  if (!banner) throw new MockApiError(404, `Banner not found with id: ${bannerId}`)
  return banner
}

/** BannerRequest as Java's record toString() writes it into the audit log. */
function describe(request: BannerRequest): string {
  return `BannerRequest[title=${request.title}, subtitle=${request.subtitle}, imageUrl=${request.imageUrl}, linkUrl=${request.linkUrl}, position=${request.position}, startsAt=${request.startsAt}, endsAt=${request.endsAt}, active=${request.active}]`
}

export function createBanner(adminUserId: number, body: Partial<BannerRequest>): Banner {
  const request = checkBanner(body)
  const banner: Banner = { id: nextBannerId++, ...fields(request), live: false }
  banners.push(banner)
  recordChange('BannerCreated', String(banner.id), adminUserId, describe(request))
  return withLive(banner)
}

export function updateBanner(adminUserId: number, bannerId: number, body: Partial<BannerRequest>): Banner {
  const banner = findBanner(bannerId)
  const request = checkBanner(body)
  Object.assign(banner, fields(request))
  recordChange('BannerUpdated', String(bannerId), adminUserId, describe(request))
  return withLive(banner)
}

export function deleteBanner(adminUserId: number, bannerId: number): void {
  const banner = findBanner(bannerId)
  banners = banners.filter((candidate) => candidate.id !== bannerId)
  recordChange('BannerDeleted', String(bannerId), adminUserId, `{"title": "${banner.title}"}`)
}
