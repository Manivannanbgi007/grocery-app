import { cn } from '../../utils/cn'
import { formatPrice } from '../../utils/format'

type PriceSize = 'sm' | 'md' | 'lg'

const SIZE_CLASSES: Record<PriceSize, string> = {
  sm: 'text-base',
  md: 'text-lg',
  lg: 'text-3xl',
}

interface PriceProps {
  /** what the customer pays */
  sellingPrice: number
  /** the price before the discount (MRP); shown crossed out when it is higher */
  price?: number
  size?: PriceSize
  className?: string
}

/** "₹36 ₹40": the selling price in green, the original price crossed out next to it. */
export function Price({ sellingPrice, price, size = 'md', className }: PriceProps) {
  const discounted = price !== undefined && price > sellingPrice
  return (
    <span className={cn('inline-flex flex-wrap items-baseline gap-x-2', className)}>
      <span className={cn('font-heading font-bold text-brand-text', SIZE_CLASSES[size])}>{formatPrice(sellingPrice)}</span>
      {discounted && (
        <span className="text-sm font-semibold text-muted line-through">
          <span className="sr-only">instead of </span>
          {formatPrice(price)}
        </span>
      )}
    </span>
  )
}
