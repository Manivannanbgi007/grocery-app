import { Bike, CreditCard, LifeBuoy, Mail, MapPin, Phone, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Avatar } from '../../../components/profile/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { StarRating } from '../../../components/ui/StarRating'
import { usePlatformSettings } from '../../../hooks/usePlatformSettings'
import { cn } from '../../../utils/cn'
import { phoneHref } from '../../../utils/format'
import { useAuth } from '../../auth/authContext'
import { VEHICLE_NAMES } from '../deliveryTexts'
import { PAGE_WIDTH } from '../layout/pageWidth'
import { usePartnerProfile } from '../usePartner'

function Detail({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: ReactNode }) {
  return (
    <div className="flex items-center gap-3 border-b border-line py-3 last:border-b-0">
      <dt className="flex shrink-0 items-center gap-3 text-sm">
        <span className="grid size-9 place-items-center rounded-lg bg-surface text-body">
          <Icon className="size-4" aria-hidden="true" />
        </span>
        {label}
      </dt>
      <dd className="ml-auto min-w-0 text-right text-sm font-bold break-words text-heading">{children}</dd>
    </div>
  )
}

function DetailCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="rounded-2xl border border-line bg-card p-5 shadow-sm sm:p-6">
      <h2 className="mb-1 text-lg">{title}</h2>
      <dl>{children}</dl>
    </section>
  )
}

/** The partner's details: account, vehicle, licence, zone and rating. An approved KYC is changed through support. */
export function DeliveryProfilePage() {
  const { partner: account } = useAuth()
  const { data: partner } = usePartnerProfile()
  const { data: settings } = usePlatformSettings()
  if (!partner || !account) return null

  return (
    <div className={cn(PAGE_WIDTH, 'space-y-4')}>
      <PageTitle title="Profile" />
      <section className="flex flex-col items-center gap-4 rounded-2xl border border-line bg-card p-6 text-center shadow-sm sm:flex-row sm:text-left">
        <Avatar name={partner.fullName} size="lg" className="size-20 text-2xl" />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
            <h1 className="text-3xl">{partner.fullName}</h1>
            <Badge tone="brand">KYC approved</Badge>
          </div>
          <p className="mt-1">Delivery partner</p>
          <p className="mt-2 flex flex-wrap items-center justify-center gap-2 text-sm sm:justify-start">
            <StarRating value={partner.ratingAverage} />
            <span className="font-bold text-heading">
              {partner.ratingAverage === null ? 'No ratings yet' : `${partner.ratingAverage} of 5 (${partner.ratingCount} ${partner.ratingCount === 1 ? 'rating' : 'ratings'})`}
            </span>
          </p>
        </div>
      </section>

      <div className="grid gap-4 md:grid-cols-2">
        <DetailCard title="Contact">
          <Detail icon={Mail} label="Email">
            {account.email}
          </Detail>
          <Detail icon={Phone} label="Phone">
            {partner.phone}
          </Detail>
        </DetailCard>
        <DetailCard title="Vehicle and zone">
          <Detail icon={Bike} label="Vehicle">
            {partner.vehicleType ? `${VEHICLE_NAMES[partner.vehicleType]} ${partner.vehicleNumber ?? ''}` : '–'}
          </Detail>
          <Detail icon={CreditCard} label="Driving licence">
            {partner.drivingLicenseNumber ?? '–'}
          </Detail>
          <Detail icon={MapPin} label="Zone">
            {partner.zoneName ?? '–'}
          </Detail>
        </DetailCard>
      </div>

      <section aria-label="Support" className="flex flex-col gap-4 rounded-2xl border border-line bg-surface p-5 sm:flex-row sm:items-center sm:p-6">
        <span className="grid size-11 shrink-0 place-items-center rounded-full bg-card text-brand-text">
          <LifeBuoy className="size-5" aria-hidden="true" />
        </span>
        <p className="flex-1 text-sm">To change your vehicle, licence or zone, contact support: an approved KYC is checked again before it changes.</p>
        {settings && (
          <div className="flex flex-wrap gap-2">
            <a href={phoneHref(settings.supportPhone)} className="inline-flex items-center gap-2 rounded-full bg-card px-3 py-1.5 text-sm font-bold text-brand-text hover:bg-brand-light">
              <Phone className="size-4" aria-hidden="true" />
              {settings.supportPhone}
            </a>
            <a href={`mailto:${settings.supportEmail}`} className="inline-flex items-center gap-2 rounded-full bg-card px-3 py-1.5 text-sm font-bold text-brand-text hover:bg-brand-light">
              <Mail className="size-4" aria-hidden="true" />
              {settings.supportEmail}
            </a>
          </div>
        )}
      </section>
    </div>
  )
}
