import { useCountdown } from '../../hooks/useCountdown'
import { cn } from '../../utils/cn'

interface CountdownProps {
  /** ISO date-time the countdown runs to, e.g. a coupon's validUntil */
  until: string
  className?: string
}

const pad = (value: number) => String(value).padStart(2, '0')

/** [ 432 Days ] [ 05 Hours ] [ 12 Mins ] [ 33 Secs ], updated every second. */
export function Countdown({ until, className }: CountdownProps) {
  const left = useCountdown(until)

  if (left.ended) {
    return <p className={cn('font-heading font-bold text-hot', className)}>Ended</p>
  }

  const parts = [
    { value: String(left.days), label: 'Days' },
    { value: pad(left.hours), label: 'Hours' },
    { value: pad(left.minutes), label: 'Mins' },
    { value: pad(left.seconds), label: 'Secs' },
  ]

  return (
    // role="timer" + a label: screen readers read "Ends in 432 days, 5 hours" instead of every changing second
    <div role="timer" aria-label={`Ends in ${left.days} days, ${left.hours} hours`} className={cn('flex gap-2', className)}>
      {parts.map((part) => (
        <span key={part.label} aria-hidden="true" className="flex min-w-14 flex-col items-center rounded-md bg-card px-2 py-1.5 shadow-sm">
          <span className="font-heading text-lg leading-tight font-bold text-brand-text">{part.value}</span>
          <span className="text-xs">{part.label}</span>
        </span>
      ))}
    </div>
  )
}
