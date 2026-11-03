import { Bike, CircleCheck, CircleX, Clock, FileQuestion, IdCard, MapPin, Star } from 'lucide-react'
import { Link } from 'react-router'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PageHeader } from '../../../components/panel/PageHeader'
import { StatusTabs } from '../../../components/panel/StatusTabs'
import { CELL, HEAD_CELL, TABLE_CARD, TABLE_HEAD } from '../../../components/panel/tableClasses'
import { Avatar } from '../../../components/profile/Avatar'
import { StatCard } from '../../../components/sales/StatCard'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { PartnersIllustration } from '../illustrations/PartnersIllustration'
import { usePartnerCount } from '../useWorkCounts'
import { KycButtons } from './KycButtons'
import { KYC_LABELS, KYC_TONES, PARTNER_TABS, VEHICLE_LABELS } from './partnerStatus'
import { useAdminPartners } from './useAdminPartners'

/**
 * The delivery partners: the KYC queue first, with Approve and Reject. Above the list, the real number of partners per
 * KYC state; each card opens its tab. /admin/partners?tab=approved&page=2
 */
export function AdminPartnersPage() {
  const { page, pageSize, filter, linkWith, goToPage, setPageSize } = useUrlFilters()
  const tab = PARTNER_TABS.find((candidate) => candidate.key === filter('tab')) ?? PARTNER_TABS[0]
  const { data: waiting } = usePartnerCount('PENDING')
  const { data: approved } = usePartnerCount('APPROVED')
  const { data: rejected } = usePartnerCount('REJECTED')
  const { data: notSent } = usePartnerCount('NOT_SUBMITTED')
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminPartners(tab.kycStatus, page, pageSize)
  /** The list with another tab (the first tab is the default, so it is left out). */
  const tabLink = (key: string) => linkWith('tab', key === PARTNER_TABS[0].key ? '' : key)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the delivery partners" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = <EmptyState icon={<PartnersIllustration className="h-32 w-auto" />} title={tab.empty} className="rounded-2xl border border-line bg-card" />
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['partner', 'partners']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn(TABLE_CARD, isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[60rem] text-left text-sm">
            <caption className="sr-only">
              {tab.label} delivery partners ({data.totalElements})
            </caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className={HEAD_CELL}>Partner</th>
                <th scope="col" className={HEAD_CELL}>Now</th>
                <th scope="col" className={HEAD_CELL}>KYC</th>
                <th scope="col" className={HEAD_CELL}>Vehicle</th>
                <th scope="col" className={HEAD_CELL}>Driving licence</th>
                <th scope="col" className={HEAD_CELL}>Zone</th>
                <th scope="col" className={HEAD_CELL}>Rating</th>
                <th scope="col" className={HEAD_CELL}>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((partner) => (
                <tr key={partner.userId}>
                  <td className={CELL}>
                    <div className="flex items-center gap-3">
                      <Avatar name={partner.fullName} />
                      <div>
                        <Link to={`/admin/accounts/${partner.userId}`} className="font-heading font-bold whitespace-nowrap text-brand-text hover:underline">
                          {partner.fullName}
                        </Link>
                        <span className="block text-xs whitespace-nowrap">{partner.phone}</span>
                      </div>
                    </div>
                  </td>
                  <td className={CELL}>
                    {/* Online now: a green dot that pulses; offline: grey */}
                    <StatusBadge tone={partner.online ? 'brand' : 'neutral'} pulse={partner.online}>
                      {partner.online ? 'Online' : 'Offline'}
                    </StatusBadge>
                  </td>
                  <td className={CELL}>
                    <StatusBadge tone={KYC_TONES[partner.kycStatus]} pulse={partner.kycStatus === 'PENDING'}>
                      {KYC_LABELS[partner.kycStatus]}
                    </StatusBadge>
                    {partner.kycNote && <span className="mt-1.5 block text-xs">“{partner.kycNote}”</span>}
                  </td>
                  <td className={CELL}>
                    {partner.vehicleType ? (
                      <span className="flex items-center gap-1.5 text-heading">
                        <Bike className="size-4 shrink-0 text-sale-text" aria-hidden="true" />
                        {VEHICLE_LABELS[partner.vehicleType]}
                      </span>
                    ) : (
                      '–'
                    )}
                    {partner.vehicleNumber && <span className="block pl-5.5 text-xs">{partner.vehicleNumber}</span>}
                  </td>
                  <td className={CELL}>{partner.drivingLicenseNumber ?? '–'}</td>
                  <td className={CELL}>
                    {partner.zoneName ? (
                      <span className="flex items-center gap-1.5 whitespace-nowrap">
                        <MapPin className="size-3.5 shrink-0 text-brand-text" aria-hidden="true" />
                        {partner.zoneName}
                      </span>
                    ) : (
                      '–'
                    )}
                  </td>
                  <td className={cn(CELL, 'whitespace-nowrap')}>
                    {partner.ratingAverage === null ? (
                      '–'
                    ) : (
                      <span className="flex items-center gap-1">
                        <Star className="size-4 fill-accent text-accent" aria-hidden="true" />
                        <strong className="text-heading">{partner.ratingAverage}</strong>
                        <span className="text-xs">({partner.ratingCount})</span>
                      </span>
                    )}
                  </td>
                  <td className={CELL}>{partner.kycStatus === 'PENDING' && <KycButtons partner={partner} />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['partner', 'partners']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Delivery partners" />
      <PageHeader
        title="Delivery partners"
        icon={IdCard}
        accent="pink"
        description="A new partner registers, then sends the vehicle and licence details (KYC). Only approved partners can go online and get deliveries."
        illustration={<PartnersIllustration />}
      />
      {/* The real number of partners per KYC state; each card opens its tab */}
      <section aria-label="Delivery partners in numbers" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <StatCard label="Waiting for review" value={waiting ?? null} hint="KYC to check" icon={Clock} accent={waiting ? 'amber' : 'green'} to={tabLink('waiting')} />
        <StatCard label="Approved" value={approved ?? null} hint="can go online" icon={CircleCheck} accent="green" to={tabLink('approved')} />
        <StatCard label="Rejected" value={rejected ?? null} hint="KYC not accepted" icon={CircleX} accent="pink" to={tabLink('rejected')} />
        <StatCard label="KYC not sent" value={notSent ?? null} hint="registered only" icon={FileQuestion} accent="blue" to={tabLink('not-sent')} />
      </section>
      <StatusTabs
        label="KYC status"
        active={tab.key}
        tabs={PARTNER_TABS.map((candidate) => ({
          key: candidate.key,
          label: candidate.label,
          to: tabLink(candidate.key),
          count: candidate.kycStatus === 'PENDING' ? waiting : undefined,
        }))}
      />
      {content}
    </>
  )
}
