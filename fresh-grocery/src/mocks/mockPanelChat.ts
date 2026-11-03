import type { ChatReply } from '../api/types'
import { daysBefore, indiaDay } from '../utils/dates'
import { formatPrice } from '../utils/format'
import type { DemoUser } from './data/users'
import { listStoreProducts } from './mockCatalog'
import { availabilityOf, earningsOf, myDeliveries, partnerById, searchDeliveries, searchPartners } from './mockDelivery'
import { listStoreOrders } from './mockOrders'
import { dailySales, dashboard, topProducts, topStores } from './mockReports'
import { searchStores, storeById } from './mockStores'

/*
 * The panel part of the mock assistant: a few rule-based answers from the mock data for vendors, delivery partners and
 * admins (the real assistant asks the language model, which calls the getVendor..., getPartner... and getAdmin... tools).
 * null = not a panel question: the shop's answers (products, offers, delivery rules) are tried next.
 */

type Answer = Omit<ChatReply, 'conversationId'>

const fromTools = (answer: string, toolsUsed: string[]): Answer => ({ answer, grounded: true, toolsUsed })

const lastSevenDays = () => ({ from: daysBefore(indiaDay(), 6), to: indiaDay() })

export function panelAnswer(text: string, user: DemoUser): Answer | null {
  if (user.role === 'VENDOR') return vendorAnswer(text, user)
  if (user.role === 'DELIVERY_PARTNER') return partnerAnswer(text, user)
  if (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN') return adminAnswer(text)
  return null
}

function vendorAnswer(text: string, user: DemoUser): Answer | null {
  const asksAboutStore = /\border|stock|running out|sell|sales|sold|best/.test(text)
  const store = user.storeId === null ? undefined : storeById(user.storeId)
  if (!store) {
    return asksAboutStore
      ? fromTools('You have no store yet. Register one in the vendor panel; an admin then approves it.', ['getVendorStoreToday'])
      : null
  }
  if (/stock|running out|restock|sold out/.test(text)) {
    const low = listStoreProducts(store.id, { q: null, status: null, categoryId: null, lowStockOnly: true })
    if (low.length === 0) return fromTools('No product is low on stock.', ['getVendorLowStock'])
    const lines = low.map((product) => `${product.name}: ${product.inStock ? `${product.availableQuantity} left` : 'sold out'}`)
    return fromTools(`${low.length} products are low on stock or sold out: ${lines.join('; ')}.`, ['getVendorLowStock'])
  }
  if (/\border/.test(text)) {
    const count = (status: 'CONFIRMED' | 'ACCEPTED' | 'PACKED' | 'READY_FOR_PICKUP') => listStoreOrders(store.id, status).length
    return fromTools(
      `${store.name} has ${count('CONFIRMED')} new orders waiting for you to accept, ${count('ACCEPTED')} accepted, ${count('PACKED')} packed and ${count('READY_FOR_PICKUP')} ready for pickup.`,
      ['getVendorStoreToday'],
    )
  }
  if (/sell|sales|sold|best/.test(text)) {
    const { from, to } = lastSevenDays()
    const sales = dailySales(store.id, from, to)
    const best = topProducts(store.id, from, to, 1)[0]
    return fromTools(
      `In the last 7 days ${store.name} sold for ${formatPrice(sales.totalSales)} in ${sales.totalOrders} orders.${best ? ` Your best seller is ${best.productName} (${best.quantity} sold).` : ''}`,
      ['getVendorSales'],
    )
  }
  return null
}

function partnerAnswer(text: string, user: DemoUser): Answer | null {
  const partner = partnerById(user.id)
  if (!partner) return null
  if (/online|offline|kyc|no deliveries/.test(text)) {
    const online = availabilityOf(user.id).online
    const kyc = partner.kycStatus === 'APPROVED' ? 'Your KYC is approved.' : `Your KYC is ${partner.kycStatus.toLowerCase().replaceAll('_', ' ')}: deliveries start once it is approved.`
    return fromTools(`You are ${online ? 'online and get new deliveries' : 'offline: go online in the delivery panel to get deliveries'}. ${kyc}`, ['getPartnerStatus'])
  }
  if (/earn|money|paid/.test(text)) {
    const { from, to } = lastSevenDays()
    const earnings = earningsOf(user.id, from, to)
    return fromTools(`In the last 7 days you made ${earnings.deliveries} deliveries and earned ${formatPrice(earnings.total)}.`, ['getPartnerEarnings'])
  }
  if (/deliver/.test(text)) {
    const current = myDeliveries(user.id, true)
    if (current.length === 0) return fromTools('You have no current deliveries.', ['getPartnerDeliveries'])
    const list = current.map((delivery) => `order ${delivery.orderId} from ${delivery.storeName} to ${delivery.customerName}`)
    return fromTools(`You have ${current.length} current ${current.length === 1 ? 'delivery' : 'deliveries'}: ${list.join('; ')}.`, ['getPartnerDeliveries'])
  }
  return null
}

function adminAnswer(text: string): Answer | null {
  if (/attention|waiting|approve|kyc|review/.test(text)) {
    const stores = searchStores({ status: 'PENDING_APPROVAL', city: null, keyword: null }).length
    const kyc = searchPartners('PENDING').length
    const deliveries = searchDeliveries('WAITING_FOR_PARTNER', null).length
    return fromTools(`Waiting for the team: ${stores} stores to approve, ${kyc} KYC to review and ${deliveries} deliveries without a partner.`, ['getAdminOverview'])
  }
  if (/today|orders/.test(text)) {
    const numbers = dashboard()
    return fromTools(`Today there are ${numbers.ordersToday} orders with sales of ${formatPrice(numbers.salesToday)}; ${numbers.totalOrders} orders in total.`, ['getAdminOverview'])
  }
  if (/sell|sales|sold|store/.test(text)) {
    const { from, to } = lastSevenDays()
    const best = topStores(from, to, 1)[0]
    const sales = dailySales(null, from, to)
    return fromTools(
      `In the last 7 days the platform sold for ${formatPrice(sales.totalSales)} in ${sales.totalOrders} orders.${best ? ` The top store is ${best.storeName} (${formatPrice(best.sales)}).` : ''}`,
      ['getAdminSales'],
    )
  }
  return null
}
