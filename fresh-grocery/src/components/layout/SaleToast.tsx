import { X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useDeals } from '../../hooks/useDeals'
import { savingsOf } from '../../utils/discount'
import { formatPrice } from '../../utils/format'
import { ProductImage } from '../product/ProductImage'
import { Price } from '../ui/Price'

/** sessionStorage lives as long as the browser tab: the pop-up appears once per visit. */
const SEEN_KEY = 'fresh-grocery.sale-toast-seen'
const SHOW_AFTER_MS = 4000

function alreadySeen(): boolean {
  try {
    return sessionStorage.getItem(SEEN_KEY) === 'true'
  } catch {
    return true // storage blocked: better never than on every page
  }
}

function markSeen(): void {
  try {
    sessionStorage.setItem(SEEN_KEY, 'true')
  } catch {
    // ignore
  }
}

/** "Tomato is on sale": a small pop-up at the bottom left, a few seconds after arriving, with the biggest real discount. */
export function SaleToast({ delayMs = SHOW_AFTER_MS }: { delayMs?: number }) {
  const [seen] = useState(alreadySeen)
  const [visible, setVisible] = useState(false)
  // The deals are only loaded when the pop-up will really be shown
  const { deals } = useDeals(!seen)
  const product = deals[0]

  useEffect(() => {
    if (seen || !product) return
    const timer = setTimeout(() => {
      setVisible(true)
      markSeen()
    }, delayMs)
    return () => clearTimeout(timer)
  }, [seen, product, delayMs])

  if (!visible || !product) return null

  return (
    <aside aria-label="On sale now" className="fixed bottom-5 left-5 z-40 flex w-80 max-w-[calc(100vw-2.5rem)] items-center gap-3 rounded-xl border border-line bg-card p-3 pr-10 shadow-xl motion-safe:animate-slide-up">
      <ProductImage product={product} className="w-16 shrink-0" emojiClassName="text-3xl" />
      <div className="text-sm">
        <p>
          <Link to={`/products/${product.id}`} onClick={() => setVisible(false)} className="font-bold text-heading hover:text-brand-text">
            {product.name}
          </Link>{' '}
          is on sale. You save {formatPrice(savingsOf(product))}.
        </p>
        <Price sellingPrice={product.sellingPrice} price={product.price} size="sm" />
      </div>
      <button type="button" onClick={() => setVisible(false)} className="absolute top-2 right-2 rounded-full p-1 text-body hover:bg-surface hover:text-heading" aria-label="Close">
        <X className="size-4" aria-hidden="true" />
      </button>
    </aside>
  )
}
