import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import type { ProductRequest } from '../../../api/vendorProducts'
import { PageTitle } from '../../../components/layout/PageTitle'
import { ProductForm } from './ProductForm'
import { newProductValues } from './productValidation'
import { useProductChanges } from './useVendorProducts'

/** "Add a product": the details first; stock and photos follow on the product's own page. */
export function VendorNewProductPage() {
  const navigate = useNavigate()
  const { create } = useProductChanges()

  async function save(request: ProductRequest) {
    const product = await create.mutateAsync(request)
    toast.success(`${product.name} saved. Now set its stock and add a photo.`)
    navigate(`/vendor/products/${product.id}`, { replace: true })
  }

  return (
    <>
      <PageTitle title="Add a product" />
      <Link to="/vendor/products" className="mb-4 inline-flex items-center gap-1.5 text-sm font-bold text-brand-text hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All products
      </Link>
      <h1 className="mb-6 text-3xl">Add a product</h1>
      <section className="max-w-3xl rounded-2xl border border-line bg-card p-6">
        <ProductForm initialValues={newProductValues()} submitLabel="Save product" onSave={save} />
      </section>
    </>
  )
}
