import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { publicCoupons } from '../../mocks/mockCoupons'
import { logInAs, renderApp } from '../../test/render'

/*
 * Coupons, reviews and sales of Fresh Mart (vendor1) on the mock API. Fresh Mart's seed coupons: FRESH20 (running) and
 * MANGO15 (switched off). Its orders: 901 delivered 3 days ago (₹177), 902 cancelled 2 days ago, 903 new today (₹196).
 */

const couponRows = () => within(screen.getByRole('table', { name: 'Your coupons' })).getAllByRole('row').slice(1)
const couponRow = (code: string) => couponRows().find((row) => row.textContent?.startsWith(code))!

describe('Vendor coupons', () => {
  it("lists the store's coupons with their state", async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/vendor/coupons')
    await screen.findByRole('table', { name: 'Your coupons' })
    expect(couponRows()).toHaveLength(2)
    expect(couponRow('FRESH20')).toHaveTextContent('20% off, up to ₹60')
    expect(couponRow('FRESH20')).toHaveTextContent('Running')
    expect(couponRow('MANGO15')).toHaveTextContent('Switched off')
    expect(within(couponRow('MANGO15')).getByRole('switch', { name: 'Coupon MANGO15 on' })).not.toBeChecked()
  })

  it('creates a coupon that customers can then use', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/coupons')
    await user.click(await screen.findByRole('button', { name: 'New coupon' }))

    const discount = screen.getByLabelText('Discount (%)')
    await user.clear(discount)
    await user.type(discount, '150')
    await user.click(screen.getByRole('button', { name: 'Create coupon' }))
    expect(screen.getByText('A percentage discount cannot be more than 100.')).toBeInTheDocument()
    expect(screen.getByText('Use 4 to 20 letters or digits, e.g. FRESH20.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Code'), 'save10')
    await user.type(screen.getByLabelText('Description'), 'Fresh Mart: 15% off')
    await user.clear(discount)
    await user.type(discount, '15')
    await user.click(screen.getByRole('button', { name: 'Create coupon' }))
    // Codes are unique on the whole platform: SAVE10 is a platform coupon
    expect(await screen.findByText('Coupon code SAVE10 is already taken. Choose another code.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Code'))
    await user.type(screen.getByLabelText('Code'), 'fresh15')
    await user.click(screen.getByRole('button', { name: 'Create coupon' }))
    expect(await screen.findByText('Coupon FRESH15 created')).toBeInTheDocument()
    await waitFor(() => expect(couponRows()).toHaveLength(3))
    expect(publicCoupons(1).map((coupon) => coupon.code)).toContain('FRESH15')
  })

  it('switches a coupon on and changes it', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/coupons')
    await screen.findByRole('table', { name: 'Your coupons' })

    await user.click(within(couponRow('MANGO15')).getByRole('switch', { name: 'Coupon MANGO15 on' }))
    expect(await screen.findByText('MANGO15 is on')).toBeInTheDocument()

    await user.click(within(couponRow('FRESH20')).getByRole('button', { name: 'Change coupon FRESH20' }))
    const form = screen.getByRole('heading', { name: 'Change coupon FRESH20' }).closest('section')!
    expect(within(form).getByLabelText('Code')).toHaveValue('FRESH20')
    const minimum = within(form).getByLabelText('Minimum items total')
    await user.clear(minimum)
    await user.type(minimum, '199')
    await user.click(within(form).getByRole('button', { name: 'Save coupon' }))
    expect(await screen.findByText('Coupon FRESH20 saved')).toBeInTheDocument()
    await waitFor(() => expect(couponRow('FRESH20')).toHaveTextContent('On orders of ₹199 or more'))
  })
})

describe('Vendor reviews', () => {
  it("shows the reviews of the store's products, one star rating at a time", async () => {
    logInAs('vendor1@grocery.com')
    const { user, router } = renderApp('/vendor/reviews')
    const list = await screen.findByRole('list', { name: 'Reviews' })
    expect(within(list).getAllByRole('listitem').length).toBeGreaterThan(1)
    // Every review names its product (looked up among the vendor's own products, also hidden ones)
    await waitFor(() => expect(within(list).queryAllByRole('link', { name: /^Product #/ })).toHaveLength(0))

    await user.click(screen.getByRole('link', { name: '5 stars' }))
    expect(router.state.location.search).toBe('?rating=5')
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Reviews' })).getAllByRole('listitem').every((item) => item.textContent?.includes('5 of 5'))).toBe(true))
  })
})

describe('Vendor sales', () => {
  it('shows the totals, the sales per day and the best sellers', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor')
    await screen.findByRole('heading', { name: 'Sales per day' })

    const tile = (label: string) => screen.getByText(label, { selector: 'p' }).nextSibling
    expect(tile('Sales')).toHaveTextContent('₹373')
    expect(tile('Orders')).toHaveTextContent('3')
    expect(tile('Cancelled or rejected')).toHaveTextContent('1')
    expect(within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')).toHaveLength(14)
    const best = screen.getByRole('list', { name: 'Best sellers' })
    expect(within(best).getAllByRole('listitem')[0]).toHaveTextContent('Farm Eggs3 sold')

    await user.click(screen.getByRole('link', { name: '7 days' }))
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')).toHaveLength(7))
  })
})
