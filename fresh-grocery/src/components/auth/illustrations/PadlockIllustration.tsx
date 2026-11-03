interface PadlockIllustrationProps {
  className?: string
  /** true once the new password is saved: the padlock opens and gets a check */
  open?: boolean
}

/** A padlock with a password, or open with a check: the password pages. Decoration only (hidden from screen readers). */
export function PadlockIllustration({ className, open = false }: PadlockIllustrationProps) {
  return (
    <svg viewBox="0 0 200 160" className={className} aria-hidden="true" focusable="false">
      <circle className="fill-brand-light" cx="100" cy="84" r="68" />
      <path d="M32 112Q32 120 40 120Q32 120 32 128Q32 120 24 120Q32 120 32 112Z" fill="#fdc040" />

      {/* The shackle: closed, or lifted out of the body on the left */}
      <path d={open ? 'M126 82V50a26 26 0 0 0-52 0v6' : 'M74 82V56a26 26 0 0 1 52 0v26'} fill="none" stroke="#9aa9b8" strokeWidth="13" strokeLinecap="round" />

      {/* The body with its keyhole */}
      <rect className="fill-brand" x="52" y="74" width="96" height="70" rx="14" />
      <rect x="62" y="84" width="8" height="40" rx="4" fill="#fff" opacity="0.25" />
      <circle className="fill-brand-dark" cx="100" cy="104" r="9" />
      <rect className="fill-brand-dark" x="96" y="108" width="8" height="18" rx="4" />

      {open ? (
        <>
          <circle cx="150" cy="70" r="18" fill="#fdc040" />
          <path d="M141 70l6 6 12-12" stroke="#253d4e" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </>
      ) : (
        <>
          <rect x="124" y="22" width="64" height="26" rx="13" fill="#253d4e" />
          {[140, 152, 164, 176].map((x) => (
            <circle key={x} cx={x} cy="35" r="4" fill="#fff" />
          ))}
        </>
      )}
    </svg>
  )
}
