import type { ReactNode } from 'react'

interface AuthHeadingProps {
  /** a short line above the title, e.g. "Welcome back" */
  eyebrow: string
  title: string
  /** what the page is for */
  children?: ReactNode
}

/** The top of a login or sign-up form: a small green pill with a pulsing dot, the page's title and a sentence. */
export function AuthHeading({ eyebrow, title, children }: AuthHeadingProps) {
  return (
    <div className="mb-6">
      <p className="inline-flex items-center gap-2 rounded-full bg-brand-light px-3 py-1 font-heading text-xs font-bold tracking-wide text-brand-text uppercase">
        {/* A small dot with a ring that keeps growing and fading (animate-ping). Decoration only. */}
        <span aria-hidden="true" className="relative flex size-2">
          <span className="absolute inline-flex size-full rounded-full bg-brand opacity-75 motion-safe:animate-ping" />
          <span className="relative inline-flex size-2 rounded-full bg-brand" />
        </span>
        {eyebrow}
      </p>
      <h1 className="mt-3 text-3xl sm:text-4xl">{title}</h1>
      {children && <div className="mt-2">{children}</div>}
    </div>
  )
}
