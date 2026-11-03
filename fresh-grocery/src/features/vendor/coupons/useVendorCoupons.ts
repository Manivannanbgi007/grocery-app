import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../api/queryKeys'
import { createCoupon, getMyCoupons, setCouponActive, updateCoupon, type CouponRequest } from '../../../api/vendorCoupons'
import { useAuth } from '../../auth/authContext'

export const VENDOR_COUPONS_PER_PAGE = 20

/** One page (1-based) of the store's coupons, the latest end date first. */
export function useMyCoupons(page: number, size = VENDOR_COUPONS_PER_PAGE) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorCoupons(vendor?.id ?? 0, page, size),
    queryFn: ({ signal }) => getMyCoupons(page - 1, size, signal),
    enabled: vendor !== null,
    placeholderData: keepPreviousData,
  })
}

/** Creating, changing and switching coupons; the list is loaded again after each. The callers show the errors. */
export function useCouponChanges() {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  const reload = () => queryClient.invalidateQueries({ queryKey: queryKeys.allVendorCoupons(vendor?.id ?? 0) })
  return {
    create: useMutation({ mutationFn: (request: CouponRequest) => createCoupon(request), onSuccess: reload }),
    update: useMutation({ mutationFn: ({ couponId, request }: { couponId: number; request: CouponRequest }) => updateCoupon(couponId, request), onSuccess: reload }),
    setActive: useMutation({ mutationFn: ({ couponId, active }: { couponId: number; active: boolean }) => setCouponActive(couponId, active), onSuccess: reload }),
  }
}
