import { twMerge } from 'tailwind-merge'

/**
 * Joins CSS class names, skips the empty ones, and lets a later class win over an earlier one of the same kind.
 *
 *   cn('rounded px-4', isActive && 'bg-brand text-white')  ->  "rounded px-4 bg-brand text-white"
 *   cn('rounded-lg px-5', 'rounded-full')                  ->  "px-5 rounded-full"
 *
 * Why the second part matters: with plain Tailwind, when two classes set the same thing ("rounded-lg" and
 * "rounded-full") the one that comes later in Tailwind's CSS file wins, not the one you wrote last.
 * tailwind-merge removes the earlier one, so a `className` passed to a component always overrides its defaults.
 */
export function cn(...classNames: Array<string | false | null | undefined>): string {
  return twMerge(...classNames)
}
