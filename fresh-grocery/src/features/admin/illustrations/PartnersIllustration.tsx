/** A partner's ID card with a "verified" seal, and a delivery helmet that drifts: the Delivery partners page. */
export function PartnersIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 240 180" className={className} aria-hidden="true" focusable="false">
      <circle cx="120" cy="92" r="82" fill="#fff" opacity="0.55" />

      {/* The ID card */}
      <rect x="44" y="46" width="134" height="94" rx="12" fill="#000" opacity="0.08" />
      <rect x="36" y="38" width="134" height="94" rx="12" fill="#fff" />
      <path d="M36 50a12 12 0 0 1 12-12h110a12 12 0 0 1 12 12v10H36Z" className="fill-sale" />
      <circle cx="70" cy="92" r="20" className="fill-sale-light" />
      <circle cx="70" cy="86" r="8" className="fill-sale-text" />
      <path d="M56 106a14 11 0 0 1 28 0Z" className="fill-sale-text" />
      <rect x="100" y="78" width="54" height="7" rx="3.5" fill="#253d4e" />
      <rect x="100" y="92" width="40" height="6" rx="3" fill="#d5dbe5" />
      <rect x="100" y="104" width="48" height="6" rx="3" fill="#d5dbe5" />

      {/* The "verified" seal */}
      <circle cx="166" cy="40" r="15" className="fill-brand" />
      <path d="M159 40l5 5 9-9" stroke="#fff" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />

      {/* A helmet that drifts */}
      <g className="motion-safe:animate-float" style={{ animationDelay: '-3s' }}>
        <path d="M150 164a36 32 0 0 1 72 0Z" className="fill-new" />
        <path d="M176 140h40a14 12 0 0 1 4 12h-44Z" fill="#253d4e" opacity="0.85" />
        <rect x="146" y="162" width="80" height="7" rx="3.5" className="fill-hot" />
      </g>
      <path d="M206 54l3 7 7 3-7 3-3 7-3-7-7-3 7-3Z" className="fill-accent" />
    </svg>
  )
}
