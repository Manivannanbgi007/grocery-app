import { TONES, type Tone } from '../../components/ui/tones'
import { cn } from '../../utils/cn'

/**
 * The step's number in a coloured circle, before a checkout step's title ("1" Delivery address). The dot after the
 * number is only for screen readers, so the title still reads "1. Delivery address" (put a space after this circle).
 */
export function StepNumber({ number, tone }: { number: number; tone: Tone }) {
  return (
    <span className={cn('inline-grid size-8 shrink-0 place-items-center rounded-full font-heading text-sm font-bold shadow-md', TONES[tone].solid)}>
      {number}
      <span className="sr-only">.</span>
    </span>
  )
}
