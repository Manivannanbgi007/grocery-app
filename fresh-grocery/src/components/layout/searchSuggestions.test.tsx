import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, onTestFinished } from 'vitest'
import { server } from '../../mocks/server'
import { renderWithProviders } from '../../test/render'
import { Header } from './Header'

/*
 * The search box's suggestions while typing (mock API: every typed word must begin a word of the name, brand or
 * category; the real ranking is Elasticsearch's). Demo catalog: Tomato is product 1 (Vegetables); there are more Amul
 * products than the six the dropdown shows; Dairy & Eggs is category 3.
 */

/** The texts the mock API was asked suggestions for, in order. */
function suggestionRequests(): string[] {
  const texts: string[] = []
  const listener = ({ request }: { request: Request }) => {
    const url = new URL(request.url)
    if (url.pathname.endsWith('/products/suggestions')) texts.push(url.searchParams.get('q') ?? '')
  }
  server.events.on('request:start', listener)
  onTestFinished(() => server.events.removeListener('request:start', listener))
  return texts
}

function searchBox() {
  return screen.getByRole('combobox', { name: 'Search for products' })
}

describe('Search suggestions', () => {
  it('suggests products for a partly typed word, and opens the chosen one', async () => {
    const { user, router } = renderWithProviders(<Header />)
    await user.type(searchBox(), 'toma')

    const list = await screen.findByRole('listbox', { name: 'Search suggestions' })
    const tomato = await within(list).findByRole('option', { name: /^Tomato/ })
    expect(within(tomato).getByText('Toma', { selector: 'strong' })).toBeInTheDocument()
    expect(tomato).toHaveTextContent('Vegetables · 1 kg')
    expect(within(list).getByRole('option', { name: 'Search for “toma”' })).toBeInTheDocument()
    const productCount = within(list).getAllByRole('option').length - 1
    expect(screen.getByText(`${productCount} suggestions`)).toHaveAttribute('aria-live', 'polite')
    expect(searchBox()).toHaveAttribute('aria-expanded', 'true')

    await user.click(tomato)
    expect(router.state.location.pathname).toBe('/products/1')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('asks once the typing pauses, not on every key press, and not for one letter', async () => {
    const asked = suggestionRequests()
    const { user } = renderWithProviders(<Header />)

    await user.type(searchBox(), 'a')
    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(asked).toEqual([])

    await user.type(searchBox(), 'mul')
    expect(await screen.findByRole('option', { name: /^Amul Butter/ })).toBeInTheDocument()
    expect(asked).toEqual(['amul'])
  })

  it('works with the keyboard: the arrows choose, Escape closes, Enter opens the choice', async () => {
    const { user, router } = renderWithProviders(<Header />)
    await user.type(searchBox(), 'amul')
    const list = await screen.findByRole('listbox', { name: 'Search suggestions' })
    await within(list).findByRole('option', { name: /^Amul Butter/ })
    const options = within(list).getAllByRole('option')
    expect(options).toHaveLength(7) // six Amul products (the most it shows) and "Search for"

    await user.keyboard('{ArrowDown}{ArrowDown}')
    expect(options[1]).toHaveAttribute('aria-selected', 'true')
    expect(searchBox()).toHaveAttribute('aria-activedescendant', options[1].id)

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(searchBox()).toHaveValue('amul')

    // ↓ opens the list again; ↑ from nothing chosen goes to the last option, the full search
    await user.keyboard('{ArrowDown}{ArrowUp}')
    expect(screen.getByRole('option', { name: 'Search for “amul”' })).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{Enter}')
    expect(router.state.location.pathname).toBe('/shop')
    expect(router.state.location.search).toBe('?q=amul')
  })

  it('stays in the chosen category, and Enter without a choice searches as before', async () => {
    const { user, router } = renderWithProviders(<Header />)
    await user.click(within(screen.getByRole('search')).getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dairy & Eggs' }))
    await user.type(searchBox(), 'toma')

    expect(await screen.findByText('No products match “toma”')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Search for “toma”' })).toBeInTheDocument()
    await user.keyboard('{Enter}')
    expect(router.state.location.search).toBe('?q=toma&category=3')
  })

  it('offers only the full search when the suggestions cannot be loaded', async () => {
    server.use(http.get('*/api/v1/products/suggestions', () => new HttpResponse(null, { status: 503 })))
    const { user } = renderWithProviders(<Header />)
    await user.type(searchBox(), 'milk')

    const list = await screen.findByRole('listbox', { name: 'Search suggestions' })
    await waitFor(() => expect(screen.queryByText('Looking for products…')).not.toBeInTheDocument())
    expect(within(list).getAllByRole('option')).toHaveLength(1)
    expect(within(list).getByRole('option', { name: 'Search for “milk”' })).toBeInTheDocument()
    expect(screen.queryByText(/No products match/)).not.toBeInTheDocument()
  })
})
