import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Product, ProductStatus } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { SearchBox } from '../../../components/panel/SearchBox'
import { ProductImage } from '../../../components/product/ProductImage'
import { Badge, type BadgeTone } from '../../../components/ui/Badge'
import { Button, ButtonLink } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { Price } from '../../../components/ui/Price'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { useStoreOptions } from '../stores/useAdminStores'
import { useAdminCategories, useAdminProducts, useProductModeration } from './useAdminCatalog'

const STATUS_FILTERS: ProductStatus[] = ['ACTIVE', 'INACTIVE', 'BLOCKED']

const STATUS_LABELS: Record<ProductStatus, string> = { ACTIVE: 'Active', INACTIVE: 'Hidden by the store', BLOCKED: 'Blocked', DELETED: 'Deleted' }
const STATUS_TONES: Record<ProductStatus, BadgeTone> = { ACTIVE: 'brand', INACTIVE: 'neutral', BLOCKED: 'hot', DELETED: 'neutral' }

/** "Block" (asks first: the vendor cannot undo it) or "Unblock" for one product. */
function ModerationButton({ product }: { product: Product }) {
  const { block, unblock } = useProductModeration()
  const [confirming, setConfirming] = useState(false)

  function failed(error: Error) {
    setConfirming(false)
    if (!isApiError(error, 401)) toast.error(error.message)
  }

  if (product.status === 'DELETED') return null
  if (product.status === 'BLOCKED') {
    return (
      <Button
        size="sm"
        variant="light"
        loading={unblock.isPending}
        aria-label={`Unblock ${product.name}`}
        onClick={() => unblock.mutate(product.id, { onSuccess: () => toast.success(`${product.name} is unblocked`), onError: failed })}
      >
        Unblock
      </Button>
    )
  }
  return (
    <>
      <Button size="sm" variant="outline" aria-label={`Block ${product.name}`} onClick={() => setConfirming(true)}>
        Block
      </Button>
      <ConfirmDialog
        open={confirming}
        title={`Block ${product.name}?`}
        message="Customers no longer see it, and the store cannot show it again until an admin unblocks it."
        confirmLabel="Block product"
        loading={block.isPending}
        onConfirm={() =>
          block.mutate(product.id, {
            onSuccess: () => {
              setConfirming(false)
              toast.success(`${product.name} is blocked`)
            },
            onError: failed,
          })
        }
        onCancel={() => setConfirming(false)}
      />
    </>
  )
}

function StockCell({ product }: { product: Product }) {
  if (!product.inStock) return <Badge tone="hot">Sold out</Badge>
  return (
    <span className="flex flex-wrap items-center gap-2">
      <span className="font-bold text-heading">{product.availableQuantity}</span>
      {product.lowStock && <Badge tone="new">Low</Badge>}
    </span>
  )
}

/** Every store's products: search, filters, and Block / Unblock. /admin/products?q=milk&store=1&category=3&status=BLOCKED&lowStock=true */
export function AdminProductsPage() {
  const { page, pageSize, filter, idFilter, setFilter, goToPage, setPageSize } = useUrlFilters()
  const q = filter('q')
  const storeId = idFilter('store')
  const categoryId = idFilter('category')
  const status = STATUS_FILTERS.find((candidate) => candidate === filter('status'))
  const lowStockOnly = filter('lowStock') === 'true'
  const { data: stores = [] } = useStoreOptions()
  const { data: categories = [] } = useAdminCategories()
  const storeName = (id: number) => stores.find((store) => store.id === id)?.name ?? `Store #${id}`
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminProducts(
    { q: q || undefined, storeId, categoryId, status, lowStockOnly: lowStockOnly || undefined, sort: 'NAME' },
    page,
    pageSize,
  )
  const filtered = Boolean(q || storeId || categoryId || status || lowStockOnly)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-96" />
  } else if (isError) {
    content = <ErrorState title="Could not load the products" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="🔍"
        title={filtered ? 'No products match these filters' : 'No products yet'}
        action={filtered ? <ButtonLink to="/admin/products">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['product', 'products']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn('relative overflow-x-auto rounded-2xl border border-line bg-card', isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[56rem] text-left text-sm">
            <caption className="sr-only">Products of every store ({data.totalElements})</caption>
            <thead className="border-b border-line bg-surface font-heading text-heading">
              <tr>
                <th scope="col" className="px-4 py-3">Product</th>
                <th scope="col" className="px-4 py-3">Store</th>
                <th scope="col" className="px-4 py-3">Category</th>
                <th scope="col" className="px-4 py-3">Price</th>
                <th scope="col" className="px-4 py-3">Can buy</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((product) => (
                <tr key={product.id}>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-12 shrink-0">
                        <ProductImage product={product} emojiClassName="text-2xl" />
                      </div>
                      <div>
                        {product.status === 'ACTIVE' ? (
                          <Link to={`/products/${product.id}`} className="font-heading font-bold text-brand-text hover:underline">
                            {product.name}
                          </Link>
                        ) : (
                          <span className="font-heading font-bold text-heading">{product.name}</span>
                        )}
                        <p className="text-xs">{product.sku}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <Link to={`/admin/stores/${product.storeId}`} className="hover:text-brand-text hover:underline">
                      {storeName(product.storeId)}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{product.categoryName}</td>
                  <td className="px-4 py-3">
                    <Price sellingPrice={product.sellingPrice} price={product.price} size="sm" />
                  </td>
                  <td className="px-4 py-3">
                    <StockCell product={product} />
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={STATUS_TONES[product.status]}>{STATUS_LABELS[product.status]}</Badge>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <ModerationButton product={product} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['product', 'products']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Products" />
      <h1 className="mb-2 text-3xl">Products</h1>
      <p className="mb-6 text-sm">Every store's products. Block one that breaks the rules: customers no longer see it, and its store cannot show it again.</p>
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <SearchBox label="Search products" placeholder="Name, brand or code" value={q} onSearch={(value) => setFilter('q', value)} />
        <FilterSelect label="Store" allLabel="All stores" value={storeId ? String(storeId) : ''} options={stores.map((store) => ({ value: String(store.id), label: store.name }))} onChange={(value) => setFilter('store', value)} />
        <FilterSelect label="Category" allLabel="All categories" value={categoryId ? String(categoryId) : ''} options={categories.map((category) => ({ value: String(category.id), label: category.name }))} onChange={(value) => setFilter('category', value)} />
        <FilterSelect label="Product status" allLabel="All statuses" value={status ?? ''} options={STATUS_FILTERS.map((value) => ({ value, label: STATUS_LABELS[value] }))} onChange={(value) => setFilter('status', value)} />
        <label className="flex items-center gap-2 text-sm text-heading">
          <input type="checkbox" checked={lowStockOnly} onChange={(event) => setFilter('lowStock', event.target.checked ? 'true' : '')} className="size-4 accent-brand" />
          Low stock only
        </label>
      </div>
      {content}
    </>
  )
}
