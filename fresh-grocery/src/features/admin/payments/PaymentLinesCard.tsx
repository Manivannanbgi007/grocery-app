import { Banknote, CreditCard, ListChecks } from 'lucide-react'
import type { PaymentMethod, PaymentTotals } from '../../../api/types'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { cn } from '../../../utils/cn'
import { formatPrice } from '../../../utils/format'
import { PAYMENT_METHOD_LABELS } from '../../orders/orderStatus'
import { PAYMENT_STATUS_ICONS, PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TONES } from './paymentStatus'

/** Online first, then cash: the order of the groups. */
const METHODS: PaymentMethod[] = ['ONLINE', 'COD']

/** The method's icon and colours: a card (online, blue) or a note (cash, orange). */
const METHOD_LOOKS: Record<PaymentMethod, { icon: typeof CreditCard; chip: string }> = {
  ONLINE: { icon: CreditCard, chip: 'bg-sale-light text-sale-text' },
  COD: { icon: Banknote, chip: 'bg-new-light text-warn-text' },
}

const sum = (lines: PaymentTotals[], pick: (line: PaymentTotals) => number) => lines.reduce((total, line) => total + pick(line), 0)
const paymentsWord = (count: number) => `${count} ${count === 1 ? 'payment' : 'payments'}`

/**
 * "Every method and status": the numbers of the ring beside it, line by line, as a table grouped by method (online,
 * then cash). Each group starts with the method and its subtotal; each line has the status (colour, icon and word), the
 * number of payments and the amount, with a thin bar for the amount next to the biggest line's (the amount is written
 * out too, so the bar needs no legend). The last row is the total.
 */
export function PaymentLinesCard({ totals }: { totals: PaymentTotals[] }) {
  const biggest = Math.max(0, ...totals.map((line) => line.amount))
  const groups = METHODS.map((method) => ({
    method,
    // The biggest amount first within a method
    lines: totals.filter((line) => line.method === method).sort((a, b) => b.amount - a.amount || b.count - a.count),
  })).filter((group) => group.lines.length > 0)

  return (
    // min-w-0: in the grid the card may get narrower than its table, which then scrolls inside it (never the page)
    <section aria-labelledby="payment-lines-title" className="min-w-0 rounded-2xl border border-line bg-card p-4 sm:p-6">
      <div className="mb-4 flex items-center gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-linear-to-br from-sale to-sale-text text-white shadow-lg shadow-sale/30">
          <ListChecks className="size-5" aria-hidden="true" />
        </span>
        <div>
          <h3 id="payment-lines-title" className="text-lg">
            Every method and status
          </h3>
          <p className="text-xs">The same payments as the ring, line by line</p>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[18rem] text-left text-sm tabular-nums">
          <caption className="sr-only">Payments by method and status</caption>
          <thead className="font-heading text-xs tracking-wide text-body uppercase">
            <tr>
              <th scope="col" className="pb-2">
                Status
              </th>
              <th scope="col" className="pb-2 text-right">
                Payments
              </th>
              <th scope="col" className="w-2/5 pb-2 text-right">
                Amount
              </th>
            </tr>
          </thead>
          {groups.map(({ method, lines }) => {
            const { icon: Icon, chip } = METHOD_LOOKS[method]
            return (
              // One body per method: its header row with the subtotal, then its lines
              <tbody key={method} className="border-t border-line">
                <tr>
                  <th scope="rowgroup" className="pt-3 pb-1.5">
                    <span className="flex items-center gap-2 font-heading font-bold text-heading">
                      <span className={cn('grid size-7 place-items-center rounded-lg', chip)}>
                        <Icon className="size-4" aria-hidden="true" />
                      </span>
                      {PAYMENT_METHOD_LABELS[method]}
                    </span>
                  </th>
                  <td className="pt-3 pb-1.5 text-right text-xs">{paymentsWord(sum(lines, (line) => line.count))}</td>
                  <td className="pt-3 pb-1.5 text-right font-heading font-bold text-heading">{formatPrice(sum(lines, (line) => line.amount))}</td>
                </tr>
                {lines.map((line) => (
                  <tr key={line.status}>
                    <td className="py-2 pl-9">
                      <StatusBadge tone={PAYMENT_STATUS_TONES[line.status]} icon={PAYMENT_STATUS_ICONS[line.status]}>
                        {PAYMENT_STATUS_LABELS[line.status]}
                      </StatusBadge>
                    </td>
                    <td className="py-2 text-right text-heading">{line.count}</td>
                    <td className="py-2 text-right">
                      <span className="font-bold text-heading">{formatPrice(line.amount)}</span>
                      {/* The amount next to the biggest line's (decoration: the amount is written beside it) */}
                      <span aria-hidden="true" className="mt-1 ml-auto block h-1.5 w-full max-w-36 rounded-full bg-brand-light">
                        <span
                          className="block h-full origin-left rounded-full bg-brand-dark motion-safe:animate-grow-x"
                          style={{ width: `${biggest > 0 ? (line.amount / biggest) * 100 : 0}%` }}
                        />
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            )
          })}
          <tfoot className="border-t-2 border-line">
            <tr>
              <th scope="row" className="pt-3 font-heading font-bold text-heading">
                All methods
              </th>
              <td className="pt-3 text-right font-bold text-heading">{sum(totals, (line) => line.count)}</td>
              <td className="pt-3 text-right font-heading text-base font-bold text-heading">{formatPrice(sum(totals, (line) => line.amount))}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  )
}
