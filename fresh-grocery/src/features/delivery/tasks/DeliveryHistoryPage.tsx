import { CalendarDays, Navigation, PackageCheck, Star, Wallet } from 'lucide-react'
import { DeliveryRiderIllustration } from '../../../components/auth/illustrations/DeliveryRiderIllustration'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PanelHero } from '../../../components/panel/PanelHero'
import { StatCard } from '../../../components/sales/StatCard'
import { Appear } from '../../../components/ui/Appear'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { daysBefore, formatLongDay, indiaDay } from '../../../utils/dates'
import { formatPrice } from '../../../utils/format'
import { PAGE_WIDTH } from '../layout/pageWidth'
import { usePartnerProfile } from '../usePartner'
import { HistoryCard } from './HistoryCard'
import { dayTitle, groupByDay } from './historyDays'
import { useEarnings, useFinishedTasks } from './useTasks'

/** The partner's numbers above the list: finished deliveries (all time), the last 7 days' earnings and the rating. */
function HistoryNumbers({ finished }: { finished: number | null }) {
  const today = indiaDay()
  const { data: week } = useEarnings(daysBefore(today, 6), today)
  const { data: partner } = usePartnerProfile()

  return (
    <section aria-label="Your deliveries in numbers" className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4">
      <StatCard label="Finished deliveries" value={finished} hint="all time" icon={PackageCheck} accent="green" />
      <StatCard
        label="Earned in 7 days"
        value={week?.total ?? null}
        format={formatPrice}
        hint={week ? `${week.deliveries} ${week.deliveries === 1 ? 'delivery' : 'deliveries'}` : undefined}
        icon={Wallet}
        accent="blue"
        to="/delivery/earnings"
      />
      <StatCard
        label="Your rating"
        value={partner?.ratingAverage ?? null}
        format={(rating) => `${rating.toFixed(1)} of 5`}
        hint={partner && partner.ratingCount > 0 ? `${partner.ratingCount} ${partner.ratingCount === 1 ? 'rating' : 'ratings'}` : 'No ratings yet'}
        icon={Star}
        accent="orange"
        to="/delivery/profile"
        className="col-span-2 sm:col-span-1"
      />
    </section>
  )
}

/** Cards in the shape of the timeline, with a light sweeping over them, while the deliveries load. */
function HistorySkeleton() {
  return (
    <div role="status" className="space-y-3">
      <span className="sr-only">Loading your deliveries…</span>
      <Skeleton className="h-8 w-56 rounded-full" />
      {[0, 1, 2].map((index) => (
        <div key={index} className="flex gap-4 rounded-2xl border border-line bg-card p-5">
          <Skeleton className="size-11 shrink-0 rounded-xl" />
          <div className="flex-1 space-y-3">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-10 w-full rounded-xl" />
            <Skeleton className="h-6 w-64 rounded-full" />
          </div>
        </div>
      ))}
    </div>
  )
}

/**
 * The partner's finished deliveries (delivered, returned to the store, cancelled), newest first, as a timeline by day.
 * Each day's heading has the day's real totals (the earnings API, so a day split over two pages is still right).
 * /delivery/history?page=2
 */
export function DeliveryHistoryPage() {
  const { page, pageSize, goToPage, setPageSize } = useUrlFilters()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useFinishedTasks(page, pageSize)
  const days = groupByDay(data?.content ?? [])
  // The days of this page, oldest to newest, for their totals (today when the page is empty)
  const newest = days[0]?.day ?? indiaDay()
  const oldest = days.at(-1)?.day ?? newest
  const { data: dayTotals } = useEarnings(oldest, newest)

  let content
  if (isPending) content = <HistorySkeleton />
  else if (isError) content = <ErrorState title="Could not load your deliveries" error={error} onRetry={() => void refetch()} />
  else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon={<DeliveryRiderIllustration className="h-32 w-auto" />}
        title="No finished deliveries yet"
        message="Every order you deliver shows up here, day by day, with what you earned."
        action={<ButtonLink to="/delivery">Go to today</ButtonLink>}
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    let order = 0 // the cards' place on the page, for their entrance one after the other
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['delivery', 'deliveries']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <ul aria-label="Finished deliveries" className={cn('space-y-8 transition-opacity', isPlaceholderData && 'opacity-60')}>
          {days.map(({ day, deliveries }) => {
            const title = dayTitle(day)
            const total = dayTotals?.days.find((candidate) => candidate.date === day)
            return (
              <li key={day}>
                {/* The day's heading stays in view (under the top bar) while its deliveries scroll past */}
                <div className="sticky top-20 z-10 mb-3 flex flex-wrap items-center gap-2">
                  <h2 className="inline-flex items-center gap-2 rounded-full border border-line bg-card/90 px-4 py-1.5 font-heading text-sm font-bold text-heading shadow-sm backdrop-blur">
                    <CalendarDays className="size-4 text-brand-text" aria-hidden="true" />
                    {title}
                    {title !== formatLongDay(day) && <span className="font-normal text-body">· {formatLongDay(day)}</span>}
                  </h2>
                  {total && (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-light px-3 py-1 text-xs font-bold text-brand-text">
                      {total.deliveries} delivered · {formatPrice(total.amount)} earned
                    </span>
                  )}
                </div>
                {/* The timeline: a line down the left, with each delivery's dot on it (HistoryCard) */}
                <ol
                  aria-label={`Deliveries of ${title}`}
                  className="relative space-y-3 before:absolute before:top-2 before:bottom-2 before:left-[1.1875rem] before:w-0.5 before:rounded-full before:bg-line sm:before:left-[1.4375rem]"
                >
                  {deliveries.map((delivery) => (
                    <HistoryCard key={delivery.id} delivery={delivery} order={order++} />
                  ))}
                </ol>
              </li>
            )
          })}
        </ul>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['delivery', 'deliveries']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <div className={cn(PAGE_WIDTH, 'space-y-6')}>
      <PageTitle title="History" />
      <Appear order={0}>
        <PanelHero
          look="delivery"
          compact
          title="History"
          subtitle="Every delivery you finished, day by day. Open one to see its details and the proof photo."
          links={[
            { to: '/delivery/earnings', label: 'My earnings', icon: Wallet },
            { to: '/delivery', label: 'Today', icon: Navigation },
          ]}
        />
      </Appear>
      <Appear order={1}>
        <HistoryNumbers finished={data?.totalElements ?? null} />
      </Appear>
      <Appear order={2}>{content}</Appear>
    </div>
  )
}

