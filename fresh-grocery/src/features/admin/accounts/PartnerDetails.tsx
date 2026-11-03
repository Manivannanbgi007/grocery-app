import { Link } from 'react-router'
import { Badge } from '../../../components/ui/Badge'
import { LoadingState } from '../../../components/ui/LoadingState'
import { usePartnerList } from '../deliveries/useAdminDeliveries'
import { KYC_LABELS, KYC_TONES, VEHICLE_LABELS } from '../partners/partnerStatus'

/** A delivery partner account's vehicle, zone, KYC and rating (delivery-service). */
export function PartnerDetails({ userId }: { userId: number }) {
  const { data: partners, isPending } = usePartnerList()
  const partner = partners?.find((candidate) => candidate.userId === userId)

  let content
  if (isPending) content = <LoadingState className="min-h-20" />
  else if (!partner) content = <p className="text-sm">No delivery profile yet (it is made from the registration a moment later).</p>
  else {
    content = (
      <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
        <div>
          <dt className="inline">KYC: </dt>
          <dd className="inline">
            <Badge tone={KYC_TONES[partner.kycStatus]}>{KYC_LABELS[partner.kycStatus]}</Badge>
            {partner.kycNote && <span className="ml-2">“{partner.kycNote}”</span>}
          </dd>
        </div>
        <div>
          <dt className="inline">Zone: </dt>
          <dd className="inline font-bold text-heading">{partner.zoneName ?? '–'}</dd>
        </div>
        <div>
          <dt className="inline">Vehicle: </dt>
          <dd className="inline font-bold text-heading">{partner.vehicleType ? `${VEHICLE_LABELS[partner.vehicleType]} ${partner.vehicleNumber ?? ''}` : '–'}</dd>
        </div>
        <div>
          <dt className="inline">Driving licence: </dt>
          <dd className="inline font-bold text-heading">{partner.drivingLicenseNumber ?? '–'}</dd>
        </div>
        <div>
          <dt className="inline">Rating: </dt>
          <dd className="inline font-bold text-heading">{partner.ratingAverage === null ? 'No ratings yet' : `${partner.ratingAverage} of 5 (${partner.ratingCount})`}</dd>
        </div>
        <div>
          <dt className="inline">Now: </dt>
          <dd className="inline font-bold text-heading">{partner.online ? 'Online' : 'Offline'}</dd>
        </div>
      </dl>
    )
  }

  return (
    <section className="rounded-2xl border border-line bg-card p-6">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl">Delivery partner</h2>
        <Link to="/admin/partners?tab=all" className="text-sm font-bold text-brand-text hover:underline">
          All partners
        </Link>
      </div>
      {content}
    </section>
  )
}
