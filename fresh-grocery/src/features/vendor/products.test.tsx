import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { catalogProduct } from '../../mocks/mockCatalog'
import { chooseOption } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'

/* The vendor's products on the mock API: vendor1 (Ravi Kumar) sells Fresh Mart's products (store 1). */

// jsdom's FormData cannot travel through Node's fetch to the mock API (it does in a browser), so in these tests the
// photo upload calls the mock catalog directly. The real upload is checked by the Playwright flow (e2e/vendor.spec.ts).
vi.mock('../../api/vendorProducts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/vendorProducts')>()
  const { addImage } = await import('../../mocks/mockCatalog')
  return {
    ...original,
    uploadProductImage: async (productId: number, image: File) => addImage(1, productId, { type: image.type, size: image.size, url: `data:${image.type};base64,AAAA` }),
  }
})

const rows = () => within(screen.getByRole('table', { name: /Your products/ })).getAllByRole('row').slice(1)
const names = () => rows().map((row) => within(row).getAllByRole('link')[0].textContent)

async function openProducts(path = '/vendor/products') {
  logInAs('vendor1@grocery.com')
  const rendered = renderApp(path)
  await screen.findByRole('table', { name: /Your products/ })
  return rendered
}

describe('Vendor products list', () => {
  it("lists only the store's products, by name, with stock and status", async () => {
    await openProducts()
    expect(names().slice(0, 3)).toEqual(['Aashirvaad Whole Wheat Atta', 'Amul Butter', 'Amul Cheese Slices'])
    expect(names()).not.toContain('Organic Spinach') // Green Basket's
    const butter = rows().find((row) => row.textContent?.includes('Amul Butter'))!
    expect(butter).toHaveTextContent('3Low')
    expect(butter).toHaveTextContent('Active')
  })

  it('searches and filters through the URL', async () => {
    const { user, router } = await openProducts()
    await user.type(screen.getByRole('searchbox', { name: 'Search your products' }), 'amul')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(names().every((name) => name?.startsWith('Amul'))).toBe(true))
    expect(router.state.location.search).toBe('?q=amul')

    await user.click(screen.getByRole('checkbox', { name: 'Low stock only' }))
    await waitFor(() => expect(names()).toEqual(['Amul Butter']))
    expect(router.state.location.search).toBe('?q=amul&lowStock=true')
  })

  it('hides a product from customers right in the list', async () => {
    const { user } = await openProducts()
    await user.click(screen.getByRole('button', { name: 'Hide Amul Butter' }))
    expect(await screen.findByText('Amul Butter is hidden from customers')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Show Amul Butter' })).toBeInTheDocument()
    expect(catalogProduct(7)?.status).toBe('INACTIVE')
  })
})

describe('Adding and changing a product', () => {
  it('adds a product, then its stock', async () => {
    logInAs('vendor1@grocery.com')
    const { user, router } = renderApp('/vendor/products/new')

    await user.click(await screen.findByRole('button', { name: 'Save product' }))
    expect(screen.getByText('Please enter the product name.')).toBeInTheDocument()
    expect(screen.getByText('Please choose a category.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Product name'), 'Cherry Tomato')
    await chooseOption(user, screen.getByLabelText('Category'), 'Vegetables')
    await user.type(screen.getByLabelText('Product code (SKU)'), 'FM-VEG-020')
    await user.clear(screen.getByLabelText('Pack size'))
    await user.type(screen.getByLabelText('Pack size'), '250')
    await chooseOption(user, screen.getByLabelText('Unit'), 'Gram (g)')
    await user.type(screen.getByLabelText('Price (MRP)'), '45')
    await user.clear(screen.getByLabelText('Discount'))
    await user.type(screen.getByLabelText('Discount'), '10')
    expect(screen.getByRole('status')).toHaveTextContent('Customers pay ₹40.50 instead of ₹45.')
    await user.click(screen.getByRole('button', { name: 'Save product' }))

    expect(await screen.findByText('Cherry Tomato saved. Now set its stock and add a photo.')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 1, name: 'Cherry Tomato' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/vendor/products/200')
    expect(await screen.findByText(/No stock yet/)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Units in the store'), '25')
    await user.click(screen.getByRole('button', { name: 'Save stock' }))
    expect(await screen.findByText('Stock saved')).toBeInTheDocument()
    const stock = screen.getByRole('heading', { name: 'Stock' }).parentElement!
    expect(within(stock).getByText('Customers can buy').nextSibling).toHaveTextContent('25')

    await user.type(within(stock).getByLabelText('Units received'), '5')
    await user.click(within(stock).getByRole('button', { name: 'Add to stock' }))
    expect(await screen.findByText('Added 5: 30 units in the store now')).toBeInTheDocument()
  })

  it('says when the product code is already used in the store', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/products/new')
    await user.type(await screen.findByLabelText('Product name'), 'Another Tomato')
    await chooseOption(user, screen.getByLabelText('Category'), 'Vegetables')
    await user.type(screen.getByLabelText('Product code (SKU)'), 'FM-VEG-001')
    await user.type(screen.getByLabelText('Price (MRP)'), '30')
    await user.click(screen.getByRole('button', { name: 'Save product' }))
    expect(await screen.findByText('Your store already has a product with SKU FM-VEG-001')).toBeInTheDocument()
  })

  it('changes the price, hides the product and shows it again', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/products/1')
    const price = await screen.findByLabelText('Price (MRP)')
    expect(price).toHaveValue('40')
    await user.clear(price)
    await user.type(price, '42')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('Tomato saved')).toBeInTheDocument()
    expect(catalogProduct(1)?.price).toBe(42)

    await user.click(screen.getByRole('button', { name: 'Hide from customers' }))
    expect(await screen.findByText('Hidden: customers do not see this product.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Show to customers' }))
    expect(await screen.findByText('Customers can see and buy this product.')).toBeInTheDocument()
  })

  it('adds a photo and removes it, and refuses files that are not images', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/products/1')
    const photos = (await screen.findByRole('heading', { name: 'Photos' })).parentElement!
    const input = within(photos).getByLabelText('Add a photo')

    await user.upload(input, new File(['fake png bytes'], 'tomato.png', { type: 'image/png' }))
    expect(await screen.findByText('Photo added')).toBeInTheDocument()
    expect(within(photos).getByText('1 of 5', { exact: false })).toBeInTheDocument()
    expect(catalogProduct(1)?.imageUrls).toHaveLength(1)

    await user.click(within(photos).getByRole('button', { name: 'Remove photo 1' }))
    expect(await screen.findByText('Photo removed')).toBeInTheDocument()
    expect(catalogProduct(1)?.imageUrls).toHaveLength(0)

    // The file picker only offers images (accept=...); a file that gets through anyway is refused before uploading
    fireEvent.change(input, { target: { files: [new File(['%PDF'], 'list.pdf', { type: 'application/pdf' })] } })
    expect(within(photos).getByRole('alert')).toHaveTextContent('Choose a JPEG, PNG or WEBP image.')
  })

  it('deletes a product after asking', async () => {
    logInAs('vendor1@grocery.com')
    const { user, router } = renderApp('/vendor/products/2')
    await user.click(await screen.findByRole('button', { name: 'Delete product' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Delete Onion?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText('Onion deleted')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe('/vendor/products'))
    expect(catalogProduct(2)).toBeUndefined()
  })

  it("does not open another store's product", async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/vendor/products/11') // Organic Spinach, Green Basket
    expect(await screen.findByRole('heading', { name: 'We could not find this product' }, { timeout: 10_000 })).toBeInTheDocument()
  })
})
