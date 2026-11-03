import { cn } from '../../utils/cn'

export type ButtonVariant = 'primary' | 'light' | 'outline' | 'accent' | 'ghost' | 'danger' | 'danger-outline'
export type ButtonSize = 'sm' | 'md' | 'lg'

/** Full class names per variant (Tailwind only finds class names that are written out completely). */
const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  // A soft green shadow that grows on hover: the main action stands out from the page
  primary: 'bg-brand text-white shadow-md shadow-brand/25 hover:bg-brand-dark hover:shadow-lg hover:shadow-brand/30',
  light: 'bg-brand-light text-brand-text hover:bg-brand hover:text-white',
  outline: 'border border-brand-soft bg-card text-brand-text hover:border-brand hover:bg-brand-light',
  accent: 'bg-accent text-ink hover:brightness-95',
  ghost: 'text-heading hover:bg-brand-light hover:text-brand-text',
  /*
   * Destructive actions, in one red (the danger colours of styles/index.css), with a red focus ring:
   * - danger: solid, for the step that really does it ("Delete" in "Delete this address?", "Confirm removal");
   * - danger-outline: for the button that starts it ("Delete", "Remove", "Empty cart", "Cancel order", "Reject").
   * Not for a "Cancel" that only closes a dialog or a form, and not for clearing filters: those are not destructive.
   * shadow-none: a link with these colours stays flat like a button, not a card ("Cards" in index.css)
   */
  danger:
    'bg-danger text-white shadow-md shadow-danger/25 hover:bg-danger-hover hover:shadow-lg hover:shadow-danger/30 active:bg-danger-hover focus-visible:outline-danger',
  'danger-outline':
    'border border-danger/60 bg-card text-danger-text shadow-none hover:border-danger hover:bg-danger-light active:bg-danger-light focus-visible:outline-danger',
}

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'h-8 gap-1.5 rounded-lg px-3 text-sm',
  md: 'h-10 gap-2 rounded-lg px-5 text-sm',
  lg: 'h-12 gap-2 rounded-xl px-6 text-base',
}

/** The classes of a button, shared by <Button> and <ButtonLink> so links can look like buttons. */
export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', className?: string): string {
  return cn(
    // active:scale: a click presses the button in a little (not a disabled one)
    'inline-flex shrink-0 items-center justify-center font-heading font-bold whitespace-nowrap transition duration-200 active:scale-[0.97]',
    'disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none disabled:active:scale-100',
    VARIANT_CLASSES[variant],
    SIZE_CLASSES[size],
    className,
  )
}

/**
 * A small round icon button that deletes or removes something (a trash can or an X next to a product, a photo, a pin
 * code): red icon, a soft red circle on hover, a red focus ring. The size and the padding come with `className`
 * (e.g. 'size-9' or 'p-1').
 */
export function dangerIconButtonClasses(className?: string): string {
  return cn(
    'inline-flex shrink-0 items-center justify-center rounded-full text-danger-text transition duration-200',
    'hover:bg-danger-light active:scale-90 focus-visible:outline-danger disabled:cursor-not-allowed disabled:opacity-50',
    className,
  )
}
