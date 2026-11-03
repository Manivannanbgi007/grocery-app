import { ShoppingCart } from 'lucide-react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import type { Product } from '../../api/types'
import { aRole, useAuth } from '../../features/auth/authContext'
import { showCartError, useAddToCart, useCart } from '../../features/cart/useCart'
import { useStoreName } from '../../hooks/useStores'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'

interface AddToCartButtonProps {
  product: Pick<Product, 'id' | 'name' | 'inStock' | 'storeId'>
  /** how many to add (the product page has a quantity box) */
  quantity?: number
  /** compact = small "Add" next to the price; full = big "Add to cart" button */
  variant?: 'compact' | 'full'
  className?: string
}

/**
 * Adds the product to the cart.
 * - A guest goes to the login page first (and comes back to this page afterwards); a vendor is told that shopping
 *   needs a customer account.
 * - A cart holds products of ONE store. If it has products of another store, the backend answers 409 and a dialog asks
 *   whether to start a new cart; "yes" adds again with replaceCart=true.
 * - Out-of-stock products get a disabled "Sold out" button.
 */
export function AddToCartButton({ product, quantity = 1, variant = 'compact', className }: AddToCartButtonProps) {
  const { user, customer } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const addToCart = useAddToCart()
  const { data: cart } = useCart()
  const cartStoreName = useStoreName(cart?.storeId)
  const productStoreName = useStoreName(product.storeId)
  const [askToReplace, setAskToReplace] = useState(false)
  const compact = variant === 'compact'

  if (!product.inStock) {
    return (
      <Button variant="light" size={compact ? 'sm' : 'md'} disabled className={className}>
        Sold out
      </Button>
    )
  }

  function add(replaceCart: boolean) {
    addToCart.mutate(
      { productId: product.id, quantity, replaceCart },
      {
        onSuccess: () => {
          setAskToReplace(false)
          const what = quantity > 1 ? `${quantity} × ${product.name}` : product.name
          toast.success(`${what} added to your cart`, { action: { label: 'View cart', onClick: () => navigate('/cart') } })
        },
        onError: (error) => {
          if (isApiError(error, 409)) setAskToReplace(true)
          else showCartError(error)
        },
      },
    )
  }

  function handleClick() {
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    if (!customer) {
      toast.info(`Shopping needs a customer account. You are logged in as ${aRole(user.role)} (${user.email}).`)
      return
    }
    add(false)
  }

  return (
    <>
      <Button
        variant={compact ? 'light' : 'primary'}
        size={compact ? 'sm' : 'md'}
        onClick={handleClick}
        loading={addToCart.isPending}
        aria-label={`Add ${product.name} to cart`}
        className={className}
      >
        {!addToCart.isPending && <ShoppingCart className="size-4" aria-hidden="true" />}
        {compact ? 'Add' : 'Add to cart'}
      </Button>

      <ConfirmDialog
        open={askToReplace}
        title="Start a new cart?"
        message={`Your cart has products from ${cartStoreName ?? 'another store'}. One order can only contain products from one store. Empty your cart and add ${product.name} from ${productStoreName ?? 'this store'}?`}
        confirmLabel="Empty cart and add"
        cancelLabel="Keep my cart"
        loading={addToCart.isPending}
        onConfirm={() => add(true)}
        onCancel={() => setAskToReplace(false)}
      />
    </>
  )
}
