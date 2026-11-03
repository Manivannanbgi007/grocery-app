import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createBanner,
  createPlatformCoupon,
  deleteBanner,
  getAllBanners,
  searchCoupons,
  searchNotifications,
  setAnyCouponActive,
  updateBanner,
  updatePlatformCoupon,
  type AdminNotificationFilter,
  type BannerRequest,
} from '../../../api/adminMarketing'
import { queryKeys } from '../../../api/queryKeys'
import type { CouponRequest } from '../../../api/vendorCoupons'
import { useAuth } from '../../auth/authContext'

export const ADMIN_COUPONS_PER_PAGE = 20
export const NOTIFICATIONS_PER_PAGE = 30

/** One page (1-based) of coupons: the platform's, one store's, or all of them. */
export function useAdminCoupons(filter: { storeId?: number; platformOnly?: boolean }, page: number, size = ADMIN_COUPONS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminCoupons(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchCoupons(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Platform coupons added and changed, any coupon switched on or off; the lists and the shop's offers follow. */
export function useAdminCouponChanges() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const saved = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminCoupons(admin?.id ?? 0) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.allCoupons })
  }
  return {
    create: useMutation({ mutationFn: (request: CouponRequest) => createPlatformCoupon(request), onSuccess: saved }),
    update: useMutation({ mutationFn: ({ couponId, request }: { couponId: number; request: CouponRequest }) => updatePlatformCoupon(couponId, request), onSuccess: saved }),
    setActive: useMutation({ mutationFn: ({ couponId, active }: { couponId: number; active: boolean }) => setAnyCouponActive(couponId, active), onSuccess: saved }),
  }
}

export function useAdminBanners() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminBanners(admin?.id ?? 0),
    queryFn: ({ signal }) => getAllBanners(signal),
    enabled: admin !== null,
  })
}

/** Banners added, changed and removed; the list and the shop's home page follow. */
export function useBannerChanges() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const saved = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.adminBanners(admin?.id ?? 0) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.banners })
  }
  return {
    create: useMutation({ mutationFn: (request: BannerRequest) => createBanner(request), onSuccess: saved }),
    update: useMutation({ mutationFn: ({ bannerId, request }: { bannerId: number; request: BannerRequest }) => updateBanner(bannerId, request), onSuccess: saved }),
    remove: useMutation({ mutationFn: (bannerId: number) => deleteBanner(bannerId), onSuccess: saved }),
  }
}

/** One page (1-based) of the notification log. */
export function useNotificationLog(filter: Omit<AdminNotificationFilter, 'page'>, page: number, size = NOTIFICATIONS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminNotifications(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchNotifications(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}
