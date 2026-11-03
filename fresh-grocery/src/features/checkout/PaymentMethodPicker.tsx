import { Banknote, CircleCheck, CreditCard, type LucideIcon } from 'lucide-react'
import type { PaymentMethod } from '../../api/types'
import { TONES, type Tone } from '../../components/ui/tones'
import { cn } from '../../utils/cn'
import { StepNumber } from './StepNumber'

interface PaymentMethodPickerProps {
  value: PaymentMethod
  onChange: (method: PaymentMethod) => void
}

/** The two ways to pay; `tone` is only the colour of the icon circle (green = cash, violet = online payment) */
const METHODS: { method: PaymentMethod; title: string; text: string; icon: LucideIcon; tone: Tone }[] = [
  { method: 'COD', title: 'Cash on delivery', text: 'Pay in cash when your groceries arrive.', icon: Banknote, tone: 'green' },
  {
    method: 'ONLINE',
    title: 'Pay online',
    // The local backend uses its test payment gateway; real payments (Razorpay) come later
    text: 'Pay right after placing the order, within 15 minutes. This shop uses a test payment gateway: no real money is taken.',
    icon: CreditCard,
    tone: 'violet',
  },
]

/**
 * "Payment method": cash on delivery or online. Each way is a tile with its icon in a coloured circle; the chosen one
 * is violet (the colour of payment) with a tick, the others lift a little under the mouse.
 */
export function PaymentMethodPicker({ value, onChange }: PaymentMethodPickerProps) {
  return (
    <fieldset>
      <legend className="mb-4 flex items-center gap-3 font-heading text-xl font-bold text-heading">
        <StepNumber number={2} tone="violet" /> Payment method
      </legend>
      <div className="grid gap-3 md:grid-cols-2">
        {METHODS.map(({ method, title, text, icon: Icon, tone }) => {
          const selected = method === value
          return (
            <label
              key={method}
              className={cn(
                'relative flex cursor-pointer gap-3 rounded-2xl border-2 p-4 transition duration-200 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand',
                selected ? 'border-violet bg-violet-light/70 shadow-md ring-4 ring-violet/15' : 'border-line bg-card hover:-translate-y-0.5 hover:border-violet/50 hover:shadow-md',
              )}
            >
              <input type="radio" name="payment-method" checked={selected} onChange={() => onChange(method)} className="mt-1 size-4 shrink-0 accent-violet" />
              <span className="text-sm">
                <span className="mb-1 flex items-center gap-2 font-heading text-base font-bold text-heading">
                  <span aria-hidden="true" className={cn('grid size-8 place-items-center rounded-full', TONES[tone].soft)}>
                    <Icon className="size-4.5" />
                  </span>
                  {title}
                </span>
                {text}
              </span>
              {/* The chosen way gets a tick in the corner */}
              {selected && <CircleCheck className="absolute top-3 right-3 size-5 text-violet-text motion-safe:animate-pop" aria-hidden="true" />}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
