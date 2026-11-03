import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { liveBanners } from '../../mocks/mockBanners'
import { activeCategories, catalogProduct, setProductStatus } from '../../mocks/mockCatalog'
import { publicCoupons } from '../../mocks/mockCoupons'
import { allMockReviews } from '../../mocks/mockRatings'
import { chooseOption, pickDate } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'

/*
 * The catalog, marketing and insights pages of the admin panel (mock API): products of both stores, the 21 demo
 * categories, the demo reviews (101 = Priya on Tomato, 5 stars), the six seed coupons, the banners (2 live, Diwali
 * ended) and the seed notifications (customer1's orders, Fresh Mart's "New order #903").
 */

const rows = (table: HTMLElement) => within(table).getAllByRole('row').slice(1)
const firstCell = (row: HTMLElement) => within(row).getAllByRole('cell')[0].textContent

describe('Admin products', () => {
  it("searches every store's products and blocks one, which its vendor cannot undo", async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/products')
    await screen.findByRole('table', { name: /Products of every store/ }, { timeout: 10_000 })
    await user.type(screen.getByLabelText('Search products'), 'tomato{Enter}')
    // Tomato, Kissan Fresh Tomato Ketchup and Maggi Hot & Sweet Tomato Chilli Sauce
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /Products of every store/ }))).toHaveLength(3))

    await user.click(screen.getByRole('button', { name: 'Block Tomato' }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Block Tomato?' })).getByRole('button', { name: 'Block product' }))
    expect(await screen.findByText('Tomato is blocked')).toBeInTheDocument()
    expect(screen.getByText('Blocked', { selector: 'span' })).toBeInTheDocument()
    expect(catalogProduct(1)?.status).toBe('BLOCKED')
    expect(() => setProductStatus(1, 1, 'ACTIVE')).toThrow('This product was blocked by an admin. Please contact support.')

    await user.click(screen.getByRole('button', { name: 'Unblock Tomato' }))
    expect(await screen.findByText('Tomato is unblocked')).toBeInTheDocument()
    expect(catalogProduct(1)?.status).toBe('ACTIVE')
  })
})

