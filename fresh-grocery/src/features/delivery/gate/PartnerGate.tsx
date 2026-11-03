import { Bike, Clock, IdCard, OctagonX, ShieldCheck, UserPlus } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import type { DeliveryPartner } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { Spinner } from '../../../components/ui/Spinner'
import { StepBar, type Step } from '../../../components/ui/StepBar'
import { cn } from '../../../utils/cn'
import { VEHICLE_NAMES } from '../deliveryTexts'
import { KycForm } from '../kyc/KycForm'
import { KYC_ZONES } from '../kyc/zones'
import { GATE_WIDTH } from '../layout/pageWidth'
import { usePartnerProfile } from '../usePartner'
import type { PartnerGateState } from './partnerGateState'

/** The way from signing up to the first delivery; each gate card marks where the partner is. */
const JOIN_STEPS: Step[] = [
  { label: 'Sign up', icon: UserPlus },
  { label: 'Send KYC', icon: IdCard },
  { label: 'Review', icon: ShieldCheck },
  { label: 'Deliver', icon: Bike },
]

interface GateCardProps {
  icon: ReactNode
  /** the colours of the round icon */
  iconClassName: string
  title: string
  /** the index in JOIN_STEPS the partner has reached */
  step: number
  children: ReactNode
}

function GateCard({ icon, iconClassName, title, step, children }: GateCardProps) {
  return (
    <section className={cn(GATE_WIDTH, 'rounded-2xl border border-line bg-card p-5 shadow-sm md:p-8')}>
      <div className="mb-6 rounded-xl bg-surface px-2 py-4 sm:px-4">
        <StepBar label="Becoming a delivery partner" steps={JOIN_STEPS} current={step} />
      </div>
      <div className="mb-4 flex items-center gap-4">
        <span className={cn('grid size-14 shrink-0 place-items-center rounded-full', iconClassName)}>{icon}</span>
        <h1 className="text-2xl md:text-3xl">{title}</h1>
      </div>
      {children}
    </section>
  )
}

/** The details that were sent, to check them while waiting. */
function KycSummary({ partner }: { partner: DeliveryPartner }) {
  const zone = partner.zoneName ?? KYC_ZONES.find((candidate) => candidate.id === partner.zoneId)?.name ?? '–'
  return (
    <dl className="grid gap-x-6 gap-y-1 rounded-xl bg-surface p-4 text-sm sm:grid-cols-2">
      <div>
        <dt className="inline">Vehicle: </dt>
        <dd className="inline font-bold text-heading">{partner.vehicleType ? `${VEHICLE_NAMES[partner.vehicleType]} ${partner.vehicleNumber ?? ''}` : '–'}</dd>
      </div>
      <div>
        <dt className="inline">Driving licence: </dt>
        <dd className="inline font-bold text-heading">{partner.drivingLicenseNumber ?? '–'}</dd>
      </div>
      <div>
        <dt className="inline">Zone: </dt>
        <dd className="inline font-bold text-heading">{zone}</dd>
      </div>
    </dl>
  )
}

/** "Send your KYC": the first thing a new partner sees. */
function SendKyc({ partner }: { partner: DeliveryPartner }) {
  return (
    <GateCard icon={<IdCard className="size-7" aria-hidden="true" />} iconClassName="bg-brand-light text-brand-text" title="Send your KYC" step={1}>
      <p className="mb-6">An admin checks your vehicle and driving licence. Once you are approved, you can go online and get deliveries.</p>
      <KycForm partner={partner} />
    </GateCard>
  )
}

/** The KYC was sent: waiting for an admin. The details can still be changed. */
function WaitingForReview({ partner }: { partner: DeliveryPartner }) {
  const [changing, setChanging] = useState(false)
  return (
    <GateCard icon={<Clock className="size-7" aria-hidden="true" />} iconClassName="bg-new-light text-heading" title="Waiting for review" step={2}>
      <p className="mb-4">An admin is checking your details. We will tell you as soon as you are approved; usually within a day.</p>
      {changing ? (
        <KycForm partner={partner} onSent={() => setChanging(false)} />
      ) : (
        <>
          <KycSummary partner={partner} />
          <Button variant="outline" className="mt-4" onClick={() => setChanging(true)}>
            Change details
          </Button>
        </>
      )}
    </GateCard>
  )
}

/** The admin rejected the KYC: the reason, and the form to send it again. */
function KycRejected({ partner }: { partner: DeliveryPartner }) {
  return (
    <GateCard icon={<OctagonX className="size-7" aria-hidden="true" />} iconClassName="bg-hot/10 text-hot" title="Your KYC was not approved" step={1}>
      <p className="mb-2">The reason the admin gave:</p>
      <p className="mb-6 rounded-xl border border-hot/30 bg-hot/5 p-4 font-bold text-heading">“{partner.kycNote ?? 'No reason given'}”</p>
      <p className="mb-4">Correct your details and send them again.</p>
      <KycForm partner={partner} />
    </GateCard>
  )
}

/** Right after the registration: delivery-service makes the profile a moment later. */
function SettingUp() {
  const profile = usePartnerProfile()
  return (
    <GateCard icon={<Spinner />} iconClassName="bg-brand-light text-brand-text" title="Setting up your profile" step={0}>
      <p className="mb-4">This takes a few seconds.</p>
      {profile.isError && (
        <Button variant="outline" onClick={() => void profile.refetch()}>
          Try again
        </Button>
      )}
    </GateCard>
  )
}

/** What the panel shows instead of its pages until the KYC is approved (see partnerGateState.ts). */
export function PartnerGate({ state }: { state: Exclude<PartnerGateState, 'ready'> }) {
  const profile = usePartnerProfile()

  if (state === 'loading') return <LoadingState label="Loading your profile…" className={cn(GATE_WIDTH, 'min-h-96')} />
  if (state === 'setting-up') return <SettingUp />
  if (state === 'error') return <ErrorState title="Could not load your profile" error={profile.error} onRetry={() => void profile.refetch()} />
  const partner = profile.data!
  if (state === 'kyc') return <SendKyc partner={partner} />
  if (state === 'pending') return <WaitingForReview partner={partner} />
  return <KycRejected partner={partner} />
}
