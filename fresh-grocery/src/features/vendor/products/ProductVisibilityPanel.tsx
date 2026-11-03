import { Eye, EyeOff, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Product } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { useProductChanges } from './useVendorProducts'

/** Show or hide the product for customers, or delete it. A product an admin blocked can only be deleted. */
export function ProductVisibilityPanel({ product }: { product: Pick<Product, 'id' | 'name' | 'status'> }) {
  const navigate = useNavigate()
  const { setStatus, remove } = useProductChanges()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const showError = (error: Error) => {
    if (!isApiError(error, 401)) toast.error(error.message)
  }

  function toggle() {
    const status = product.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setStatus.mutate(
      { productId: product.id, status },
      { onSuccess: () => toast.success(status === 'ACTIVE' ? `${product.name} is visible to customers` : `${product.name} is hidden from customers`), onError: showError },
    )
  }

  function deleteIt() {
    remove.mutate(product.id, {
      onSuccess: () => {
        toast.success(`${product.name} deleted`)
        navigate('/vendor/products', { replace: true })
      },
      onError: (error) => {
        setConfirmDelete(false)
        showError(error)
      },
    })
  }

  let state
  if (product.status === 'BLOCKED') {
    state = <p className="text-heading">Blocked by an admin: customers do not see it, and only support can unblock it.</p>
  } else {
    const active = product.status === 'ACTIVE'
    state = (
      <>
        <p className="text-heading">{active ? 'Customers can see and buy this product.' : 'Hidden: customers do not see this product.'}</p>
        <Button variant={active ? 'outline' : 'primary'} size="sm" loading={setStatus.isPending} onClick={toggle}>
          {active ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
          {active ? 'Hide from customers' : 'Show to customers'}
        </Button>
      </>
    )
  }

  return (
    <section aria-labelledby="visibility-title" className="space-y-3 rounded-2xl border border-line bg-card p-6 text-sm">
      <h2 id="visibility-title" className="text-xl">
        Visibility
      </h2>
      {state}
      <div className="border-t border-line pt-3">
        <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(true)} className="text-hot hover:text-hot">
          <Trash2 className="size-4" aria-hidden="true" />
          Delete product
        </Button>
      </div>
      <ConfirmDialog
        open={confirmDelete}
        title={`Delete ${product.name}?`}
        message="It disappears from the shop and from your product list. Orders that already have it keep it."
        confirmLabel="Delete"
        loading={remove.isPending}
        onConfirm={deleteIt}
        onCancel={() => setConfirmDelete(false)}
      />
    </section>
  )
}
