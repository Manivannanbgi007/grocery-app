import { Trash2 } from 'lucide-react'
import { Link } from 'react-router'
import { ProductImage } from '../../components/product/ProductImage'
import { Button } from '../../components/ui/Button'
import { Price } from '../../components/ui/Price'
import { StarRating } from '../../components/ui/StarRating'
import { useProductsByIds } from '../../hooks/useProductsByIds'
import { useRecentlyViewed } from './useRecentlyViewed'

/**
 * "Recently viewed products": the last products opened in this browser (newest first).
 * Prices come fresh from the API; products the shop no longer sells are left out.
 */
export function RecentlyViewed() {
  const { products: viewed, clear } = useRecentlyViewed()
  const { products: current, missing } = useProductsByIds(viewed.map((product) => product.id))
  const shown = viewed.filter((product) => !missing.has(product.id))

  if (shown.length === 0) return null

  return (
    <section aria-labelledby="recently-viewed" className="rounded-2xl border-2 border-dashed border-line p-6">
      <div className="mb-5 flex items-center justify-between gap-4">
        <h2 id="recently-viewed" className="text-2xl">
          Recently viewed products
        </h2>
        <Button variant="danger-outline" size="sm" onClick={clear}>
          <Trash2 className="size-4" aria-hidden="true" />
          Clear list
        </Button>
      </div>
      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {shown.map((saved) => {
          // Today's data when it has arrived, the saved copy until then
          const product = current.get(saved.id) ?? saved
          return (
            <li key={saved.id} className="flex items-center gap-4 rounded-xl border border-line bg-card p-4">
              <Link to={`/products/${saved.id}`} tabIndex={-1} aria-hidden="true" className="w-20 shrink-0">
                <ProductImage product={product} emojiClassName="text-4xl" />
              </Link>
              <div className="flex min-w-0 flex-col items-start gap-1">
                <Link to={`/products/${saved.id}`} className="line-clamp-2 font-heading text-sm font-bold text-heading hover:text-brand-text">
                  {product.name}
                </Link>
                <StarRating value={product.ratingAverage} count={product.ratingCount} />
                <Price sellingPrice={product.sellingPrice} price={product.price} size="sm" />
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
