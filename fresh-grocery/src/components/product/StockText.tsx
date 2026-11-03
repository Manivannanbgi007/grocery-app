import type { Product } from '../../api/types'

interface StockTextProps {
  /** today's data; undefined while it loads */
  product: Product | undefined
  /** the shop no longer sells it (the API answered 404) */
  missing?: boolean
}

/** "70 in stock", "Only 3 left", "Out of stock": the stock in a wishlist row or the comparison table. */
export function StockText({ product, missing = false }: StockTextProps) {
  if (missing) return <span className="font-bold text-hot">No longer available</span>
  if (!product) return <span>Checking stock…</span>
  if (!product.inStock) return <span className="font-bold text-hot">Out of stock</span>
  if (product.lowStock) return <span className="font-bold text-new">Only {product.availableQuantity} left</span>
  return <span className="font-bold text-brand-text">{product.availableQuantity} in stock</span>
}
