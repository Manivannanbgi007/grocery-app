import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../api/client'
import type { Product } from '../../api/types'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { AddToCartButton } from '../../components/product/AddToCartButton'
import { CompareButton } from '../../components/product/CompareButton'
import { productBadge } from '../../components/product/productBadge'
import { WishlistButton } from '../../components/product/WishlistButton'
import { Badge } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Price } from '../../components/ui/Price'
import { QuantityStepper } from '../../components/ui/QuantityStepper'
import { Skeleton } from '../../components/ui/Skeleton'
import { StarRating } from '../../components/ui/StarRating'
import { useProduct } from '../../hooks/useProduct'
import { savingsOf } from '../../utils/discount'
import { formatPrice, formatUnit } from '../../utils/format'
import { shopLink } from '../../utils/links'
import { ShopSidebar } from '../shop/ShopSidebar'
import { RecentlyViewed } from '../wishlist/RecentlyViewed'
import { useRecentlyViewed } from '../wishlist/useRecentlyViewed'
import { ProductGallery } from './ProductGallery'
import { ProductTabs, type ProductTab } from './ProductTabs'
import { RelatedProducts } from './RelatedProducts'
import { StoreInfo } from './StoreInfo'

/** The cart accepts 1 to 20 of a product (and never more than the store has). */
const MAX_QUANTITY_PER_ITEM = 20

function StockStatus({ product }: { product: Product }) {
  if (!product.inStock) return <p className="font-bold text-hot">Out of stock</p>
  if (product.lowStock) return <p className="font-bold text-new">Only {product.availableQuantity} left in stock</p>
  return <p className="font-bold text-brand-text">In stock</p>
}

function ProductDetailSkeleton() {
  return (
    <div className="page-container mt-8 grid gap-10 lg:grid-cols-2" aria-hidden="true">
      <Skeleton className="aspect-square rounded-2xl" />
      <div className="space-y-4">
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-12 w-48" />
        <Skeleton className="h-24" />
      </div>
    </div>
  )
}

/**
 * One product: photos, everything about it, the quantity and Add to cart, Compare and Add to wishlist; tabs with the
 * description, the details and the reviews; the shop's categories and top rated products on the right (from lg); related
 * and recently viewed products below.
 */
export function ProductDetailPage() {
  const productId = Number(useParams().productId)
  const { data: product, isPending, isError, error, refetch } = useProduct(productId)
  const { record } = useRecentlyViewed()
  const [quantity, setQuantity] = useState(1)
  const [tab, setTab] = useState<ProductTab>('description')
  const tabsRef = useRef<HTMLDivElement>(null)

  // Remember the product in "recently viewed" once it has loaded: again for every other product,
  // but not on every background refresh of the same one (so the effect depends on the id only)
  const loadedId = product?.id
  const recordView = useEffectEvent(() => {
    if (product) record(product)
  })
  useEffect(() => {
    if (loadedId !== undefined) recordView()
  }, [loadedId])

  if (Number.isNaN(productId) || isApiError(error, 404)) {
    return (
      <>
        <PageTitle title="Product not available" />
        <BreadcrumbBar crumbs={[{ label: 'Shop', to: '/shop' }, { label: 'Product' }]} />
        <EmptyState
          icon="🧺"
          title="This product is not available"
          message="It may have been removed from the shop. Have a look at the other products."
          action={<ButtonLink to="/shop">Back to the shop</ButtonLink>}
        />
      </>
    )
  }
  if (isError) return <ErrorState title="Could not load the product" error={error} onRetry={() => void refetch()} className="page-container" />
  if (isPending) return <ProductDetailSkeleton />

  const badge = productBadge(product)
  const maxQuantity = Math.min(MAX_QUANTITY_PER_ITEM, product.availableQuantity)
  const packSize = formatUnit(product.unit, product.unitQuantity)

  function openReviews() {
    setTab('reviews')
    tabsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <>
      <PageTitle title={product.name} />
      <BreadcrumbBar
        crumbs={[
          { label: 'Shop', to: '/shop' },
          { label: product.categoryName, to: shopLink({ category: product.categoryId }) },
          { label: product.name },
        ]}
      />

      <div className="page-container mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem] xl:grid-cols-[minmax(0,1fr)_20rem] xl:gap-10">
        <div className="min-w-0 space-y-10">
          <div className="grid gap-8 md:grid-cols-2 xl:gap-12">
            <ProductGallery product={product} />

            <div className="space-y-5">
              {badge && (
                <Badge tone={badge.tone} className="px-3 py-1 text-sm">
                  {badge.label}
                </Badge>
              )}
              <h1 className="text-3xl lg:text-4xl">{product.name}</h1>
              <button type="button" onClick={openReviews} className="inline-flex hover:underline">
                <StarRating value={product.ratingAverage} count={product.ratingCount} />
                <span className="sr-only">, read the reviews</span>
              </button>

              <div className="flex flex-wrap items-center gap-4">
                <Price sellingPrice={product.sellingPrice} price={product.price} size="lg" />
                {product.sellingPrice < product.price && (
                  <Badge tone="hot" className="text-sm">
                    Save {formatPrice(savingsOf(product))} ({Math.round(product.discountPercentage)}% off)
                  </Badge>
                )}
              </div>
              <p className="text-sm">Price for {packSize}, including GST</p>

              {product.description && <p className="leading-relaxed">{product.description}</p>}

              <div>
                <p className="mb-2 font-heading text-sm font-bold text-heading">Pack size</p>
                <span className="inline-flex rounded-md border-2 border-brand bg-brand-light px-3 py-1 font-heading text-sm font-bold text-brand-text">{packSize}</span>
              </div>

              <StockStatus product={product} />

              <div className="flex flex-wrap items-center gap-3">
                {product.inStock && (
                  <QuantityStepper value={quantity} onChange={setQuantity} max={maxQuantity} label={`Quantity of ${product.name}`} className="h-12" />
                )}
                <AddToCartButton product={product} quantity={quantity} variant="full" className="h-12 px-8" />
              </div>
              <div className="flex flex-wrap gap-3">
                <CompareButton product={product} variant="text" />
                <WishlistButton product={product} variant="text" />
              </div>

              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 border-t border-line pt-5 text-sm sm:grid-cols-[auto_1fr_auto_1fr]">
                <dt>SKU:</dt>
                <dd className="font-bold text-heading">{product.sku}</dd>
                <dt>Category:</dt>
                <dd>
                  <Link to={shopLink({ category: product.categoryId })} className="font-bold text-brand-text hover:underline">
                    {product.categoryName}
                  </Link>
                </dd>
                {product.brand && (
                  <>
                    <dt>Brand:</dt>
                    <dd className="font-bold text-heading">{product.brand}</dd>
                  </>
                )}
                <dt>GST:</dt>
                <dd className="font-bold text-heading">{product.taxPercentage} %</dd>
              </dl>

              <StoreInfo storeId={product.storeId} />
            </div>
          </div>

          <div ref={tabsRef} className="scroll-mt-6">
            <ProductTabs product={product} tab={tab} onTabChange={setTab} />
          </div>

          <RelatedProducts product={product} />
        </div>

        {/* From lg: the shop's categories and its top rated products. The categories show their counts only when the shop
            has loaded them: asking for them here (one request per category) would hit the gateway's rate limit */}
        <ShopSidebar widgets={['categories', 'topRated']} categoryCounts={false} className="hidden lg:block" />
      </div>

      <div className="page-container mt-12">
        <RecentlyViewed />
      </div>
    </>
  )
}
