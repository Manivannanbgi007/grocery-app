import { cn } from '../../utils/cn'
import { initialsOf } from '../../utils/format'

interface AvatarProps {
  name: string
  /** sm: the top bar; lg: the profile card */
  size?: 'sm' | 'lg'
  className?: string
}

/** A round avatar with the person's initials; decoration only (the name is always written next to it). */
export function Avatar({ name, size = 'sm', className }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'grid shrink-0 place-items-center rounded-full font-heading font-bold',
        size === 'sm' ? 'size-9 bg-brand-light text-sm text-brand-text' : 'size-14 bg-linear-to-br from-brand to-brand-dark text-lg text-white shadow-md shadow-brand/30',
        className,
      )}
    >
      {initialsOf(name)}
    </span>
  )
}
