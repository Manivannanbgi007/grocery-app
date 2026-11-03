import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { publicStores, storeOfOwner } from '../../mocks/mockStores'
import { chooseOption } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'

/* Store settings on the mock API: vendor1 (Ravi Kumar, user 3) owns Fresh Mart, open 07:00-22:00 every day. */

async function openSettings() {
  logInAs('vendor1@grocery.com')
  const rendered = renderApp('/vendor/store')
  await screen.findByRole('heading', { level: 1, name: 'Store settings' })
  return rendered
}

const section = (title: string) => screen.getByRole('heading', { name: title }).closest('section')!

describe('Store settings', () => {
  it('changes the store details, which the shop then shows', async () => {
    const { user } = await openSettings()
    const details = section('Store details')
    const name = within(details).getByLabelText('Store name')
    expect(name).toHaveValue('Fresh Mart')
    await user.clear(name)
    await user.type(name, 'Fresh Mart Indiranagar')
    await user.click(within(details).getByRole('button', { name: 'Save details' }))

    expect(await screen.findByText('Store details saved')).toBeInTheDocument()
    expect(publicStores().map((store) => store.name)).toContain('Fresh Mart Indiranagar')
  })

  it('checks and saves the opening hours, with closed days', async () => {
    const { user } = await openSettings()
    const hours = section('Opening hours')
    expect(within(hours).getByLabelText('Monday opens at')).toHaveTextContent('7:00 am')

    await user.click(within(hours).getByRole('checkbox', { name: 'Open on Sunday' }))
    expect(within(hours).getByText('Closed all day')).toBeInTheDocument()
    const closes = within(hours).getByLabelText('Monday closes at')
    await chooseOption(user, closes, '6:00 am')
    await user.click(within(hours).getByRole('button', { name: 'Save opening hours' }))
    expect(within(hours).getByText('The store must open before it closes.')).toBeInTheDocument()

    await chooseOption(user, closes, '8:30 pm')
    await user.click(within(hours).getByRole('button', { name: 'Save opening hours' }))
    expect(await screen.findByText('Opening hours saved')).toBeInTheDocument()
    const saved = storeOfOwner(3).openingHours
    expect(saved[0]).toMatchObject({ dayOfWeek: 'MONDAY', opensAt: '07:00:00', closesAt: '20:30:00', closed: false })
    expect(saved[6]).toMatchObject({ dayOfWeek: 'SUNDAY', closed: true })
  })

  it('adds and removes delivery PIN codes', async () => {
    const { user } = await openSettings()
    const areas = section('Delivery areas')
    const list = within(areas).getByRole('list', { name: 'PIN codes you deliver to' })
    expect(within(list).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['560008', '560038', '560071'])
    expect(within(areas).getByRole('button', { name: 'Save delivery areas' })).toBeDisabled()

    await user.type(within(areas).getByLabelText('PIN code to add'), '560038')
    await user.click(within(areas).getByRole('button', { name: 'Add' }))
    expect(within(areas).getByRole('alert')).toHaveTextContent('560038 is already in the list.')

    await user.clear(within(areas).getByLabelText('PIN code to add'))
    await user.type(within(areas).getByLabelText('PIN code to add'), '560001')
    await user.click(within(areas).getByRole('button', { name: 'Add' }))
    await user.click(within(areas).getByRole('button', { name: 'Remove 560071' }))
    await user.click(within(areas).getByRole('button', { name: 'Save delivery areas' }))

    expect(await screen.findByText('Delivery areas saved')).toBeInTheDocument()
    expect(storeOfOwner(3).deliveryPincodes).toEqual(['560001', '560008', '560038'])
  })
})
