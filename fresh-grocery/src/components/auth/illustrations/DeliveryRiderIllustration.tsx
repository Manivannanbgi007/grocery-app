/** A rider on a scooter, on the way from a store to a house: the delivery panel's login. Decoration only. */
export function DeliveryRiderIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 400 320" className={className} aria-hidden="true" focusable="false">
      {/* Backdrop and the shadow on the ground */}
      <circle cx="200" cy="160" r="140" className="fill-card" opacity="0.65" />
      <ellipse cx="210" cy="292" rx="150" ry="9" fill="#253d4e" opacity="0.08" />

      {/* The route: from the store's pin to the customer's pin */}
      <path d="M54 92C104 40 168 112 226 70S318 30 348 64" fill="none" stroke="#67bcee" strokeWidth="4" strokeLinecap="round" strokeDasharray="2 12" />
      <path className="fill-brand" d="M54 100c-12-14-18-24-18-32a18 18 0 0 1 36 0c0 8-6 18-18 32Z" />
      <circle cx="54" cy="68" r="7" fill="#fff" />
      <path d="M348 78c-12-14-18-24-18-32a18 18 0 0 1 36 0c0 8-6 18-18 32Z" fill="#f59758" />
      <path d="M340 48l8-7 8 7v8h-16Z" fill="#fff" />

      {/* Speed lines */}
      <path d="M36 206h44M22 228h58M40 250h40" stroke="#67bcee" strokeWidth="6" strokeLinecap="round" opacity="0.6" />

      {/* Wheels */}
      <circle cx="140" cy="262" r="26" fill="#253d4e" />
      <circle cx="140" cy="262" r="10" fill="#e7eaf3" />
      <circle cx="292" cy="262" r="26" fill="#253d4e" />
      <circle cx="292" cy="262" r="10" fill="#e7eaf3" />

      {/* The delivery box on its rack */}
      <rect x="96" y="196" width="58" height="8" rx="4" fill="#253d4e" />
      <rect x="90" y="138" width="64" height="60" rx="8" fill="#fdc040" />
      <rect x="86" y="132" width="72" height="14" rx="6" fill="#f2b32a" />
      <circle cx="122" cy="172" r="13" fill="#fff" />
      <path className="fill-brand" d="M115 179c-2-9 4-16 15-16 0 11-6 16-15 16Z" />

      {/* The scooter */}
      <path className="fill-brand" d="M108 244c0-24 18-38 44-38h58c8 0 12 6 10 14l-6 24Z" />
      <rect className="fill-brand-dark" x="206" y="236" width="62" height="14" rx="7" />
      <path className="stroke-brand" d="M262 246L246 158" strokeWidth="14" strokeLinecap="round" />
      <path className="stroke-brand" d="M262 256a30 30 0 0 1 60 0" fill="none" strokeWidth="10" strokeLinecap="round" />
      <circle cx="259" cy="184" r="7" fill="#fdc040" />
      <path d="M232 156h30" stroke="#253d4e" strokeWidth="8" strokeLinecap="round" />
      <rect x="138" y="196" width="66" height="14" rx="7" fill="#253d4e" />

      {/* The rider */}
      <path d="M180 198L226 200L234 238" fill="none" stroke="#253d4e" strokeWidth="16" strokeLinecap="round" strokeLinejoin="round" />
      <ellipse cx="240" cy="241" rx="11" ry="6" fill="#16252f" />
      <path d="M166 202c-6-30 4-56 30-62l18 6c6 26-2 48-14 56Z" fill="#67bcee" />
      <path d="M202 152l20 22 18-16" fill="none" stroke="#4aa6dc" strokeWidth="12" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="242" cy="157" r="6" fill="#f2c4a0" />
      <rect x="198" y="122" width="12" height="20" rx="4" fill="#f2c4a0" />
      <circle cx="206" cy="112" r="17" fill="#f2c4a0" />
      <circle cx="216" cy="118" r="2" fill="#253d4e" />
      <path className="fill-brand" d="M184 112a22 22 0 0 1 44 0Z" />
      <path d="M212 112h20" stroke="#253d4e" strokeWidth="5" strokeLinecap="round" />
    </svg>
  )
}
