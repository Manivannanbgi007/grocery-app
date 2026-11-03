import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isApiError } from '../../../api/client'
import { queryKeys } from '../../../api/queryKeys'
import type { Product, SavedProduct, Stock } from '../../../api/types'
import {
  addStock,
  createProduct,
  deleteProduct,
  getMyProduct,
  getMyProducts,
  getMyStock,
  removeProductImage,
  setProductStatus,
  setStock,
  updateProduct,
  uploadProductImage,
  type ProductRequest,
  type VendorProductFilter,
} from '../../../api/vendorProducts'
import { useAuth } from '../../auth/authContext'

/*
 * Products are written to product-service but read from product-read-service, which gets the change a second or two
 * later (CQRS). So after a change: the answer is merged into the cached product at once, and the lists and the product
 * are loaded again now and once more after READ_MODEL_DELAY_MS.
 */
const READ_MODEL_DELAY_MS = 2000

/** A 404 right after creating a product (not in the read model yet): ask again every second, for a few seconds. */
const retryWhileIndexing = {
  retry: (failureCount: number, error: Error) => (isApiError(error, 404) ? failureCount < 6 : !isApiError(error) && failureCount < 1),
  retryDelay: 1000,
}

export const VENDOR_PRODUCTS_PER_PAGE = 20

export function useMyProducts(filter: VendorProductFilter) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorProducts(vendor?.id ?? 0, filter),
    queryFn: ({ signal }) => getMyProducts(filter, signal),
    enabled: vendor !== null,
    placeholderData: keepPreviousData,
  })
}

/** How many products are low on stock (one tiny request: size=1, totalElements). */
export function useLowStockCount() {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorProducts(vendor?.id ?? 0, { lowStockOnly: true, size: 1 }),
    queryFn: ({ signal }) => getMyProducts({ lowStockOnly: true, size: 1 }, signal).then((page) => page.totalElements),
    enabled: vendor !== null,
  })
}

export function useMyProduct(productId: number) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorProduct(vendor?.id ?? 0, productId),
    queryFn: ({ signal }) => getMyProduct(productId, signal),
    enabled: vendor !== null,
    ...retryWhileIndexing,
  })
}

/** Loads the vendor's products again: now, and once more when the read model has caught up. */
function useReloadProducts() {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  return (productId?: number) => {
    const reload = () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.allVendorProducts(vendor?.id ?? 0) })
      if (productId !== undefined) void queryClient.invalidateQueries({ queryKey: queryKeys.vendorProduct(vendor?.id ?? 0, productId) })
    }
    reload()
    setTimeout(reload, READ_MODEL_DELAY_MS)
  }
}

/** Adding, changing, hiding and deleting products and their photos. The callers show the errors. */
export function useProductChanges() {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  const reloadProducts = useReloadProducts()

  /** The saved product (from product-service) replaces those fields of the cached one; stock and rating stay. */
  function saved(product: SavedProduct) {
    const { createdAt: _createdAt, ...fields } = product
    queryClient.setQueryData<Product>(queryKeys.vendorProduct(vendor?.id ?? 0, product.id), (current) => (current ? { ...current, ...fields } : current))
    reloadProducts(product.id)
  }

  return {
    create: useMutation({ mutationFn: (request: ProductRequest) => createProduct(request), onSuccess: (product) => reloadProducts(product.id) }),
    update: useMutation({ mutationFn: ({ productId, request }: { productId: number; request: ProductRequest }) => updateProduct(productId, request), onSuccess: saved }),
    setStatus: useMutation({
      mutationFn: ({ productId, status }: { productId: number; status: 'ACTIVE' | 'INACTIVE' }) => setProductStatus(productId, status),
      onSuccess: saved,
    }),
    remove: useMutation({ mutationFn: (productId: number) => deleteProduct(productId), onSuccess: () => reloadProducts() }),
    uploadImage: useMutation({ mutationFn: ({ productId, image }: { productId: number; image: File }) => uploadProductImage(productId, image), onSuccess: saved }),
    removeImage: useMutation({ mutationFn: ({ productId, imageUrl }: { productId: number; imageUrl: string }) => removeProductImage(productId, imageUrl), onSuccess: saved }),
  }
}

/** The product's stock; null while it has none (a new product: "no stock record ... yet"). */
export function useStock(productId: number) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorStock(vendor?.id ?? 0, productId),
    queryFn: async ({ signal }): Promise<Stock | null> => {
      try {
        return await getMyStock(productId, signal)
      } catch (error) {
        if (isApiError(error, 404) && error.message.includes('no stock record')) return null
        throw error
      }
    },
    enabled: vendor !== null,
    ...retryWhileIndexing,
  })
}

/** Setting the counted units or adding received goods. The answer replaces the cached stock; the lists catch up later. */
export function useStockChanges(productId: number) {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  const reloadProducts = useReloadProducts()
  const saved = (stock: Stock) => {
    queryClient.setQueryData(queryKeys.vendorStock(vendor?.id ?? 0, productId), stock)
    reloadProducts(productId)
  }
  return {
    set: useMutation({
      mutationFn: ({ quantityOnHand, lowStockThreshold }: { quantityOnHand: number; lowStockThreshold: number | null }) => setStock(productId, quantityOnHand, lowStockThreshold),
      onSuccess: saved,
    }),
    add: useMutation({ mutationFn: (quantity: number) => addStock(productId, quantity), onSuccess: saved }),
  }
}
