import { Plus, Search } from 'lucide-react'
import { useId, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Product, ProductStatus } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { ProductImage } from '../../../components/product/ProductImage'
import { Badge } from '../../../components/ui/Badge'
import { Button, ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { Price } from '../../../components/ui/Price'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useCategories } from '../../../hooks/useCategories'
import { cn } from '../../../utils/cn'
import { PRODUCT_STATUS_LABELS, PRODUCT_STATUS_TONES } from './productStatus'
import { useMyProducts, useProductChanges, VENDOR_PRODUCTS_PER_PAGE } from './useVendorProducts'

const STATUS_FILTERS: ProductStatus[] = ['ACTIVE', 'INACTIVE', 'BLOCKED']

/** The list's filters live in the URL: /vendor/products?q=milk&status=INACTIVE&category=3&lowStock=true&page=2 */
function readFilters(params: URLSearchParams) {
  const status = params.get('status') as ProductStatus | null
  const category = Number(params.get('category'))
  return {
    q: params.get('q')?.trim() ?? '',
    status: status && STATUS_FILTERS.includes(status) ? status : undefined,
    categoryId: Number.isInteger(category) && category > 0 ? category : undefined,
    lowStockOnly: params.get('lowStock') === 'true',
    page: Math.max(1, Number.parseInt(params.get('page') ?? '1', 10) || 1),
  }
}

/** "Show" / "Hide" right in the list. */
function VisibilityButton({ product }: { product: Product }) {
  const { setStatus } = useProductChanges()
  if (product.status === 'BLOCKED') return null
  const active = product.status === 'ACTIVE'

  function toggle() {
    setStatus.mutate(
      { productId: product.id, status: active ? 'INACTIVE' : 'ACTIVE' },
      {
        onSuccess: () => toast.success(active ? `${product.name} is hidden from customers` : `${product.name} is visible to customers`),
        onError: (error) => {
          if (!isApiError(error, 401)) toast.error(error.message)
        },
      },
    )
  }

  return (
    <Button variant="ghost" size="sm" loading={setStatus.isPending} onClick={toggle} aria-label={`${active ? 'Hide' : 'Show'} ${product.name}`}>
      {active ? 'Hide' : 'Show'}
    </Button>
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

/** The store's products with their stock: search, filters, show / hide, and the way to add or change a product. */
export function VendorProductsPage() {
  const searchId = useId()
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = readFilters(searchParams)
  const { pageSize, setPageSize } = useUrlFilters(VENDOR_PRODUCTS_PER_PAGE)
  const { data: categories = [] } = useCategories()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useMyProducts({
    q: filters.q || undefined,
    status: filters.status,
    categoryId: filters.categoryId,
    lowStockOnly: filters.lowStockOnly || undefined,
    page: filters.page - 1,
    size: pageSize,
  })
  const filtered = Boolean(filters.q || filters.status || filters.categoryId || filters.lowStockOnly)

  /** Changes one filter (back to page 1); empty values are left out of the URL. */
  function setFilter(name: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(name, value)
    else next.delete(name)
    next.delete('page')
    setSearchParams(next)
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFilter('q', String(new FormData(event.currentTarget).get('q') ?? '').trim())
  }

  function goToPage(page: number) {
    const next = new URLSearchParams(searchParams)
    if (page > 1) next.set('page', String(page))
    else next.delete('page')
    setSearchParams(next)
  }

  let content
  if (isPending) {
    content = <LoadingState className="min-h-96" />
  } else if (isError) {
    content = <ErrorState title="Could not load your products" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = filtered ? (
      <EmptyState icon="🔍" title="No products match these filters" action={<ButtonLink to="/vendor/products">Clear the filters</ButtonLink>} className="rounded-2xl bg-card" />
    ) : (
      <EmptyState icon="🥕" title="No products yet" message="Add your first product: customers see it once it has stock." action={<ButtonLink to="/vendor/products/new">Add a product</ButtonLink>} className="rounded-2xl bg-card" />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={filters.page} pageSize={pageSize} itemWords={['product', 'products']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn('relative overflow-x-auto rounded-2xl border border-line bg-card', isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[48rem] text-left text-sm">
            <caption className="sr-only">Your products ({data.totalElements})</caption>
            <thead className="border-b border-line bg-surface font-heading text-heading">
              <tr>
                <th scope="col" className="px-4 py-3">Product</th>
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
                        <Link to={`/vendor/products/${product.id}`} className="font-heading font-bold text-brand-text hover:underline">
                          {product.name}
                        </Link>
                        <p className="text-xs">{product.sku}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">{product.categoryName}</td>
                  <td className="px-4 py-3">
                    <Price sellingPrice={product.sellingPrice} price={product.price} size="sm" />
                  </td>
                  <td className="px-4 py-3">
                    <StockCell product={product} />
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={PRODUCT_STATUS_TONES[product.status]}>{PRODUCT_STATUS_LABELS[product.status]}</Badge>
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <VisibilityButton product={product} />
                    <ButtonLink to={`/vendor/products/${product.id}`} variant="light" size="sm" className="ml-2" aria-label={`Edit ${product.name}`}>
                      Edit
                    </ButtonLink>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={filters.page} pageSize={pageSize} itemWords={['product', 'products']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }


  return (
    <>
      <PageTitle title="Products" />
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl">Products</h1>
        <ButtonLink to="/vendor/products/new">
          <Plus className="size-4" aria-hidden="true" />
          Add a product
        </ButtonLink>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        {/* A new key empties the box when the search is cleared elsewhere (e.g. "Clear the filters") */}
        <form key={filters.q} onSubmit={handleSearch} role="search" className="flex">
          <label htmlFor={searchId} className="sr-only">
            Search your products
          </label>
          <input id={searchId} type="search" name="q" defaultValue={filters.q} placeholder="Name, brand or code" className="h-10 w-56 rounded-l-lg border border-line bg-card px-3 text-sm text-heading outline-none focus:border-brand" />
          <button type="submit" className="flex h-10 items-center rounded-r-lg bg-brand px-3 text-white hover:bg-brand-dark" aria-label="Search">
            <Search className="size-4" aria-hidden="true" />
          </button>
        </form>
        <FilterSelect
          label="Status"
          allLabel="All statuses"
          value={filters.status ?? ''}
          options={STATUS_FILTERS.map((status) => ({ value: status, label: PRODUCT_STATUS_LABELS[status] }))}
          onChange={(status) => setFilter('status', status)}
        />
        <FilterSelect
          label="Category"
          allLabel="All categories"
          value={filters.categoryId ? String(filters.categoryId) : ''}
          options={categories.map((category) => ({ value: String(category.id), label: category.name }))}
          onChange={(categoryId) => setFilter('category', categoryId)}
        />
        <label className="flex items-center gap-2 text-sm text-heading">
          <input type="checkbox" checked={filters.lowStockOnly} onChange={(event) => setFilter('lowStock', event.target.checked ? 'true' : '')} className="size-4 accent-brand" />
          Low stock only
        </label>
      </div>

      {content}
    </>
  )
}
