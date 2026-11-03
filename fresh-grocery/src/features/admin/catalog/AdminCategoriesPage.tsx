import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Category } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { SearchBox } from '../../../components/panel/SearchBox'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { ImageWithFallback } from '../../../components/ui/ImageWithFallback'
import { LoadingState } from '../../../components/ui/LoadingState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { categoryEmoji } from '../../../utils/emoji'
import { pageOfList } from '../../../utils/pagination'
import { CategoryForm } from './CategoryForm'
import { useAdminCategories, useCategoryChanges } from './useAdminCatalog'

/** Rows per page when the URL does not choose (10, 20 or 50 in "Rows per page"). */
const PAGE_SIZE = 10

/** The categories whose name, slug or description contains the search (capitals do not matter); all for no search. */
function matching(categories: Category[], search: string): Category[] {
  const wanted = search.toLowerCase()
  if (!wanted) return categories
  return categories.filter((category) => [category.name, category.slug, category.description ?? ''].some((text) => text.toLowerCase().includes(wanted)))
}

/** "Hide" / "Show" for one category. */
function VisibilityButton({ category }: { category: Category }) {
  const { setActive } = useCategoryChanges()
  function toggle() {
    setActive.mutate(
      { categoryId: category.id, active: !category.active },
      {
        onSuccess: (saved) => toast.success(saved.active ? `${saved.name} is shown in the shop` : `${saved.name} is hidden from the shop`),
        onError: (error) => {
          if (!isApiError(error, 401)) toast.error(error.message)
        },
      },
    )
  }
  return (
    <Button size="sm" variant="ghost" loading={setActive.isPending} onClick={toggle} aria-label={`${category.active ? 'Hide' : 'Show'} ${category.name}`}>
      {category.active ? 'Hide' : 'Show'}
    </Button>
  )
}

/**
 * The shop's categories (vendors choose from them): add, change, show and hide; search and pages.
 * The API returns all categories at once (a shop has a few dozen), so the search and the pages work in the browser;
 * both are in the URL (?q=, ?page=, ?size=) like on the other admin lists.
 */
export function AdminCategoriesPage() {
  const { data: categories, isPending, isError, error, refetch } = useAdminCategories()
  const { page, pageSize, filter, setFilter, goToPage, setPageSize } = useUrlFilters(PAGE_SIZE)
  const search = filter('q')
  /** the category being changed, 'new' while adding one, or null */
  const [editing, setEditing] = useState<Category | 'new' | null>(null)

  const list = pageOfList(matching(categories ?? [], search), page, pageSize)
  const pagination = (position: 'top' | 'bottom') => (
    <TablePagination
      position={position}
      list={list}
      page={list.page}
      pageSize={pageSize}
      itemWords={['category', 'categories']}
      onPageChange={goToPage}
      onPageSizeChange={setPageSize}
      className={position === 'top' ? 'mb-4' : 'mt-4'}
    />
  )

  let content
  if (isPending) content = <LoadingState className="min-h-64" />
  else if (isError) content = <ErrorState title="Could not load the categories" error={error} onRetry={() => void refetch()} />
  else if (categories.length === 0) content = <EmptyState icon="🗂️" title="No categories yet" className="rounded-2xl bg-card" />
  else if (list.totalElements === 0) {
    content = (
      <EmptyState
        icon="🔍"
        title={`No categories match “${search}”`}
        message="Search for a part of a category's name, its slug or its description."
        action={
          <Button variant="outline" onClick={() => setFilter('q', '')}>
            Clear the search
          </Button>
        }
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        {pagination('top')}
        <div className="relative overflow-x-auto rounded-2xl border border-line bg-card">
          <table className="w-full min-w-[44rem] text-left text-sm">
            <caption className="sr-only">
              Categories ({list.totalElements}
              {search && ` matching “${search}”`}, page {list.page} of {list.totalPages})
            </caption>
            <thead className="border-b border-line bg-surface font-heading text-heading">
              <tr>
                <th scope="col" className="px-4 py-3">Category</th>
                <th scope="col" className="px-4 py-3">Description</th>
                <th scope="col" className="px-4 py-3">Position</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {list.rows.map((category) => (
                <tr key={category.id}>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <span className="flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-surface text-2xl" aria-hidden="true">
                        <ImageWithFallback src={category.imageUrl} alt="" fallback={categoryEmoji(category.slug)} className="size-full object-cover" />
                      </span>
                      <span>
                        <span className="block font-heading font-bold text-heading">{category.name}</span>
                        <span className="text-xs">{category.slug}</span>
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3">{category.description ?? '–'}</td>
                  <td className="px-4 py-3">{category.displayOrder ?? 0}</td>
                  <td className="px-4 py-3">
                    <Badge tone={category.active ? 'brand' : 'neutral'}>{category.active ? 'Shown' : 'Hidden'}</Badge>
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <VisibilityButton category={category} />
                    <Button size="sm" variant="light" className="ml-2" onClick={() => setEditing(category)} aria-label={`Change ${category.name}`}>
                      Change
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {pagination('bottom')}
      </>
    )
  }

  return (
    <>
      <PageTitle title="Categories" />
      <div className="mb-2 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl">Categories</h1>
        {editing === null && (
          <Button onClick={() => setEditing('new')}>
            <Plus className="size-4" aria-hidden="true" />
            New category
          </Button>
        )}
      </div>
      <p className="mb-6 text-sm">Vendors put their products into these categories. A hidden category disappears from the shop's menus; a change shows up there a moment later.</p>
      {editing !== null && (
        <section aria-labelledby="category-form-title" className="mb-6 rounded-2xl border border-brand/40 bg-card p-6">
          <h2 id="category-form-title" className="mb-4 text-xl">
            {editing === 'new' ? 'New category' : `Change ${editing.name}`}
          </h2>
          <CategoryForm key={editing === 'new' ? 'new' : editing.id} category={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />
        </section>
      )}
      {categories && categories.length > 0 && (
        <div className="mb-4">
          <SearchBox label="Search categories" placeholder="Name, slug or description" value={search} onSearch={(value) => setFilter('q', value)} />
        </div>
      )}
      {content}
    </>
  )
}
