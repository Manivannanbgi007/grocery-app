import type { Cart } from '../../api/types'
import { formatPrice, formatUnit } from '../../utils/format'

/**
 * A WhatsApp "share" link with the cart as a shopping list (wa.me opens WhatsApp with this text ready to send):
 *
 *   My Fresh Grocery cart (Fresh Mart):
 *   - 2 × Tomato (1 kg): ₹72
 *   Total to pay: ₹231
 */
export function whatsappShareLink(cart: Cart, storeName: string | undefined): string {
  const lines = [
    `My Fresh Grocery cart${storeName ? ` (${storeName})` : ''}:`,
    ...cart.items.map((item) => `- ${item.quantity} × ${item.productName} (${formatUnit(item.unit, item.unitQuantity)}): ${formatPrice(item.lineTotal)}`),
    `Total to pay: ${formatPrice(cart.totals.payableAmount)}`,
  ]
  return `https://wa.me/?text=${encodeURIComponent(lines.join('\n'))}`
}
