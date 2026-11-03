import { ArrowLeft } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { Link, ScrollRestoration } from 'react-router'
import { cn } from '../../utils/cn'
import { Logo } from '../layout/Logo'
import { PageTitle } from '../layout/PageTitle'
import { AUTH_LOOKS, type AuthLook } from './authLooks'

const THIS_YEAR = new Date().getFullYear()

interface AuthScreenProps {
  /** the colours, illustration and points of the side panel: 'shop' for customers, or a panel's */
  look: AuthLook
  /** the browser tab's title */
  title: string
  /** the panel's name next to the logo, e.g. "Vendor panel" (left out in the shop) */
  panelName?: string
  /** false on pages that show their own illustration above the form (the password pages) */
  phoneBanner?: boolean
  children: ReactNode
}

/**
 * The full-screen frame of every login and sign-up page (and of "this panel is for ..."), without the shop's header and
 * footer. Big screens: an illustrated side panel on the left, the form in a card on the right. Phones: a small
 * illustration above the form. The form comes first in the page, so screen readers and the keyboard reach it before the
 * side panel.
 *
 * The movement (all written as motion-safe:animate-..., so "reduce motion" turns it off; the animations are in
 * styles/index.css): the coloured circles drift (animate-blob), the illustration floats (animate-float), and the form
 * card, the headline and the points rise in one after the other (animate-rise, each a little later via animationDelay).
 */
export function AuthScreen({ look, title, panelName, phoneBanner = true, children }: AuthScreenProps) {
  const { Illustration, headline, text, points, dark, classes } = AUTH_LOOKS[look]
  const headlineId = useId()

  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <PageTitle title={title} />

      {/* ---------- The form side: the right on big screens, the whole screen on phones ---------- */}
      {/* isolate + overflow-hidden: the circles below stay behind the content and never widen the page */}
      <div className="relative isolate flex min-w-0 flex-col overflow-hidden">
        {/* Two soft circles in the look's colour that drift slowly behind the form card. Decoration only. */}
        <span aria-hidden="true" className={cn('absolute top-1/4 -right-24 -z-10 size-72 rounded-full opacity-60 blur-3xl motion-safe:animate-blob', classes.glow)} />
        <span
          aria-hidden="true"
          className={cn('absolute bottom-16 -left-24 -z-10 size-64 rounded-full opacity-40 blur-3xl motion-safe:animate-blob', classes.glow)}
          style={{ animationDelay: '-7s' }}
        />

        <header className="flex items-center justify-between gap-4 px-4 py-4 sm:px-8 sm:py-6">
          <div className="flex min-w-0 items-center gap-3">
            <Logo />
            {panelName && <span className="hidden rounded-full bg-surface px-3 py-1 font-heading text-sm font-bold text-heading sm:inline">{panelName}</span>}
          </div>
          <Link
            to="/"
            aria-label="Back to the shop"
            className="group flex shrink-0 items-center gap-1.5 rounded-full border border-line px-3 py-2 text-sm font-bold text-heading transition-colors hover:border-brand hover:text-brand-text"
          >
            {/* The arrow steps to the left on hover (group-hover: when the mouse is over the link) */}
            <ArrowLeft className="size-4 transition-transform duration-200 group-hover:-translate-x-0.5" aria-hidden="true" />
            <span className="hidden sm:inline">Back to the shop</span>
            <span className="sm:hidden">Shop</span>
          </Link>
        </header>

        <main id="main" className="flex flex-1 justify-center px-4 pb-12 sm:items-center sm:px-8">
          <div className="w-full max-w-lg">
            {phoneBanner && (
              <div className={cn('mb-6 overflow-hidden rounded-3xl px-6 py-3 motion-safe:animate-rise lg:hidden', classes.banner)}>
                <Illustration className="mx-auto h-32 w-auto max-w-full motion-safe:animate-float" />
              </div>
            )}
            {/*
              The form's card. bg-card with rounded-3xl makes it a see-through pane of glass, and "bg-card border
              border-line" gives it the soft card shadow (both from styles/index.css). It rises in when the page opens.
            */}
            <div className="rounded-3xl border border-line bg-card p-6 motion-safe:animate-rise sm:p-8">{children}</div>
          </div>
        </main>

        <footer className="px-4 pb-6 text-center text-xs sm:px-8">© {THIS_YEAR} Fresh Grocery · Fresh produce from local stores</footer>
      </div>

      {/* ---------- The side panel: big screens only, on the left ---------- */}
      {/* sticky + the window's height (less its 0.75rem margin on each side): it stays in view while a long form scrolls */}
      <aside
        aria-labelledby={headlineId}
        className={cn(
          'relative m-3 hidden flex-col justify-between gap-10 overflow-hidden rounded-[2rem] p-10 lg:sticky lg:top-3 lg:order-first lg:flex lg:h-[calc(100dvh-1.5rem)] lg:self-start xl:p-14',
          classes.panel,
        )}
      >
        {/* A faint pattern of dots over the panel's colour (each dot is a tiny radial gradient, repeated every 22px) */}
        <span
          aria-hidden="true"
          className={cn(
            'absolute inset-0 bg-[radial-gradient(currentColor_1px,transparent_1px)] bg-size-[22px_22px] opacity-[0.08]',
            dark ? 'text-white' : 'text-heading',
          )}
        />
        {/* Soft blurred circles that drift slowly behind the illustration (a negative delay starts each at another point) */}
        <span aria-hidden="true" className={cn('absolute -top-24 -right-20 size-80 rounded-full blur-3xl motion-safe:animate-blob', classes.glow)} />
        <span
          aria-hidden="true"
          className={cn('absolute -bottom-32 -left-24 size-96 rounded-full blur-3xl motion-safe:animate-blob', classes.glow)}
          style={{ animationDelay: '-5s' }}
        />

        {/* The illustration takes the room the text leaves (min-h-0 lets it shrink on short screens) */}
        <div className="relative flex min-h-0 flex-1 items-center justify-center">
          <Illustration className="h-full max-h-[42vh] w-full max-w-lg motion-safe:animate-float" />
        </div>

        <div className="relative">
          <h2 id={headlineId} className={cn('text-3xl leading-tight motion-safe:animate-rise xl:text-4xl', dark && 'text-white')}>
            {headline}
          </h2>
          <p className={cn('mt-3 text-lg motion-safe:animate-rise', dark ? 'text-ink-text' : 'text-heading/80')} style={{ animationDelay: '100ms' }}>
            {text}
          </p>

          {/* The points come in one after the other: 120 ms apart, after the headline and the text */}
          <ul className="mt-8 space-y-3">
            {points.map(({ icon: Icon, text: point }, index) => (
              <li
                key={point}
                className={cn(
                  'flex items-center gap-3 rounded-2xl p-2 pr-4 font-heading font-bold ring-1 motion-safe:animate-rise',
                  dark ? 'bg-white/5 text-white ring-white/10' : 'bg-card/50 text-heading ring-card/70',
                )}
                style={{ animationDelay: `${250 + index * 120}ms` }}
              >
                <span className={cn('grid size-10 shrink-0 place-items-center rounded-xl shadow-sm', classes.pointIcon)}>
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                {point}
              </li>
            ))}
          </ul>
        </div>
      </aside>

      {/* These pages are outside the shop's and the panels' layouts, which scroll to the top on a new page */}
      <ScrollRestoration />
    </div>
  )
}
