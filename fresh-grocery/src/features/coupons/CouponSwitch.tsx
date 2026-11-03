import type { CouponDetails } from '../../api/types'
import { Switch } from '../../components/ui/Switch'

interface CouponSwitchProps {
  coupon: CouponDetails
  /** switches the coupon to the other state (the caller saves it and says so) */
  onToggle: () => void
  pending: boolean
}

/** On / off switch of one coupon (customers can only use coupons that are on). */
export function CouponSwitch({ coupon, onToggle, pending }: CouponSwitchProps) {
  return <Switch checked={coupon.active} label={`Coupon ${coupon.code} on`} onToggle={onToggle} disabled={pending} />
}
