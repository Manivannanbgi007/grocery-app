import { CalendarClock, EyeOff, History, Images, Link2, ListOrdered, Pencil, Plus, Radio, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Banner } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PageHeader } from '../../../components/panel/PageHeader'
import { KpiTile } from '../../../components/sales/KpiTile'
import type { BadgeTone } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { cn } from '../../../utils/cn'
import { formatDateTime } from '../../../utils/format'
import { BannersIllustration } from '../illustrations/BannersIllustration'
import { BannerForm } from './BannerForm'
import { BannerPreview } from './BannerPreview'
import { useAdminBanners, useBannerChanges } from './useAdminMarketing'

/** Whether customers see a banner now, and if not, why. */
function bannerState(banner: Banner, now = Date.now()): { label: string; tone: BadgeTone } {
  if (banner.live) return { label: 'Live', tone: 'brand' }
  if (!banner.active) return { label: 'Switched off', tone: 'neutral' }
  if (banner.startsAt && Date.parse(banner.startsAt) > now) return { label: 'Scheduled', tone: 'new' }
  return { label: 'Ended', tone: 'neutral' }
}

function DeleteButton({ banner }: { banner: Banner }) {
  const { remove } = useBannerChanges()
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <Button size="sm" variant="danger-outline" onClick={() => setConfirming(true)} aria-label={`Delete the banner ${banner.title}`}>
        <Trash2 className="size-3.5" aria-hidden="true" />
        Delete
      </Button>
      <ConfirmDialog
        open={confirming}
        title={`Delete “${banner.title}”?`}
        message="It disappears from the home page at once. To show it again later, switch it off instead."
        confirmLabel="Delete banner"
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(banner.id, {
            onSuccess: () => {
              setConfirming(false)
              toast.success('Banner deleted')
            },
            onError: (error) => {
              setConfirming(false)
              if (!isApiError(error, 401)) toast.error(error.message)
            },
          })
        }
        onCancel={() => setConfirming(false)}
      />
    </>
  )
}

/** The home page banners: what customers see in the big slider, in which order and when. */
export function AdminBannersPage() {
  const { data, isPending, isError, error, refetch } = useAdminBanners()
  /** the banner being changed, 'new' while adding one, or null */
  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const banners = [...(data ?? [])].sort((a, b) => a.position - b.position || a.id - b.id)
  const nextPosition = Math.min(100, (banners.at(-1)?.position ?? 0) + 1)

  let content
  if (isPending) content = <LoadingState className="min-h-64" />
  else if (isError) content = <ErrorState title="Could not load the banners" error={error} onRetry={() => void refetch()} />
  else if (banners.length === 0) {
    content = (
      <EmptyState
        icon={<BannersIllustration className="h-32 w-auto" />}
        title="No banners yet"
        message="Without banners the home page shows its own welcome slides."
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    const states = banners.map((banner) => bannerState(banner).label)
    const count = (label: string) => states.filter((state) => state === label).length
    content = (
      <>
        {/* The banners at a glance, counted from the list on this page */}
        <section aria-label="Banners in numbers" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
          <KpiTile label="Live now" value={String(count('Live'))} hint="customers see them" icon={Radio} accent="green" />
          <KpiTile label="Scheduled" value={String(count('Scheduled'))} hint="start later" icon={CalendarClock} accent="blue" />
          <KpiTile label="Switched off" value={String(count('Switched off'))} hint="kept, not shown" icon={EyeOff} accent="orange" />
          <KpiTile label="Ended" value={String(count('Ended'))} hint="their time is over" icon={History} accent="pink" />
        </section>
        <ul aria-label="Banners" className="grid gap-4 lg:grid-cols-2">
          {banners.map((banner) => {
            const state = bannerState(banner)
            return (
              <li
                key={banner.id}
                className={cn('rounded-2xl border border-line bg-card p-5 transition duration-300', editing !== banner.id && 'hover:-translate-y-1 hover:shadow-xl hover:shadow-heading/10')}
              >
                {editing === banner.id ? (
                  <>
                    <h2 className="mb-4 text-xl">Change the banner</h2>
                    <BannerForm banner={banner} nextPosition={nextPosition} onDone={() => setEditing(null)} />
                  </>
                ) : (
                  <>
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                      {/* A live banner's dot pulses: customers see it right now */}
                      <StatusBadge tone={state.tone} pulse={banner.live}>
                        {state.label}
                      </StatusBadge>
                      <span className="flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-1 font-heading text-xs font-bold text-heading">
                        <ListOrdered className="size-3.5" aria-hidden="true" />
                        Position {banner.position}
                      </span>
                    </div>
                    <BannerPreview title={banner.title} subtitle={banner.subtitle} imageUrl={banner.imageUrl} />
                    <p className="mt-3 flex items-start gap-1.5 text-xs">
                      <CalendarClock className="size-3.5 shrink-0 text-brand-text" aria-hidden="true" />
                      {banner.startsAt || banner.endsAt
                        ? `Shown ${banner.startsAt ? `from ${formatDateTime(banner.startsAt)} ` : ''}${banner.endsAt ? `until ${formatDateTime(banner.endsAt)}` : 'with no end'}`
                        : 'Shown as long as it is on'}
                    </p>
                    {banner.linkUrl && (
                      <p className="mt-1 flex items-center gap-1.5 text-xs">
                        <Link2 className="size-3.5 shrink-0 text-brand-text" aria-hidden="true" />
                        links to {banner.linkUrl}
                      </p>
                    )}
                    <div className="mt-4 flex gap-2">
                      <Button size="sm" variant="light" onClick={() => setEditing(banner.id)} aria-label={`Change the banner ${banner.title}`}>
                        <Pencil className="size-3.5" aria-hidden="true" />
                        Change
                      </Button>
                      <DeleteButton banner={banner} />
                    </div>
                  </>
                )}
              </li>
            )
          })}
        </ul>
      </>
    )
  }

  return (
    <>
      <PageTitle title="Banners" />
      <PageHeader
        title="Banners"
        icon={Images}
        accent="orange"
        description="The big slides at the top of the home page, lowest position first."
        illustration={<BannersIllustration />}
        actions={
          editing !== 'new' && (
            <Button onClick={() => setEditing('new')}>
              <Plus className="size-4" aria-hidden="true" />
              New banner
            </Button>
          )
        }
      />
      {editing === 'new' && (
        <section aria-labelledby="new-banner-title" className="mb-6 rounded-2xl border border-brand/40 bg-card p-6 shadow-lg shadow-brand/10 motion-safe:animate-rise">
          <h2 id="new-banner-title" className="mb-4 text-xl">
            New banner
          </h2>
          <BannerForm nextPosition={nextPosition} onDone={() => setEditing(null)} />
        </section>
      )}
      {content}
    </>
  )
}
