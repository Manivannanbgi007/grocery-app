import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { DeliveryPartner } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { ReasonDialog } from '../../../components/ui/ReasonDialog'
import { useKycReview } from './useAdminPartners'

const REJECT_REASONS = ['The driving licence number is not valid', 'The vehicle number does not match the licence', 'The documents are not readable']

/** "Approve" and "Reject" for a KYC waiting for a review. The partner is told; a rejected one can send it again. */
export function KycButtons({ partner }: { partner: DeliveryPartner }) {
  const { approve, reject } = useKycReview(partner.userId)
  const [dialog, setDialog] = useState<'approve' | 'reject' | null>(null)

  const done = (message: string) => () => {
    setDialog(null)
    toast.success(message)
  }
  function failed(error: Error) {
    setDialog(null)
    if (!isApiError(error, 401)) toast.error(error.message)
  }

  return (
    <div className="flex justify-end gap-2">
      <Button size="sm" onClick={() => setDialog('approve')} aria-label={`Approve the KYC of ${partner.fullName}`}>
        Approve
      </Button>
      <Button size="sm" variant="outline" onClick={() => setDialog('reject')} aria-label={`Reject the KYC of ${partner.fullName}`}>
        Reject
      </Button>
      <ConfirmDialog
        open={dialog === 'approve'}
        title={`Approve ${partner.fullName}?`}
        message={`${partner.fullName} can then go online and gets deliveries in ${partner.zoneName ?? 'their zone'}.`}
        confirmLabel="Approve KYC"
        loading={approve.isPending}
        onConfirm={() => approve.mutate(undefined, { onSuccess: done(`${partner.fullName} is approved`), onError: failed })}
        onCancel={() => setDialog(null)}
      />
      <ReasonDialog
        open={dialog === 'reject'}
        title={`Reject the KYC of ${partner.fullName}?`}
        message="The partner sees the reason and can send the KYC again."
        commonReasons={REJECT_REASONS}
        requiredMessage="Please tell the partner what to fix."
        confirmLabel="Reject KYC"
        loading={reject.isPending}
        onConfirm={(reason) => reject.mutate(reason, { onSuccess: done(`The KYC of ${partner.fullName} is rejected`), onError: failed })}
        onCancel={() => setDialog(null)}
      />
    </div>
  )
}
