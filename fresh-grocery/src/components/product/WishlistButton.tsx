import { Heart } from 'lucide-react'
import { toast } from 'sonner'
import type { Product } from '../../api/types'
import { useWishlist } from '../../features/wishlist/useWishlist'
import { openWishlistPopup } from '../../features/wishlist/wishlistPopupState'
import { cn } from '../../utils/cn'
import { Button } from '../ui/Button'

interface WishlistButtonProps {
  product: Product
  /** icon = the round heart on product cards; text = "Add to wishlist" on the product page */
  variant?: 'icon' | 'text'
  className?: string
}

/**
 * The heart button: adds the product to the wishlist (saved in this browser) and opens the wishlist pop-up, or removes
 * it again. It is a toggle (aria-pressed), so its name stays the same in both states.
 */
export function WishlistButton({ product, variant = 'icon', className }: WishlistButtonProps) {
  const wishlist = useWishlist()
  const saved = wishlist.isInWishlist(product.id)
  const heart = <Heart className={cn('size-4', saved ? 'fill-hot text-hot' : 'text-heading')} aria-hidden="true" />

  function handleClick() {
    const added = wishlist.toggle(product)
    if (added) openWishlistPopup()
    else toast.success(`${product.name} removed from your wishlist`)
  }

  if (variant === 'text') {
    return (
      <Button variant="outline" size="lg" onClick={handleClick} aria-pressed={saved} className={cn('text-heading aria-pressed:border-hot/50 aria-pressed:bg-hot/5', className)}>
        {heart}
        Add to wishlist
      </Button>
    )
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-pressed={saved}
      aria-label={`Save ${product.name} to wishlist`}
      className={cn('flex size-9 items-center justify-center rounded-full border border-line bg-card transition-colors hover:border-hot', className)}
    >
      {heart}
    </button>
  )
}
