import { BadgePercent, Boxes, FileText, IndianRupee, LayoutGrid, Package, Scale, ShoppingCart, Star, Store, Tag, Trash2, X, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageHero } from '../../components/layout/PageHero'
import { PageTitle } from '../../components/layout/PageTitle'
import { AddToCartButton } from '../../components/product/AddToCartButton'
import { ProductImage } from '../../components/product/ProductImage'
import { StockText } from '../../components/product/StockText'
import { Button, ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Price } from '../../components/ui/Price'
import { StarRating } from '../../components/ui/StarRating'
import { ToneIcon } from '../../components/ui/ToneIcon'
import type { Tone } from '../../components/ui/tones'
import { useProductsByIds } from '../../hooks/useProductsByIds'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { savingsOf } from '../../utils/discount'
import { formatPrice, formatUnit } from '../../utils/format'
import type { SavedProduct } from '../wishlist/useWishlist'
import { MAX_COMPARE_ITEMS, useCompare } from './useCompare'

/** One column of the table: the saved copy, and today's data once it has loaded. */
interface Column {
  saved: SavedProduct
  product: Product | undefined
  missing: boolean
}

/**
 * The rows of the table, top to bottom. Each one turns a column into the content of its cell; `icon` and `tone` are
 * only the look of the row's name (a small coloured icon in the colours of tones.ts).
 */
const ROWS: { label: string; icon: LucideIcon; tone: Tone; cell: (column: Column) => ReactNode }[] = [
  { label: 'Price', icon: IndianRupee, tone: 'green', cell: ({ saved, product }) => <Price sellingPrice={(product ?? saved).sellingPrice} price={(product ?? saved).price} /> },
  {
    label: 'You save',
    icon: BadgePercent,
    tone: 'pink',
    cell: ({ product }) => (product && product.sellingPrice < product.price ? `${formatPrice(savingsOf(product))} (${Math.round(product.discountPercentage)}% off)` : '–'),
  },
  { label: 'Rating', icon: Star, tone: 'yellow', cell: ({ saved, product }) => <StarRating value={(product ?? saved).ratingAverage} count={(product ?? saved).ratingCount} /> },
  { label: 'Pack size', icon: Package, tone: 'orange', cell: ({ saved }) => formatUnit(saved.unit, saved.unitQuantity) },
  { label: 'Brand', icon: Tag, tone: 'violet', cell: ({ product }) => product?.brand ?? '–' },
  { label: 'Category', icon: LayoutGrid, tone: 'blue', cell: ({ saved }) => saved.categoryName },
  { label: 'Sold by', icon: Store, tone: 'orange', cell: ({ saved }) => <StoreName storeId={saved.storeId} /> },
  { label: 'Stock', icon: Boxes, tone: 'blue', cell: ({ product, missing }) => <StockText product={product} missing={missing} /> },
  { label: 'Description', icon: FileText, tone: 'violet', cell: ({ product }) => product?.description ?? '–' },
  { label: 'Add to cart', icon: ShoppingCart, tone: 'green', cell: ({ product }) => (product ? <AddToCartButton product={product} variant="full" /> : null) },
]

/** The coloured bar over each product's column: the columns take turns in these colours (only the look) */
const COLUMN_BARS = ['bg-sale', 'bg-violet', 'bg-new', 'bg-hot']

function StoreName({ storeId }: { storeId: number }) {
  return <>{useStoreName(storeId) ?? '–'}</>
}

/**
 * The products chosen with the compare buttons, side by side: one column per product, one row per fact.
 * The facts are today's (from the API). On small screens the table scrolls sideways; the row names stay in view.
 */
