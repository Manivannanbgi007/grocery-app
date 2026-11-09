import { LogOut, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { TONES, type Tone } from './tones'

interface ToneIconProps {
  icon: LucideIcon
  tone: Tone
  /** a full-colour tile with a white icon (default: a light tile with a coloured icon) */
  solid?: boolean
  className?: string
}

/** A rounded icon tile in one of the colours of tones.ts, e.g. at the start of a card's title. Decoration only. */
export function ToneIcon({ icon: Icon, tone, solid = false, className }: ToneIconProps) {
  return (
    <span aria-hidden="true" className={cn('grid size-9 shrink-0 place-items-center rounded-xl', solid ? cn('shadow-md', TONES[tone].solid) : TONES[tone].soft, className)}>
      <Icon className="size-5" />
    </span>
  )
}

interface MenuIconProps {
  icon: LucideIcon
  tone: Tone
  /** the link is the current page: the tile turns see-through white on the green pill of every menu */
  active?: boolean
  className?: string
}

/**
 * A menu item's icon in a filled tile of its colour (a gradient with a white icon; a dark one on yellow), the same in
 * every menu: the panels' menus, the delivery panel's bar, My account, the phone menu and the profile cards. On the
 * current page's green pill it becomes a see-through white tile. It grows and tilts a little when its link is hovered
 * (the link needs the `group` class; motion-safe). Decoration only: the link's text names it.
 */
export function MenuIcon({ icon: Icon, tone, active = false, className }: MenuIconProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'grid size-8 shrink-0 place-items-center rounded-lg transition duration-200 motion-safe:group-hover:scale-110 motion-safe:group-hover:-rotate-6',
        active ? 'bg-white/20 text-white' : cn('shadow-md', TONES[tone].tile),
        className,
      )}
    >
      <Icon className="size-4.5" strokeWidth={2.25} />
    </span>
  )
}

/**
 * The "Log out" icon of the menus: a light pink tile that fills when its button is hovered (the button needs the
 * `group` class). Log out is no page, so it has no filled tile like the links around it. Decoration only.
 */
export function LogOutIcon({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('grid size-8 shrink-0 place-items-center rounded-lg bg-hot/10 text-hot-text transition duration-200 group-hover:bg-deep-pink group-hover:text-white', className)}
    >
      <LogOut className="size-4.5" strokeWidth={2.25} />
    </span>
  )
}

/** The coloured line along the top of a card made with toneCard() (tones.ts). Decoration only. */
export function ToneBar({ tone }: { tone: Tone }) {
  return <span aria-hidden="true" className={cn('absolute inset-x-0 top-0 h-1', TONES[tone].bar)} />
}
