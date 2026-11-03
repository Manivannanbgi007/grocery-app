import { cn } from '../../utils/cn'

/**
 * A grey block that stands in for content while it loads, with a light that sweeps across it (motion-safe; a still
 * block with "reduce motion"). Size it with classes: <Skeleton className="h-4 w-24" />.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'relative block overflow-hidden rounded-md bg-line/70',
        'before:absolute before:inset-0 before:-translate-x-full before:bg-linear-to-r before:from-transparent before:via-white/40 before:to-transparent motion-safe:before:animate-shimmer',
        className,
      )}
      aria-hidden="true"
    />
  )
}
