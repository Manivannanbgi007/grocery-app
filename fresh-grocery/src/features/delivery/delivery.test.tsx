import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { loadSession } from '../../api/session'
import { goOnline, partnerById, rejectPartner, searchDeliveries } from '../../mocks/mockDelivery'
import { acceptOrder, adminGetOrder, markReadyForPickup, packOrder } from '../../mocks/mockOrders'
import { chooseOption } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'

/*
 * The delivery panel on the mock API. delivery1 = Arjun Singh (user 5, zone 1 Indiranagar: Fresh Mart's zone), approved
 * and offline; he delivered order 901 three days ago (₹40). Order 903 (₹196 cash) waits for Fresh Mart.
 */

// jsdom's FormData cannot reach the mock API through Node's fetch: the photo upload calls the mock directly
vi.mock('../../api/delivery', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/delivery')>()
  const { attachProof } = await import('../../mocks/mockDelivery')
  return { ...original, uploadProof: async (deliveryId: number, photo: File) => attachProof(5, deliveryId, `data:${photo.type};base64,AAAA`) }
})

/** Fresh Mart hands order 903 over: a delivery job for zone 1. */
function orderReadyForPickup() {
  acceptOrder(1, 903)
  packOrder(1, 903)
  markReadyForPickup(1, 903)
}

/** The label of the step marked as the current one in a step bar. */
function stepNow(barName: string): string | null {
  return within(screen.getByRole('list', { name: barName })).queryByRole('listitem', { current: 'step' })?.textContent ?? null
}

