import { Bike, MoonStar, PackageCheck, Star, Wallet, type LucideIcon } from 'lucide-react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PanelHero } from '../../../components/panel/PanelHero'
import { KpiTile } from '../../../components/sales/KpiTile'
import { Appear } from '../../../components/ui/Appear'
import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { cn } from '../../../utils/cn'
import { indiaDay } from '../../../utils/dates'
import { formatPrice, formatTime } from '../../../utils/format'
import { useAuth } from '../../auth/authContext'
import { PAGE_WIDTH } from '../layout/pageWidth'
import { useAvailability, useAvailabilityChanges, usePartnerProfile } from '../usePartner'
import { TaskPanel } from './TaskPanel'
import { useCurrentTasks, useEarnings } from './useTasks'

function GoOnlineButton() {
  const { online } = useAvailabilityChanges()
  return (
    <Button
      size="lg"
      loading={online.isPending}
      onClick={() =>
        online.mutate(undefined, {
          onSuccess: () => toast.success('You are online: new deliveries come to you'),
          onError: (error) => {
            if (!isApiError(error, 401)) toast.error(error.message)
          },
        })
      }
    >
      Go online
    </Button>
  )
}

/** The big round icon of the empty states; the waiting one pulses (a new delivery can come at any moment). */
function StateIcon({ icon: Icon, pulse = false }: { icon: LucideIcon; pulse?: boolean }) {
  return (
    <span className={cn('relative grid size-20 place-items-center rounded-full', pulse ? 'bg-brand-light text-brand-text' : 'bg-surface text-muted')}>
      {pulse && <span className="absolute inset-0 animate-ping rounded-full bg-brand/20 motion-reduce:animate-none" />}
      <Icon className="relative size-9" />
    </span>
  )
}

/** Deliveries done and money earned today, and the partner's rating, at a glance. */
function TodayNumbers() {
  const today = indiaDay()
  const { data } = useEarnings(today, today)
  const { data: partner } = usePartnerProfile()
  const rating = partner?.ratingAverage ?? null

  return (
    <section aria-label="Today in numbers" className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      <KpiTile label="Delivered today" value={data ? String(data.deliveries) : '–'} icon={PackageCheck} accent="green" to="/delivery/history" />
      <KpiTile label="Earned today" value={data ? formatPrice(data.total) : '–'} icon={Wallet} accent="blue" to="/delivery/earnings" />
      <KpiTile
        label="Your rating"
        value={rating === null ? '–' : `${rating} of 5`}
        hint={partner && partner.ratingCount > 0 ? `${partner.ratingCount} ${partner.ratingCount === 1 ? 'rating' : 'ratings'}` : 'No ratings yet'}
        icon={Star}
        accent="orange"
        to="/delivery/profile"
        className="col-span-2 sm:col-span-1"
      />
    </section>
  )
}

/** The delivery panel's start page: online or not, today's numbers, and the current delivery with its next step. */
export function DeliveryTodayPage() {
  const { partner } = useAuth()
  const { data: availability } = useAvailability(true)
  const online = availability?.online ?? false
  const tasks = useCurrentTasks(online)

  let content
  if (tasks.isPending) content = <LoadingState label="Loading your deliveries…" className="min-h-72" />
  else if (tasks.isError) content = <ErrorState title="Could not load your deliveries" error={tasks.error} onRetry={() => void tasks.refetch()} />
  else if (tasks.data.content.length === 0) {
    content = online ? (
      <EmptyState
        icon={<StateIcon icon={Bike} pulse />}
        title="Waiting for a delivery"
        message="A new delivery shows up here by itself, and the bell rings. Keep this page open."
        className="rounded-2xl border border-line bg-card shadow-sm"
      />
    ) : (
      <EmptyState
        icon={<StateIcon icon={MoonStar} />}
        title="You are offline"
        message="Go online to get deliveries from the stores in your zone."
        action={<GoOnlineButton />}
        className="rounded-2xl border border-line bg-card shadow-sm"
      />
    )
  } else {
    content = (
      <div className="space-y-10">
        {tasks.data.content.map((delivery) => (
          <TaskPanel key={delivery.id} delivery={delivery} />
        ))}
      </div>
    )
  }

  return (
    <div className={cn(PAGE_WIDTH, 'space-y-6')}>
      <PageTitle title="Today" />
      <Appear order={0}>
        <PanelHero look="delivery" title={`Hello, ${partner?.fullName.split(' ')[0] ?? ''}`} wave>
          <p className="mt-3 flex items-center gap-2 text-sm text-heading">
            <span aria-hidden="true" className={cn('size-2.5 shrink-0 rounded-full', online ? 'bg-brand' : 'bg-muted')} />
            {online && availability?.onlineUntil
              ? `You are online until ${formatTime(availability.onlineUntil)}; while this panel is open you stay online.`
              : 'You are offline: no new deliveries come to you.'}
          </p>
        </PanelHero>
      </Appear>
      <Appear order={1}>
        <TodayNumbers />
      </Appear>
      <Appear order={2}>{content}</Appear>
    </div>
  )
}