describe('Admin categories', () => {
  const table = () => screen.getByRole('table', { name: /Categories/ })
  const search = async (user: ReturnType<typeof renderApp>['user'], text: string) => {
    const box = screen.getByRole('searchbox', { name: 'Search categories' })
    await user.clear(box)
    await user.type(box, `${text}{Enter}`)
  }

  it('shows 10 categories per page, pages through them and finds them by a part of the name, slug or description', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/categories')
    await screen.findByRole('table', { name: /Categories/ }, { timeout: 10_000 })
    expect(rows(table())).toHaveLength(10)
    expect(screen.getAllByText((_, element) => element?.tagName === 'P' && element.textContent === 'Showing 1–10 of 21 categories')).toHaveLength(2)

    await user.click(within(screen.getByRole('navigation', { name: 'Pages, below the list' })).getByRole('button', { name: 'Page 3' }))
    await waitFor(() => expect(rows(table())).toHaveLength(1))
    expect(router.state.location.search).toBe('?page=3')

    // A search goes back to page 1; it matches the slug too ("frozen-foods")
    await search(user, 'FROZEN')
    await waitFor(() => expect(rows(table())).toHaveLength(1))
    expect(within(table()).getByText('Frozen Foods')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?q=FROZEN')

    await search(user, 'no such thing')
    expect(await screen.findByRole('heading', { name: 'No categories match “no such thing”' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear the search' }))
    await waitFor(() => expect(rows(table())).toHaveLength(10))
    expect(router.state.location.search).toBe('')
  })

  it('adds a category, refuses a name that exists, and hides one from the shop', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/categories?size=50')
    await screen.findByRole('table', { name: /Categories/ }, { timeout: 10_000 })
    expect(rows(table())).toHaveLength(21)

    await user.click(screen.getByRole('button', { name: 'New category' }))
    const form = screen.getByRole('region', { name: 'New category' })
    await user.type(within(form).getByLabelText('Category name'), 'vegetables')
    await user.click(within(form).getByRole('button', { name: 'Add category' }))
    expect(await within(form).findByText('A category with this name already exists: vegetables')).toBeInTheDocument()

    await user.clear(within(form).getByLabelText('Category name'))
    await user.type(within(form).getByLabelText('Category name'), 'Organic Corner')
    await user.type(within(form).getByLabelText('Position'), '12')
    await user.click(within(form).getByRole('button', { name: 'Add category' }))
    expect(await screen.findByText('Organic Corner added')).toBeInTheDocument()
    await search(user, 'organic')
    expect(await within(table()).findByText('organic-corner')).toBeInTheDocument()

    await search(user, 'frozen')
    await user.click(await screen.findByRole('button', { name: 'Hide Frozen Foods' }))
    expect(await screen.findByText('Frozen Foods is hidden from the shop')).toBeInTheDocument()
    expect(activeCategories().map((category) => category.name)).not.toContain('Frozen Foods')
  })
})

describe('Admin reviews', () => {
  it('hides a review with a reason (customers no longer see it) and shows it again', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/reviews')
    const list = await screen.findByRole('list', { name: 'Reviews' }, { timeout: 10_000 })
    await user.click(screen.getByRole('link', { name: '4 stars' }))
    await waitFor(() => expect(router.state.location.search).toBe('?rating=4'))
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Reviews' })).getAllByRole('listitem').every((item) => item.textContent?.includes('4 of 5'))).toBe(true))
    expect(list).toBeInTheDocument()

    // Rahul's review of Tomato (102): "a couple were a bit soft"
    const item = within(screen.getByRole('list', { name: 'Reviews' }))
      .getAllByRole('listitem')
      .find((candidate) => candidate.textContent?.includes('a couple were a bit soft'))!
    expect(await within(item).findByText('Tomato')).toBeInTheDocument()
    await user.click(within(item).getByRole('button', { name: 'Hide the review of Rahul Verma' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Hide the review of Rahul Verma?' })
    await user.click(within(dialog).getByRole('button', { name: 'Not about the product' }))
    await user.click(within(dialog).getByRole('button', { name: 'Hide review' }))
    expect(await screen.findByText('The review is hidden')).toBeInTheDocument()
    expect(allMockReviews().find((review) => review.id === 102)?.status).toBe('HIDDEN')
    expect(await screen.findByText('Hidden because: “Not about the product”')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Show the review of Rahul Verma' }))
    expect(await screen.findByText('The review is visible again')).toBeInTheDocument()
    expect(allMockReviews().find((review) => review.id === 102)?.status).toBe('VISIBLE')
  })
})

describe('Admin coupons', () => {
  it('creates a platform coupon, and only switches store coupons on or off', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/coupons')
    const platform = await screen.findByRole('table', { name: /Platform coupons/ }, { timeout: 10_000 })
    expect(rows(platform).map(firstCell)).toEqual(['SUMMER25', 'SAVE10', 'WELCOME50'])

    await user.click(screen.getByRole('button', { name: 'New platform coupon' }))
    await user.type(screen.getByLabelText('Code'), 'diwali10')
    await user.type(screen.getByLabelText('Description'), 'Diwali: 10% off everywhere')
    await user.click(screen.getByRole('button', { name: 'Create coupon' }))
    expect(await screen.findByText('Coupon DIWALI10 created')).toBeInTheDocument()
    expect(publicCoupons(null).map((coupon) => coupon.code)).toContain('DIWALI10')

    await user.click(screen.getByRole('link', { name: 'All coupons' }))
    // The platform coupons stay on screen (dimmed) until the whole list has loaded
    const mango = await waitFor(() => {
      const row = rows(screen.getByRole('table', { name: /All coupons/ })).find((candidate) => firstCell(candidate) === 'MANGO15')
      if (!row) throw new Error('MANGO15 is not listed yet')
      return row
    })
    expect(within(mango).queryByRole('button', { name: /Change coupon/ })).not.toBeInTheDocument()
    await user.click(within(mango).getByRole('switch', { name: 'Coupon MANGO15 on' }))
    expect(await screen.findByText('MANGO15 is on')).toBeInTheDocument()
  })
})

describe('Admin banners', () => {
  it('adds a banner after checking its dates, and deletes one', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/banners')
    const list = await screen.findByRole('list', { name: 'Banners' }, { timeout: 10_000 })
    expect(within(list).getAllByText('Live')).toHaveLength(2)
    expect(within(list).getByText('Ended')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New banner' }))
    const form = screen.getByRole('region', { name: 'New banner' })
    await user.type(within(form).getByLabelText('Title'), 'Mango season')
    await user.type(within(form).getByLabelText('Picture address'), 'https://example.com/mango.jpg')
    await pickDate(user, within(form).getByLabelText('Shown from (optional), day'), '2026-12-10')
    await chooseOption(user, within(form).getByLabelText('Shown from (optional), time'), '9:00 am')
    await pickDate(user, within(form).getByLabelText('Shown until (optional), day'), '2026-12-01')
    await chooseOption(user, within(form).getByLabelText('Shown until (optional), time'), '9:00 am')
    await user.click(within(form).getByRole('button', { name: 'Add banner' }))
    expect(within(form).getByText('The banner must end after it starts.')).toBeInTheDocument()

    // Clearing a day empties its time too
    for (const field of ['Shown from (optional), day', 'Shown until (optional), day']) {
      await user.click(within(form).getByLabelText(field))
      await user.click(within(screen.getByRole('dialog', { name: /choose a date/ })).getByRole('button', { name: 'Clear' }))
    }
    expect(within(form).getByLabelText('Shown from (optional), time')).toBeDisabled()
    await user.click(within(form).getByRole('button', { name: 'Add banner' }))
    expect(await screen.findByText('Banner added')).toBeInTheDocument()
    expect(liveBanners().map((banner) => banner.title)).toContain('Mango season')

    await user.click(await screen.findByRole('button', { name: 'Delete the banner Mango season' }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Delete “Mango season”?' })).getByRole('button', { name: 'Delete banner' }))
    expect(await screen.findByText('Banner deleted')).toBeInTheDocument()
    expect(liveBanners().map((banner) => banner.title)).not.toContain('Mango season')
  })
})

describe('Admin insights', () => {
  it('shows a report for a chosen period and checks the days', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/reports')
    expect(await screen.findByRole('heading', { name: 'Sales per day' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')).toHaveLength(30)
    expect(await screen.findByRole('list', { name: 'Top stores' })).toHaveTextContent('Fresh Mart')

    const [from, to] = [screen.getByLabelText('From'), screen.getByLabelText('To')]
    await pickDate(user, from, '2026-10-20')
    await pickDate(user, to, '2026-10-10')
    await user.click(screen.getByRole('button', { name: 'Show' }))
    expect(screen.getByRole('alert')).toHaveTextContent('The first day must not be after the last day.')

    await pickDate(user, to, '2026-10-22')
    await user.click(screen.getByRole('button', { name: 'Show' }))
    await waitFor(() => expect(router.state.location.search).toBe('?from=2026-10-20&to=2026-10-22'))
    // The mock has no orders on those days: the card names the period and says so instead of drawing a flat line
    expect(await screen.findByText('20 Oct – 22 Oct 2026 · 3 days')).toBeInTheDocument()
    expect(screen.getByText('No sales in this period')).toBeInTheDocument()
  })

  it('lists the notifications that were sent, by type', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/notifications')
    const table = await screen.findByRole('table', { name: /Notifications, newest first/ }, { timeout: 10_000 })
    expect(rows(table)).toHaveLength(5)
    await chooseOption(user, screen.getByRole('combobox', { name: 'Type' }), 'New order')
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /Notifications, newest first/ }))).toHaveLength(1))
    expect(screen.getByRole('link', { name: 'Account #3' })).toHaveAttribute('href', '/admin/accounts/3')
  })
})