export function ComparePage() {
  const compare = useCompare()
  const { products, missing } = useProductsByIds(compare.products.map((product) => product.id))
  const columns: Column[] = compare.products.map((saved) => ({ saved, product: products.get(saved.id), missing: missing.has(saved.id) }))

  return (
    <>
      <PageTitle title="Compare products" />
      <BreadcrumbBar crumbs={[{ label: 'Shop', to: '/shop' }, { label: 'Compare products' }]} />
      <div className="page-container mt-8">
        {/* Blue: a calm colour for weighing products up; the movement is motion-safe:animate-... ("reduce motion" turns it off) */}
        <PageHero
          icon={Scale}
          tone="blue"
          title="Compare products"
          subtitle={
            compare.count > 0
              ? `${compare.count} of ${MAX_COMPARE_ITEMS} products. Add more with the compare button on a product.`
              : `Pick up to ${MAX_COMPARE_ITEMS} products with the compare button to see them side by side.`
          }
        >
          {compare.count > 0 && (
            <Button variant="danger-outline" onClick={compare.clear}>
              <Trash2 className="size-4" aria-hidden="true" />
              Remove all
            </Button>
          )}
        </PageHero>

        {compare.count === 0 ? (
          <div className="rounded-3xl border border-line bg-linear-to-br from-sale-light via-card to-violet-light motion-safe:animate-rise">
            <EmptyState
              icon="⚖️"
              title="No products to compare yet"
              message={`Tap the compare button on up to ${MAX_COMPARE_ITEMS} products to see them side by side.`}
              action={
                <ButtonLink to="/shop" className="shadow-lg shadow-brand/30">
                  Browse products
                </ButtonLink>
              }
            />
          </div>
        ) : (
          // The table scrolls sideways inside its card on a small screen (the page itself never does). `relative` matters:
          // the screen-reader-only texts in the cells (sr-only) are positioned absolutely, and without a positioned box
          // here they were placed against the page and made the whole page scroll sideways on phones.
          <div className="relative overflow-x-auto rounded-3xl border border-line bg-card shadow-sm motion-safe:animate-rise">
            <table className="w-full min-w-max border-collapse text-left text-sm">
              <caption className="sr-only">The chosen products side by side</caption>
              <thead>
                <tr className="border-b border-line">
                  <td className="sticky left-0 z-10 w-36 bg-card px-4 py-4 sm:w-44" />
                  {columns.map(({ saved }, index) => (
                    <th key={saved.id} scope="col" className="relative w-60 min-w-60 px-4 pt-5 pb-4 align-top font-normal">
                      {/* The column's colour along its top */}
                      <span aria-hidden="true" className={cn('absolute inset-x-3 top-0 h-1 rounded-b-full', COLUMN_BARS[index % COLUMN_BARS.length])} />
                      {/* The picture grows a little under the mouse */}
                      <Link
                        to={`/products/${saved.id}`}
                        tabIndex={-1}
                        aria-hidden="true"
                        className="block w-40 max-w-full overflow-hidden rounded-2xl transition-transform duration-300 hover:scale-105"
                      >
                        <ProductImage product={saved} emojiClassName="text-7xl" />
                      </Link>
                      <Link to={`/products/${saved.id}`} className="mt-3 line-clamp-2 font-heading text-base font-bold text-heading hover:text-brand-text">
                        {saved.name}
                      </Link>
                      <Button
                        variant="danger-outline"
                        size="sm"
                        onClick={() => compare.remove(saved.id)}
                        aria-label={`Remove ${saved.name} from the comparison`}
                        className="mt-2"
                      >
                        <X className="size-3.5" aria-hidden="true" />
                        Remove
                      </Button>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {ROWS.map((row) => (
                  // Solid stripes: the row name takes its row's colour (bg-inherit) and covers what scrolls under it
                  <tr key={row.label} className="border-b border-line bg-card last:border-b-0 odd:bg-surface">
                    {/* The row names stay on the left while the table scrolls sideways */}
                    <th scope="row" className="sticky left-0 z-10 bg-inherit px-4 py-3.5 align-top font-heading font-bold text-heading">
                      <span className="flex items-center gap-2">
                        <ToneIcon icon={row.icon} tone={row.tone} className="size-7 rounded-lg [&_svg]:size-4" />
                        {row.label}
                      </span>
                    </th>
                    {columns.map((column) => (
                      <td key={column.saved.id} className="max-w-60 px-4 py-3.5 align-top text-body">
                        {row.cell(column)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
