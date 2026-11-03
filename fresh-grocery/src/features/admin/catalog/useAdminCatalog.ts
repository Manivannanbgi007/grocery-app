import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  blockProduct,
  createCategory,
  getAllCategories,
  getAnyProduct,
  hideReview,
  searchAllProducts,
  searchReviews,
  setCategoryActive,
  showReview,
  unblockProduct,
  updateCategory,
  type AdminProductFilter,
  type AdminReviewFilter,
  type CategoryRequest,
} from '../../../api/adminCatalog'
import { queryKeys } from '../../../api/queryKeys'
import type { Category, PageResponse, Product, ProductStatus } from '../../../api/types'
import { useAuth } from '../../auth/authContext'

/*
 * Products and categories are changed in product-service but read from product-read-service, which gets the change a
 * second or two later (CQRS). So after a change the answer is put into the cached lists at once, and the lists are
 * loaded again after READ_MODEL_DELAY_MS. Reviews live in product-service only: no delay.
 */
const READ_MODEL_DELAY_MS = 2000

export const ADMIN_PRODUCTS_PER_PAGE = 20
export const ADMIN_REVIEWS_PER_PAGE = 20

export function useAdminProducts(filter: Omit<AdminProductFilter, 'page'>, page: number, size = ADMIN_PRODUCTS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminProducts(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchAllProducts(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Block and unblock a product. The new status shows in the lists at once; the lists are loaded again a moment later. */
export function useProductModeration() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0

  function saved(productId: number, status: ProductStatus) {
    queryClient.setQueriesData<PageResponse<Product>>({ queryKey: queryKeys.adminProductLists(userId) }, (page) =>
      page ? { ...page, content: page.content.map((product) => (product.id === productId ? { ...product, status } : product)) } : page,
    )
    setTimeout(() => void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminProducts(userId) }), READ_MODEL_DELAY_MS)
  }

  return {
    block: useMutation({ mutationFn: (productId: number) => blockProduct(productId), onSuccess: (product) => saved(product.id, product.status) }),
    unblock: useMutation({ mutationFn: (productId: number) => unblockProduct(productId), onSuccess: (product) => saved(product.id, product.status) }),
  }
}

/** The names of some products of any store (one request per product, cached), e.g. for the reviews. */
export function useProductNames(productIds: number[]): Map<number, string> {
  const { admin } = useAuth()
  const results = useQueries({
    queries: productIds.map((productId) => ({
      queryKey: queryKeys.adminProduct(admin?.id ?? 0, productId),
      queryFn: ({ signal }: { signal: AbortSignal }) => getAnyProduct(productId, signal),
      enabled: admin !== null,
      staleTime: 5 * 60_000,
    })),
  })
  const names = new Map<number, string>()
  productIds.forEach((productId, index) => {
    const name = results[index]?.data?.name
    if (name) names.set(productId, name)
  })
  return names
}

/** Every category, also the hidden ones. */
export function useAdminCategories() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminCategories(admin?.id ?? 0),
    queryFn: ({ signal }) => getAllCategories(signal),
    enabled: admin !== null,
  })
}

/** Add, change, show and hide categories. The saved one goes into the cached list at once; the shop's menus follow. */
export function useCategoryChanges() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0

  function saved(category: Category) {
    queryClient.setQueryData<Category[]>(queryKeys.adminCategories(userId), (current = []) => {
      const others = current.filter((candidate) => candidate.id !== category.id)
      return [...others, category].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0) || a.name.localeCompare(b.name))
    })
    setTimeout(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminCategories(userId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.categories })
    }, READ_MODEL_DELAY_MS)
  }

  return {
    create: useMutation({ mutationFn: (request: CategoryRequest) => createCategory(request), onSuccess: saved }),
    update: useMutation({ mutationFn: ({ categoryId, request }: { categoryId: number; request: CategoryRequest }) => updateCategory(categoryId, request), onSuccess: saved }),
    setActive: useMutation({ mutationFn: ({ categoryId, active }: { categoryId: number; active: boolean }) => setCategoryActive(categoryId, active), onSuccess: saved }),
  }
}

export function useAdminReviews(filter: Omit<AdminReviewFilter, 'page'>, page: number, size = ADMIN_REVIEWS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminReviews(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchReviews(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Hide and show reviews; the lists are loaded again (the product's rating changes with them). */
export function useReviewModeration() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const reload = () => void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminReviews(admin?.id ?? 0) })
  return {
    hide: useMutation({ mutationFn: ({ reviewId, reason }: { reviewId: number; reason: string }) => hideReview(reviewId, reason), onSuccess: reload }),
    show: useMutation({ mutationFn: (reviewId: number) => showReview(reviewId), onSuccess: reload }),
  }
}
