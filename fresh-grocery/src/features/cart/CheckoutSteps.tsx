import { Check } from 'lucide-react'
import { cn } from '../../utils/cn'

/** The three steps of buying */
const STEPS = ['Cart', 'Checkout', 'Order placed']

/**
 * Cart · Checkout · Order placed, so the customer sees where they are (the cart page and the checkout show it in their
 * top card). A circle each: the steps done are green with a tick, the current one is green with its number and named
 * in bold, the next ones wait in grey. On a phone only the circles show.
 *
 * @param current 0 on the cart page, 1 on the checkout
 */
export function CheckoutSteps({ current }: { current: number }) {
  return (
    <ol aria-label="Steps to order" className="flex items-center gap-2 text-sm">
      {STEPS.map((step, index) => {
        const done = index < current
        const isCurrent = index === current
        return (
          <li key={step} aria-current={isCurrent ? 'step' : undefined} className="flex items-center gap-2">
            {index > 0 && <span aria-hidden="true" className={cn('h-0.5 w-5 rounded-full sm:w-8', index <= current ? 'bg-brand' : 'bg-line')} />}
            <span
              aria-hidden="true"
              className={cn(
                'grid size-7 place-items-center rounded-full text-xs font-bold',
                isCurrent ? 'bg-brand text-white shadow-md ring-4 shadow-brand/30 ring-brand/20' : done ? 'bg-brand text-white' : 'border-2 border-line bg-card text-body',
              )}
            >
              {done ? <Check className="size-3.5" /> : index + 1}
            </span>
            <span className={cn('hidden sm:inline', isCurrent ? 'font-heading font-bold text-heading' : done ? 'text-heading' : 'text-body')}>{step}</span>
          </li>
        )
      })}
    </ol>
  )
}
