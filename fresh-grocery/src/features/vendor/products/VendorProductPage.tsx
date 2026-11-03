import { ArrowLeft, ExternalLink } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Product } from '../../../api/types'
import type { ProductRequest } from '../../../api/vendorProducts'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { ProductForm } from './ProductForm'
import { ProductImagesPanel } from './ProductImagesPanel'
import { PRODUCT_STATUS_LABELS, PRODUCT_STATUS_TONES } from './productStatus'
import { productValuesOf } from './productValidation'
import { ProductVisibilityPanel } from './ProductVisibilityPanel'
import { StockPanel } from './StockPanel'
import { useMyProduct, useProductChanges } from './useVendorProducts'

/** One of the vendor's products: details, stock, photos and visibility. */
export function VendorProductPage() {
  const { productId } = useParams()
  const id = Number(productId)
  return Number.isInteger(id) && id > 0 ? <ProductContent productId={id} /> : <ProductNotFound />
}

function ProductNotFound() {
  return (
    <>
      <PageTitle title="Product not found" />
      <EmptyState icon="🥕" title="We could not find this product" message="It may have been deleted, or it belongs to another store." className="rounded-2xl bg-card" />
    </>
  )
}

function ProductContent({ productId }: { productId: number }) {
  // A product created a moment ago answers 404 until the read side has it: useMyProduct asks again for a few seconds
  const { data: product, isPending, isError, error, refetch } = useMyProduct(productId)

  if (isError && isApiError(error, 404)) return <ProductNotFound />

  return (
    <>
      <PageTitle title={product?.name ?? 'Product'} />
      <Link to="/vendor/products" className="mb-4 inline-flex items-center gap-1.5 text-sm font-bold text-brand-text hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All products
      </Link>
      {isPending ? (
        <Skeleton className="h-96" />
      ) : isError ? (
        <ErrorState title="Could not load this product" error={error} onRetry={() => void refetch()} />
      ) : (
        <ProductDetails product={product} />
      )}
    </>
  )
}

function ProductDetails({ product }: { product: Product }) {
  const { update } = useProductChanges()

  async function save(request: ProductRequest) {
    await update.mutateAsync({ productId: product.id, request })
    toast.success(`${request.name} saved`)
  }

  return (
    <>
      <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-2">
        <h1 className="text-3xl">{product.name}</h1>
        <Badge tone={PRODUCT_STATUS_TONES[product.status]} className="text-sm">
          {PRODUCT_STATUS_LABELS[product.status]}
        </Badge>
        {product.status === 'ACTIVE' && (
          <a href={`/products/${product.id}`} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-sm font-bold text-brand-text hover:underline">
            <ExternalLink className="size-4" aria-hidden="true" />
            See it in the shop
          </a>
        )}
      </div>
      <div className="grid items-start gap-6 xl:grid-cols-[1fr_26rem]">
        <section aria-labelledby="details-title" className="rounded-2xl border border-line bg-card p-6">
          <h2 id="details-title" className="mb-4 text-xl">
            Details
          </h2>
          <ProductForm key={product.id} initialValues={productValuesOf(product)} submitLabel="Save changes" onSave={save} />
        </section>
        <div className="space-y-6">
          <StockPanel productId={product.id} />
          <ProductImagesPanel product={product} />
          <ProductVisibilityPanel product={product} />
        </div>
      </div>
    </>
  )
}
