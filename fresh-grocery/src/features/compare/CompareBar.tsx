import { ArrowLeftRight, X } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { ProductImage } from '../../components/product/ProductImage'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { MAX_COMPARE_ITEMS, useCompare } from './useCompare'

/**
 * While products are chosen for the comparison: a bar at the bottom of the shop's pages with their pictures, the way to
 * the comparison and a button that empties it. Hidden on the comparison page itself.
 * On a phone it leaves the bottom right corner free for the assistant's button, and shows the count without pictures.
 */
export function CompareBar() {
  const compare = useCompare()
  const { pathname } = useLocation()

  if (compare.count === 0 || pathname === '/compare') return null

  return (
    <aside
      aria-label="Products to compare"
      className="fixed right-20 bottom-5 left-4 z-40 flex items-center gap-3 rounded-2xl border border-line bg-card p-2 pl-3 shadow-xl motion-safe:animate-slide-up sm:right-auto sm:left-1/2 sm:-translate-x-1/2 print:hidden"
    >
      {/* The pictures from sm: on a phone the button and the × fill the bar */}
      <ul aria-label="Chosen products" className="hidden -space-x-2 sm:flex">
        {compare.products.map((product) => (
          <li key={product.id} title={product.name} className="size-10 overflow-hidden rounded-full border-2 border-card bg-card">
            <ProductImage product={product} emojiClassName="text-xl" />
            <span className="sr-only">{product.name}</span>
          </li>
        ))}
      </ul>
      <span className="text-sm whitespace-nowrap">
        {compare.count} of {MAX_COMPARE_ITEMS}
      </span>
      <Link to="/compare" className={buttonClasses('primary', 'md', 'ml-auto')}>
        <ArrowLeftRight className="size-4" aria-hidden="true" />
        Compare ({compare.count})
      </Link>
      <button type="button" onClick={compare.clear} aria-label="Empty the comparison" className="rounded-md p-2 text-heading hover:bg-surface hover:text-hot">
        <X className="size-5" aria-hidden="true" />
      </button>
    </aside>
  )
}
