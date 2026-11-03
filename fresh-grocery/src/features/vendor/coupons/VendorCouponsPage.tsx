import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { CouponDetails } from '../../../api/types'
import type { CouponRequest } from '../../../api/vendorCoupons'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { LoadingState } from '../../../components/ui/LoadingState'
import { cn } from '../../../utils/cn'
import { couponBenefit, couponCondition } from '../../../utils/discount'
import { formatDate } from '../../../utils/format'
import { CouponForm } from '../../coupons/CouponForm'
import { CouponSwitch } from '../../coupons/CouponSwitch'
import { couponState, couponValuesOf, newCouponValues } from '../../coupons/couponValidation'
import { useCouponChanges, useMyCoupons } from './useVendorCoupons'

/** The on / off switch of one of the store's coupons. */
function ActiveSwitch({ coupon }: { coupon: CouponDetails }) {
  const { setActive } = useCouponChanges()

  function toggle() {
    setActive.mutate(
      { couponId: coupon.id, active: !coupon.active },
      {
        onSuccess: (changed) => toast.success(changed.active ? `${changed.code} is on` : `${changed.code} is switched off`),
        onError: (error) => {
          if (!isApiError(error, 401)) toast.error(error.message)
        },
      },
    )
  }

  return <CouponSwitch coupon={coupon} onToggle={toggle} pending={setActive.isPending} />
}

/** The store's own coupons: list, create, change, switch on and off. */
export function VendorCouponsPage() {
  const { page, pageSize, goToPage, setPageSize } = useUrlFilters()
  /** the coupon being changed, 'new' while creating one, null otherwise */
  const [editing, setEditing] = useState<CouponDetails | 'new' | null>(null)
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useMyCoupons(page, pageSize)
  const { create, update } = useCouponChanges()

  async function save(request: CouponRequest) {
    if (editing === 'new') {
      await create.mutateAsync(request)
      toast.success(`Coupon ${request.code} created`)
    } else if (editing) {
      await update.mutateAsync({ couponId: editing.id, request })
      toast.success(`Coupon ${request.code} saved`)
    }
    setEditing(null)
  }

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load your coupons" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = <EmptyState icon="🏷️" title="No coupons yet" message="A coupon gives customers a reason to order from you today." className="rounded-2xl bg-card" />
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['coupon', 'coupons']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn('relative overflow-x-auto rounded-2xl border border-line bg-card', isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[46rem] text-left text-sm">
            <caption className="sr-only">Your coupons</caption>
            <thead className="border-b border-line bg-surface font-heading text-heading">
              <tr>
                <th scope="col" className="px-4 py-3">Code</th>
                <th scope="col" className="px-4 py-3">Offer</th>
                <th scope="col" className="px-4 py-3">Valid</th>
                <th scope="col" className="px-4 py-3">Used</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">On</th>
                <th scope="col" className="px-4 py-3">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((coupon) => {
                const state = couponState(coupon)
                return (
                  <tr key={coupon.id}>
                    <td className="px-4 py-3 font-heading font-bold tracking-wider text-heading">{coupon.code}</td>
                    <td className="px-4 py-3">
                      <span className="block text-heading">{couponBenefit(coupon)}</span>
                      <span className="text-xs">{couponCondition(coupon) ?? 'No minimum order'}</span>
                    </td>
                    <td className="px-4 py-3">
                      {formatDate(coupon.validFrom)} – {formatDate(coupon.validUntil)}
                    </td>
                    <td className="px-4 py-3">
                      {coupon.usedCount}
                      {coupon.usageLimit !== null && ` of ${coupon.usageLimit}`}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={state.tone}>{state.label}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <ActiveSwitch coupon={coupon} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button variant="light" size="sm" onClick={() => setEditing(coupon)} aria-label={`Change coupon ${coupon.code}`}>
                        Change
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['coupon', 'coupons']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Coupons" />
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl">Coupons</h1>
        {editing === null && (
          <Button onClick={() => setEditing('new')}>
            <Plus className="size-4" aria-hidden="true" />
            New coupon
          </Button>
        )}
      </div>
      {editing !== null && (
        <section aria-labelledby="coupon-form-title" className="mb-6 rounded-2xl border-2 border-brand-soft bg-card p-6">
          <h2 id="coupon-form-title" className="mb-4 text-xl">
            {editing === 'new' ? 'New coupon' : `Change coupon ${editing.code}`}
          </h2>
          <CouponForm
            key={editing === 'new' ? 'new' : editing.id}
            initialValues={editing === 'new' ? newCouponValues() : couponValuesOf(editing)}
            submitLabel={editing === 'new' ? 'Create coupon' : 'Save coupon'}
            onSave={save}
            onCancel={() => setEditing(null)}
          />
        </section>
      )}
      {content}
    </>
  )
}