async function fillLogin(user: ReturnType<typeof renderApp>['user'], email: string) {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), 'Password@123')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Delivery login and sign-up', () => {
  it('sends a guest to the delivery login, and a partner to today afterwards', async () => {
    const { user, router } = renderApp('/delivery/history')
    expect(await screen.findByRole('heading', { name: 'Delivery login' })).toBeInTheDocument()
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent('/delivery/history')}`)
    await fillLogin(user, 'delivery1@grocery.com')
    expect(await screen.findByRole('heading', { level: 1, name: 'History' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(loadSession()?.user.role).toBe('DELIVERY_PARTNER')
  })

  it('sends a customer to the shop login', async () => {
    const { user } = renderApp('/delivery/login')
    await fillLogin(user, 'customer1@grocery.com')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This is a customer account. Customers log in on the shop.')
    expect(within(alert).getByRole('link', { name: "Go to the shop's login" })).toHaveAttribute('href', '/login')
  })

  it('signs a new partner up, takes the KYC, and shows a rejection with its reason', async () => {
    const { user } = renderApp('/register?role=DELIVERY_PARTNER')
    expect(await screen.findByRole('heading', { level: 1, name: 'Become a delivery partner' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Full name'), 'Vikram Rao')
    await user.type(screen.getByLabelText('Email'), 'vikram@example.com')
    await user.type(screen.getByLabelText('Mobile number'), '9876543210')
    await user.type(screen.getByLabelText('Password', { selector: 'input' }), 'Rider1234')
    await user.click(screen.getByRole('button', { name: 'Create partner account' }))

    expect(await screen.findByRole('heading', { name: 'Send your KYC' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(stepNow('Becoming a delivery partner')).toBe('Send KYC')
    await user.click(screen.getByRole('button', { name: 'Send for review' }))
    expect(screen.getByText('Choose your vehicle.')).toBeInTheDocument()
    expect(screen.getByText('Enter the number as on the plate, e.g. KA01AB1234.')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: /Scooter/ }))
    await user.type(screen.getByLabelText('Vehicle number'), 'ka 03 xy 0001')
    await user.type(screen.getByLabelText('Driving licence number'), 'KA0320210000001')
    await chooseOption(user, screen.getByLabelText('Zone'), 'Indiranagar, Bengaluru')
    await user.click(screen.getByRole('button', { name: 'Send for review' }))
    expect(await screen.findByRole('heading', { name: 'Waiting for review' })).toBeInTheDocument()
    expect(stepNow('Becoming a delivery partner')).toBe('Review')
    expect(screen.getByText('Scooter KA03XY0001')).toBeInTheDocument()

    const vikram = loadSession()!.user.id
    expect(partnerById(vikram)?.kycStatus).toBe('PENDING')
    rejectPartner(vikram, 'The licence photo is not readable')
    renderApp('/delivery')
    expect(await screen.findByRole('heading', { name: 'Your KYC was not approved' })).toBeInTheDocument()
    expect(screen.getByText('“The licence photo is not readable”')).toBeInTheDocument()
  })
})

describe('Delivery panel: today', () => {
  it('goes online and offline', async () => {
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery')
    expect(await screen.findByRole('heading', { name: 'You are offline' }, { timeout: 10_000 })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Go online' }))
    expect(await screen.findByText('You are online: new deliveries come to you')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Waiting for a delivery' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Online' })).toBeChecked()

    await user.click(screen.getByRole('switch', { name: 'Online' }))
    expect(await screen.findByText('You are offline: no new deliveries')).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Offline' })).not.toBeChecked()
  })

  it('takes a waiting delivery when going online, and delivers it step by step', async () => {
    orderReadyForPickup()
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery')
    await user.click(await screen.findByRole('button', { name: 'Go online' }, { timeout: 10_000 }))

    // The waiting job of Fresh Mart's zone comes at once
    expect(await screen.findByRole('heading', { name: 'A new delivery for you' })).toBeInTheDocument()
    expect(screen.getByText('Collect in cash').nextElementSibling).toHaveTextContent('₹196')
    expect(screen.getByRole('link', { name: /Call Anita/ })).toHaveAttribute('href', 'tel:9000000009')
    expect(screen.getByRole('link', { name: 'Directions (Google Maps)' }).getAttribute('href')).toContain('google.com/maps/dir')
    expect(adminGetOrder(903).deliveryPartnerName).toBe('Arjun Singh')

    // A new job: the step bar shows the way ahead, nothing reached yet
    expect(stepNow('Delivery progress')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Accept' }))
    expect(await screen.findByRole('heading', { name: 'Go to the store' })).toBeInTheDocument()
    expect(stepNow('Delivery progress')).toBe('Accepted')
    await user.click(screen.getByRole('button', { name: 'I picked up the order' }))
    expect(await screen.findByRole('heading', { name: 'Take it to the customer' })).toBeInTheDocument()
    expect(adminGetOrder(903).status).toBe('OUT_FOR_DELIVERY')
    await user.click(screen.getByRole('button', { name: 'I am on my way' }))

    // A photo at the door, then "Delivered" asks whether the cash was collected
    expect(await screen.findByRole('heading', { name: 'Bring it to the customer' })).toBeInTheDocument()
    expect(stepNow('Delivery progress')).toBe('On the way')
    await user.upload(screen.getByLabelText('Add a photo'), new File(['photo'], 'door.png', { type: 'image/png' }))
    expect(await screen.findByRole('img', { name: 'Proof of delivery of order #903' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delivered' }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Collected ₹196?' })).getByRole('button', { name: 'Yes, delivered' }))
    expect(await screen.findByText('Delivered. Well done!')).toBeInTheDocument()
    expect(adminGetOrder(903).status).toBe('DELIVERED')
    expect(adminGetOrder(903).paymentStatus).toBe('PAID')

    // The job is finished: today shows the earnings and waits for the next one
    expect(await screen.findByRole('heading', { name: 'Waiting for a delivery' })).toBeInTheDocument()
    expect(screen.getByText('Earned today').nextElementSibling).toHaveTextContent('₹40')
  })

  it('rejects a new delivery, which goes back to waiting', async () => {
    goOnline(5)
    orderReadyForPickup()
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery')
    await user.click(await screen.findByRole('button', { name: 'Reject' }, { timeout: 10_000 }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Reject this delivery?' })).getByRole('button', { name: 'Reject delivery' }))
    expect(await screen.findByText('Rejected: it goes to another partner')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Waiting for a delivery' })).toBeInTheDocument()
    expect(searchDeliveries('WAITING_FOR_PARTNER', null).map((delivery) => delivery.orderId)).toContain(903)
  })

  it('reports a problem on the way, and the items go back to the store', async () => {
    goOnline(5)
    orderReadyForPickup()
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery')
    await user.click(await screen.findByRole('button', { name: 'Accept' }, { timeout: 10_000 }))
    await user.click(await screen.findByRole('button', { name: 'I picked up the order' }))

    await user.click(await screen.findByRole('button', { name: 'Report a problem' }))
    const dialog = screen.getByRole('alertdialog', { name: 'What went wrong?' })
    await user.click(within(dialog).getByRole('button', { name: 'Report problem' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Choose what went wrong.')
    await user.click(within(dialog).getByRole('radio', { name: 'The customer is not there' }))
    await user.type(within(dialog).getByLabelText('Note (optional)'), 'Called twice, no answer')
    await user.click(within(dialog).getByRole('button', { name: 'Report problem' }))

    expect(await screen.findByRole('heading', { name: 'Take the items back to the store' })).toBeInTheDocument()
    expect(adminGetOrder(903).status).toBe('DELIVERY_FAILED')
    expect(adminGetOrder(903).statusReason).toBe('Delivery failed: customer unavailable (Called twice, no answer)')
    await user.click(screen.getByRole('button', { name: 'Items returned to the store' }))
    expect(await screen.findByText('The items are back at the store')).toBeInTheDocument()
  })
})

describe('Delivery panel: history, earnings, profile', () => {
  it('lists the finished deliveries and opens one', async () => {
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery/history')
    const list = await screen.findByRole('list', { name: 'Finished deliveries' }, { timeout: 10_000 })
    const card = within(list).getByRole('link', { name: /Order #901/ })
    expect(card).toHaveTextContent('₹40')
    expect(card).toHaveTextContent(/Delivered at/)
    // The timeline: a heading per day, with the day's totals once the earnings are there
    const dayHeading = within(list).getAllByRole('heading', { level: 2 })[0]
    await waitFor(() => expect(dayHeading.parentElement).toHaveTextContent(/\d+ delivered · ₹\d+ earned/))
    // The numbers above the list
    expect(within(screen.getByRole('region', { name: 'Your deliveries in numbers' })).getByText('Finished deliveries')).toBeInTheDocument()
    await user.click(card)
    expect(await screen.findByRole('heading', { name: 'Delivered' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delivered' })).not.toBeInTheDocument()
  })

  it('shows the earnings of the period, every day of it', async () => {
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery/earnings')
    expect(await screen.findByRole('heading', { name: 'Earned per day' }, { timeout: 10_000 })).toBeInTheDocument()
    const tile = (label: string) => screen.getByText(label, { selector: 'p' }).nextElementSibling
    expect(tile('Earned')).toHaveTextContent('₹40')
    expect(tile('Deliveries')).toHaveTextContent('1')
    expect(within(screen.getByRole('list', { name: 'Earned per day' })).getAllByRole('listitem')).toHaveLength(14)
    await user.click(screen.getByRole('link', { name: '7 days' }))
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Earned per day' })).getAllByRole('listitem')).toHaveLength(7))
  })

  it("shows the partner's details", async () => {
    logInAs('delivery1@grocery.com')
    renderApp('/delivery/profile')
    expect(await screen.findByRole('heading', { level: 1, name: 'Arjun Singh' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('Scooter KA01AB1234')).toBeInTheDocument()
    expect(screen.getByText('Indiranagar', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('No ratings yet')).toBeInTheDocument()
    const support = screen.getByRole('region', { name: 'Support' })
    expect(await within(support).findByRole('link', { name: /support@/ })).toHaveAttribute('href', expect.stringMatching(/^mailto:/))
  })

  it('offers the pages in a bar at the bottom on phones, with the current one marked', async () => {
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery')
    const bar = await screen.findByRole('navigation', { name: 'Delivery pages' }, { timeout: 10_000 })
    expect(within(bar).getAllByRole('link').map((link) => link.textContent)).toEqual(['Today', 'History', 'Earnings', 'Profile'])
    expect(within(bar).getByRole('link', { name: 'Today' })).toHaveAttribute('aria-current', 'page')
    await user.click(within(bar).getByRole('link', { name: 'History' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'History' })).toBeInTheDocument()
    expect(within(bar).getByRole('link', { name: 'History' })).toHaveAttribute('aria-current', 'page')
  })
})
